package com.example.lib4gz.auth.service

import com.example.lib4gz.auth.model.entity.OtpPurpose
import com.example.lib4gz.auth.model.entity.PendingRegistration
import com.example.lib4gz.auth.model.entity.User
import com.example.lib4gz.auth.model.entity.UserRole
import com.example.lib4gz.auth.model.mapper.toResponse
import com.example.lib4gz.auth.model.mapper.toStartedResponse
import com.example.lib4gz.auth.model.payload.CompleteRegistrationRequest
import com.example.lib4gz.auth.model.payload.LoginResponse
import com.example.lib4gz.auth.model.payload.OtpSentResponse
import com.example.lib4gz.auth.model.payload.RegistrationStartedResponse
import com.example.lib4gz.auth.repo.PendingRegistrationRepo
import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.common.config.security.JwtProvider
import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.ValidationException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant

/**
 * Email sign-up: code → pending registration → account. The Google path joins at [complete].
 *
 * Each body runs in `transactionTemplate.execute` inside the callable: a `@Transactional`
 * on this class would not reach into `Mono.fromCallable`.
 */
interface RegistrationService {
    /** Sends a sign-up code. Calling it again is the resend. */
    fun startEmail(email: String): Mono<OtpSentResponse>
    fun verifyEmail(email: String, code: String): Mono<RegistrationStartedResponse>
    fun complete(request: CompleteRegistrationRequest): Mono<LoginResponse>
}

@Service
class RegistrationServiceImpl(
    private val userRepo: UserRepo,
    private val pendingRepo: PendingRegistrationRepo,
    private val otpService: OtpService,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider,
    private val transactionTemplate: TransactionTemplate,
    @Value("\${application.registration.pending-ttl-ms}") private val pendingTtlMs: Long,
    @Value("\${application.otp.resend-cooldown-ms}") private val resendCooldownMs: Long
) : RegistrationService {

    override fun startEmail(email: String): Mono<OtpSentResponse> {
        return Mono.fromCallable {
            val normalized = normalizeEmail(email)
            transactionTemplate.execute {
                if (userRepo.existsByEmailIgnoreCase(normalized)) {
                    throw ResponseStatusException(HttpStatus.CONFLICT, "Email already in use")
                }
                otpService.issue(normalized, OtpPurpose.REGISTER)
            }
            OtpSentResponse(normalized, resendCooldownMs / 1000)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun verifyEmail(email: String, code: String): Mono<RegistrationStartedResponse> {
        return Mono.fromCallable {
            val normalized = normalizeEmail(email)
            transactionTemplate.execute {
                // Verify first: it commits its own attempt counter (see OtpService.verify).
                otpService.verify(normalized, OtpPurpose.REGISTER, code)
                pendingRepo.deleteByEmail(normalized)
                pendingRepo.save(
                    PendingRegistration(
                        email = normalized,
                        emailVerified = true,
                        expiresAt = Instant.now().toEpochMilli() + pendingTtlMs
                    )
                ).toStartedResponse()
            }!!
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun complete(request: CompleteRegistrationRequest): Mono<LoginResponse> {
        return Mono.fromCallable {
            val pending = pendingRepo.findById(request.registrationId).orElseThrow {
                ResourceNotFoundException("Registration not found or expired")
            }
            if (pending.expiresAt < Instant.now().toEpochMilli()) {
                pendingRepo.delete(pending)
                throw ResourceNotFoundException("Registration not found or expired")
            }

            val username = request.username.trim()
            if (username.length < 3 || !USERNAME_PATTERN.matches(username)) {
                throw ValidationException("Username must be at least 3 letters or digits")
            }
            val password = if (pending.googleId == null) {
                if (request.password == null || request.password.length < 8) {
                    throw ValidationException("Password must be at least 8 characters")
                }
                passwordEncoder.encode(request.password)
            } else null

            transactionTemplate.execute {
                if (userRepo.existsByUsername(username)) {
                    throw ResponseStatusException(HttpStatus.CONFLICT, "Username already taken")
                }
                if (userRepo.existsByEmailIgnoreCase(pending.email)) {
                    throw ResponseStatusException(HttpStatus.CONFLICT, "Email already in use")
                }
                val user = userRepo.save(
                    User(
                        email = pending.email,
                        username = username,
                        name = request.name?.trim().orEmpty().ifBlank { username },
                        password = password,
                        googleId = pending.googleId,
                        avatarUrl = pending.avatarUrl,
                        emailVerified = pending.emailVerified,
                        roles = mutableSetOf(UserRole.USER)
                    )
                )
                pendingRepo.delete(pending)
                LoginResponse(user.toResponse(), jwtProvider.generateAccessToken(user))
            }!!
        }.subscribeOn(Schedulers.boundedElastic())
    }

    private fun normalizeEmail(email: String): String {
        val normalized = email.trim().lowercase()
        if (!normalized.contains('@')) throw ValidationException("Invalid email")
        return normalized
    }

    companion object {
        private val USERNAME_PATTERN = Regex("^[a-zA-Z0-9]+$")
    }
}
