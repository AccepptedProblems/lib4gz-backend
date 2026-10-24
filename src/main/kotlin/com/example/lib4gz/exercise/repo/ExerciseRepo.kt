package com.example.lib4gz.exercise.repo

import com.example.lib4gz.exercise.model.entity.Exercise
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
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

    fun findByLesson_IdOrderByOrderIndexAscCreatedAtAsc(lessonId: String): List<Exercise>

    fun findByLesson_Id(lessonId: String): List<Exercise>

    @Query("SELECT MAX(e.orderIndex) FROM Exercise e WHERE e.lesson.id = :lessonId")
    fun findMaxOrderIndexByLessonId(lessonId: String): Int?

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

    // The hide* updates serve a parent's delete. `NOW()` is the transaction's start, the same value
    // the parent's own `@SQLDelete` writes, so one timestamp finds the whole delete.

    @Modifying
    @Query(
        "UPDATE exercises SET deleted_at = CAST(EXTRACT(EPOCH FROM NOW()) * 1000 AS bigint) " +
            "WHERE lesson_id = :lessonId AND deleted_at IS NULL",
        nativeQuery = true
    )
    fun hideByLessonId(lessonId: String): Int

    @Modifying
    @Query(
        "UPDATE exercises SET deleted_at = CAST(EXTRACT(EPOCH FROM NOW()) * 1000 AS bigint) " +
            "WHERE deleted_at IS NULL AND lesson_id IN (SELECT id FROM lessons WHERE module_id = :moduleId)",
        nativeQuery = true
    )
    fun hideByModuleId(moduleId: String): Int

    @Modifying
    @Query(
        "UPDATE exercises SET deleted_at = CAST(EXTRACT(EPOCH FROM NOW()) * 1000 AS bigint) " +
            "WHERE deleted_at IS NULL AND lesson_id IN (" +
            "SELECT l.id FROM lessons l JOIN modules m ON l.module_id = m.id WHERE m.course_id = :courseId)",
        nativeQuery = true
    )
    fun hideByCourseId(courseId: String): Int
}