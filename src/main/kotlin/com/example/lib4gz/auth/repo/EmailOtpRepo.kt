package com.example.lib4gz.auth.repo

import com.example.lib4gz.auth.model.entity.EmailOtp
import com.example.lib4gz.auth.model.entity.OtpPurpose
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface EmailOtpRepo : JpaRepository<EmailOtp, String> {
    fun findByEmailAndPurpose(email: String, purpose: OtpPurpose): EmailOtp?

    @Transactional
    @Modifying
    fun deleteByExpiresAtBefore(now: Long): Long
}
