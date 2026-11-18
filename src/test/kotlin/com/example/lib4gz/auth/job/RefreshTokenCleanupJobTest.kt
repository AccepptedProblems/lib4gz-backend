package com.example.lib4gz.auth.job

import com.example.lib4gz.auth.repo.RefreshTokenRepo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.time.Instant

class RefreshTokenCleanupJobTest {

    private val repo = mock(RefreshTokenRepo::class.java)
    private val job = RefreshTokenCleanupJob(repo, revokedRetentionMs = 604_800_000)

    @Test
    fun `sweep deletes expired rows and rows revoked over the retention ago`() {
        val before = Instant.now().toEpochMilli()

        job.sweep()

        val after = Instant.now().toEpochMilli()
        val now = ArgumentCaptor.forClass(Long::class.java)
        val revokedBefore = ArgumentCaptor.forClass(Long::class.java)
        verify(repo).deleteExpiredOrRevokedBefore(now.capture(), revokedBefore.capture())
        assertTrue(now.value in before..after)
        assertEquals(now.value - 604_800_000, revokedBefore.value)
    }
}
