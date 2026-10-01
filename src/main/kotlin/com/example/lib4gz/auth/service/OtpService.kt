package com.example.lib4gz.auth.service

import com.example.lib4gz.auth.model.entity.EmailOtp
import com.example.lib4gz.auth.model.entity.OtpPurpose
import com.example.lib4gz.auth.repo.EmailOtpRepo
import com.example.lib4gz.common.exception.BusinessException
import com.example.lib4gz.common.utils.IdGenerator
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant

/**
 * Six-digit email codes. Blocking on purpose: callers invoke these inside their own
 * `Mono.fromCallable` + `TransactionTemplate` block.
 */
interface OtpService {
    /** How long an issued code stays valid, for whoever words the message that carries it. */
    val ttlMinutes: Long

    /**
     * Stores a fresh code for (email, purpose) and returns the plain code, for the caller to hand
     * to `NotifierService`. Throws while the resend cooldown runs. Callers must never log it.
     */
    fun issue(email: String, purpose: OtpPurpose): String

    /** Consumes the code when it matches; otherwise throws [BusinessException]. */
    fun verify(email: String, purpose: OtpPurpose, code: String)
}

@Service
@Transactional
class OtpServiceImpl(
    private val repo: EmailOtpRepo,
    @Value("\${application.security.jwt.secret-key}") private val pepper: String,
    @Value("\${application.otp.ttl-ms}") private val ttlMs: Long,
    @Value("\${application.otp.max-attempts}") private val maxAttempts: Int,
    @Value("\${application.otp.resend-cooldown-ms}") private val resendCooldownMs: Long
) : OtpService {

    override val ttlMinutes: Long get() = ttlMs / 60_000

    override fun issue(email: String, purpose: OtpPurpose): String {
        val now = Instant.now().toEpochMilli()
        val existing = repo.findByEmailAndPurpose(email, purpose)
        if (existing != null && now - existing.createdAt < resendCooldownMs) {
            throw BusinessException("Please wait a minute before requesting another code")
        }

        val code = IdGenerator.generateNumericCode(6)
        // Reuse the row instead of delete + insert: Hibernate flushes inserts before deletes,
        // which would trip the (email, purpose) unique constraint.
        val otp = existing ?: EmailOtp(email = email, purpose = purpose, codeHash = "", expiresAt = 0)
        otp.codeHash = hash(email, purpose, code)
        otp.attempts = 0
        otp.createdAt = now
        otp.expiresAt = now + ttlMs
        repo.save(otp)

        return code
    }

    // REQUIRES_NEW + noRollbackFor: a wrong guess must commit `attempts++` even though it throws
    // and the caller's transaction rolls back. Callers verify before any other write.
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = [BusinessException::class])
    override fun verify(email: String, purpose: OtpPurpose, code: String) {
        val otp = repo.findByEmailAndPurpose(email, purpose)
            ?: throw BusinessException("Code expired or not found. Request a new one")

        if (otp.expiresAt < Instant.now().toEpochMilli()) {
            repo.delete(otp)
            throw BusinessException("Code expired or not found. Request a new one")
        }
        if (otp.attempts >= maxAttempts) {
            repo.delete(otp)
            throw BusinessException("Too many attempts. Request a new code")
        }
        if (!MessageDigest.isEqual(otp.codeHash.toByteArray(), hash(email, purpose, code.trim()).toByteArray())) {
            otp.attempts++
            repo.save(otp)
            throw BusinessException("Invalid code")
        }
        repo.delete(otp)
    }

    // A 6-digit code has 10^6 values; the attempt cap and TTL stop brute force, not hash cost.
    private fun hash(email: String, purpose: OtpPurpose, code: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$pepper:$email:$purpose:$code".toByteArray())
            .joinToString("") { "%02x".format(it) }
}
