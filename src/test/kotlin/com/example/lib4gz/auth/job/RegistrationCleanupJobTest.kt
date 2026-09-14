package com.example.lib4gz.auth.job

import com.example.lib4gz.auth.repo.EmailOtpRepo
import com.example.lib4gz.auth.repo.PendingRegistrationRepo
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.time.Instant

class RegistrationCleanupJobTest {

    private val otpRepo = mock(EmailOtpRepo::class.java)
    private val pendingRepo = mock(PendingRegistrationRepo::class.java)
    private val job = RegistrationCleanupJob(otpRepo, pendingRepo)

    @Test
    fun `sweep deletes rows that expired before now in both tables`() {
        val before = Instant.now().toEpochMilli()

        job.sweep()

        val after = Instant.now().toEpochMilli()
        val otpCutoff = ArgumentCaptor.forClass(Long::class.java)
        val pendingCutoff = ArgumentCaptor.forClass(Long::class.java)
        verify(otpRepo).deleteByExpiresAtBefore(otpCutoff.capture())
        verify(pendingRepo).deleteByExpiresAtBefore(pendingCutoff.capture())
        assertTrue(otpCutoff.value in before..after)
        assertTrue(pendingCutoff.value in before..after)
    }
}
