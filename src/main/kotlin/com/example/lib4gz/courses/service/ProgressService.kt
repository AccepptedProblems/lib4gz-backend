package com.example.lib4gz.courses.service

import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.courses.model.entity.Enrollment
import com.example.lib4gz.courses.model.payload.CourseProgress
import com.example.lib4gz.courses.repo.EnrollmentRepo
import com.example.lib4gz.courses.repo.LessonRepo
import com.example.lib4gz.exercise.model.entity.SubmissionStatus
import com.example.lib4gz.exercise.repo.ExerciseRepo
import com.example.lib4gz.exercise.repo.SubmissionRepo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * Per-caller course progress, derived from submissions rather than stored:
 * a lesson is "done" when every one of its exercises has a SUBMITTED or APPROVED
 * submission from the caller. Lessons without exercises don't participate.
 * Deriving (instead of persisting completion rows) keeps progress retroactively
 * correct and impossible to drift from the underlying submissions.
 *
 * The only persisted state is resume position (`Enrollment.lastVisitedLessonId`),
 * written by [recordVisit].
 *
 * The `compute*` functions are synchronous on purpose: they are called from
 * inside other services' `Mono.fromCallable { }` blocks (SyllabusService,
 * CourseService) and issue a fixed number of grouped queries regardless of
 * course count.
 */
/** Per-lesson submission state for the caller: done vs total exercises. */
data class LessonCompletion(val doneExercises: Int, val totalExercises: Int) {
    val completed: Boolean get() = totalExercises > 0 && doneExercises >= totalExercises
    val partial: Boolean get() = doneExercises in 1 until totalExercises
}

interface ProgressService {

    fun recordVisit(lessonId: String, userId: String): Mono<Void>

    /** Batched: one CourseProgress per enrolled course id, keyed by course id. */
    fun computeProgressForCourses(
        courseIds: List<String>,
        userId: String,
        enrollmentByCourseId: Map<String, Enrollment>
    ): Map<String, CourseProgress>

    /**
     * Single-course, per-lesson view, only for exercise-bearing lessons
     * (absent key = lesson has no exercises = no completion concept).
     */
    fun computeLessonCompletion(courseId: String, userId: String): Map<String, LessonCompletion>
}

@Service
class ProgressServiceImpl(
    private val lessonRepo: LessonRepo,
    private val enrollmentRepo: EnrollmentRepo,
    private val exerciseRepo: ExerciseRepo,
    private val submissionRepo: SubmissionRepo
) : ProgressService {

    companion object {
        private val DONE_STATUSES = listOf(SubmissionStatus.SUBMITTED, SubmissionStatus.APPROVED)
    }

    @Transactional
    override fun recordVisit(lessonId: String, userId: String): Mono<Void> {
        return Mono.fromCallable {
            val authView = lessonRepo.findCourseAuthByLessonId(lessonId)
                ?: throw ResourceNotFoundException("Lesson not found with id: $lessonId")

            // No active enrollment (e.g. a browsing non-enrolled visitor on a
            // PUBLIC course): nothing to resume — a silent no-op, not an error,
            // because the client fires this on every lesson open.
            val enrollment = enrollmentRepo.findActiveEnrollment(authView.courseId, userId)
            if (enrollment != null) {
                enrollment.recordVisit(lessonId)
                enrollmentRepo.save(enrollment)
            }
        }.subscribeOn(Schedulers.boundedElastic()).then()
    }

    @Transactional(readOnly = true)
    override fun computeProgressForCourses(
        courseIds: List<String>,
        userId: String,
        enrollmentByCourseId: Map<String, Enrollment>
    ): Map<String, CourseProgress> {
        if (courseIds.isEmpty()) return emptyMap()

        // lessonId → exercise count, grouped per course
        val totals = exerciseRepo.countPerLessonByCourseIds(courseIds)
            .groupBy({ it.courseId }, { it.lessonId to it.count })
        val done = submissionRepo.countDonePerLessonByCourseIds(courseIds, userId, DONE_STATUSES)
            .groupBy({ it.courseId }, { it.lessonId to it.count })

        // Resolve last-visited lesson titles in one batch; @Where on Lesson
        // filters soft-deleted ones, which then simply resolve to no title/id.
        val lastVisitedIds = enrollmentByCourseId.values.mapNotNull { it.lastVisitedLessonId }
        val lessonTitleById: Map<String, String> =
            if (lastVisitedIds.isEmpty()) emptyMap()
            else lessonRepo.findAllById(lastVisitedIds).associate { it.id to it.title }

        return courseIds.associateWith { courseId ->
            val totalByLesson = totals[courseId].orEmpty().toMap()
            val doneByLesson = done[courseId].orEmpty().toMap()
            val completedLessons = totalByLesson.count { (lessonId, total) ->
                (doneByLesson[lessonId] ?: 0L) >= total
            }

            val enrollment = enrollmentByCourseId[courseId]
            val visitedId = enrollment?.lastVisitedLessonId
            val visitedTitle = visitedId?.let { lessonTitleById[it] }

            CourseProgress(
                completedLessons = completedLessons,
                totalLessons = totalByLesson.size,
                // A deleted lesson leaves a dangling id — hide it rather than
                // deep-linking the client to a 404.
                lastVisitedLessonId = if (visitedTitle != null) visitedId else null,
                lastVisitedLessonTitle = visitedTitle,
                lastVisitedAt = if (visitedTitle != null) enrollment?.lastVisitedAt else null
            )
        }
    }

    @Transactional(readOnly = true)
    override fun computeLessonCompletion(courseId: String, userId: String): Map<String, LessonCompletion> {
        val courseIds = listOf(courseId)
        val totalByLesson = exerciseRepo.countPerLessonByCourseIds(courseIds)
            .associate { it.lessonId to it.count }
        val doneByLesson = submissionRepo.countDonePerLessonByCourseIds(courseIds, userId, DONE_STATUSES)
            .associate { it.lessonId to it.count }

        return totalByLesson.mapValues { (lessonId, total) ->
            LessonCompletion(
                doneExercises = (doneByLesson[lessonId] ?: 0L).toInt(),
                totalExercises = total.toInt()
            )
        }
    }
}
