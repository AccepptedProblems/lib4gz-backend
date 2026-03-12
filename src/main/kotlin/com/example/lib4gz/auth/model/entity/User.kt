package com.example.lib4gz.auth.model.entity

import jakarta.persistence.*
import org.hibernate.annotations.SQLDelete
import java.time.Instant
import com.example.lib4gz.common.utils.IdGenerator

@Entity
@Table(name = "users")
@SQLDelete(sql = "UPDATE users SET deleted_at = (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint WHERE id = ?")
data class User(
    @Id
    @Column(length = 50)
    val id: String = IdGenerator.generate("usr"),

    @Column(nullable = true, unique = true, length = 255)
    val email: String,

    @Column(nullable = false, unique = true, length = 255)
    val username: String,

    @Column(name = "password_hash", length = 255)
    val password: String? = null,

    @Column(nullable = false, length = 255)
    var name: String,

    @Column(name = "avatar_url")
    var avatarUrl: String? = null,

    @ElementCollection(targetClass = UserRole::class, fetch = FetchType.EAGER)
    @CollectionTable(name = "user_global_roles", joinColumns = [JoinColumn(name = "user_id")])
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    val roles: MutableSet<UserRole> = mutableSetOf(UserRole.USER),

    @Column(name = "email_verified", nullable = false)
    var emailVerified: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: UserStatus = UserStatus.ACTIVE,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Long = Instant.now().toEpochMilli(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Long = Instant.now().toEpochMilli(),

    @Column(name = "deleted_at")
    var deletedAt: Long? = null
) {
    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now().toEpochMilli()
    }

    fun hasRole(role: UserRole): Boolean = roles.contains(role)
    fun isAdmin(): Boolean = hasRole(UserRole.ADMIN)
    fun isTeacher(): Boolean = hasRole(UserRole.TEACHER)
}

enum class UserRole {
    ADMIN,
    USER,
    TEACHER
}

enum class UserStatus {
    ACTIVE,
    INACTIVE,
    BANNED
}