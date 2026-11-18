package com.example.lib4gz.auth.service

import com.example.lib4gz.auth.model.entity.RefreshToken
import com.example.lib4gz.auth.model.mapper.toResponse
import com.example.lib4gz.auth.model.payload.LoginResponse
import com.example.lib4gz.auth.model.payload.Token
import com.example.lib4gz.auth.repo.RefreshTokenRepo
import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.common.config.security.JwtProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

/**
 * Refresh tokens: a random value the browser holds, stored here only as its SHA-256 hex. Each refresh
 * revokes the token it was given and hands out a new one. A revoked token presented again, past the
 * reuse grace, means two holders: every token of that user is deleted. So does a logout with a token a
 * refresh rotated longer ago than the grace.
 */
interface RefreshTokenService {
    /** Blocking: call it inside the callable that builds the sign-in response. */
    fun issue(userId: String): Token
    fun refresh(rawToken: String): Mono<LoginResponse>
    /**
     * Revokes this one token. A token a refresh rotated longer ago than the reuse grace, and not yet
     * expired, deletes every token of its user instead. Never an error: an unknown, expired or already
     * revoked token completes too.
     */
    fun logout(rawToken: String): Mono<Void>
}

@Service
class RefreshTokenServiceImpl(
    private val refreshTokenRepo: RefreshTokenRepo,
    private val userRepo: UserRepo,
    private val jwtProvider: JwtProvider,
    private val transactionTemplate: TransactionTemplate,
    @Value("\${application.security.refresh-token.ttl-ms}") private val ttlMs: Long,
    @Value("\${application.security.refresh-token.reuse-grace-ms}") private val reuseGraceMs: Long
) : RefreshTokenService {

    private val random = SecureRandom()

    override fun issue(userId: String): Token {
        val bytes = ByteArray(32).also(random::nextBytes)
        val raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val expiresAt = Instant.now().toEpochMilli() + ttlMs
        refreshTokenRepo.save(RefreshToken(userId = userId, tokenHash = hash(raw), expiresAt = expiresAt))
        return Token(raw, expiresAt)
    }

    override fun refresh(rawToken: String): Mono<LoginResponse> {
        return Mono.fromCallable {
            val tokenHash = hash(rawToken)
            val now = Instant.now().toEpochMilli()

            // The theft delete must commit, so the 401 is thrown after execute, never inside it.
            val response: LoginResponse? = transactionTemplate.execute {
                // First statement: of two refreshes racing on one token only one UPDATE matches.
                refreshTokenRepo.claim(tokenHash, now)?.let { return@execute signIn(it) }

                val row = refreshTokenRepo.findByTokenHash(tokenHash)
                when {
                    row == null || row.expiresAt <= now -> null
                    row.rotatedAt != null && now - row.rotatedAt <= reuseGraceMs -> signIn(row.userId)
                    else -> {
                        refreshTokenRepo.deleteAllForUser(row.userId)
                        null
                    }
                }
            }
            response ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, SESSION_ENDED)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun logout(rawToken: String): Mono<Void> {
        return Mono.fromCallable {
            val tokenHash = hash(rawToken)
            val now = Instant.now().toEpochMilli()

            // Nothing is thrown, so the theft delete commits and logout still completes.
            transactionTemplate.execute {
                if (refreshTokenRepo.revoke(tokenHash, now) > 0) return@execute

                val row = refreshTokenRepo.findByTokenHash(tokenHash) ?: return@execute
                // Same grace boundary as refresh: a token is never inside it for one and outside it for the other.
                if (row.expiresAt > now && row.rotatedAt != null && now - row.rotatedAt > reuseGraceMs) {
                    refreshTokenRepo.deleteAllForUser(row.userId)
                }
            }
        }
            .subscribeOn(Schedulers.boundedElastic())
            .then()
    }

    private fun signIn(userId: String): LoginResponse {
        val user = userRepo.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.UNAUTHORIZED, SESSION_ENDED)
        }
        return LoginResponse(user.toResponse(), jwtProvider.generateAccessToken(user), issue(user.id))
    }

    private fun hash(raw: String): String =
        MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val SESSION_ENDED = "Your session has ended. Please sign in again."
    }
}
