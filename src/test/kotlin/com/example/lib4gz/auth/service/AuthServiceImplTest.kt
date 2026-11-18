package com.example.lib4gz.auth.service

import com.example.lib4gz.auth.model.entity.User
import com.example.lib4gz.auth.model.entity.UserRole
import com.example.lib4gz.auth.model.payload.LoginReq
import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.common.config.security.JwtProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.web.server.ResponseStatusException

class AuthServiceImplTest {

    private val userRepo = mock(UserRepo::class.java)
    private val passwordEncoder = BCryptPasswordEncoder(4)
    private val jwtProvider = JwtProvider(TEST_JWT_SECRET, 60_000)
    private val service = AuthServiceImpl(userRepo, passwordEncoder, jwtProvider, fakeRefreshTokenService())

    private val alice = User(
        id = "usr_alice",
        email = "alice@x.com",
        username = "alice",
        name = "Alice",
        password = passwordEncoder.encode("secret123"),
    )

    @Test
    fun `logs in by username`() {
        `when`(userRepo.findByUsername("alice")).thenReturn(alice)

        val response = service.auth(LoginReq("alice", "secret123")).block()!!

        assertEquals("usr_alice", response.user.id)
        assertEquals("Alice", response.user.name)
        assertEquals(false, response.user.emailVerified)
        assertEquals("refresh-usr_alice", response.refreshToken.token)
    }

    @Test
    fun `input containing @ looks the user up by email`() {
        `when`(userRepo.findByEmailIgnoreCase("Alice@X.com")).thenReturn(alice)

        val response = service.auth(LoginReq("  Alice@X.com ", "secret123")).block()!!

        assertEquals("alice", response.user.username)
    }

    @Test
    fun `unknown user is 401`() {
        assertUnauthorized(LoginReq("nobody", "secret123"))
    }

    @Test
    fun `wrong password is 401`() {
        `when`(userRepo.findByUsername("alice")).thenReturn(alice)
        assertUnauthorized(LoginReq("alice", "wrong-password"))
    }

    @Test
    fun `account without a password is 401`() {
        `when`(userRepo.findByUsername("gina")).thenReturn(
            User(id = "usr_gina", email = "gina@x.com", username = "gina", name = "Gina", password = null)
        )
        assertUnauthorized(LoginReq("gina", "anything"))
    }

    @Test
    fun `role reports TEACHER when the user holds USER and TEACHER`() {
        alice.roles.add(UserRole.TEACHER)
        `when`(userRepo.findByUsername("alice")).thenReturn(alice)

        val response = service.auth(LoginReq("alice", "secret123")).block()!!

        assertEquals(UserRole.TEACHER, response.user.role)
    }

    private fun assertUnauthorized(req: LoginReq) {
        val ex = assertThrows(ResponseStatusException::class.java) { service.auth(req).block() }
        assertEquals(HttpStatus.UNAUTHORIZED, ex.statusCode)
        assertEquals("Invalid username or password", ex.reason)
    }
}
