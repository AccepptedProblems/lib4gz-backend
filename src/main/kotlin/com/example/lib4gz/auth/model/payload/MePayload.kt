package com.example.lib4gz.auth.model.payload

import com.example.lib4gz.courses.model.payload.CourseResponse

/**
 * Self-scoped dashboard payload. Combines the two `GET /courses?type=enrolled|created`
 * round-trips the dashboard previously issued into a single request.
 */
data class MeCoursesResponse(
    val enrolled: List<CourseResponse>,
    val created: List<CourseResponse>
)
