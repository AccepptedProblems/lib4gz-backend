package com.example.lib4gz.race.repo

import com.example.lib4gz.race.model.entity.RaceQuestion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface RaceQuestionRepo : JpaRepository<RaceQuestion, String> {

    fun findByCourseIdOrderByOrderIndexAscCreatedAtAsc(courseId: String): List<RaceQuestion>

    fun findByCourseId(courseId: String): List<RaceQuestion>

    fun findByCourseIdAndModuleId(courseId: String, moduleId: String): List<RaceQuestion>

    // The hide* updates serve a parent's delete. `NOW()` is the transaction's start, the same value
    // the parent's own `@SQLDelete` writes, so one timestamp finds the whole delete.

    @Modifying
    @Query(
        "UPDATE race_questions SET deleted_at = CAST(EXTRACT(EPOCH FROM NOW()) * 1000 AS bigint) " +
            "WHERE module_id = :moduleId AND deleted_at IS NULL",
        nativeQuery = true
    )
    fun hideByModuleId(moduleId: String): Int

    @Modifying
    @Query(
        "UPDATE race_questions SET deleted_at = CAST(EXTRACT(EPOCH FROM NOW()) * 1000 AS bigint) " +
            "WHERE course_id = :courseId AND deleted_at IS NULL",
        nativeQuery = true
    )
    fun hideByCourseId(courseId: String): Int
}
