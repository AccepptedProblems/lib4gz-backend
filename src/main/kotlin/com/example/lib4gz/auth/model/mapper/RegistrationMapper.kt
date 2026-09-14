package com.example.lib4gz.auth.model.mapper

import com.example.lib4gz.auth.model.entity.PendingRegistration
import com.example.lib4gz.auth.model.payload.RegistrationStartedResponse

fun PendingRegistration.toStartedResponse() = RegistrationStartedResponse(
    registrationId = id,
    email = email,
    name = name,
    avatarUrl = avatarUrl,
    requiresPassword = googleId == null
)
