package com.example.lib4gz.common.notification.email.brevo

import com.example.lib4gz.common.notification.email.MailProperties
import com.example.lib4gz.common.notification.email.MailProvider
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.http.client.reactive.MockClientHttpRequest
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

class BrevoEmailSenderTest {

    private var captured: ClientRequest? = null

    @Test
    fun `send posts the message to Brevo with the api-key header and a plain-text body`() {
        val sender = sender(respond = { json(HttpStatus.CREATED, """{"messageId":"<202609.15@brevo>"}""") })

        sender.send("alice@x.com", "Your Lib4GZ sign-up code", "Your Lib4GZ sign-up code: 123456")

        val request = captured!!
        assertEquals("POST", request.method().name())
        assertEquals("https://api.brevo.com/v3/smtp/email", request.url().toString())
        assertEquals("secret-key", request.headers().getFirst("api-key"))
        assertEquals(MediaType.APPLICATION_JSON, request.headers().contentType)

        val body = ObjectMapper().readTree(bodyOf(request))
        assertEquals("Lib4GZ", body.at("/sender/name").asText())
        assertEquals("no-reply@lib4gz.test", body.at("/sender/email").asText())
        assertEquals(1, body.at("/to").size())
        assertEquals("alice@x.com", body.at("/to/0/email").asText())
        assertEquals("Your Lib4GZ sign-up code", body.at("/subject").asText())
        assertEquals("Your Lib4GZ sign-up code: 123456", body.at("/textContent").asText())
        assertTrue(body.at("/htmlContent").isMissingNode, "the code mail is plain text only")
    }

    @Test
    fun `the configured From name and address are what Brevo is told to send as`() {
        val sender = sender(fromName = "Lib4GZ Support", fromAddress = "support@lib4gz.test")

        sender.send("alice@x.com", "s", "b")

        val body = ObjectMapper().readTree(bodyOf(captured!!))
        assertEquals("Lib4GZ Support", body.at("/sender/name").asText())
        assertEquals("support@lib4gz.test", body.at("/sender/email").asText())
    }

    @Test
    fun `a blank From name falls back to Lib4GZ, since a blank env var beats the yaml default`() {
        val sender = sender(fromName = "")

        sender.send("alice@x.com", "s", "b")

        val body = ObjectMapper().readTree(bodyOf(captured!!))
        assertEquals("Lib4GZ", body.at("/sender/name").asText())
    }

    @Test
    fun `a key Brevo rejects becomes a 503, so the caller's transaction rolls back`() {
        val sender = sender(respond = {
            json(HttpStatus.UNAUTHORIZED, """{"code":"unauthorized","message":"Key not found"}""")
        })

        val ex = assertThrows(ResponseStatusException::class.java) { sender.send("alice@x.com", "s", "b") }

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.statusCode)
        assertEquals("Could not send email", ex.reason)
    }

    @Test
    fun `a provider that never answers becomes the same 503 once the timeout runs out`() {
        val sender = sender(timeoutMs = 100, respond = { Mono.never() })

        val ex = assertThrows(ResponseStatusException::class.java) { sender.send("alice@x.com", "s", "b") }

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.statusCode)
        assertEquals("Could not send email", ex.reason)
    }

    @Test
    fun `a missing key and a missing From address fail the boot together, both named`() {
        val ex = assertThrows(IllegalStateException::class.java) { sender(apiKey = "", fromAddress = "") }

        assertEquals("application.mail.provider=brevo needs: BREVO_API_KEY, MAIL_FROM_ADDRESS", ex.message)
    }

    @Test
    fun `a configured key with no From address still fails the boot`() {
        val ex = assertThrows(IllegalStateException::class.java) { sender(fromAddress = "") }

        assertEquals("application.mail.provider=brevo needs: MAIL_FROM_ADDRESS", ex.message)
    }

    private fun sender(
        apiKey: String = "secret-key",
        fromName: String = "Lib4GZ",
        fromAddress: String = "no-reply@lib4gz.test",
        timeoutMs: Long = 10_000,
        respond: (ClientRequest) -> Mono<ClientResponse> = { json(HttpStatus.CREATED, "{}") }
    ) = BrevoEmailSender(
        mail = MailProperties(
            provider = MailProvider.BREVO,
            from = MailProperties.From(name = fromName, address = fromAddress),
            timeoutMs = timeoutMs
        ),
        brevo = BrevoProperties(apiKey),
        webClientBuilder = WebClient.builder().exchangeFunction { request ->
            captured = request
            respond(request)
        }
    )

    private fun json(status: HttpStatus, body: String): Mono<ClientResponse> =
        Mono.just(
            ClientResponse.create(status)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build()
        )

    /** The request body as it would go on the wire; a stub exchange never writes it itself. */
    private fun bodyOf(request: ClientRequest): String {
        val wire = MockClientHttpRequest(request.method(), request.url())
        request.writeTo(wire, ExchangeStrategies.withDefaults()).block()
        return wire.bodyAsString.block()!!
    }
}
