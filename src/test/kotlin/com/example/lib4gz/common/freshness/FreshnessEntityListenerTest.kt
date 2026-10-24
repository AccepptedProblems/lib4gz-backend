package com.example.lib4gz.common.freshness

import com.example.lib4gz.auth.model.entity.User
import com.example.lib4gz.courses.model.entity.Course
import com.example.lib4gz.courses.model.entity.CourseResourceFreshness
import com.example.lib4gz.courses.model.entity.Enrollment
import com.example.lib4gz.courses.model.entity.EnrollmentRole
import com.example.lib4gz.courses.model.entity.FreshnessResourceType.EXERCISE
import com.example.lib4gz.courses.model.entity.FreshnessResourceType.LESSON
import com.example.lib4gz.courses.model.entity.FreshnessResourceType.SUBMISSION
import com.example.lib4gz.courses.model.entity.FreshnessResourceType.SUMMARY
import com.example.lib4gz.courses.model.entity.FreshnessResourceType.SYLLABUS
import com.example.lib4gz.courses.model.entity.Lesson
import com.example.lib4gz.courses.model.entity.Module
import com.example.lib4gz.courses.model.entity.Summary
import com.example.lib4gz.exercise.model.entity.Exercise
import com.example.lib4gz.exercise.model.entity.ExerciseType
import com.example.lib4gz.exercise.model.entity.Question
import com.example.lib4gz.exercise.model.entity.StudentAnswer
import com.example.lib4gz.exercise.model.entity.Submission
import com.example.lib4gz.exercise.model.entity.SubmissionStatus
import org.hibernate.action.spi.BeforeTransactionCompletionProcess
import org.hibernate.engine.spi.ActionQueue
import org.hibernate.event.spi.EventSource
import org.hibernate.event.spi.PostDeleteEvent
import org.hibernate.jdbc.Work
import org.hibernate.persister.entity.EntityPersister
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Savepoint

/**
 * Pins the declarative entity → (ref, resource types) map — the single place the
 * former "bump matrix" lives. If a payload gains or loses a denormalized field,
 * this map (and these tests) are what must change.
 */
class FreshnessEntityListenerTest {

    private val listener = FreshnessEntityListener()

    private val user = User(id = "usr_1", email = "e@e.e", username = "u", name = "U")
    private val course = Course(id = "crs_1", title = "C", createdBy = user)
    private val module = Module(id = "mod_1", course = course, title = "M", orderIndex = 0)
    private val lesson = Lesson(id = "les_1", module = module, title = "L", orderIndex = 0)
    private val exercise = Exercise(id = "exc_1", lesson = lesson, title = "E", type = ExerciseType.TEXT_ANSWER, orderIndex = 0)

    @Test
    fun `course maps to syllabus via its own id`() {
        val bump = listener.toPendingBump(course)!!
        assertEquals(FreshnessEntityListener.CourseRef("crs_1"), bump.ref)
        assertEquals(setOf(SYLLABUS), bump.types)
    }

    @Test
    fun `module and enrollment map to syllabus via the course ref`() {
        val moduleBump = listener.toPendingBump(module)!!
        assertEquals(FreshnessEntityListener.CourseRef("crs_1"), moduleBump.ref)
        assertEquals(setOf(SYLLABUS), moduleBump.types)

        val enrollment = Enrollment(course = course, user = user, role = EnrollmentRole.LEARNER)
        val enrollmentBump = listener.toPendingBump(enrollment)!!
        assertEquals(FreshnessEntityListener.CourseRef("crs_1"), enrollmentBump.ref)
        assertEquals(setOf(SYLLABUS), enrollmentBump.types)
    }

    @Test
    fun `lesson maps to LESSON and SYLLABUS`() {
        val bump = listener.toPendingBump(lesson)!!
        assertEquals(FreshnessEntityListener.ModuleRef("mod_1"), bump.ref)
        assertEquals(setOf(LESSON, SYLLABUS), bump.types)
    }

    @Test
    fun `summary maps to SUMMARY, LESSON and SYLLABUS`() {
        val summary = Summary(lesson = lesson, content = "text", editedBy = user)
        val bump = listener.toPendingBump(summary)!!
        assertEquals(FreshnessEntityListener.LessonRef("les_1"), bump.ref)
        assertEquals(setOf(SUMMARY, LESSON, SYLLABUS), bump.types)
    }

    @Test
    fun `exercise maps to EXERCISE, LESSON and SYLLABUS`() {
        val bump = listener.toPendingBump(exercise)!!
        assertEquals(FreshnessEntityListener.LessonRef("les_1"), bump.ref)
        assertEquals(setOf(EXERCISE, LESSON, SYLLABUS), bump.types)
    }

    @Test
    fun `question maps to EXERCISE and SUBMISSION via its exerciseId column`() {
        val question = Question(exerciseId = "exc_1", content = "q", orderIndex = 0)
        val bump = listener.toPendingBump(question)!!
        assertEquals(FreshnessEntityListener.ExerciseRef("exc_1"), bump.ref)
        assertEquals(setOf(EXERCISE, SUBMISSION), bump.types)
    }

    @Test
    fun `draft submission never bumps - the autosave rule`() {
        val draft = Submission(exercise = exercise, user = user, status = SubmissionStatus.DRAFT)
        assertNull(listener.toPendingBump(draft))
    }

    @Test
    fun `non-draft submission bumps SUBMISSION`() {
        for (status in listOf(SubmissionStatus.SUBMITTED, SubmissionStatus.APPROVED, SubmissionStatus.NEEDS_REVISION)) {
            val bump = listener.toPendingBump(Submission(exercise = exercise, user = user, status = status))!!
            assertEquals(FreshnessEntityListener.ExerciseRef("exc_1"), bump.ref)
            assertEquals(setOf(SUBMISSION), bump.types)
        }
    }

    @Test
    fun `student answer defers the draft check to the submission ref`() {
        val submission = Submission(id = "sub_1", exercise = exercise, user = user)
        val answer = StudentAnswer(
            question = Question(exerciseId = "exc_1", content = "q", orderIndex = 0),
            submission = submission
        )
        val bump = listener.toPendingBump(answer)!!
        assertEquals(FreshnessEntityListener.SubmissionRef("sub_1"), bump.ref)
        assertEquals(setOf(SUBMISSION), bump.types)
    }

    @Test
    fun `a course, module or lesson delete bumps every type of its subtree`() {
        val subtree = setOf(SYLLABUS, LESSON, SUMMARY, EXERCISE, SUBMISSION)
        assertEquals(subtree, listener.toPendingBump(course, deleted = true)!!.types)
        assertEquals(subtree, listener.toPendingBump(module, deleted = true)!!.types)
        assertEquals(subtree, listener.toPendingBump(lesson, deleted = true)!!.types)
        assertEquals(FreshnessEntityListener.ModuleRef("mod_1"), listener.toPendingBump(lesson, deleted = true)!!.ref)
    }

    @Test
    fun `an exercise delete also bumps SUBMISSION`() {
        val bump = listener.toPendingBump(exercise, deleted = true)!!
        assertEquals(FreshnessEntityListener.LessonRef("les_1"), bump.ref)
        assertEquals(setOf(EXERCISE, LESSON, SYLLABUS, SUBMISSION), bump.types)
    }

    /**
     * The lesson's exercises are hidden by a bulk update, which fires no event, so the lesson's own
     * delete event must carry their tokens. Runs the listener end to end against a mocked session.
     */
    @Test
    fun `deleting a lesson bumps the exercise and submission tokens of its course`() {
        val actionQueue = mock(ActionQueue::class.java)
        val session = mock(EventSource::class.java)
        `when`(session.actionQueue).thenReturn(actionQueue)

        val bumped = mutableSetOf<String>()
        val upsert = mock(PreparedStatement::class.java)
        doAnswer { bumped += it.arguments[1] as String; null }.`when`(upsert).setString(org.mockito.ArgumentMatchers.eq(3), anyString())
        val courseOfModule = mock(PreparedStatement::class.java)
        val row = mock(ResultSet::class.java)
        `when`(row.next()).thenReturn(true)
        `when`(row.getString(anyInt())).thenReturn("crs_1")
        `when`(courseOfModule.executeQuery()).thenReturn(row)
        val connection = mock(Connection::class.java)
        `when`(connection.prepareStatement(anyString())).thenAnswer {
            if ((it.arguments[0] as String).contains("INSERT INTO course_resource_freshness")) upsert else courseOfModule
        }
        doAnswer { (it.arguments[0] as Work).execute(connection); null }.`when`(session).doWork(any(Work::class.java))

        listener.onPostDelete(PostDeleteEvent(lesson, "les_1", null, mock(EntityPersister::class.java), session))

        val beforeCommit = ArgumentCaptor.forClass(BeforeTransactionCompletionProcess::class.java)
        verify(actionQueue).registerProcess(beforeCommit.capture())
        beforeCommit.value.doBeforeTransactionCompletion(session)

        verify(courseOfModule).setString(1, "mod_1")
        assertEquals(setOf("SYLLABUS", "LESSON", "SUMMARY", "EXERCISE", "SUBMISSION"), bumped)
    }

    /**
     * A failed statement aborts a Postgres transaction, and the commit would then drop the write
     * without an error. A failed bump must be undone on its own, back to its savepoint.
     */
    @Test
    fun `a failed bump rolls back to its savepoint and leaves the write alone`() {
        val actionQueue = mock(ActionQueue::class.java)
        val session = mock(EventSource::class.java)
        `when`(session.actionQueue).thenReturn(actionQueue)
        val savepoint = mock(Savepoint::class.java)
        val upsert = mock(PreparedStatement::class.java)
        `when`(upsert.executeBatch()).thenThrow(SQLException("check constraint violated", "23514"))
        val connection = mock(Connection::class.java)
        `when`(connection.setSavepoint()).thenReturn(savepoint)
        `when`(connection.prepareStatement(anyString())).thenReturn(upsert)
        doAnswer { (it.arguments[0] as Work).execute(connection); null }.`when`(session).doWork(any(Work::class.java))

        listener.onPostDelete(PostDeleteEvent(course, "crs_1", null, mock(EntityPersister::class.java), session))
        val beforeCommit = ArgumentCaptor.forClass(BeforeTransactionCompletionProcess::class.java)
        verify(actionQueue).registerProcess(beforeCommit.capture())
        beforeCommit.value.doBeforeTransactionCompletion(session)

        verify(connection).rollback(savepoint)
        verify(connection, never()).releaseSavepoint(savepoint)
    }

    @Test
    fun `unmapped entities never bump`() {
        assertNull(listener.toPendingBump(user))
        assertNull(listener.toPendingBump(null))
        assertNull(listener.toPendingBump("not-an-entity"))
        // The freshness entity itself must never bump — prevents recursion.
        assertNull(listener.toPendingBump(CourseResourceFreshness(courseId = "crs_1", resourceType = SYLLABUS)))
    }
}
