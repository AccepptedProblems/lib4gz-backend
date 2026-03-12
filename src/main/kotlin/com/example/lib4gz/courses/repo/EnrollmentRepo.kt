package com.example.lib4gz.courses.repo

import com.example.lib4gz.courses.model.entity.Enrollment
import com.example.lib4gz.courses.model.entity.EnrollmentStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface EnrollmentRepo : JpaRepository<Enrollment, String> {

    fun findByCourse_Id(courseId: String): List<Enrollment>

    fun findByUser_Id(userId: String): List<Enrollment>

    fun findByCourse_IdAndUser_Id(courseId: String, userId: String): Enrollment?

    fun existsByCourse_IdAndUser_Id(courseId: String, userId: String): Boolean

    fun findByCourse_IdAndStatus(courseId: String, status: EnrollmentStatus): List<Enrollment>

    @Query("SELECT e FROM Enrollment e WHERE e.course.id = :courseId AND e.user.id = :userId AND e.status = 'ACTIVE'")
    fun findActiveEnrollment(courseId: String, userId: String): Enrollment?

    fun countByCourse_IdAndStatus(courseId: String, status: EnrollmentStatus): Long
}