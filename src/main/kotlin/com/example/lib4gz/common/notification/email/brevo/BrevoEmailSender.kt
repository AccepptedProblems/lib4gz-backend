package com.example.lib4gz.common.notification.email.brevo

import com.example.lib4gz.common.notification.email.EmailSender
import com.example.lib4gz.common.notification.email.MailProperties
import com.fasterxml.jackson.databind.ObjectMapper
import io.netty.channel.ChannelOption
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.server.ResponseStatusException
import reactor.netty.http.client.HttpClient
import java.net.URI
import java.time.Duration

/** The body of Brevo's `POST /v3/smtp/email`. `textContent` is the plain-text alternative to `htmlContent`. */
internal data class BrevoSendRequest(
    val sender: BrevoSender,
    val to: List<BrevoRecipient>,
    val subject: String,
    val textContent: String
)

internal data class BrevoSender(val name: String, val email: String)

internal data class BrevoRecipient(val email: String)

/**
 * Sends over Brevo's HTTP API rather than its SMTP relay: no handshake per send, errors that say
 * what is wrong, a `messageId` to trace, and no dependency on outbound port 587.
 *
 * Failure is always a 503, the contract the OTP callers already rely on, so the stored code rolls
 * back with the send.
 */
@Component
@ConditionalOnProperty(name = ["application.mail.provider"], havingValue = "brevo")
class BrevoEmailSender(
    private val mail: MailProperties,
    private val brevo: BrevoProperties,
    webClientBuilder: WebClient.Builder
) : EmailSender {

    private val logger = LoggerFactory.getLogger(BrevoEmailSender::class.java)

    init {
        mail.requireConfigured(listOfNotNull("BREVO_API_KEY".takeIf { brevo.apiKey.isBlank() }))
    }

    private val timeout = Duration.ofMillis(mail.timeoutMs)

    private val webClient: WebClient = webClientBuilder
        .clientConnector(
            ReactorClientHttpConnector(
                HttpClient.create()
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, mail.timeoutMs.coerceIn(1, Int.MAX_VALUE.toLong()).toInt())
                    .responseTimeout(timeout)
            )
        )
        .build()

    override fun send(to: String, subject: String, body: String) {
        val request = BrevoSendRequest(
            sender = BrevoSender(name = mail.from.name, email = mail.from.address),
            to = listOf(BrevoRecipient(email = to)),
            subject = subject,
            textContent = body
        )
        try {
            val response = webClient.post()
                .uri(URI.create(SEND_URL))
                .header(API_KEY_HEADER, brevo.apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String::class.java)
                .block(timeout)
            // Brevo's handle for this message: what to search for in its dashboard.
            logger.info("Emailed {} via Brevo, messageId {}", to, jsonField(response, "messageId"))
        } catch (e: WebClientResponseException) {
            // Brevo's own wording, never the key.
            logger.error(
                "Brevo refused the email to {}: HTTP {}, {}",
                to, e.statusCode.value(), jsonField(e.responseBodyAsString, "message")
            )
            throw undeliverable()
        } catch (e: Exception) {
            logger.error("Could not reach Brevo to email {}", to, e)
            throw undeliverable()
        }
    }

    private fun undeliverable() =
        ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not send email")

    /** One field out of a small JSON body, or null when the body is missing, empty or not JSON. */
    private fun jsonField(json: String?, field: String): String? =
        json?.let { runCatching { JSON.readTree(it).path(field).asText("") }.getOrNull() }
            ?.takeIf { it.isNotBlank() }

    private companion object {
        const val SEND_URL = "https://api.brevo.com/v3/smtp/email"
        const val API_KEY_HEADER = "api-key"
        val JSON = ObjectMapper()
    }
}
