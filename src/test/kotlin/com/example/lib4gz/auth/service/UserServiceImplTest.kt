package com.example.lib4gz.auth.service

import com.example.lib4gz.auth.model.entity.OtpPurpose
import com.example.lib4gz.auth.model.entity.User
import com.example.lib4gz.auth.model.entity.UserRole
import com.example.lib4gz.auth.model.payload.UpdateMeRequest
import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.common.exception.BusinessException
import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.ValidationException
import com.example.lib4gz.courses.model.entity.Visibility
import com.example.lib4gz.courses.model.payload.CourseResponse
import com.example.lib4gz.courses.model.payload.UserSummary
import com.example.lib4gz.courses.service.CourseService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.AdditionalAnswers.returnsFirstArg
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import java.util.Optional

class UserServiceImplTest {

    private val userRepo = mock(UserRepo::class.java)
    private val courseService = mock(CourseService::class.java)
    private val otpService = mock(OtpService::class.java)
    private val service = UserServiceImpl(
        userRepo = userRepo,
        courseService = courseService,
        otpService = otpService,
        transactionTemplate = passThroughTransactionTemplate(),
        resendCooldownMs = 60_000
    )

    private val alice = User(id = "usr_alice", email = "alice@x.com", username = "alice", name = "")

    @Test
    fun `getMe falls back to username when name is blank and reports the highest role`() {
        alice.roles.addAll(listOf(UserRole.TEACHER, UserRole.ADMIN))
        `when`(userRepo.findById("usr_alice")).thenReturn(Optional.of(alice))

        val me = service.getMe("usr_alice").block()!!

        assertEquals("alice", me.name)
        assertEquals(UserRole.ADMIN, me.role)
    }

    @Test
    fun `getMe for a missing user is 404`() {
        `when`(userRepo.findById("usr_gone")).thenReturn(Optional.empty())

        assertThrows(ResourceNotFoundException::class.java) { service.getMe("usr_gone").block() }
    }

    @Test
    fun `updateMe trims and saves the name`() {
        `when`(userRepo.findById("usr_alice")).thenReturn(Optional.of(alice))
        `when`(userRepo.save(anyValue<User>())).then(returnsFirstArg<User>())

        val me = service.updateMe("usr_alice", UpdateMeRequest(name = "  Alice B  ")).block()!!

        assertEquals("Alice B", me.name)
    }

    @Test
    fun `updateMe with a blank name is rejected`() {
        `when`(userRepo.findById("usr_alice")).thenReturn(Optional.of(alice))

        val ex = assertThrows(ValidationException::class.java) {
            service.updateMe("usr_alice", UpdateMeRequest(name = "   ")).block()
        }
        assertEquals("Name must not be blank", ex.message)
    }

    @Test
    fun `getMyCourses combines enrolled and created lists`() {
        val enrolled = course("crs_enrolled")
        val created = course("crs_created")
        `when`(courseService.listEnrolledCourses("usr_alice")).thenReturn(Flux.just(enrolled))
        `when`(courseService.listUserCreatedCourses("usr_alice")).thenReturn(Flux.just(created))

        val courses = service.getMyCourses("usr_alice").block()!!

        assertEquals(listOf("crs_enrolled"), courses.enrolled.map { it.id })
        assertEquals(listOf("crs_created"), courses.created.map { it.id })
    }

    // ---- email change ----

    @Test
    fun `updateMe with a new email keeps the old one and sends a code to the new address`() {
        givenAliceSaves()

        val me = service.updateMe("usr_alice", UpdateMeRequest(email = " New@X.com ")).block()!!

        assertEquals("alice@x.com", me.email)
        assertEquals("new@x.com", me.pendingEmail)
        verify(otpService).issue("new@x.com", OtpPurpose.EMAIL_CHANGE)
    }

    @Test
    fun `updateMe repeating the already pending email does not send another code`() {
        alice.pendingEmail = "new@x.com"
        givenAliceSaves()

        val me = service.updateMe("usr_alice", UpdateMeRequest(name = "Alice", email = "new@x.com")).block()!!

        assertEquals("new@x.com", me.pendingEmail)
        verify(otpService, never()).issue(anyValue(), anyValue())
    }

    @Test
    fun `updateMe with the current email clears a pending change`() {
        alice.pendingEmail = "new@x.com"
        givenAliceSaves()

        val me = service.updateMe("usr_alice", UpdateMeRequest(email = "ALICE@x.com")).block()!!

        assertNull(me.pendingEmail)
        verify(otpService, never()).issue(anyValue(), anyValue())
    }

    @Test
    fun `updateMe with an email used by another account is 409`() {
        givenAliceSaves()
        `when`(userRepo.existsByEmailIgnoreCaseAndIdNot("bob@x.com", "usr_alice")).thenReturn(true)

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.updateMe("usr_alice", UpdateMeRequest(email = "bob@x.com")).block()
        }
        assertEquals(HttpStatus.CONFLICT, ex.statusCode)
        verify(otpService, never()).issue(anyValue(), anyValue())
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "no-at-sign"])
    fun `updateMe never clears the email or accepts one without @`(email: String) {
        givenAliceSaves()

        assertThrows(ValidationException::class.java) {
            service.updateMe("usr_alice", UpdateMeRequest(email = email)).block()
        }
    }

    @Test
    fun `verifyEmail with a pending change swaps it in as verified`() {
        alice.pendingEmail = "new@x.com"
        givenAliceSaves()

        val me = service.verifyEmail("usr_alice", "123456").block()!!

        verify(otpService).verify("new@x.com", OtpPurpose.EMAIL_CHANGE, "123456")
        assertEquals("new@x.com", me.email)
        assertNull(me.pendingEmail)
        assertTrue(me.emailVerified)
    }

    @Test
    fun `verifyEmail without a pending change confirms the current address`() {
        givenAliceSaves()

        val me = service.verifyEmail("usr_alice", "123456").block()!!

        verify(otpService).verify("alice@x.com", OtpPurpose.EMAIL_CHANGE, "123456")
        assertEquals("alice@x.com", me.email)
        assertTrue(me.emailVerified)
    }

    @Test
    fun `verifyEmail is 409 when another account took the pending address meanwhile`() {
        alice.pendingEmail = "new@x.com"
        givenAliceSaves()
        `when`(userRepo.existsByEmailIgnoreCaseAndIdNot("new@x.com", "usr_alice")).thenReturn(true)

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.verifyEmail("usr_alice", "123456").block()
        }
        assertEquals(HttpStatus.CONFLICT, ex.statusCode)
    }

    @Test
    fun `verifyEmail with a bad code changes nothing`() {
        alice.pendingEmail = "new@x.com"
        givenAliceSaves()
        doThrow(BusinessException("Invalid code")).`when`(otpService).verify("new@x.com", OtpPurpose.EMAIL_CHANGE, "000000")

        assertThrows(BusinessException::class.java) { service.verifyEmail("usr_alice", "000000").block() }

        assertEquals("alice@x.com", alice.email)
        assertFalse(alice.emailVerified)
    }

    @Test
    fun `resendEmailCode sends to the pending address when there is one`() {
        alice.pendingEmail = "new@x.com"
        givenAliceSaves()

        val sent = service.resendEmailCode("usr_alice").block()!!

        assertEquals("new@x.com", sent.email)
        verify(otpService).issue("new@x.com", OtpPurpose.EMAIL_CHANGE)
    }

    @Test
    fun `resendEmailCode for an unverified current address sends to it`() {
        givenAliceSaves()

        service.resendEmailCode("usr_alice").block()

        verify(otpService).issue("alice@x.com", OtpPurpose.EMAIL_CHANGE)
    }

    @Test
    fun `resendEmailCode when already verified and nothing pending is refused`() {
        alice.emailVerified = true
        givenAliceSaves()

        val ex = assertThrows(BusinessException::class.java) { service.resendEmailCode("usr_alice").block() }
        assertEquals("Email is already verified", ex.message)
    }

    private fun givenAliceSaves() {
        `when`(userRepo.findById("usr_alice")).thenReturn(Optional.of(alice))
        `when`(userRepo.save(anyValue<User>())).then(returnsFirstArg<User>())
    }

    private fun course(id: String) = CourseResponse(
        id = id,
        code = "0000000000",
        title = id,
        description = null,
        visibility = Visibility.PRIVATE,
        createdBy = UserSummary(id = "usr_t", name = "T", email = "t@x.com", avatarUrl = null),
        settings = emptyMap(),
        createdAt = 0,
        updatedAt = 0
    )
}
