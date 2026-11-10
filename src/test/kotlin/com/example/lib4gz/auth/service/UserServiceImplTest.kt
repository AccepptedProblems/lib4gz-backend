package com.example.lib4gz.auth.service

import com.example.lib4gz.auth.model.entity.User
import com.example.lib4gz.auth.model.entity.UserRole
import com.example.lib4gz.auth.model.payload.UpdateMeRequest
import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.ValidationException
import com.example.lib4gz.courses.model.entity.Visibility
import com.example.lib4gz.courses.model.payload.CourseResponse
import com.example.lib4gz.courses.model.payload.UserSummary
import com.example.lib4gz.courses.service.CourseService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.AdditionalAnswers.returnsFirstArg
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import reactor.core.publisher.Flux
import java.util.Optional

class UserServiceImplTest {

    private val userRepo = mock(UserRepo::class.java)
    private val courseService = mock(CourseService::class.java)
    private val service = UserServiceImpl(
        userRepo = userRepo,
        courseService = courseService,
        transactionTemplate = passThroughTransactionTemplate()
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
    fun `updateMe saves a character and a kart`() {
        `when`(userRepo.findById("usr_alice")).thenReturn(Optional.of(alice))
        `when`(userRepo.save(anyValue<User>())).then(returnsFirstArg<User>())
        val look = mapOf("characterId" to "chr_bolt", "kartId" to "krt_classic")

        val me = service.updateMe("usr_alice", UpdateMeRequest(raceAppearance = look)).block()!!

        assertEquals(look, me.raceAppearance)
        assertEquals(look, alice.raceAppearance)
    }

    @Test
    fun `updateMe refuses a look that is not a character and a kart`() {
        `when`(userRepo.findById("usr_alice")).thenReturn(Optional.of(alice))
        val bad = listOf(
            mapOf("characterId" to "chr_bolt"),
            mapOf("characterId" to "chr_bolt", "kartId" to " "),
            mapOf("characterId" to "chr_bolt", "kartId" to 3),
            mapOf("characterId" to "chr_bolt", "kartId" to "krt_classic", "gold" to 999)
        )

        for (look in bad) {
            val ex = assertThrows(ValidationException::class.java) {
                service.updateMe("usr_alice", UpdateMeRequest(raceAppearance = look)).block()
            }
            assertEquals("Choose a character and a kart.", ex.message)
        }
        verify(userRepo, never()).save(anyValue<User>())
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
