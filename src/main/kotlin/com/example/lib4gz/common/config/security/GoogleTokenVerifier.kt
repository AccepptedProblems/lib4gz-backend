package com.example.lib4gz.common.config.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

data class GoogleIdentity(
    val sub: String,
    val email: String,
    val emailVerified: Boolean,
    val name: String?,
    val picture: String?
)

interface GoogleTokenVerifier {
    /** Returns the identity inside a Google ID token, or throws 401 when the token is not valid for our client. */
    fun verify(idToken: String): GoogleIdentity
}

@Component
class NimbusGoogleTokenVerifier(
    @Value("\${application.security.google.client-id}") private val clientId: String
) : GoogleTokenVerifier {

    // Google's signing keys are fetched on first use and cached; no network at startup.
    private val decoder = NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build().apply {
        setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtTimestampValidator(),
                // Google issues both spellings.
                JwtClaimValidator<Any>("iss") { it?.toString() in GOOGLE_ISSUERS },
                JwtClaimValidator<Collection<String>>("aud") { it != null && clientId.isNotBlank() && clientId in it }
            )
        )
    }

    override fun verify(idToken: String): GoogleIdentity = try {
        val jwt = decoder.decode(idToken)
        GoogleIdentity(
            sub = jwt.subject,
            email = jwt.getClaimAsString("email") ?: throw JwtException("Google token has no email"),
            emailVerified = jwt.getClaimAsBoolean("email_verified") ?: false,
            name = jwt.getClaimAsString("name"),
            picture = jwt.getClaimAsString("picture")
        )
    } catch (e: JwtException) {
        throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token")
    }

    companion object {
        private val GOOGLE_ISSUERS = setOf("https://accounts.google.com", "accounts.google.com")
    }
}
