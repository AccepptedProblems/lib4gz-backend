package com.example.lib4gz.exercise.model.payload

import com.example.lib4gz.exercise.model.entity.QuestionVisibility

// Batch Create Questions
data class CreateQuestionsRequest(
    val questions: List<CreateQuestionItem>
)

data class CreateQuestionItem(
    val content: String,
    val orderIndex: Int? = null,
    val meta: Map<String, Any> = emptyMap(),
    val visibility: QuestionVisibility = QuestionVisibility.VISIBLE
)

// Batch Update Questions
data class UpdateQuestionsRequest(
    val questions: List<UpdateQuestionItem>
)

data class UpdateQuestionItem(
    val action: QuestionAction,
    val questionId: String? = null, // Required for UPDATE and DELETE
    val content: String? = null, // Required for CREATE and UPDATE
    val orderIndex: Int? = null,
    val meta: Map<String, Any>? = null,
    val visibility: QuestionVisibility? = null
)

enum class QuestionAction {
    CREATE,
    UPDATE,
    DELETE
}

// Single Question Requests (for single operations)
data class CreateQuestionRequest(
    val content: String,
    val orderIndex: Int? = null,
    val meta: Map<String, Any> = emptyMap(),
    val visibility: QuestionVisibility = QuestionVisibility.VISIBLE
)

data class UpdateQuestionRequest(
    val content: String? = null,
    val orderIndex: Int? = null,
    val meta: Map<String, Any>? = null,
    val visibility: QuestionVisibility? = null
)

// Responses
data class QuestionResponse(
    val id: String,
    val exerciseId: String,
    val content: String,
    val orderIndex: Int,
    val meta: Map<String, Any>,
    val visibility: QuestionVisibility,
    val createdAt: Long,
    val updatedAt: Long
)

data class QuestionsResponse(
    val created: List<QuestionResponse>,
    val updated: List<QuestionResponse>,
    val deleted: List<String>
)