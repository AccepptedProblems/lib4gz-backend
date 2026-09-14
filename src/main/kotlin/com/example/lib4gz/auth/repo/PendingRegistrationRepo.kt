package com.example.lib4gz.auth.repo

import com.example.lib4gz.auth.model.entity.PendingRegistration
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface PendingRegistrationRepo : JpaRepository<PendingRegistration, String> {
    @Transactional
    @Modifying
    fun deleteByEmail(email: String): Long

    @Transactional
    @Modifying
    fun deleteByExpiresAtBefore(now: Long): Long
}
