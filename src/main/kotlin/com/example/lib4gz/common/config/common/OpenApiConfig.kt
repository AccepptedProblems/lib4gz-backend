package com.example.lib4gz.common.config.common

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Makes the generated API reference say what a client really sends. Controllers read the caller
 * from `X-User-Id`, but that header is written by `JwtAuthFilter` and stripped from anything a
 * client sends; the client's credential is the `access_token` header.
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(Info().title("Lib4GZ API").version("v1"))
        .components(
            Components().addSecuritySchemes(
                ACCESS_TOKEN,
                SecurityScheme().type(SecurityScheme.Type.APIKEY).`in`(SecurityScheme.In.HEADER).name(ACCESS_TOKEN)
            )
        )

    @Bean
    fun callerComesFromTheToken() = OperationCustomizer { operation, _ ->
        val parameters = operation.parameters.orEmpty()
        if (parameters.any { it.name == PZRequestHeader.USER_ID }) {
            operation.parameters = parameters.filterNot { it.name == PZRequestHeader.USER_ID }.ifEmpty { null }
            operation.addSecurityItem(SecurityRequirement().addList(ACCESS_TOKEN))
        }
        operation
    }

    private companion object {
        const val ACCESS_TOKEN = "access_token"
    }
}
