package com.example.lib4gz.courses.model.payload

import com.example.lib4gz.courses.model.entity.Visibility

// Requests
data class CreateCourseRequest(
    val title: String,
    val description: String? = null,
    val visibility: Visibility = Visibility.PRIVATE,
    val settings: Map<String, Any> = emptyMap()
)

data class UpdateCourseRequest(
    val title: String? = null,
    val description: String? = null,
    val visibility: Visibility? = null,
    val settings: Map<String, Any>? = null
)

// Responses
data class CourseResponse(
    val id: String,
    val code: String,
    val title: String,
    val description: String?,
    val visibility: Visibility,
    val createdBy: UserSummary,
    val settings: Map<String, Any>,
    val createdAt: Long,
    val updatedAt: Long,
    val moduleCount: Int? = null,
    val enrollmentCount: Int? = null,
    // Denormalized: the caller's enrollment (null when unenrolled or anonymous).
    // Eliminates the separate GET /courses/{id}/my-enrollment round-trip.
    val myEnrollment: EnrollmentSummary? = null,
    // The caller's progress in this course (null on created/public listings where
    // progress is meaningless — only populated for enrolled reads).
    val progress: CourseProgress? = null
)

/**
 * Per-caller course progress, derived entirely from submissions:
 * a lesson counts as done when every one of its exercises has a SUBMITTED or
 * APPROVED submission from the caller. Lessons without exercises are excluded
 * from both counts, so `totalLessons` is the number of exercise-bearing lessons.
 * `totalLessons == 0` means "no gradable content yet" — render no percentage.
 */
data class CourseProgress(
    val completedLessons: Int,
    val totalLessons: Int,
    val lastVisitedLessonId: String? = null,
    val lastVisitedLessonTitle: String? = null,
    val lastVisitedAt: Long? = null
)

data class UserSummary(
    val id: String,
    val name: String,
    val email: String,
    val avatarUrl: String?
)