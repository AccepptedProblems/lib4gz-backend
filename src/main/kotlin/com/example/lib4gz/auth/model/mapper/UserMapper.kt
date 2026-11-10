package com.example.lib4gz.auth.model.mapper

import com.example.lib4gz.auth.model.entity.User
import com.example.lib4gz.auth.model.entity.UserRole
import com.example.lib4gz.auth.model.payload.UserResponse

fun User.toResponse() = UserResponse(
    id = id,
    username = username,
    email = email,
    name = name.ifBlank { username },
    avatarUrl = avatarUrl,
    role = primaryRole(),
    emailVerified = emailVerified,
    googleConnected = googleId != null,
    raceAppearance = raceAppearance
)

/** The single role the frontend shows: ADMIN > TEACHER > USER. */
private fun User.primaryRole(): UserRole = when {
    isAdmin() -> UserRole.ADMIN
    isTeacher() -> UserRole.TEACHER
    else -> UserRole.USER
}
