package com.example.lib4gz.common.config.error

import com.example.lib4gz.common.exception.BadRequestException
import com.example.lib4gz.common.exception.BusinessException
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.dao.DataIntegrityViolationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.InsufficientAuthenticationException
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.net.ConnectException
import java.sql.SQLException
import java.net.URI

/**
 * What goes on the wire when a request fails. Text written for a person passes through; text that
 * describes what failed inside never does, whatever the status.
 */
class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()
    private val request = MockHttpServletRequest("POST", "/api/v1/users/me/email/change").apply {
        servletPath = "/v1/users/me/email/change"
    }

    private val sql = "could not execute statement [ERROR: new row for relation \"email_otps\" " +
        "violates check constraint \"email_otps_purpose_check\"] [insert into email_otps (attempts, code_hash)]"

    @Test
    fun `an unhandled exception is a 500 that says nothing about what failed`() {
        val response = handler.handleException(RuntimeException(sql), request)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals(GlobalExceptionHandler.UNEXPECTED_ERROR, reasonOf(response.body!!))
        assertFalse(wire(response.body!!).contains("email_otps"), "the table name reached the client")
        assertFalse(wire(response.body!!).contains("check constraint"), "the SQL reached the client")
    }

    @Test
    fun `a failed call to another service is a 500 that names neither the service nor the cause`() {
        val ex = WebClientRequestException(
            ConnectException("Connection refused"), HttpMethod.POST,
            URI.create("https://api.brevo.com/v3/smtp/email"), HttpHeaders()
        )

        val response = handler.handleWebClientException(ex, request)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals(GlobalExceptionHandler.UNEXPECTED_ERROR, reasonOf(response.body!!))
        assertFalse(wire(response.body!!).contains("brevo"), "the upstream URL reached the client")
        assertFalse(wire(response.body!!).contains("refused"), "the cause reached the client")
    }

    @Test
    fun `a 400 with no reason of its own does not borrow the cause's message`() {
        val ex = ResponseStatusException(HttpStatus.BAD_REQUEST, null, IllegalStateException("internal detail"))

        val response = handler.handleResponseStatusException(request, ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(GlobalExceptionHandler.INVALID_REQUEST, reasonOf(response.body!!))
        assertFalse(wire(response.body!!).contains("internal detail"))
    }

    @Test
    fun `a reason written for the caller passes through with its status`() {
        val conflict = handler.handleResponseStatusException(
            request, ResponseStatusException(HttpStatus.CONFLICT, "Email already in use")
        )
        val mailDown = handler.handleResponseStatusException(
            request, ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not send email")
        )

        assertEquals(HttpStatus.CONFLICT, conflict.statusCode)
        assertEquals("Email already in use", reasonOf(conflict.body!!))
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, mailDown.statusCode)
        assertEquals("Could not send email", reasonOf(mailDown.body!!))
    }

    @Test
    fun `a refused state change is a 400 with its own wording, not a 500`() {
        val response = handler.handleBadRequestException(
            BadRequestException("Cannot submit a submission that is already submitted"), request
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("Cannot submit a submission that is already submitted", reasonOf(response.body!!))
    }

    @Test
    fun `a request without a valid token is told to sign in, not given Spring's wording`() {
        val response = handler.handleAuthenticationException(
            InsufficientAuthenticationException("Full authentication is required to access this resource"), request
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(GlobalExceptionHandler.SIGN_IN_REQUIRED, reasonOf(response.body!!))
    }

    @Test
    fun `a write that collided with another is a 409 that names neither the constraint nor the SQL`() {
        val duplicate = "ERROR: duplicate key value violates unique constraint \"ukb4e1cu95o25xf7c0mog5gib51\""
        val ex = DataIntegrityViolationException(
            "could not execute statement [$duplicate] [insert into lessons (created_at, deleted_at)]",
            SQLException(duplicate, "23505")
        )

        val response = handler.handleDataIntegrityViolationException(ex, request)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(GlobalExceptionHandler.CONFLICT, reasonOf(response.body!!))
        assertFalse(wire(response.body!!).contains("ukb4e1cu95o25xf7c0mog5gib51"), "the constraint reached the client")
        assertFalse(wire(response.body!!).contains("insert into"), "the SQL reached the client")
    }

    @Test
    fun `a value too long is not a collision and stays a 500`() {
        val tooLong = "ERROR: value too long for type character varying(500)"
        val ex = DataIntegrityViolationException(
            "could not execute statement [$tooLong]", SQLException(tooLong, "22001")
        )

        val response = handler.handleDataIntegrityViolationException(ex, request)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals(GlobalExceptionHandler.UNEXPECTED_ERROR, reasonOf(response.body!!))
    }

    @Test
    fun `a business rule keeps its message`() {
        val response = handler.handleBusinessException(BusinessException("Invalid code"), request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("Invalid code", reasonOf(response.body!!))
    }

    @Test
    fun `a path with no route in the app is a 404, not a 500`() {
        val response = handler.handleNoRouteException(NoResourceFoundException(HttpMethod.POST, "v1/nowhere"), request)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(GlobalExceptionHandler.NO_ROUTE, reasonOf(response.body!!))
    }

    private fun reasonOf(resp: ErrorResp) = (resp.errors.single() as GenericError).reason

    /** The body as the client would receive it. */
    private fun wire(resp: ErrorResp): String = ObjectMapper().writeValueAsString(resp)
}
