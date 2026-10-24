package com.example.lib4gz.common.config.error

import com.example.lib4gz.common.exception.BadRequestException
import com.example.lib4gz.common.exception.BusinessException
import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.UnauthorizedException
import com.example.lib4gz.common.exception.ValidationException
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import jakarta.servlet.http.HttpServletRequest
import lombok.extern.slf4j.Slf4j
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.InsufficientAuthenticationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.reactive.function.client.WebClientException
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.*

/**
 * Maps exceptions to the `ErrorResp` envelope. The rule for what goes on the wire: text the code wrote
 * for a person (a `BusinessException` message, a `ResponseStatusException` reason) is passed through;
 * text that describes what failed inside (an exception message, a cause, a URL) goes to the log under the
 * request id and the client gets a fixed sentence instead. An exception message has carried SQL and table
 * names onto a screen before.
 */

@Slf4j
@RestControllerAdvice
class GlobalExceptionHandler {
    val logger: Logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    /**
     * Process error contained in ResponseStatusException, caused by System Exceptions that are
     * known.
     *
     * @param ex      the ResponseStatusException
     * @param request original ServerHttpRequest Request
     * @return ErrorResp
     */
    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(request: HttpServletRequest, ex: ResponseStatusException)
            : ResponseEntity<ErrorResp?> {
        logger.error(ex.message)
        val resp: ErrorResp = buildErrorResp(ex, request)

        if (ex.statusCode == HttpStatus.BAD_REQUEST) {
            val c = getFinalCause(ex)
            logger.error(c!!.message)
            when (c) {
                is InvalidFormatException -> {
                    return handleInvalidFormatException(c, request)
                }

                is MismatchedInputException -> {
                    resp.addError(InputError(c.pathReference, "Missing or invalid"))
                }

                else -> {
                    // Only the reason was written for a person; the cause's message is whatever blew up.
                    resp.addError(GenericError(ex.reason?.takeIf { it.isNotBlank() } ?: INVALID_REQUEST))
                }
            }
        } else {
            resp.addError(GenericError(ex.reason))
        }
//        logErrors(request, resp)
        return ResponseEntity(resp, ex.statusCode)
    }

    /**
     * Process the exception caused by invalid data input format.
     *
     * @param ex      InvalidFormatException
     * @param request original request
     * @return detailed errors message
     */
    @ExceptionHandler(InvalidFormatException::class)
    fun handleInvalidFormatException(
        ex: InvalidFormatException, request: HttpServletRequest
    ): ResponseEntity<ErrorResp?> {
        val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
        logger.error(ex.message)
        val resp: ErrorResp = buildErrorResp(ex, request)
//        logErrors(request, resp)
        return ResponseEntity<ErrorResp?>(resp, HttpStatus.BAD_REQUEST)
    }

    /**
     * A request with no valid token. Spring's own message ("Full authentication is required to access
     * this resource") is framework wording; the person is told to sign in.
     */
    @ExceptionHandler(InsufficientAuthenticationException::class)
    fun handleAuthenticationException(
        ex: InsufficientAuthenticationException, request: HttpServletRequest
    ): ResponseEntity<ErrorResp?> {
        logger.error("Unauthenticated {} {}: {}", request.method, request.servletPath, ex.message)
        val resp: ErrorResp = buildErrorResp(request, HttpStatus.UNAUTHORIZED)
        resp.addError(GenericError(SIGN_IN_REQUIRED))
//        logErrors(request, resp)
        return ResponseEntity<ErrorResp?>(resp, HttpStatus.UNAUTHORIZED)
    }

    /**
     * Handle ResourceNotFoundException - when entity is not found in database
     */
    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleResourceNotFoundException(
        ex: ResourceNotFoundException, request: HttpServletRequest
    ): ResponseEntity<ErrorResp> {
        logger.error("Resource not found: ${ex.message}")
        val resp: ErrorResp = buildErrorResp(request, HttpStatus.NOT_FOUND)
        resp.addError(GenericError(ex.message))
        return ResponseEntity(resp, HttpStatus.NOT_FOUND)
    }

    /**
     * Handle UnauthorizedException - when user doesn't have permission
     */
    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorizedException(
        ex: UnauthorizedException, request: HttpServletRequest
    ): ResponseEntity<ErrorResp> {
        logger.error("Unauthorized access: ${ex.message}")
        val resp: ErrorResp = buildErrorResp(request, HttpStatus.FORBIDDEN)
        resp.addError(GenericError(ex.message))
        return ResponseEntity(resp, HttpStatus.FORBIDDEN)
    }

    /**
     * Handle ValidationException - when business validation fails
     */
    @ExceptionHandler(ValidationException::class)
    fun handleValidationException(
        ex: ValidationException, request: HttpServletRequest
    ): ResponseEntity<ErrorResp> {
        logger.error("Validation failed: ${ex.message}")
        val resp: ErrorResp = buildErrorResp(request, HttpStatus.BAD_REQUEST)
        resp.addError(GenericError(ex.message))
        return ResponseEntity(resp, HttpStatus.BAD_REQUEST)
    }

    /**
     * Handle BusinessException - when business rule is violated
     */
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        ex: BusinessException, request: HttpServletRequest
    ): ResponseEntity<ErrorResp> {
        logger.error("Business rule violation: ${ex.message}")
        val resp: ErrorResp = buildErrorResp(request, HttpStatus.BAD_REQUEST)
        resp.addError(GenericError(ex.message))
        return ResponseEntity(resp, HttpStatus.BAD_REQUEST)
    }

    /**
     * Handle BadRequestException - a state change the entity refuses, worded for the caller.
     */
    @ExceptionHandler(BadRequestException::class)
    fun handleBadRequestException(
        ex: BadRequestException, request: HttpServletRequest
    ): ResponseEntity<ErrorResp> {
        logger.error("Bad request: ${ex.message}")
        val resp: ErrorResp = buildErrorResp(request, HttpStatus.BAD_REQUEST)
        resp.addError(GenericError(ex.message))
        return ResponseEntity(resp, HttpStatus.BAD_REQUEST)
    }

    /**
     * A write that collided with another: a duplicate key or a parent that is gone. Usually two changes
     * crossed, so the client is asked to reload; which constraint, and the SQL, go to the log. Spring
     * uses the same exception for a value too long or a null where none is allowed, which are not
     * collisions, so those stay a 500.
     */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolationException(
        ex: DataIntegrityViolationException, request: HttpServletRequest
    ): ResponseEntity<ErrorResp> {
        val sqlState = generateSequence<Throwable>(ex) { it.cause }
            .filterIsInstance<java.sql.SQLException>()
            .firstOrNull()?.sqlState
        if (sqlState !in COLLISION_SQL_STATES) return handleException(ex, request)

        val resp: ErrorResp = buildErrorResp(request, HttpStatus.CONFLICT)
        logger.error(
            "Integrity violation on {} {} (requestId {})",
            request.method, request.servletPath, resp.requestId, ex
        )
        resp.addError(GenericError(CONFLICT))
        return ResponseEntity(resp, HttpStatus.CONFLICT)
    }

    /**
     * Handle a failed call to another service. Which call, and why, is logged; the client is told only
     * that something failed on our side.
     *
     * @param e       WebClientException
     * @param request ServerHttpRequest
     * @return detailed errors message
     */
    @ExceptionHandler(WebClientException::class)
    fun handleWebClientException(
        e: WebClientException, request: HttpServletRequest
    ): ResponseEntity<ErrorResp> {
        val resp: ErrorResp = buildErrorResp(request, HttpStatus.INTERNAL_SERVER_ERROR)
        val target = when (e) {
            is WebClientResponseException -> e.request?.let { "${it.method.name()} ${it.uri}" }
            is WebClientRequestException -> "${e.method} ${e.uri}"
            else -> null
        }
        val finalEx = getFinalCause(e)
        logger.error(
            "Upstream call {} failed (requestId {}): {}",
            target ?: "<unknown>", resp.requestId, finalEx?.message ?: finalEx?.javaClass?.simpleName, e
        )
        resp.addError(GenericError(UNEXPECTED_ERROR))
        return ResponseEntity<ErrorResp>(resp, HttpStatus.INTERNAL_SERVER_ERROR)
    }


    /**
     * Process error contained in WebExchangeBindException, caused by wrong input data.
     *
     * @param ex      the WebExchangeBindException
     * @param request original ServerHttpRequest Request
     * @return ErrorResp
     */
    @ExceptionHandler(WebExchangeBindException::class)
    fun handleWebExchangeBindException(
        ex: WebExchangeBindException, request: HttpServletRequest
    ): ResponseEntity<ErrorResp?> {
        val resp: ErrorResp = buildErrorResp(ex, request)
        for (error in ex.bindingResult.fieldErrors) {
            val field = error.field.camelToSnakeCase()
            resp.addError(InputError(field, error.defaultMessage))
        }
        logger.error(ex.message)
        return ResponseEntity(resp, ex.statusCode)
    }

    private fun String.camelToSnakeCase(): String {
        return this.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
    }

    /**
     * Process all unexpected errors. The exception, its cause chain and the stack go to the log under
     * the request id; the client gets one sentence a person can read.
     *
     * @param request original ServerHttpRequest Request
     * @return ErrorResp
     */
    @ExceptionHandler(Exception::class)
    fun handleException(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResp> {
        val resp: ErrorResp = buildErrorResp(request, HttpStatus.INTERNAL_SERVER_ERROR)
        logger.error(
            "Unhandled {} on {} {} (requestId {})",
            ex.javaClass.name, request.method, request.servletPath, resp.requestId, ex
        )
        resp.addError(GenericError(UNEXPECTED_ERROR))
        return ResponseEntity<ErrorResp>(resp, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    private fun buildErrorResp(
        ex: ResponseStatusException,
        request: HttpServletRequest
    ): ErrorResp {
        return ErrorResp(
            timestamp = Instant.now().toEpochMilli(),
            path = request.servletPath,
            status = HttpStatus.valueOf(ex.statusCode.value()),
            requestId = request.requestId,
            traceId = request.requestId
        )
    }

    private fun buildErrorResp(ex: InvalidFormatException, request: HttpServletRequest): ErrorResp {
        val resp = ErrorResp(
            timestamp = Instant.now().toEpochMilli(),
            path = request.servletPath,
            status = HttpStatus.BAD_REQUEST,
            requestId = request.requestId,
            traceId = request.requestId
        )
        val fieldsNames: List<String> = ex.path
            .map { it.fieldName }
            .filter { obj: Any? -> Objects.nonNull(obj) }

        val field = fieldsNames.joinToString(".")
        val type: Class<*> = ex.targetType
        var message = "Cannot convert value: ${ex.value} to type ${type.simpleName}"
        if (type.isEnum) {
            val possibleValues = listOf(*type.enumConstants)
            message = "$message. Possible values are: $possibleValues"
        }
        resp.addError(InputError(field, message))
        return resp
    }

    private fun buildErrorResp(request: HttpServletRequest, status: HttpStatus): ErrorResp {
        return ErrorResp(
            timestamp = Instant.now().toEpochMilli(),
            path = request.servletPath,
            status = status,
            requestId = request.requestId,
            traceId = request.requestId
        )

    }

    protected fun getFinalCause(e: Exception?): Throwable? {
        var x: Throwable? = e
        while (x!!.cause != null) {
            x = x.cause
        }
        return x
    }

    companion object {
        /** What a client is told when the server itself failed. The detail is in the log, never on the wire. */
        const val UNEXPECTED_ERROR = "Something went wrong on our side. Please try again."

        /** Unique, foreign key and exclusion violations: the SQLStates that mean two writes collided. */
        private val COLLISION_SQL_STATES = setOf("23505", "23503", "23P01")

        /** A write that clashed with what the database already holds. */
        const val CONFLICT = "This change clashes with something already saved, perhaps by someone " +
            "else at the same moment. Refresh the page and try again."

        /** A 400 with no reason of its own. */
        const val INVALID_REQUEST = "Invalid request"

        /** A request without a valid token. */
        const val SIGN_IN_REQUIRED = "Please sign in to continue"
    }
}