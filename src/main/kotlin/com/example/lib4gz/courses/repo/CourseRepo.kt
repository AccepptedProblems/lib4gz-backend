package com.example.lib4gz.courses.repo

import com.example.lib4gz.courses.model.entity.Course
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface CourseRepo : JpaRepository<Course, String> {

    fun findByCode(code: String): Course?

    fun findByCreatedBy_Id(userId: String): List<Course>

    @Query("SELECT c FROM Course c JOIN c.enrollments e WHERE e.user.id = :userId AND e.status = 'ACTIVE'")
    fun findByEnrolledUser(userId: String): List<Course>

    fun existsByIdAndCreatedBy_Id(courseId: String, userId: String): Boolean

    @Query("SELECT c FROM Course c WHERE c.visibility = com.example.lib4gz.courses.model.entity.Visibility.PUBLIC")
    fun findPublicCourses(): List<Course>
}