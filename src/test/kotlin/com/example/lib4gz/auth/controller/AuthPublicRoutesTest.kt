package com.example.lib4gz.auth.controller

import com.example.lib4gz.auth.job.RefreshTokenCleanupJob
import com.example.lib4gz.auth.job.RegistrationCleanupJob
import com.example.lib4gz.auth.model.entity.UserRole
import com.example.lib4gz.auth.model.payload.LoginResponse
import com.example.lib4gz.auth.model.payload.Token
import com.example.lib4gz.auth.model.payload.UserResponse
import com.example.lib4gz.auth.service.RefreshTokenService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import reactor.core.publisher.Mono

/**
 * Refresh and logout answer without an access token: a browser whose access token has expired must
 * still reach them. No database, for the reason given in `Lib4gzApplicationTests`.
 */
@SpringBootTest(
    properties = [
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
    ]
)
@AutoConfigureMockMvc
class AuthPublicRoutesTest {

    // The real jobs sweep at startup: they would open a connection and delete expired rows in that database.
    @MockitoBean
    private lateinit var registrationCleanupJob: RegistrationCleanupJob

    @MockitoBean
    private lateinit var refreshTokenCleanupJob: RefreshTokenCleanupJob

    @MockitoBean
    private lateinit var refreshTokenService: RefreshTokenService

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val body = """{"refreshToken":"raw"}"""

    @Test
    fun `refresh answers 200 without an access token`() {
        val user = UserResponse("usr_a", "alice", "a@x.com", "Alice", null, UserRole.USER, true, false, null)
        `when`(refreshTokenService.refresh("raw")).thenReturn(
            Mono.just(LoginResponse(user, Token("access", 1), Token("next", 2)))
        )

        val started = mockMvc.perform(post("/v1/auth/refresh-token").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(started))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.refreshToken.token").value("next"))
    }

    @Test
    fun `logout answers 204 without an access token`() {
        `when`(refreshTokenService.logout("raw")).thenReturn(Mono.empty())

        val started = mockMvc.perform(post("/v1/auth/logout").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(started)).andExpect(status().isNoContent)
    }

    @Test
    fun `a body without refreshToken is 400`() {
        mockMvc.perform(post("/v1/auth/refresh-token").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest)
        mockMvc.perform(post("/v1/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `google connect still needs a token`() {
        mockMvc.perform(post("/v1/auth/google:connect").contentType(MediaType.APPLICATION_JSON).content("""{"idToken":"x"}"""))
            .andExpect(status().isUnauthorized)
    }
}
