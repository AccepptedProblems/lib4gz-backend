package com.example.lib4gz.courses.repo

import com.example.lib4gz.courses.model.entity.Module
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ModuleRepo : JpaRepository<Module, String> {

    fun findByCourse_IdOrderByOrderIndexAscCreatedAtAsc(courseId: String): List<Module>

    fun findByCourse_Id(courseId: String): List<Module>

    @Query("SELECT MAX(m.orderIndex) FROM Module m WHERE m.course.id = :courseId")
    fun findMaxOrderIndexByCourseId(courseId: String): Int?

    fun countByCourse_Id(courseId: String): Long

    /**
     * Hides a course's modules for the course delete. `NOW()` is the transaction's start, the same
     * value the course's own `@SQLDelete` writes, so one timestamp finds the whole delete.
     */
    @Modifying
    @Query(
        "UPDATE modules SET deleted_at = CAST(EXTRACT(EPOCH FROM NOW()) * 1000 AS bigint) " +
            "WHERE course_id = :courseId AND deleted_at IS NULL",
        nativeQuery = true
    )
    fun hideByCourseId(courseId: String): Int
}