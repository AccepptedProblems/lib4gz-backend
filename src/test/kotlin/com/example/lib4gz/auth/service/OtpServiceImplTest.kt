package com.example.lib4gz.auth.service

import com.example.lib4gz.auth.model.entity.EmailOtp
import com.example.lib4gz.auth.model.entity.OtpPurpose
import com.example.lib4gz.auth.model.entity.OtpPurpose.LOGIN
import com.example.lib4gz.auth.model.entity.OtpPurpose.REGISTER
import com.example.lib4gz.auth.repo.EmailOtpRepo
import com.example.lib4gz.common.exception.BusinessException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.AdditionalAnswers.returnsFirstArg
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Instant

class OtpServiceImplTest {

    private val repo = mock(EmailOtpRepo::class.java)
    private val service = OtpServiceImpl(
        repo = repo,
        pepper = "pepper",
        ttlMs = 600_000,
        maxAttempts = 5,
        resendCooldownMs = 60_000
    )

    private val now get() = Instant.now().toEpochMilli()

    init {
        `when`(repo.save(anyValue<EmailOtp>())).then(returnsFirstArg<EmailOtp>())
    }

    // ---- issue ----

    @Test
    fun `ttlMinutes reports the configured lifetime, for whoever words the message`() {
        assertEquals(10, service.ttlMinutes)
    }

    @Test
    fun `issue stores a hashed code expiring in ten minutes and returns the plain code`() {
        val (row, code) = issue()

        assertTrue(code.matches(Regex("^\\d{6}$")))
        assertTrue(row.codeHash.matches(Regex("^[0-9a-f]{64}$")))
        assertNotEquals(code, row.codeHash)
        assertEquals(0, row.attempts)
        assertTrue(row.expiresAt in (now + 590_000)..(now + 600_000))
    }

    @Test
    fun `issue after the cooldown reuses the existing row with a fresh code and zero attempts`() {
        val existing = otp(codeHash = "0".repeat(64), attempts = 3, createdAt = now - 61_000)
        `when`(repo.findByEmailAndPurpose("a@x.com", REGISTER)).thenReturn(existing)

        val (row, _) = issue()

        assertSame(existing, row)
        assertNotEquals("0".repeat(64), row.codeHash)
        assertEquals(0, row.attempts)
    }

    @Test
    fun `issue within the cooldown is refused and stores nothing`() {
        `when`(repo.findByEmailAndPurpose("a@x.com", REGISTER)).thenReturn(otp(createdAt = now - 10_000))

        val ex = assertThrows(BusinessException::class.java) { service.issue("a@x.com", REGISTER) }

        assertEquals("Please wait a minute before requesting another code", ex.message)
        verify(repo, never()).save(anyValue())
    }

    // ---- verify ----

    @Test
    fun `verify without a code on record is refused`() {
        val ex = assertThrows(BusinessException::class.java) { service.verify("a@x.com", REGISTER, "123456") }
        assertEquals("Code expired or not found. Request a new one", ex.message)
    }

    @Test
    fun `verify an expired code deletes it and is refused`() {
        val (row, code) = issueAndStore()
        row.expiresAt = now - 1

        val ex = assertThrows(BusinessException::class.java) { service.verify("a@x.com", REGISTER, code) }

        assertEquals("Code expired or not found. Request a new one", ex.message)
        verify(repo).delete(row)
    }

    @Test
    fun `verify a wrong code counts the attempt`() {
        val (row, code) = issueAndStore()

        val ex = assertThrows(BusinessException::class.java) { service.verify("a@x.com", REGISTER, otherCode(code)) }

        assertEquals("Invalid code", ex.message)
        assertEquals(1, row.attempts)
    }

    @Test
    fun `verify after five wrong attempts refuses even the correct code`() {
        val (row, code) = issueAndStore()
        repeat(5) {
            assertThrows(BusinessException::class.java) { service.verify("a@x.com", REGISTER, otherCode(code)) }
        }

        val ex = assertThrows(BusinessException::class.java) { service.verify("a@x.com", REGISTER, code) }

        assertEquals("Too many attempts. Request a new code", ex.message)
        verify(repo).delete(row)
    }

    @Test
    fun `verify the correct code consumes it`() {
        val (row, code) = issueAndStore()

        service.verify("a@x.com", REGISTER, code)

        verify(repo).delete(row)
    }

    @Test
    fun `a code issued for one purpose does not verify another`() {
        val (row, code) = issueAndStore()
        `when`(repo.findByEmailAndPurpose("a@x.com", LOGIN)).thenReturn(
            otp(purpose = LOGIN, codeHash = row.codeHash)
        )

        assertThrows(BusinessException::class.java) { service.verify("a@x.com", LOGIN, code) }
    }

    /** Issues a REGISTER code and returns the saved row plus the code handed back to the caller. */
    private fun issue(): Pair<EmailOtp, String> {
        val code = service.issue("a@x.com", REGISTER)

        val saved = ArgumentCaptor.forClass(EmailOtp::class.java)
        verify(repo, atLeastOnce()).save(saved.capture())

        return saved.value to code
    }

    /** Issues a code and makes the repository return that row on lookup, like the database would. */
    private fun issueAndStore(): Pair<EmailOtp, String> {
        val issued = issue()
        `when`(repo.findByEmailAndPurpose("a@x.com", REGISTER)).thenReturn(issued.first)
        return issued
    }

    private fun otherCode(code: String) = if (code == "000000") "111111" else "000000"

    private fun otp(
        purpose: OtpPurpose = REGISTER,
        codeHash: String = "f".repeat(64),
        attempts: Int = 0,
        createdAt: Long = now,
    ) = EmailOtp(
        email = "a@x.com",
        purpose = purpose,
        codeHash = codeHash,
        attempts = attempts,
        expiresAt = createdAt + 600_000,
        createdAt = createdAt
    )
}
