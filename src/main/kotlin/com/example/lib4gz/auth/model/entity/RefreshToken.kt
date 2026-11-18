package com.example.lib4gz.auth.model.entity

import com.example.lib4gz.common.utils.IdGenerator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * One sign-in on one browser. Holds the SHA-256 hex of the token, never the token. Hard-deleted by
 * the theft response in `RefreshTokenService` (`refresh` and `logout`) and by
 * `RefreshTokenCleanupJob`. After insert only the SQL in `RefreshTokenRepo` changes it.
 */
@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Id
    @Column(length = 50)
    val id: String = IdGenerator.generate("rt"),

    @Column(name = "user_id", nullable = false, length = 50)
    val userId: String,

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    val tokenHash: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Long,

    @Column(name = "revoked_at")
    val revokedAt: Long? = null,

    /** Set only when a refresh revoked this row; logout leaves it null. */
    @Column(name = "rotated_at")
    val rotatedAt: Long? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Long = Instant.now().toEpochMilli()
)
