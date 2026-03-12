package com.example.lib4gz.courses.model.payload

// Requests
data class CreateModuleRequest(
    val title: String,
    val orderIndex: Int? = null
)

data class UpdateModuleRequest(
    val title: String? = null,
    val orderIndex: Int? = null
)

// Responses
data class ModuleResponse(
    val id: String,
    val courseId: String,
    val title: String,
    val orderIndex: Int,
    val lessonCount: Int? = null,
    val createdAt: Long,
    val updatedAt: Long
)