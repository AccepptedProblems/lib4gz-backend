package com.example.lib4gz.common.config.error

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.springframework.http.HttpStatus

class ErrorResp(
    val timestamp: Long = 0,
    val path: String?,
    val status: HttpStatus?,
    val requestId: String?,
    val traceId: String?
) {
    val errors: MutableList<ApiError> = ArrayList()

    /**
     * Add error to the list of ApiErrors.
     *
     * @param error the ApiError. It could be InputError or SystemError.
     */
    fun addError(error: ApiError) {
        errors.add(error)
    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    Type(value = InputError::class, name = "InputError"),
    Type(value = GenericError::class, name = "SystemError")
)
interface ApiError


@JsonInclude(Include.NON_NULL)
data class InputError(val field: String?, val message: String?) : ApiError {
    override fun toString(): String {
        return "InputError{field='$field', message='$message'}"
    }
}


@JsonInclude(Include.NON_NULL)
data class GenericError(val reason: String?) : ApiError {
    override fun toString(): String {
        return "SystemError{reason='$reason'}"
    }
}