package com.example.lib4gz.courses.model.payload

// Requests
data class CreateSummaryRequest(
    val content: String
)

data class UpdateSummaryRequest(
    val content: String
)

// Responses
data class SummaryResponse(
    val id: String,
    val lessonId: String,
    val content: String,
    val editedBy: UserSummary,
    val version: Int,
    val createdAt: Long,
    val updatedAt: Long
)