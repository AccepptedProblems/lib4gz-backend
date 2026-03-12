### Overview
> Model definitions: [models/common.yaml](../models/common.yaml)

Global exception handling with structured error responses.

### Custom Exceptions (all in `common/exception/` package)
- Package: `com.example.lib4gz.common.exception`
- IMPORTANT: All extend RuntimeException directly (NOT hierarchical)
  - class BusinessException(message: String) : RuntimeException(message)
  - class BadRequestException(message: String) : RuntimeException(message)
  - class ResourceNotFoundException(message: String) : RuntimeException(message)
  - class UnauthorizedException(message: String) : RuntimeException(message)
  - class ValidationException(message: String) : RuntimeException(message)
- Each is in its own file: BusinessException.kt, BadRequestException.kt, ResourceNotFoundException.kt, UnauthorizedException.kt, ValidationException.kt

### Error Response Model (`common/config/error/ErrorResp.kt`)
- Package: `com.example.lib4gz.common.config.error`

```kotlin
class ErrorResp(
    val timestamp: Long = 0,
    val path: String?,
    val status: HttpStatus?,
    val requestId: String?,
    val traceId: String?
) {
    val errors: MutableList<ApiError> = ArrayList()
    fun addError(error: ApiError)
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = InputError::class, name = "InputError"),
    JsonSubTypes.Type(value = GenericError::class, name = "SystemError")
)
interface ApiError

data class InputError(val field: String?, val message: String?) : ApiError
data class GenericError(val reason: String?) : ApiError
```

### GlobalExceptionHandler (`common/config/error/GlobalExceptionHandler.kt`)
- Package: `com.example.lib4gz.common.config.error`
- @RestControllerAdvice

| Handler Method | Exception | HTTP Status |
|---|---|---|
| handleResponseStatusException | ResponseStatusException | Extracts from exception |
| handleInvalidFormatException | InvalidFormatException | 400 Bad Request |
| handleAuthenticationException | InsufficientAuthenticationException | 401 Unauthorized |
| handleResourceNotFoundException | ResourceNotFoundException | 404 Not Found |
| handleUnauthorizedException | UnauthorizedException | 403 Forbidden |
| handleValidationException | ValidationException | 400 Bad Request |
| handleBusinessException | BusinessException | 400 Bad Request |
| handleWebClientException | WebClientException | 500 Internal Server Error |
| handleWebExchangeBindException | WebExchangeBindException | Extracts from exception |
| handleException | Exception (catch-all) | 500 Internal Server Error |

Each handler creates ErrorResp with: timestamp from request attribute, path from request URI, status, requestId from request header, traceId. Adds GenericError or InputError to errors list.
