package com.example.lib4gz.auth.repo

import com.example.lib4gz.auth.model.entity.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface RefreshTokenRepo : JpaRepository<RefreshToken, String> {

    fun findByTokenHash(tokenHash: String): RefreshToken?

    /**
     * Revokes a live, unexpired token for a refresh and returns its user id, or null when no live row
     * matches. One statement, so of two refreshes racing on one token exactly one gets the user id.
     * No `@Transactional` of its own: call it inside the caller's read-write transaction.
     */
    @Query(
        "UPDATE refresh_tokens SET revoked_at = :now, rotated_at = :now " +
            "WHERE token_hash = :hash AND revoked_at IS NULL AND expires_at > :now " +
            "RETURNING user_id",
        nativeQuery = true
    )
    fun claim(hash: String, now: Long): String?

    /** Logout. A token already revoked keeps its first revoke time. */
    @Transactional
    @Modifying
    @Query(
        "UPDATE refresh_tokens SET revoked_at = :now WHERE token_hash = :hash AND revoked_at IS NULL",
        nativeQuery = true
    )
    fun revoke(hash: String, now: Long): Int

    /**
     * Theft response. Deletes, not marks: a deleted token reads as unknown (a plain 401), so no token
     * presented later can trigger another theft response against sign-ins made after this one.
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM refresh_tokens WHERE user_id = :userId", nativeQuery = true)
    fun deleteAllForUser(userId: String): Int

    @Transactional
    @Modifying
    @Query(
        "DELETE FROM refresh_tokens WHERE expires_at <= :now OR revoked_at < :revokedBefore",
        nativeQuery = true
    )
    fun deleteExpiredOrRevokedBefore(now: Long, revokedBefore: Long): Int
}
