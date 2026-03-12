package com.example.lib4gz.courses.repo

import com.example.lib4gz.courses.model.entity.Lesson
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface LessonRepo : JpaRepository<Lesson, String> {

    fun findByModule_IdOrderByOrderIndexAsc(moduleId: String): List<Lesson>

    fun findByModule_Id(moduleId: String): List<Lesson>

    @Query("SELECT MAX(l.orderIndex) FROM Lesson l WHERE l.module.id = :moduleId")
    fun findMaxOrderIndexByModuleId(moduleId: String): Int?

    fun existsByModule_IdAndOrderIndex(moduleId: String, orderIndex: Int): Boolean

    fun countByModule_Id(moduleId: String): Long

    @Query("SELECT l FROM Lesson l WHERE l.module.course.id = :courseId")
    fun findByCourseId(courseId: String): List<Lesson>
}