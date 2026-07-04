package com.example.lib4gz.exercise.repo

import com.example.lib4gz.exercise.model.entity.Exercise
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * Grouped per-lesson count row used by progress computation. Shared by
 * ExerciseRepo (exercises per lesson) and SubmissionRepo (done exercises per
 * lesson for a user) so the two maps join on identical keys.
 */
data class LessonCountRow(val courseId: String, val lessonId: String, val count: Long)

@Repository
interface ExerciseRepo : JpaRepository<Exercise, String> {

    fun findByLesson_IdOrderByOrderIndexAsc(lessonId: String): List<Exercise>

    fun findByLesson_Id(lessonId: String): List<Exercise>

    @Query("SELECT MAX(e.orderIndex) FROM Exercise e WHERE e.lesson.id = :lessonId")
    fun findMaxOrderIndexByLessonId(lessonId: String): Int?

    fun existsByLesson_IdAndOrderIndex(lessonId: String, orderIndex: Int): Boolean

    fun countByLesson_Id(lessonId: String): Long

    @Query("SELECT e FROM Exercise e WHERE e.lesson.module.course.id = :courseId")
    fun findByCourseId(courseId: String): List<Exercise>

    @Query(
        "SELECT new com.example.lib4gz.exercise.repo.LessonCountRow(" +
                "e.lesson.module.course.id, e.lesson.id, COUNT(e)) " +
                "FROM Exercise e WHERE e.lesson.module.course.id IN :courseIds " +
                "GROUP BY e.lesson.module.course.id, e.lesson.id"
    )
    fun countPerLessonByCourseIds(courseIds: Collection<String>): List<LessonCountRow>
}