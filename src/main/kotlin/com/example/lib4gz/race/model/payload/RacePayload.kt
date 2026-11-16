package com.example.lib4gz.race.model.payload

import com.fasterxml.jackson.annotation.JsonProperty

// PUT /courses/{courseId}/race-questions: the whole bank. An item with an id updates that
// question; without one it is created; a question whose id is absent is hidden.
data class RaceQuestionItem(
    val id: String? = null,
    val moduleId: String,
    val content: String,
    val options: List<String>,
    val correctIndex: Int
)

data class ReplaceRaceQuestionsRequest(
    val questions: List<RaceQuestionItem>
)

data class RaceQuestionResponse(
    val id: String,
    val courseId: String,
    val moduleId: String,
    val content: String,
    /** Empty unless the caller teaches the course. */
    val options: List<String>,
    /** The right option. Null unless the caller teaches the course. */
    val correctIndex: Int?,
    val orderIndex: Int,
    val createdAt: Long,
    val updatedAt: Long
)

// POST /courses/{courseId}/races
data class StartRaceRequest(
    /** Null for a race over the whole course. */
    val moduleId: String? = null,
    /** How many questions the race needs. The server deals at most `application.race.max-questions`. */
    val questionCount: Int
)

data class DealtQuestion(
    /** The question number, from 0; the same as its position in the list. */
    val seq: Int,
    val questionId: String,
    val moduleId: String,
    val content: String,
    /** At most four, in the order to show. */
    val options: List<String>
)

data class StartRaceResponse(
    val raceId: String,
    val seed: Long,
    val questions: List<DealtQuestion>
)

// POST /courses/{courseId}/races/{raceId}/answers
data class AnswerRequest(
    @param:JsonProperty(required = true)
    val seq: Int,
    /** The shown option pressed, from 0; null when the countdown ran out. */
    val choice: Int? = null
)

data class AnswerResponse(
    val seq: Int,
    val correct: Boolean,
    val correctCount: Int,
    val wrongCount: Int
)

// POST /courses/{courseId}/races/{raceId}/finish
data class FinishRaceRequest(
    val finishMs: Int
)

data class RaceResultResponse(
    val id: String,
    val userId: String,
    val courseId: String,
    val moduleId: String?,
    val finishMs: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val seed: Long,
    val createdAt: Long,
    /**
     * One entry per question number of the race: the right shown option for the first `MAX_SHOWN_KEYS`
     * questions judged wrong, a timeout included, and null for every other number. Sent only by a
     * successful finish.
     */
    val answerKey: List<Int?>
)

data class LeaderboardRow(
    val userId: String,
    val username: String,
    val name: String,
    /** The racer's look, `{"characterId", "kartId"}`, or null when never chosen. */
    val appearance: Map<String, Any>?,
    val finishMs: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val createdAt: Long
)

data class LeaderboardResponse(
    /** Ten fastest people in the scope, best result each, fastest first. */
    val rows: List<LeaderboardRow>,
    /** The caller's own best in the scope, or null when they have not finished a race there. */
    val mine: LeaderboardRow?
)
