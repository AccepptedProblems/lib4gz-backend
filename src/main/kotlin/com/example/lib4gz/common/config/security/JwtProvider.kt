package com.example.lib4gz.common.config.security

import com.example.lib4gz.auth.model.entity.User
import com.example.lib4gz.auth.model.mapper.toResponse
import com.example.lib4gz.auth.model.payload.Token
import io.jsonwebtoken.*
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.*
import java.util.function.Function
import javax.crypto.SecretKey

@Component
class JwtProvider(
    @Value("\${application.security.jwt.secret-key}") private val secretKey: String,
    @Value("\${application.security.jwt.refresh-token.expiration}") private val refreshExpiration: Long,
    @Value("\${application.security.jwt.expiration}") private val tokenExpiration: Long,
) {
    private fun getSigningKey(): SecretKey {
        val keyBytes = Decoders.BASE64.decode(secretKey)
        return Keys.hmacShaKeyFor(keyBytes)
    }

    fun getUsernameFromToken(token: String): String {
        return getClaimsFromToken(token, Claims::getSubject)
    }

    fun <T> getClaimsFromToken(token: String, claimsResolver: Function<Claims, T>): T {
        val claims = getAllClaimsFromToken(token)
        return claimsResolver.apply(claims)
    }

    private fun getAllClaimsFromToken(token: String): Claims {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parse(token).payload as Claims
    }

    fun getUserIdFromToken(token: String): String {
        val claims = getAllClaimsFromToken(token)
        val userMap = claims["user"] as Map<*, *>
        return userMap["id"] as String
    }

    fun generateAccessToken(user: User): Token {
        val claims: MutableMap<String, Any> = HashMap()
        claims["user"] = user.toResponse()

        return Token(
            token = doGenerateToken(claims, user.username, tokenExpiration),
            expiredAt = Instant.now().toEpochMilli() + tokenExpiration
        )
    }

    fun generateRefreshToken(user: User): String {
        return doGenerateToken(HashMap(), user.username, refreshExpiration)
    }

    private fun doGenerateToken(claims: Map<String, Any>, subject: String, expiration: Long): String {
        return Jwts.builder()
            .claims(claims)
            .subject(subject)
            .issuedAt(Date(System.currentTimeMillis()))
            .expiration(Date(System.currentTimeMillis() + expiration))
            .signWith(getSigningKey())
            .compact()
    }

    fun validateToken(token: String): Boolean {
        val logger = LoggerFactory.getLogger(JwtProvider::class.java);
        try {
            getAllClaimsFromToken(token)
            return true
        } catch (ex: MalformedJwtException) {
            logger.error("Invalid JWT token")
        } catch (ex: ExpiredJwtException) {
            logger.error("Expired JWT token")
        } catch (ex: UnsupportedJwtException) {
            logger.error("Unsupported JWT token")
        } catch (ex: IllegalArgumentException) {
            logger.error("JWT claims string is empty.")
        }
        return false
    }
}