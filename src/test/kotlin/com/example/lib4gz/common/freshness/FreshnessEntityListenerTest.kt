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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

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
    fun `unmapped entities never bump`() {
        assertNull(listener.toPendingBump(user))
        assertNull(listener.toPendingBump(null))
        assertNull(listener.toPendingBump("not-an-entity"))
        // The freshness entity itself must never bump — prevents recursion.
        assertNull(listener.toPendingBump(CourseResourceFreshness(courseId = "crs_1", resourceType = SYLLABUS)))
    }
}
