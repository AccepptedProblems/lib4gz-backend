package com.example.lib4gz.courses.model.payload

// Requests
data class CreateLessonRequest(
    val title: String,
    val orderIndex: Int? = null
)

data class UpdateLessonRequest(
    val title: String? = null,
    val orderIndex: Int? = null
)

// Responses
data class LessonResponse(
    val id: String,
    val moduleId: String,
    val title: String,
    val orderIndex: Int,
    val hasSummary: Boolean,
    val exerciseCount: Int? = null,
    val createdAt: Long,
    val updatedAt: Long
)