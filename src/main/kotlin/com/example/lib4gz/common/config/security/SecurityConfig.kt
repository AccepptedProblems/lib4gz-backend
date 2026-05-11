package com.example.lib4gz.common.config.security

import com.example.lib4gz.common.config.security.loader.AppSecurityConfigLoader
import jakarta.servlet.DispatcherType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy.STATELESS
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter,
    private val authenticationProvider: DaoAuthenticationProvider,
    private val authEntryPoint: AuthEntryPoint,
    private val securityConfigLoader: AppSecurityConfigLoader
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val config = securityConfigLoader.loadConfig()

        http
            .csrf { obj -> obj.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .authorizeHttpRequests { req ->
                // Apply whitelist URLs
                req.requestMatchers(*config.permittedEndpoints.toTypedArray()).permitAll()

                // Apply role-based access rules
                config.authorizedEndpoints.forEach { rule ->
                    req.requestMatchers(rule.endpoint)
                        .hasAnyRole(*rule.roles.toTypedArray())
                }

                req.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.FORWARD).permitAll()
                    .anyRequest()
                    .authenticated()

            }
            .exceptionHandling { exception -> exception.authenticationEntryPoint(authEntryPoint) }
            .authenticationProvider(authenticationProvider)
            .sessionManagement { STATELESS }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = securityConfigLoader.loadConfig()
        val corsConfig = config.cors

        val configuration = CorsConfiguration()
        configuration.allowedOriginPatterns = corsConfig.allowedOriginPatterns
        configuration.allowedMethods = corsConfig.allowedMethods
        configuration.allowedHeaders = corsConfig.allowedHeaders
        configuration.allowCredentials = corsConfig.allowCredentials
        configuration.maxAge = corsConfig.maxAge

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

}
