package com.example.lib4gz.courses.model.payload

import com.example.lib4gz.courses.model.entity.EnrollmentRole
import com.example.lib4gz.courses.model.entity.EnrollmentStatus

// Requests
data class EnrollmentRequest(
    val role: EnrollmentRole = EnrollmentRole.LEARNER
)

data class UpdateEnrollmentRequest(
    val role: EnrollmentRole? = null,
    val status: EnrollmentStatus? = null
)

// Responses
data class EnrollmentResponse(
    val id: String,
    val courseId: String,
    val courseName: String,
    val user: UserSummary,
    val role: EnrollmentRole,
    val status: EnrollmentStatus,
    val joinedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Lightweight enrollment view embedded in CourseResponse / SyllabusResponse
 * to identify the caller's relationship to the course without a separate request.
 */
data class EnrollmentSummary(
    val id: String,
    val role: EnrollmentRole,
    val status: EnrollmentStatus,
    val joinedAt: Long?
)