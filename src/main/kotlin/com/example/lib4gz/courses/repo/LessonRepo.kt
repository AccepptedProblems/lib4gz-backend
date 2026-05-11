package com.example.lib4gz.courses.repo

import com.example.lib4gz.courses.model.entity.Lesson
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * Lightweight projection used by aggregate endpoints to authorize on a lesson
 * without hydrating the lesson, module, course, or creator entities. Reads only
 * the FK columns already present on the joined rows.
 */
data class LessonCourseAuthView(val courseId: String, val creatorId: String)

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

    @Query(
        "SELECT new com.example.lib4gz.courses.repo.LessonCourseAuthView(c.id, c.createdBy.id) " +
                "FROM Lesson l JOIN l.module m JOIN m.course c WHERE l.id = :lessonId"
    )
    fun findCourseAuthByLessonId(lessonId: String): LessonCourseAuthView?
}