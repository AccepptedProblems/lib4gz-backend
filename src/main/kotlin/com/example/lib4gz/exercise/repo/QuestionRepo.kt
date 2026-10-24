package com.example.lib4gz.exercise.repo

import com.example.lib4gz.exercise.model.entity.Question
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface QuestionRepo : JpaRepository<Question, String> {

    fun findByExerciseIdOrderByOrderIndexAsc(exerciseId: String): List<Question>

    fun findByExerciseId(exerciseId: String): List<Question>

    @Query("SELECT MAX(q.orderIndex) FROM Question q WHERE q.exerciseId = :exerciseId")
    fun findMaxOrderIndexByExerciseId(exerciseId: String): Int?

    fun existsByExerciseIdAndOrderIndex(exerciseId: String, orderIndex: Int): Boolean

    fun countByExerciseId(exerciseId: String): Long

    fun findByExerciseIdAndVisibility(exerciseId: String, visibility: com.example.lib4gz.exercise.model.entity.QuestionVisibility): List<Question>

    /**
     * Batched fetch for aggregate endpoints to avoid N+1 fan-out across many exercises.
     */
    fun findByExerciseIdInOrderByOrderIndexAsc(exerciseIds: Collection<String>): List<Question>

    // The hide* updates serve a parent's delete. `NOW()` is the transaction's start, the same value
    // the parent's own `@SQLDelete` writes, so one timestamp finds the whole delete.

    @Modifying
    @Query(
        "UPDATE questions SET deleted_at = CAST(EXTRACT(EPOCH FROM NOW()) * 1000 AS bigint) " +
            "WHERE exercise_id = :exerciseId AND deleted_at IS NULL",
        nativeQuery = true
    )
    fun hideByExerciseId(exerciseId: String): Int

    @Modifying
    @Query(
        "UPDATE questions SET deleted_at = CAST(EXTRACT(EPOCH FROM NOW()) * 1000 AS bigint) " +
            "WHERE deleted_at IS NULL AND exercise_id IN (SELECT id FROM exercises WHERE lesson_id = :lessonId)",
        nativeQuery = true
    )
    fun hideByLessonId(lessonId: String): Int

    @Modifying
    @Query(
        "UPDATE questions SET deleted_at = CAST(EXTRACT(EPOCH FROM NOW()) * 1000 AS bigint) " +
            "WHERE deleted_at IS NULL AND exercise_id IN (" +
            "SELECT e.id FROM exercises e JOIN lessons l ON e.lesson_id = l.id WHERE l.module_id = :moduleId)",
        nativeQuery = true
    )
    fun hideByModuleId(moduleId: String): Int

    @Modifying
    @Query(
        "UPDATE questions SET deleted_at = CAST(EXTRACT(EPOCH FROM NOW()) * 1000 AS bigint) " +
            "WHERE deleted_at IS NULL AND exercise_id IN (" +
            "SELECT e.id FROM exercises e JOIN lessons l ON e.lesson_id = l.id " +
            "JOIN modules m ON l.module_id = m.id WHERE m.course_id = :courseId)",
        nativeQuery = true
    )
    fun hideByCourseId(courseId: String): Int
}