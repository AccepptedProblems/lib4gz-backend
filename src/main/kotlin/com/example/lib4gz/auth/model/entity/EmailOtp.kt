package com.example.lib4gz.auth.model.entity

import com.example.lib4gz.common.utils.IdGenerator
import jakarta.persistence.*
import java.time.Instant

/**
 * One live one-time code per (email, purpose). Hard-deleted: codes are transient credentials.
 * Reissuing a code overwrites the row in place (see OtpService.issue).
 */
@Entity
@Table(
    name = "email_otps",
    uniqueConstraints = [UniqueConstraint(columnNames = ["email", "purpose"])]
)
class EmailOtp(
    @Id
    @Column(length = 50)
    val id: String = IdGenerator.generate("otp"),

    @Column(nullable = false, length = 255)
    val email: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val purpose: OtpPurpose,

    @Column(name = "code_hash", nullable = false, length = 64)
    var codeHash: String,

    @Column(nullable = false)
    var attempts: Int = 0,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Long,

    @Column(name = "created_at", nullable = false)
    var createdAt: Long = Instant.now().toEpochMilli()
)

enum class OtpPurpose {
    REGISTER,
    EMAIL_CHANGE,
    LOGIN,
    PASSWORD_RESET
}
