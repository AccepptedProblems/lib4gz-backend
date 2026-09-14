package com.example.lib4gz.auth.model.payload

data class StartEmailRegistrationRequest(
    val email: String
)

data class VerifyEmailRegistrationRequest(
    val email: String,
    val code: String
)

data class CompleteRegistrationRequest(
    val registrationId: String,
    val username: String,
    val name: String? = null,
    /** Required on the email path, ignored on the Google path. */
    val password: String? = null
)

data class OtpSentResponse(
    val email: String,
    val cooldownSeconds: Long = 60
)

data class RegistrationStartedResponse(
    val registrationId: String,
    val email: String,
    val name: String?,
    val avatarUrl: String?,
    val requiresPassword: Boolean
)

data class VerifyEmailCodeRequest(
    val code: String
)

data class GoogleSignInRequest(
    /** The credential the Google Identity Services button hands the browser. */
    val idToken: String
)

/** Exactly one field is non-null: `login` for a known account, `registration` for a new one. */
data class GoogleSignInResponse(
    val login: LoginResponse? = null,
    val registration: RegistrationStartedResponse? = null
)
