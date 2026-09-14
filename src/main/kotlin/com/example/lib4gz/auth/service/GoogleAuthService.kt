package com.example.lib4gz.auth.service

import com.example.lib4gz.auth.model.entity.PendingRegistration
import com.example.lib4gz.auth.model.entity.User
import com.example.lib4gz.auth.model.mapper.toResponse
import com.example.lib4gz.auth.model.mapper.toStartedResponse
import com.example.lib4gz.auth.model.payload.GoogleSignInResponse
import com.example.lib4gz.auth.model.payload.LoginResponse
import com.example.lib4gz.auth.repo.PendingRegistrationRepo
import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.common.config.security.GoogleTokenVerifier
import com.example.lib4gz.common.config.security.JwtProvider
import com.example.lib4gz.common.exception.BusinessException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant

/**
 * Google sign-in. Match by Google id, then by an email the account has verified;
 * otherwise start a registration that finishes on the create-account screen.
 */
interface GoogleAuthService {
    fun signIn(idToken: String): Mono<GoogleSignInResponse>
}

@Service
class GoogleAuthServiceImpl(
    private val userRepo: UserRepo,
    private val pendingRepo: PendingRegistrationRepo,
    private val verifier: GoogleTokenVerifier,
    private val jwtProvider: JwtProvider,
    private val transactionTemplate: TransactionTemplate,
    @Value("\${application.registration.pending-ttl-ms}") private val pendingTtlMs: Long
) : GoogleAuthService {

    override fun signIn(idToken: String): Mono<GoogleSignInResponse> {
        return Mono.fromCallable {
            // Outside the transaction: may fetch Google's keys over the network.
            val identity = verifier.verify(idToken)
            if (!identity.emailVerified) {
                throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google account email is not verified")
            }
            val email = identity.email.trim().lowercase()

            transactionTemplate.execute {
                userRepo.findByGoogleId(identity.sub)?.let { return@execute GoogleSignInResponse(login = login(it)) }

                val existing = userRepo.findByEmailIgnoreCase(email)
                if (existing != null) {
                    if (!existing.emailVerified) {
                        throw BusinessException("Sign in with your password and verify your email in Profile first")
                    }
                    if (existing.googleId != null) {
                        // Linked to another Google account already: never re-link silently.
                        throw BusinessException("This account is linked to a different Google account")
                    }
                    existing.googleId = identity.sub
                    if (existing.avatarUrl == null) existing.avatarUrl = identity.picture
                    return@execute GoogleSignInResponse(login = login(userRepo.save(existing)))
                }

                pendingRepo.deleteByEmail(email)
                val pending = pendingRepo.save(
                    PendingRegistration(
                        email = email,
                        googleId = identity.sub,
                        name = identity.name,
                        avatarUrl = identity.picture,
                        emailVerified = true,
                        expiresAt = Instant.now().toEpochMilli() + pendingTtlMs
                    )
                )
                GoogleSignInResponse(registration = pending.toStartedResponse())
            }!!
        }.subscribeOn(Schedulers.boundedElastic())
    }

    private fun login(user: User) = LoginResponse(user.toResponse(), jwtProvider.generateAccessToken(user))
}
