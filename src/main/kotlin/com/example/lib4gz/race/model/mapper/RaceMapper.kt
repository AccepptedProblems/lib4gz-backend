package com.example.lib4gz.race.model.mapper

import com.example.lib4gz.auth.model.entity.User
import com.example.lib4gz.race.model.entity.RaceQuestion
import com.example.lib4gz.race.model.entity.RaceResult
import com.example.lib4gz.race.model.payload.LeaderboardRow
import com.example.lib4gz.race.model.payload.RaceQuestionResponse
import com.example.lib4gz.race.model.payload.RaceResultResponse
import org.springframework.stereotype.Component

@Component
class RaceMapper {

    fun toResponse(question: RaceQuestion) = RaceQuestionResponse(
        id = question.id,
        courseId = question.courseId,
        moduleId = question.moduleId,
        content = question.content,
        options = question.options.map { it["text"]?.toString() ?: "" },
        correctIndex = question.correctIndex,
        orderIndex = question.orderIndex,
        createdAt = question.createdAt,
        updatedAt = question.updatedAt
    )

    fun toResponse(result: RaceResult) = RaceResultResponse(
        id = result.id,
        userId = result.userId,
        courseId = result.courseId,
        moduleId = result.moduleId,
        finishMs = result.finishMs,
        correctCount = result.correctCount,
        wrongCount = result.wrongCount,
        seed = result.seed,
        createdAt = result.createdAt
    )

    /** [user] is null only for a result whose user row is gone; the row then shows the id. */
    fun toRow(result: RaceResult, user: User?) = LeaderboardRow(
        userId = result.userId,
        username = user?.username ?: result.userId,
        name = user?.let { it.name.ifBlank { it.username } } ?: result.userId,
        appearance = user?.raceAppearance,
        finishMs = result.finishMs,
        correctCount = result.correctCount,
        wrongCount = result.wrongCount,
        createdAt = result.createdAt
    )
}
