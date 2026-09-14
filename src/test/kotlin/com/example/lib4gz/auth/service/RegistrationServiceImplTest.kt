package com.example.lib4gz.auth.service

import com.example.lib4gz.auth.model.entity.OtpPurpose.REGISTER
import com.example.lib4gz.auth.model.entity.PendingRegistration
import com.example.lib4gz.auth.model.entity.User
import com.example.lib4gz.auth.model.entity.UserRole
import com.example.lib4gz.auth.model.payload.CompleteRegistrationRequest
import com.example.lib4gz.auth.repo.PendingRegistrationRepo
import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.common.config.security.JwtProvider
import com.example.lib4gz.common.exception.BusinessException
import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.ValidationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.AdditionalAnswers.returnsFirstArg
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional

class RegistrationServiceImplTest {

    private val userRepo = mock(UserRepo::class.java)
    private val pendingRepo = mock(PendingRegistrationRepo::class.java)
    private val otpService = mock(OtpService::class.java)
    private val passwordEncoder = BCryptPasswordEncoder(4)
    private val service = RegistrationServiceImpl(
        userRepo = userRepo,
        pendingRepo = pendingRepo,
        otpService = otpService,
        passwordEncoder = passwordEncoder,
        jwtProvider = JwtProvider(TEST_JWT_SECRET, 60_000, 60_000),
        transactionTemplate = passThroughTransactionTemplate(),
        pendingTtlMs = 900_000,
        resendCooldownMs = 60_000
    )

    private val now get() = Instant.now().toEpochMilli()

    init {
        `when`(userRepo.save(anyValue<User>())).then(returnsFirstArg<User>())
        `when`(pendingRepo.save(anyValue<PendingRegistration>())).then(returnsFirstArg<PendingRegistration>())
    }

    // ---- startEmail ----

    @Test
    fun `startEmail normalizes the address and sends a code`() {
        val response = service.startEmail("  New@X.com ").block()!!

        assertEquals("new@x.com", response.email)
        assertEquals(60, response.cooldownSeconds)
        verify(otpService).issue("new@x.com", REGISTER)
    }

    @Test
    fun `startEmail without @ is rejected`() {
        val ex = assertThrows(ValidationException::class.java) { service.startEmail("not-an-email").block() }
        assertEquals("Invalid email", ex.message)
    }

    @Test
    fun `startEmail for an address already used is 409`() {
        `when`(userRepo.existsByEmailIgnoreCase("taken@x.com")).thenReturn(true)

        val ex = assertThrows(ResponseStatusException::class.java) { service.startEmail("taken@x.com").block() }

        assertEquals(HttpStatus.CONFLICT, ex.statusCode)
        assertEquals("Email already in use", ex.reason)
        verify(otpService, never()).issue(anyValue(), anyValue())
    }

    // ---- verifyEmail ----

    @Test
    fun `verifyEmail with the right code replaces older pending rows and starts a registration`() {
        val response = service.verifyEmail("New@x.com", "123456").block()!!

        verify(otpService).verify("new@x.com", REGISTER, "123456")
        verify(pendingRepo).deleteByEmail("new@x.com")
        val saved = savedPending()
        assertTrue(saved.emailVerified)
        assertNull(saved.googleId)
        assertTrue(saved.expiresAt in (now + 890_000)..(now + 900_000))
        assertEquals(saved.id, response.registrationId)
        assertTrue(response.requiresPassword)
    }

    @Test
    fun `verifyEmail with a bad code starts nothing`() {
        doThrow(BusinessException("Invalid code")).`when`(otpService).verify("new@x.com", REGISTER, "000000")

        assertThrows(BusinessException::class.java) { service.verifyEmail("new@x.com", "000000").block() }

        verify(pendingRepo, never()).save(anyValue())
    }

    // ---- complete ----

    @Test
    fun `complete on the email path creates the user, drops the pending row, and logs in`() {
        val pending = pending()

        val response = service.complete(
            CompleteRegistrationRequest(pending.id, username = " alice ", name = "  ", password = "longenough")
        ).block()!!

        val user = savedUser()
        assertEquals("alice", user.username)
        assertEquals("alice", user.name)
        assertEquals("new@x.com", user.email)
        assertTrue(user.emailVerified)
        assertEquals(setOf(UserRole.USER), user.roles)
        assertTrue(passwordEncoder.matches("longenough", user.password))
        verify(pendingRepo).delete(pending)
        assertEquals(user.id, response.user.id)
        assertTrue(response.accessToken.token.isNotBlank())
    }

    @Test
    fun `complete on the Google path needs no password and keeps the Google details`() {
        val pending = pending(googleId = "g-123", name = "Alice Google", avatarUrl = "https://pic")

        service.complete(CompleteRegistrationRequest(pending.id, username = "alice", name = "Alice Google")).block()

        val user = savedUser()
        assertEquals("g-123", user.googleId)
        assertEquals("https://pic", user.avatarUrl)
        assertEquals("Alice Google", user.name)
        assertNull(user.password)
    }

    @Test
    fun `complete with an unknown id is 404`() {
        `when`(pendingRepo.findById("reg_missing")).thenReturn(Optional.empty())

        val ex = assertThrows(ResourceNotFoundException::class.java) {
            service.complete(CompleteRegistrationRequest("reg_missing", "alice", password = "longenough")).block()
        }
        assertEquals("Registration not found or expired", ex.message)
    }

    @Test
    fun `complete with an expired registration deletes it and is 404`() {
        val pending = pending(expiresAt = now - 1)

        assertThrows(ResourceNotFoundException::class.java) {
            service.complete(CompleteRegistrationRequest(pending.id, "alice", password = "longenough")).block()
        }
        verify(pendingRepo).delete(pending)
    }

    @ParameterizedTest
    @ValueSource(strings = ["al", "alice!", "ali ce", ""])
    fun `complete rejects usernames that are short or not alphanumeric`(username: String) {
        val pending = pending()

        assertThrows(ValidationException::class.java) {
            service.complete(CompleteRegistrationRequest(pending.id, username, password = "longenough")).block()
        }
        verify(userRepo, never()).save(anyValue())
    }

    @Test
    fun `complete with a taken username is 409`() {
        val pending = pending()
        `when`(userRepo.existsByUsername("alice")).thenReturn(true)

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.complete(CompleteRegistrationRequest(pending.id, "alice", password = "longenough")).block()
        }
        assertEquals(HttpStatus.CONFLICT, ex.statusCode)
        assertEquals("Username already taken", ex.reason)
    }

    @Test
    fun `complete when the email got taken meanwhile is 409`() {
        val pending = pending()
        `when`(userRepo.existsByEmailIgnoreCase("new@x.com")).thenReturn(true)

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.complete(CompleteRegistrationRequest(pending.id, "alice", password = "longenough")).block()
        }
        assertEquals(HttpStatus.CONFLICT, ex.statusCode)
        assertEquals("Email already in use", ex.reason)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "short"])
    fun `complete on the email path requires a password of at least 8 characters`(password: String) {
        val pending = pending()

        assertThrows(ValidationException::class.java) {
            service.complete(CompleteRegistrationRequest(pending.id, "alice", password = password)).block()
        }
        verify(userRepo, never()).save(anyValue())
    }

    private fun pending(
        googleId: String? = null,
        name: String? = null,
        avatarUrl: String? = null,
        expiresAt: Long = now + 900_000
    ): PendingRegistration {
        val row = PendingRegistration(
            email = "new@x.com",
            googleId = googleId,
            name = name,
            avatarUrl = avatarUrl,
            emailVerified = true,
            expiresAt = expiresAt
        )
        `when`(pendingRepo.findById(row.id)).thenReturn(Optional.of(row))
        return row
    }

    private fun savedPending(): PendingRegistration {
        val captor = ArgumentCaptor.forClass(PendingRegistration::class.java)
        verify(pendingRepo).save(captor.capture())
        return captor.value
    }

    private fun savedUser(): User {
        val captor = ArgumentCaptor.forClass(User::class.java)
        verify(userRepo).save(captor.capture())
        return captor.value
    }
}
