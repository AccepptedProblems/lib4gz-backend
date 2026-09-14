package com.example.lib4gz.auth.model.entity

import com.example.lib4gz.common.utils.IdGenerator
import jakarta.persistence.*
import java.time.Instant

/**
 * A sign-up that proved its email (by code or by Google) but has not chosen a username yet.
 * The id is the only credential the browser holds. Hard-deleted on completion or expiry.
 */
@Entity
@Table(name = "pending_registrations")
class PendingRegistration(
    @Id
    @Column(length = 50)
    val id: String = IdGenerator.generate("reg"),

    @Column(nullable = false, length = 255)
    val email: String,

    @Column(name = "google_id", length = 255)
    val googleId: String? = null,

    @Column(length = 255)
    val name: String? = null,

    @Column(name = "avatar_url", columnDefinition = "text")
    val avatarUrl: String? = null,

    @Column(name = "email_verified", nullable = false)
    val emailVerified: Boolean,

    @Column(name = "created_at", nullable = false)
    val createdAt: Long = Instant.now().toEpochMilli(),

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Long
)
