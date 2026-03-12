package com.example.lib4gz.common.config.security.loader

data class SecurityConfigProperties(
    val permittedEndpoints: List<String> = emptyList(),
    val authorizedEndpoints: List<AuthorizedEndpoint> = emptyList(),
    val cors: CorsConfig = CorsConfig()
)

data class AuthorizedEndpoint(
    val endpoint: String,
    val roles: List<String>
)

data class CorsConfig(
    val allowedOriginPatterns: List<String> = emptyList(),
    val allowedMethods: List<String> = emptyList(),
    val allowedHeaders: List<String> = emptyList(),
    val allowCredentials: Boolean = true,
    val maxAge: Long = 3600
)