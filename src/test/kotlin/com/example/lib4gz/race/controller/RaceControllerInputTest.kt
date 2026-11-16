package com.example.lib4gz.race.controller

import com.example.lib4gz.common.config.common.PZRequestHeader
import com.example.lib4gz.common.config.error.GlobalExceptionHandler
import com.example.lib4gz.race.service.RaceService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * Bad input on the race routes is a 4xx a person can read, never the 500 of an unhandled error.
 * The controller and the error handler only; no context, no database.
 */
class RaceControllerInputTest {

    private val raceService = mock(RaceService::class.java)
    private val mvc: MockMvc = MockMvcBuilders.standaloneSetup(RaceController(raceService))
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    private fun postJson(path: String, body: String?) = mvc.perform(
        post(path).header(PZRequestHeader.USER_ID, "usr_1").contentType(MediaType.APPLICATION_JSON).apply {
            if (body != null) content(body)
        }
    )

    private fun expectUnreadable(path: String, body: String?) {
        postJson(path, body)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors[0].reason").value(GlobalExceptionHandler.UNREADABLE_REQUEST))
    }

    @Test
    fun `an answer with text for its number, no body, or no number is a 400`() {
        expectUnreadable("/v1/courses/crs_1/races/rce_1/answers", """{"seq":"abc","choice":1}""")
        expectUnreadable("/v1/courses/crs_1/races/rce_1/answers", null)
        expectUnreadable("/v1/courses/crs_1/races/rce_1/answers", """{"choice":1}""")
        verifyNoInteractions(raceService)
    }

    @Test
    fun `a number too large for its field is a 400`() {
        expectUnreadable("/v1/courses/crs_1/races", """{"questionCount":3000000000}""")
        expectUnreadable("/v1/courses/crs_1/races/rce_1/finish", """{"finishMs":3e9}""")
        verifyNoInteractions(raceService)
    }

    @Test
    fun `an unknown route is a 404, including the removed result post`() {
        postJson("/v1/courses/crs_1/race-results", """{"finishMs":1}""")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errors[0].reason").value(GlobalExceptionHandler.NO_ROUTE))
    }

    @Test
    fun `a wrong method is a 405`() {
        mvc.perform(get("/v1/courses/crs_1/races").header(PZRequestHeader.USER_ID, "usr_1"))
            .andExpect(status().isMethodNotAllowed)
            .andExpect(jsonPath("$.errors[0].reason").value(GlobalExceptionHandler.METHOD_NOT_ALLOWED))
    }
}
