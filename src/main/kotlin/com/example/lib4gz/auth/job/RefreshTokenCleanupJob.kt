package com.example.lib4gz.auth.job

import com.example.lib4gz.auth.repo.RefreshTokenRepo
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** Removes refresh tokens that expired, or were revoked longer ago than the retention, once a day. */
@Component
class RefreshTokenCleanupJob(
    private val refreshTokenRepo: RefreshTokenRepo,
    @Value("\${application.security.refresh-token.revoked-retention-ms}") private val revokedRetentionMs: Long
) {
    private val logger = LoggerFactory.getLogger(RefreshTokenCleanupJob::class.java)

    @Scheduled(fixedDelay = 86_400_000)
    @Transactional
    fun sweep() {
        val now = Instant.now().toEpochMilli()
        val removed = refreshTokenRepo.deleteExpiredOrRevokedBefore(now, now - revokedRetentionMs)
        if (removed > 0) {
            logger.info("Refresh token cleanup removed {} rows", removed)
        }
    }
}
