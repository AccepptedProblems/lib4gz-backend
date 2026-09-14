package com.example.lib4gz.auth.job

import com.example.lib4gz.auth.repo.EmailOtpRepo
import com.example.lib4gz.auth.repo.PendingRegistrationRepo
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** Removes expired one-time codes and unfinished registrations every ten minutes. */
@Component
class RegistrationCleanupJob(
    private val otpRepo: EmailOtpRepo,
    private val pendingRepo: PendingRegistrationRepo
) {
    private val logger = LoggerFactory.getLogger(RegistrationCleanupJob::class.java)

    @Scheduled(fixedDelay = 600_000)
    @Transactional
    fun sweep() {
        val now = Instant.now().toEpochMilli()
        val otps = otpRepo.deleteByExpiresAtBefore(now)
        val pending = pendingRepo.deleteByExpiresAtBefore(now)
        if (otps > 0 || pending > 0) {
            logger.info("Registration cleanup removed {} expired codes and {} expired registrations", otps, pending)
        }
    }
}
