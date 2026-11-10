package com.example.lib4gz.auth.model.payload

import com.example.lib4gz.auth.model.entity.UserRole
import com.example.lib4gz.courses.model.payload.CourseResponse

data class UserResponse(
    val id: String,
    val username: String,
    val email: String,
    val name: String,
    val avatarUrl: String?,
    val role: UserRole,
    val emailVerified: Boolean,
    /** A Google account signs this user in; the email is that account's address. */
    val googleConnected: Boolean,
    /** Lightning Kart look, `{"characterId", "kartId"}`, or null when never chosen. */
    val raceAppearance: Map<String, Any>?
)

data class UpdateMeRequest(
    val name: String? = null,
    val raceAppearance: Map<String, Any>? = null
)

/**
 * Self-scoped dashboard payload. Combines the two `GET /courses?type=enrolled|created`
 * round-trips the dashboard previously issued into a single request.
 */
data class MeCoursesResponse(
    val enrolled: List<CourseResponse>,
    val created: List<CourseResponse>
)

data class LoginReq(
    /** Username, or email when it contains `@`. */
    val username: String,
    val password: String
)

data class Token (
    val token: String,
    val expiredAt: Long
)

data class LoginResponse(
    val user: UserResponse,
    val accessToken: Token
)
