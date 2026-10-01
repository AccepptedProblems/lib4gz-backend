package com.example.lib4gz.auth.service

import com.example.lib4gz.auth.model.entity.PendingRegistration
import com.example.lib4gz.auth.model.entity.User
import com.example.lib4gz.auth.repo.PendingRegistrationRepo
import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.common.config.security.GoogleIdentity
import com.example.lib4gz.common.config.security.GoogleTokenVerifier
import com.example.lib4gz.common.config.security.JwtProvider
import com.example.lib4gz.common.exception.BusinessException
import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.notification.NotifierService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.AdditionalAnswers.returnsFirstArg
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional

class GoogleAuthServiceImplTest {

    private val userRepo = mock(UserRepo::class.java)
    private val pendingRepo = mock(PendingRegistrationRepo::class.java)
    private val verifier = mock(GoogleTokenVerifier::class.java)
    private val notifierService = mock(NotifierService::class.java)
    private val service = GoogleAuthServiceImpl(
        userRepo = userRepo,
        pendingRepo = pendingRepo,
        verifier = verifier,
        jwtProvider = JwtProvider(TEST_JWT_SECRET, 60_000, 60_000),
        transactionTemplate = passThroughTransactionTemplate(),
        notifierService = notifierService,
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

    @Nested
    inner class SignIn {

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
        fun `the user connected to the Google id logs in`() {
            `when`(verifier.verify("token")).thenReturn(identity)
            `when`(userRepo.findByGoogleId("g-123")).thenReturn(user(googleId = "g-123"))

            val response = service.signIn("token").block()!!

            assertEquals("usr_alice", response.login!!.user.id)
            assertNull(response.registration)
        }

        @Test
        fun `an existing account with the same email is refused, verified or not, and never linked`() {
            `when`(verifier.verify("token")).thenReturn(identity)
            `when`(userRepo.existsByEmailIgnoreCase("alice@gmail.com")).thenReturn(true)

            val ex = assertThrows(BusinessException::class.java) { service.signIn("token").block() }

            assertEquals(
                "An account with this email already exists. Sign in with your password, then connect Google on your profile.",
                ex.message
            )
            verify(userRepo, never()).save(anyValue())
            verify(pendingRepo, never()).save(anyValue())
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
    }

    @Nested
    inner class Connect {

        @Test
        fun `connects the Google account, taking id, email, verified flag and avatar from Google`() {
            val alice = user(email = "old@x.com", emailVerified = false, avatarUrl = "https://old", password = "hash")
            `when`(verifier.verify("token")).thenReturn(identity)
            `when`(userRepo.findById("usr_alice")).thenReturn(Optional.of(alice))

            val response = service.connect("usr_alice", "token").block()!!

            assertEquals("g-123", alice.googleId)
            assertEquals("alice@gmail.com", alice.email)
            assertTrue(alice.emailVerified)
            assertEquals("https://pic", alice.avatarUrl)
            assertEquals("hash", alice.password)
            assertEquals("Alice", alice.name)
            verify(userRepo).save(alice)
            assertEquals("alice@gmail.com", response.email)
            assertTrue(response.googleConnected)
            verify(notifierService).sendEmailChanged("old@x.com", "alice@gmail.com")
        }

        @Test
        fun `keeps the avatar when Google has no picture`() {
            val alice = user(avatarUrl = "https://mine")
            `when`(verifier.verify("token")).thenReturn(identity.copy(picture = null))
            `when`(userRepo.findById("usr_alice")).thenReturn(Optional.of(alice))

            service.connect("usr_alice", "token").block()

            assertEquals("https://mine", alice.avatarUrl)
        }

        @Test
        fun `the same account again succeeds, refreshes from Google and sends no mail`() {
            val alice = user(googleId = "g-123", email = "alice@gmail.com", emailVerified = true, avatarUrl = "https://old")
            `when`(verifier.verify("token")).thenReturn(identity)
            `when`(userRepo.findById("usr_alice")).thenReturn(Optional.of(alice))
            `when`(userRepo.findByGoogleId("g-123")).thenReturn(alice)

            val response = service.connect("usr_alice", "token").block()!!

            assertEquals("https://pic", alice.avatarUrl)
            assertTrue(response.googleConnected)
            verify(notifierService, never()).sendEmailChanged(anyValue(), anyValue())
        }

        @Test
        fun `a failure to mail the old address does not undo the connect`() {
            val alice = user(email = "old@x.com")
            `when`(verifier.verify("token")).thenReturn(identity)
            `when`(userRepo.findById("usr_alice")).thenReturn(Optional.of(alice))
            doThrow(ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "mail down"))
                .`when`(notifierService).sendEmailChanged("old@x.com", "alice@gmail.com")

            val response = service.connect("usr_alice", "token").block()!!

            assertEquals("alice@gmail.com", response.email)
            verify(userRepo).save(alice)
        }

        @Test
        fun `a Google account connected to another user is 409`() {
            val alice = user()
            `when`(verifier.verify("token")).thenReturn(identity)
            `when`(userRepo.findById("usr_alice")).thenReturn(Optional.of(alice))
            `when`(userRepo.findByGoogleId("g-123")).thenReturn(user(id = "usr_bob", googleId = "g-123"))

            val ex = assertThrows(ResponseStatusException::class.java) { service.connect("usr_alice", "token").block() }

            assertEquals(HttpStatus.CONFLICT, ex.statusCode)
            assertEquals("That Google account is already connected to another account", ex.reason)
            assertNull(alice.googleId)
            verify(userRepo, never()).save(anyValue())
        }

        @Test
        fun `a Google address owned by another account is 409`() {
            val alice = user()
            `when`(verifier.verify("token")).thenReturn(identity)
            `when`(userRepo.findById("usr_alice")).thenReturn(Optional.of(alice))
            `when`(userRepo.existsByEmailIgnoreCaseAndIdNot("alice@gmail.com", "usr_alice")).thenReturn(true)

            val ex = assertThrows(ResponseStatusException::class.java) { service.connect("usr_alice", "token").block() }

            assertEquals(HttpStatus.CONFLICT, ex.statusCode)
            assertEquals("Email already in use", ex.reason)
            verify(userRepo, never()).save(anyValue())
        }

        @Test
        fun `a Google account whose email Google has not verified is 401`() {
            `when`(verifier.verify("token")).thenReturn(identity.copy(emailVerified = false))

            val ex = assertThrows(ResponseStatusException::class.java) { service.connect("usr_alice", "token").block() }

            assertEquals(HttpStatus.UNAUTHORIZED, ex.statusCode)
            verify(userRepo, never()).findById(anyValue())
        }

        @Test
        fun `an unknown user is 404`() {
            `when`(verifier.verify("token")).thenReturn(identity)
            `when`(userRepo.findById("usr_gone")).thenReturn(Optional.empty())

            assertThrows(ResourceNotFoundException::class.java) { service.connect("usr_gone", "token").block() }
        }
    }

    private fun user(
        id: String = "usr_alice",
        email: String = "alice@gmail.com",
        googleId: String? = null,
        emailVerified: Boolean = false,
        avatarUrl: String? = null,
        password: String? = null
    ) = User(
        id = id,
        email = email,
        username = "alice",
        name = "Alice",
        password = password,
        googleId = googleId,
        emailVerified = emailVerified,
        avatarUrl = avatarUrl
    )
}
