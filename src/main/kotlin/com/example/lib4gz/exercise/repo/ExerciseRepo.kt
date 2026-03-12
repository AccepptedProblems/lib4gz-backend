package com.example.lib4gz.exercise.repo

import com.example.lib4gz.exercise.model.entity.Exercise
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

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
}