package com.example.lib4gz.exercise.model.payload

import com.example.lib4gz.courses.model.payload.UserSummary
import com.example.lib4gz.exercise.model.entity.SubmissionStatus

// Requests
data class CreateSubmissionRequest(
    val answers: List<AnswerRequest> = emptyList()
)

data class UpdateSubmissionRequest(
    val answers: List<AnswerRequest>
)

data class AnswerRequest(
    val questionId: String,
    val answer: String?
)

// Responses
data class SubmissionResponse(
    val id: String,
    val exerciseId: String,
    val user: UserSummary,
    val status: SubmissionStatus,
    val answers: List<StudentAnswerResponse>? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val submittedAt: Long?,
    val approvedAt: Long?
)

data class StudentAnswerResponse(
    val id: String,
    val questionId: String,
    val questionContent: String,
    val answer: String?,
    val teacherComment: String?,
    val createdAt: Long,
    val updatedAt: Long
)
