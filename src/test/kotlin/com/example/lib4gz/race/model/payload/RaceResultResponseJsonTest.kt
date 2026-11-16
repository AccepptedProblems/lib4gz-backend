package com.example.lib4gz.race.model.payload

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest

/**
 * The finish reply through the application's own ObjectMapper: the game reads the key by question
 * number, so a withheld entry must stay in the list as null, never be dropped.
 */
@JsonTest
class RaceResultResponseJsonTest {

    @Autowired private lateinit var objectMapper: ObjectMapper

    @Test
    fun `the finish reply keeps every null entry and the deck's length`() {
        val reply = RaceResultResponse(
            id = "rrs_1", userId = "usr_1", courseId = "crs_1", moduleId = null, finishMs = 190_000,
            correctCount = 3, wrongCount = 2, seed = 9, createdAt = 1, answerKey = listOf(null, 2, null, 0, null)
        )

        val json = objectMapper.readTree(objectMapper.writeValueAsString(reply))

        assertEquals("[null,2,null,0,null]", json["answerKey"].toString())
        assertTrue(json.has("moduleId") && json["moduleId"].isNull, "a null field is sent as null, not dropped")
    }
}
