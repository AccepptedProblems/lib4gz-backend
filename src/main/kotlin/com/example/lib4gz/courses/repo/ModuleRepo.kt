package com.example.lib4gz.courses.repo

import com.example.lib4gz.courses.model.entity.Module
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ModuleRepo : JpaRepository<Module, String> {

    fun findByCourse_IdOrderByOrderIndexAsc(courseId: String): List<Module>

    fun findByCourse_Id(courseId: String): List<Module>

    @Query("SELECT MAX(m.orderIndex) FROM Module m WHERE m.course.id = :courseId")
    fun findMaxOrderIndexByCourseId(courseId: String): Int?

    fun existsByCourse_IdAndOrderIndex(courseId: String, orderIndex: Int): Boolean

    fun countByCourse_Id(courseId: String): Long
}