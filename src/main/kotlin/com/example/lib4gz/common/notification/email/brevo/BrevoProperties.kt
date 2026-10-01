package com.example.lib4gz.common.notification.email.brevo

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Brevo's own credential. Its API key is a different credential from its SMTP key; this HTTP
 * provider needs the API key, from SMTP & API > API keys in the Brevo console.
 */
@ConfigurationProperties("application.mail.brevo")
class BrevoProperties(
    val apiKey: String = ""
)
