package com.example.lib4gz.exercise.model.payload

import com.example.lib4gz.exercise.model.entity.ExerciseType

// Requests
data class CreateExerciseRequest(
    val title: String,
    val type: ExerciseType,
    val settings: Map<String, Any> = emptyMap(),
    val orderIndex: Int? = null,
    val questions: List<CreateQuestionItem> = emptyList()
)

data class UpdateExerciseRequest(
    val title: String? = null,
    val type: ExerciseType? = null,
    val settings: Map<String, Any>? = null,
    val orderIndex: Int? = null
)

// Responses
data class ExerciseResponse(
    val id: String,
    val lessonId: String,
    val title: String,
    val type: ExerciseType,
    val settings: Map<String, Any>,
    val orderIndex: Int,
    val questionCount: Int? = null,
    val createdAt: Long,
    val updatedAt: Long
)