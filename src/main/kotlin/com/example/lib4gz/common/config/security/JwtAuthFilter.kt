package com.example.lib4gz.common.config.security

import com.example.lib4gz.common.config.common.MutableHttpServletRequest
import com.example.lib4gz.common.config.common.PZRequestHeader
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils.hasText
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.HandlerExceptionResolver
import kotlin.jvm.Throws

@Component
class JwtAuthFilter(
    val tokenProvider: JwtProvider,
    val userDetailsService: CustomUserDetailService,
    @Qualifier("handlerExceptionResolver") val resolver: HandlerExceptionResolver,
): OncePerRequestFilter() {
    @Throws(ResponseStatusException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (isAuthRequest(request)) {
            filterChain.doFilter(request, response)
            return
        }

        val wrappedRequest = clearCustomHeaders(request)
        try {
            val jwt = getJwtFromRequest(request)
            if (hasText(jwt) && tokenProvider.validateToken(jwt!!)) {
                addUserIdHeader(wrappedRequest, jwt)
                setAuthenticateForToken(wrappedRequest, jwt)
            }
        } catch (ex: Exception) {
            val log: Logger = LoggerFactory.getLogger(JwtAuthFilter::class.java)
            log.error("failed on set user authentication", ex)
        }
        filterChain.doFilter(wrappedRequest, response)
    }

    private fun clearCustomHeaders(request: HttpServletRequest): MutableHttpServletRequest {
        val wrappedRequest = MutableHttpServletRequest(request)
        wrappedRequest.removeHeader(PZRequestHeader.USER_ID)
        return wrappedRequest
    }

    private fun addUserIdHeader(wrappedRequest: MutableHttpServletRequest, jwt: String) {
        val userId = tokenProvider.getUserIdFromToken(jwt)
        wrappedRequest.addHeader(PZRequestHeader.USER_ID, userId)
    }

    private fun isAuthRequest(request: HttpServletRequest) =
        request.servletPath.contains("/v1/register") || request.servletPath.contains("/v1/login")


    private fun setAuthenticateForToken(request: HttpServletRequest, jwt: String) {
        val username = tokenProvider.getUsernameFromToken(jwt)
        val userDetails: UserDetails = userDetailsService.loadUserByUsername(username)
        val authentication = UsernamePasswordAuthenticationToken(
            userDetails, null,
            userDetails.authorities
        )
        authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
        SecurityContextHolder.getContext().authentication = authentication
    }

    private fun getJwtFromRequest(request: HttpServletRequest): String? {
        val token = request.getHeader("access_token")
        return token
    }

}