package com.example.lib4gz.auth.model.mapper

import com.example.lib4gz.auth.model.entity.User
import com.example.lib4gz.auth.model.payload.UserResponse

fun User.toResponse() = UserResponse(
    id = id,
    username = username,
    email = email
)