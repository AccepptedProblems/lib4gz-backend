package com.example.lib4gz.exercise.model.payload

import com.example.lib4gz.exercise.model.entity.ExerciseType
import com.example.lib4gz.exercise.model.entity.SubmissionStatus

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
    val updatedAt: Long,
    // Denormalized: status + id of the caller's submission for this exercise.
    // Removes the per-exercise GET /exercises/{id}/my-submission for status chips.
    val mySubmissionStatus: SubmissionStatus? = null,
    val mySubmissionId: String? = null,
    // ?expand=questions → ordered question list for this exercise
    val questions: List<QuestionResponse>? = null,
    // ?expand=mySubmission → caller's full submission (with answers)
    val mySubmission: SubmissionResponse? = null
)