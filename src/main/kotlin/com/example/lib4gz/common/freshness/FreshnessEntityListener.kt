package com.example.lib4gz.common.freshness

import com.example.lib4gz.common.utils.IdGenerator
import com.example.lib4gz.courses.model.entity.Course
import com.example.lib4gz.courses.model.entity.Enrollment
import com.example.lib4gz.courses.model.entity.FreshnessResourceType
import com.example.lib4gz.courses.model.entity.Lesson
import com.example.lib4gz.courses.model.entity.Module
import com.example.lib4gz.courses.model.entity.Summary
import com.example.lib4gz.exercise.model.entity.Exercise
import com.example.lib4gz.exercise.model.entity.Question
import com.example.lib4gz.exercise.model.entity.StudentAnswer
import com.example.lib4gz.exercise.model.entity.Submission
import com.example.lib4gz.exercise.model.entity.SubmissionStatus
import org.hibernate.action.spi.AfterTransactionCompletionProcess
import org.hibernate.action.spi.BeforeTransactionCompletionProcess
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.event.spi.EventSource
import org.hibernate.event.spi.PostDeleteEvent
import org.hibernate.event.spi.PostDeleteEventListener
import org.hibernate.event.spi.PostInsertEvent
import org.hibernate.event.spi.PostInsertEventListener
import org.hibernate.event.spi.PostUpdateEvent
import org.hibernate.event.spi.PostUpdateEventListener
import org.hibernate.persister.entity.EntityPersister
import org.hibernate.proxy.HibernateProxy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.sql.Connection
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Maintains the `course_resource_freshness` table (client-cache invalidation tokens)
 * automatically from Hibernate entity events, so no feature service ever has to
 * remember to "bump" a token.
 *
 * Flow: entity write → post-insert/update/delete event (fired during flush) →
 * declarative entity→resource-types map ([toPendingBump]) → id extraction only, never
 * proxy initialization or DB access at event time → transaction-scoped buffer →
 * before-transaction-completion: resolve ids to courseId and batch-upsert via plain
 * JDBC on the transaction's own connection.
 *
 * Why Hibernate's ActionQueue processes and not Spring's TransactionSynchronization:
 * services in this codebase never flush early, so entity events fire during the
 * COMMIT-TIME flush — which happens *after* Spring has already invoked its
 * beforeCommit/beforeCompletion synchronizations. A synchronization registered from an
 * event here would never run. `ActionQueue.registerProcess` callbacks are Hibernate's
 * own post-flush / pre-commit phase (the mechanism Envers uses), inside the same
 * transaction, so bumps commit or roll back atomically with the mutation.
 *
 * Why plain SQL for resolution: it deliberately bypasses the `@Where(deleted_at IS
 * NULL)` soft-delete filters, so a soft-deleted lesson/module/exercise still resolves
 * to its course and the deletion itself invalidates client caches.
 *
 * Never-throw guarantee: a freshness bump is a cache hint. Any failure here is logged
 * at WARN and swallowed — it must never fail the domain transaction. Worst case is one
 * missed invalidation until the next real mutation.
 */
@Component
class FreshnessEntityListener :
    PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    private val log = LoggerFactory.getLogger(FreshnessEntityListener::class.java)

    /** Per-transaction buffer, keyed by session identity; removed after completion. */
    private val pendingBySession =
        ConcurrentHashMap<SharedSessionContractImplementor, MutableSet<PendingBump>>()

    /**
     * moduleId/lessonId/exerciseId → courseId. These relationships are immutable
     * (content never moves between courses) and ids are prefix-typed, so one map is
     * safe. Submission resolution is NOT cached — its query also checks live status.
     */
    private val courseIdCache = ConcurrentHashMap<String, String>()

    override fun requiresPostCommitHandling(persister: EntityPersister): Boolean = false

    override fun onPostInsert(event: PostInsertEvent) = collect(event.session, event.entity)

    override fun onPostUpdate(event: PostUpdateEvent) = collect(event.session, event.entity)

    override fun onPostDelete(event: PostDeleteEvent) = collect(event.session, event.entity)

    private fun collect(session: EventSource, entity: Any?) {
        try {
            val bump = toPendingBump(entity) ?: return
            pendingBySession.computeIfAbsent(session) {
                session.actionQueue.registerProcess(
                    BeforeTransactionCompletionProcess { s -> flushBumps(s) }
                )
                session.actionQueue.registerProcess(
                    AfterTransactionCompletionProcess { _, s -> pendingBySession.remove(s) }
                )
                ConcurrentHashMap.newKeySet()
            }.add(bump)
        } catch (e: Exception) {
            log.warn("Freshness bump collection failed for {}; skipping", entity?.javaClass?.simpleName, e)
        }
    }

    /**
     * The declarative map: which resource-type tokens an entity write invalidates, and
     * how to locate the owning course. Cross-type entries exist because aggregate
     * payloads embed denormalized fields (syllabus embeds lessons and enrollment info;
     * lesson payloads embed hasSummary/exerciseCount; the teacher submissions tab
     * expands questions).
     *
     * DRAFT rule: a student's 2-second autosave writes DRAFT submissions/answers and
     * must not thrash the teacher's submissions cache. Submission status is checked
     * directly on the entity; StudentAnswer defers the check to resolution SQL because
     * reading its submission proxy at event time could trigger a load mid-flush.
     */
    internal fun toPendingBump(entity: Any?): PendingBump? = when (entity) {
        is Course -> PendingBump(CourseRef(entity.id), SYLLABUS_ONLY)
        is Module -> idOf(entity.course)?.let { PendingBump(CourseRef(it), SYLLABUS_ONLY) }
        // Course ref captured now: enrollments are hard-deleted, so the row is gone
        // by resolution time.
        is Enrollment -> idOf(entity.course)?.let { PendingBump(CourseRef(it), SYLLABUS_ONLY) }
        is Lesson -> idOf(entity.module)?.let {
            PendingBump(ModuleRef(it), setOf(FreshnessResourceType.LESSON, FreshnessResourceType.SYLLABUS))
        }
        is Summary -> idOf(entity.lesson)?.let {
            PendingBump(
                LessonRef(it),
                setOf(FreshnessResourceType.SUMMARY, FreshnessResourceType.LESSON, FreshnessResourceType.SYLLABUS)
            )
        }
        is Exercise -> idOf(entity.lesson)?.let {
            PendingBump(
                LessonRef(it),
                setOf(FreshnessResourceType.EXERCISE, FreshnessResourceType.LESSON, FreshnessResourceType.SYLLABUS)
            )
        }
        is Question -> PendingBump(
            ExerciseRef(entity.exerciseId),
            setOf(FreshnessResourceType.EXERCISE, FreshnessResourceType.SUBMISSION)
        )
        is Submission ->
            if (entity.status == SubmissionStatus.DRAFT) null
            else idOf(entity.exercise)?.let { PendingBump(ExerciseRef(it), SUBMISSION_ONLY) }
        is StudentAnswer -> idOf(entity.submission)?.let { PendingBump(SubmissionRef(it), SUBMISSION_ONLY) }
        else -> null
    }

    /** Extract an association's id without initializing a lazy proxy. */
    private fun idOf(association: Any?): String? = when (association) {
        null -> null
        is HibernateProxy -> association.hibernateLazyInitializer.identifier as? String
        is Course -> association.id
        is Module -> association.id
        is Lesson -> association.id
        is Exercise -> association.id
        is Submission -> association.id
        else -> null
    }

    /** Post-flush, pre-commit, same transaction: resolve courseIds and upsert tokens. */
    private fun flushBumps(session: SharedSessionContractImplementor) {
        val pending = pendingBySession[session] ?: return
        if (pending.isEmpty()) return
        try {
            (session as org.hibernate.Session).doWork { connection ->
                val bumps = HashSet<Pair<String, FreshnessResourceType>>()
                for (p in pending) {
                    val courseId = resolveCourseId(connection, p.ref) ?: continue
                    p.types.forEach { bumps += courseId to it }
                }
                if (bumps.isEmpty()) return@doWork

                val now = Instant.now().toEpochMilli()
                connection.prepareStatement(UPSERT_SQL).use { ps ->
                    for ((courseId, type) in bumps) {
                        ps.setString(1, IdGenerator.generate("frs"))
                        ps.setString(2, courseId)
                        ps.setString(3, type.name)
                        ps.setLong(4, now)
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
            }
        } catch (e: Exception) {
            log.warn("Freshness bump flush failed; client caches may serve one stale read", e)
        }
    }

    private fun resolveCourseId(connection: Connection, ref: FreshnessRef): String? = when (ref) {
        is CourseRef -> ref.courseId
        is ModuleRef -> courseIdCache[ref.moduleId]
            ?: queryCourseId(connection, MODULE_COURSE_SQL, ref.moduleId)
                ?.also { courseIdCache[ref.moduleId] = it }
        is LessonRef -> courseIdCache[ref.lessonId]
            ?: queryCourseId(connection, LESSON_COURSE_SQL, ref.lessonId)
                ?.also { courseIdCache[ref.lessonId] = it }
        is ExerciseRef -> courseIdCache[ref.exerciseId]
            ?: queryCourseId(connection, EXERCISE_COURSE_SQL, ref.exerciseId)
                ?.also { courseIdCache[ref.exerciseId] = it }
        // Not cached: the query also enforces the live DRAFT filter.
        is SubmissionRef -> queryCourseId(connection, SUBMISSION_COURSE_SQL, ref.submissionId)
    }

    private fun queryCourseId(connection: Connection, sql: String, id: String): String? =
        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }

    internal data class PendingBump(val ref: FreshnessRef, val types: Set<FreshnessResourceType>)

    internal sealed interface FreshnessRef
    internal data class CourseRef(val courseId: String) : FreshnessRef
    internal data class ModuleRef(val moduleId: String) : FreshnessRef
    internal data class LessonRef(val lessonId: String) : FreshnessRef
    internal data class ExerciseRef(val exerciseId: String) : FreshnessRef
    internal data class SubmissionRef(val submissionId: String) : FreshnessRef

    companion object {
        private val SYLLABUS_ONLY = setOf(FreshnessResourceType.SYLLABUS)
        private val SUBMISSION_ONLY = setOf(FreshnessResourceType.SUBMISSION)

        private const val UPSERT_SQL = """
            INSERT INTO course_resource_freshness (id, course_id, resource_type, last_update)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (course_id, resource_type)
            DO UPDATE SET last_update = EXCLUDED.last_update
        """

        private const val MODULE_COURSE_SQL =
            "SELECT course_id FROM modules WHERE id = ?"

        private const val LESSON_COURSE_SQL =
            "SELECT m.course_id FROM lessons l JOIN modules m ON l.module_id = m.id WHERE l.id = ?"

        private const val EXERCISE_COURSE_SQL =
            "SELECT m.course_id FROM exercises e " +
                "JOIN lessons l ON e.lesson_id = l.id " +
                "JOIN modules m ON l.module_id = m.id WHERE e.id = ?"

        private const val SUBMISSION_COURSE_SQL =
            "SELECT m.course_id FROM submissions s " +
                "JOIN exercises e ON s.exercise_id = e.id " +
                "JOIN lessons l ON e.lesson_id = l.id " +
                "JOIN modules m ON l.module_id = m.id " +
                "WHERE s.id = ? AND s.status <> 'DRAFT'"
    }
}
