package com.example.lib4gz.courses.repo

import com.example.lib4gz.courses.model.entity.Summary
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface SummaryRepo : JpaRepository<Summary, String> {

    fun findByLesson_Id(lessonId: String): Summary?

    fun existsByLesson_Id(lessonId: String): Boolean

    fun deleteByLesson_Id(lessonId: String)

    /**
     * Returns the IDs of lessons that have a summary, scoped to one course.
     * Single query (no N+1) used by the syllabus aggregate to populate `hasSummary`.
     */
    @Query("SELECT s.lesson.id FROM Summary s WHERE s.lesson.module.course.id = :courseId")
    fun findLessonIdsWithSummaryByCourseId(courseId: String): List<String>
}