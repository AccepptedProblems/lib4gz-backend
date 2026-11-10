package com.example.lib4gz.race.model.payload

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
    val options: List<String>,
    val correctIndex: Int,
    val orderIndex: Int,
    val createdAt: Long,
    val updatedAt: Long
)

data class CreateRaceResultRequest(
    /** Null for a race over the whole course. */
    val moduleId: String? = null,
    val finishMs: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val seed: Long
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
    val createdAt: Long
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
