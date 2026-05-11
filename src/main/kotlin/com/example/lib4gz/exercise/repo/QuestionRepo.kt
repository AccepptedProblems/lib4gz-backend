package com.example.lib4gz.exercise.repo

import com.example.lib4gz.exercise.model.entity.Question
import org.springframework.data.jpa.repository.JpaRepository
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
}