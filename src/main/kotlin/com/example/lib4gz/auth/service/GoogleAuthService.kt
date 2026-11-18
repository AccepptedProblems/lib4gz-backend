package com.example.lib4gz.auth.service

import com.example.lib4gz.auth.model.entity.PendingRegistration
import com.example.lib4gz.auth.model.entity.User
import com.example.lib4gz.auth.model.mapper.toResponse
import com.example.lib4gz.auth.model.mapper.toStartedResponse
import com.example.lib4gz.auth.model.payload.GoogleSignInResponse
import com.example.lib4gz.auth.model.payload.LoginResponse
import com.example.lib4gz.auth.model.payload.UserResponse
import com.example.lib4gz.auth.repo.PendingRegistrationRepo
import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.common.config.security.GoogleIdentity
import com.example.lib4gz.common.config.security.GoogleTokenVerifier
import com.example.lib4gz.common.config.security.JwtProvider
import com.example.lib4gz.common.exception.BusinessException
import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.notification.NotifierService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant

/**
 * Google as an identity. A user is matched by the Google account id (`sub`), never by address:
 * addresses change and get reassigned, the id does not. Sign-in only reads that link; the
 * signed-in person creates or replaces it with [connect].
 */
interface GoogleAuthService {
    /** Signs in the user connected to this Google account, or starts a registration for a new one. */
    fun signIn(idToken: String): Mono<GoogleSignInResponse>

    /**
     * Connects the Google account in [idToken] to [userId], replacing any previous one. The Google
     * id, the email, its verified flag and the avatar all come from Google. Connecting the same
     * account again simply refreshes them.
     */
    fun connect(userId: String, idToken: String): Mono<UserResponse>
}

@Service
class GoogleAuthServiceImpl(
    private val userRepo: UserRepo,
    private val pendingRepo: PendingRegistrationRepo,
    private val verifier: GoogleTokenVerifier,
    private val jwtProvider: JwtProvider,
    private val refreshTokenService: RefreshTokenService,
    private val transactionTemplate: TransactionTemplate,
    private val notifierService: NotifierService,
    @Value("\${application.registration.pending-ttl-ms}") private val pendingTtlMs: Long
) : GoogleAuthService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun signIn(idToken: String): Mono<GoogleSignInResponse> {
        return Mono.fromCallable {
            val identity = verifiedIdentity(idToken)
            val email = normalizedEmail(identity)

            transactionTemplate.execute {
                userRepo.findByGoogleId(identity.sub)?.let { return@execute GoogleSignInResponse(login = login(it)) }

                // Never joined here, whatever the account's verified flag: connecting a Google account
                // is an explicit action by the signed-in owner (see connect).
                if (userRepo.existsByEmailIgnoreCase(email)) {
                    throw BusinessException(
                        "An account with this email already exists. Sign in with your password, then connect Google on your profile."
                    )
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

    override fun connect(userId: String, idToken: String): Mono<UserResponse> {
        return Mono.fromCallable {
            val identity = verifiedIdentity(idToken)
            val email = normalizedEmail(identity)

            val (response, previousEmail) = transactionTemplate.execute {
                val user = userRepo.findById(userId).orElseThrow {
                    ResourceNotFoundException("User not found with id: $userId")
                }
                val owner = userRepo.findByGoogleId(identity.sub)
                if (owner != null && owner.id != user.id) {
                    throw ResponseStatusException(HttpStatus.CONFLICT, "That Google account is already connected to another account")
                }
                if (userRepo.existsByEmailIgnoreCaseAndIdNot(email, user.id)) {
                    throw ResponseStatusException(HttpStatus.CONFLICT, "Email already in use")
                }

                val previousEmail = user.email
                user.googleId = identity.sub
                user.email = email
                user.emailVerified = true
                // The old picture belonged to the old Google account. Google having none is not a reason to blank it.
                identity.picture?.let { user.avatarUrl = it }
                userRepo.save(user).toResponse() to previousEmail
            }!!

            // After the commit: the change stands whether or not the old address can be reached.
            if (!previousEmail.equals(email, ignoreCase = true)) notifyEmailChanged(previousEmail, email)
            response
        }.subscribeOn(Schedulers.boundedElastic())
    }

    /** Outside the transaction: may fetch Google's keys over the network. */
    private fun verifiedIdentity(idToken: String): GoogleIdentity {
        val identity = verifier.verify(idToken)
        if (!identity.emailVerified) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google account email is not verified")
        }
        return identity
    }

    private fun normalizedEmail(identity: GoogleIdentity) = identity.email.trim().lowercase()

    private fun notifyEmailChanged(previousEmail: String, email: String) {
        try {
            notifierService.sendEmailChanged(previousEmail, email)
        } catch (e: Exception) {
            log.warn("Could not tell {} that the account email changed: {}", previousEmail, e.message)
        }
    }

    private fun login(user: User) =
        LoginResponse(user.toResponse(), jwtProvider.generateAccessToken(user), refreshTokenService.issue(user.id))
}
