package com.example.lib4gz.auth.service

import com.example.lib4gz.auth.model.entity.PendingRegistration
import com.example.lib4gz.auth.model.entity.User
import com.example.lib4gz.auth.repo.PendingRegistrationRepo
import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.common.config.security.GoogleIdentity
import com.example.lib4gz.common.config.security.GoogleTokenVerifier
import com.example.lib4gz.common.config.security.JwtProvider
import com.example.lib4gz.common.exception.BusinessException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.AdditionalAnswers.returnsFirstArg
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class GoogleAuthServiceImplTest {

    private val userRepo = mock(UserRepo::class.java)
    private val pendingRepo = mock(PendingRegistrationRepo::class.java)
    private val verifier = mock(GoogleTokenVerifier::class.java)
    private val service = GoogleAuthServiceImpl(
        userRepo = userRepo,
        pendingRepo = pendingRepo,
        verifier = verifier,
        jwtProvider = JwtProvider(TEST_JWT_SECRET, 60_000, 60_000),
        transactionTemplate = passThroughTransactionTemplate(),
        pendingTtlMs = 900_000
    )

    private val identity = GoogleIdentity(
        sub = "g-123",
        email = "Alice@Gmail.com",
        emailVerified = true,
        name = "Alice Google",
        picture = "https://pic"
    )

    init {
        `when`(userRepo.save(anyValue<User>())).then(returnsFirstArg<User>())
        `when`(pendingRepo.save(anyValue<PendingRegistration>())).then(returnsFirstArg<PendingRegistration>())
    }

    @Test
    fun `an invalid token is 401`() {
        `when`(verifier.verify("junk")).thenThrow(ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token"))

        val ex = assertThrows(ResponseStatusException::class.java) { service.signIn("junk").block() }

        assertEquals(HttpStatus.UNAUTHORIZED, ex.statusCode)
    }

    @Test
    fun `a Google account whose email Google has not verified is 401`() {
        `when`(verifier.verify("token")).thenReturn(identity.copy(emailVerified = false))

        val ex = assertThrows(ResponseStatusException::class.java) { service.signIn("token").block() }

        assertEquals(HttpStatus.UNAUTHORIZED, ex.statusCode)
        assertEquals("Google account email is not verified", ex.reason)
    }

    @Test
    fun `a user already linked to the Google id logs in`() {
        `when`(verifier.verify("token")).thenReturn(identity)
        `when`(userRepo.findByGoogleId("g-123")).thenReturn(user(googleId = "g-123"))

        val response = service.signIn("token").block()!!

        assertEquals("usr_alice", response.login!!.user.id)
        assertNull(response.registration)
    }

    @Test
    fun `a verified account with the same email gets linked and logs in`() {
        val alice = user(emailVerified = true, avatarUrl = null)
        `when`(verifier.verify("token")).thenReturn(identity)
        `when`(userRepo.findByEmailIgnoreCase("alice@gmail.com")).thenReturn(alice)

        val response = service.signIn("token").block()!!

        assertEquals("g-123", alice.googleId)
        assertEquals("https://pic", alice.avatarUrl)
        verify(userRepo).save(alice)
        assertEquals("usr_alice", response.login!!.user.id)
    }

    @Test
    fun `a verified account already linked to a different Google account is not re-linked`() {
        val alice = user(googleId = "g-other", emailVerified = true)
        `when`(verifier.verify("token")).thenReturn(identity)
        `when`(userRepo.findByEmailIgnoreCase("alice@gmail.com")).thenReturn(alice)

        val ex = assertThrows(BusinessException::class.java) { service.signIn("token").block() }

        assertEquals("This account is linked to a different Google account", ex.message)
        assertEquals("g-other", alice.googleId)
        verify(userRepo, never()).save(anyValue())
    }

    @Test
    fun `linking keeps an avatar the account already has`() {
        val alice = user(emailVerified = true, avatarUrl = "https://mine")
        `when`(verifier.verify("token")).thenReturn(identity)
        `when`(userRepo.findByEmailIgnoreCase("alice@gmail.com")).thenReturn(alice)

        service.signIn("token").block()

        assertEquals("https://mine", alice.avatarUrl)
    }

    @Test
    fun `an unverified account with the same email must verify in Profile first`() {
        `when`(verifier.verify("token")).thenReturn(identity)
        `when`(userRepo.findByEmailIgnoreCase("alice@gmail.com")).thenReturn(user(emailVerified = false))

        val ex = assertThrows(BusinessException::class.java) { service.signIn("token").block() }

        assertEquals("Sign in with your password and verify your email in Profile first", ex.message)
        verify(userRepo, never()).save(anyValue())
    }

    @Test
    fun `a new Google account starts a registration without a password`() {
        `when`(verifier.verify("token")).thenReturn(identity)

        val response = service.signIn("token").block()!!

        verify(pendingRepo).deleteByEmail("alice@gmail.com")
        val captor = ArgumentCaptor.forClass(PendingRegistration::class.java)
        verify(pendingRepo).save(captor.capture())
        val pending = captor.value
        assertEquals("g-123", pending.googleId)
        assertEquals("Alice Google", pending.name)
        assertEquals("https://pic", pending.avatarUrl)
        assertTrue(pending.emailVerified)

        assertNull(response.login)
        val registration = response.registration!!
        assertEquals(pending.id, registration.registrationId)
        assertEquals("Alice Google", registration.name)
        assertFalse(registration.requiresPassword)
    }

    private fun user(googleId: String? = null, emailVerified: Boolean = false, avatarUrl: String? = null) = User(
        id = "usr_alice",
        email = "alice@gmail.com",
        username = "alice",
        name = "Alice",
        googleId = googleId,
        emailVerified = emailVerified,
        avatarUrl = avatarUrl
    )
}
