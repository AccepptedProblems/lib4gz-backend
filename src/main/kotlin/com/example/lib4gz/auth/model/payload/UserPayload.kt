package com.example.lib4gz.auth.model.payload

import com.example.lib4gz.auth.model.entity.User
import com.example.lib4gz.common.utils.IdGenerator

data class UserCreationRequest(
    val username: String,
    val email: String,
    val password: String
) {
    fun toEntity() = User(
        id = IdGenerator.generate("usr"),
        email = email,
        password = password,
        name = username,
        username = username
    )
}

data class UserResponse(
    val id: String,
    val username: String,
    val email: String
)

data class LoginReq(
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