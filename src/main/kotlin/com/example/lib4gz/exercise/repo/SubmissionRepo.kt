package com.example.lib4gz.exercise.repo

import com.example.lib4gz.exercise.model.entity.Submission
import com.example.lib4gz.exercise.model.entity.SubmissionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface SubmissionRepo : JpaRepository<Submission, String> {

    fun findByExercise_Id(exerciseId: String): List<Submission>

    fun findByUser_Id(userId: String): List<Submission>

    fun findByExercise_IdAndUser_Id(exerciseId: String, userId: String): Submission?

    fun existsByExercise_IdAndUser_Id(exerciseId: String, userId: String): Boolean

    fun findByExercise_IdAndStatus(exerciseId: String, status: SubmissionStatus): List<Submission>

    fun findByUser_IdAndStatus(userId: String, status: SubmissionStatus): List<Submission>

    @Query("SELECT s FROM Submission s WHERE s.exercise.lesson.module.course.id = :courseId")
    fun findByCourseId(courseId: String): List<Submission>

    @Query("SELECT s FROM Submission s WHERE s.exercise.lesson.module.course.id = :courseId AND s.user.id = :userId")
    fun findByCourseIdAndUserId(courseId: String, userId: String): List<Submission>

    fun countByExercise_IdAndStatus(exerciseId: String, status: SubmissionStatus): Long

    /**
     * Batched lookups used by aggregate endpoints to avoid N+1 fan-out.
     */
    fun findByExercise_IdIn(exerciseIds: Collection<String>): List<Submission>

    fun findByExercise_IdInAndUser_Id(exerciseIds: Collection<String>, userId: String): List<Submission>

    @Query(
        "SELECT s FROM Submission s JOIN FETCH s.user WHERE s.exercise.id IN :exerciseIds"
    )
    fun findByExerciseIdInFetchUser(exerciseIds: Collection<String>): List<Submission>

    @Query("SELECT s FROM Submission s WHERE s.exercise.lesson.id = :lessonId")
    fun findByLessonId(lessonId: String): List<Submission>

    /**
     * Per-lesson count of the user's "done" exercises (one submission per
     * exercise per user is enforced by the unique constraint, so COUNT(s) is
     * the number of distinct exercises done in that lesson).
     */
    @Query(
        "SELECT new com.example.lib4gz.exercise.repo.LessonCountRow(" +
                "s.exercise.lesson.module.course.id, s.exercise.lesson.id, COUNT(s)) " +
                "FROM Submission s WHERE s.user.id = :userId AND s.status IN :statuses " +
                "AND s.exercise.lesson.module.course.id IN :courseIds " +
                "GROUP BY s.exercise.lesson.module.course.id, s.exercise.lesson.id"
    )
    fun countDonePerLessonByCourseIds(
        courseIds: Collection<String>,
        userId: String,
        statuses: Collection<SubmissionStatus>
    ): List<LessonCountRow>
}