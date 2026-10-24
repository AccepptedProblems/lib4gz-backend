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

/** Every visible module of the course, in the new order. */
data class ReorderModulesRequest(
    val moduleIds: List<String>
)

// Responses
data class ModuleResponse(
    val id: String,
    val courseId: String,
    val title: String,
    val orderIndex: Int,
    val lessonCount: Int? = null,
    // ?expand=submissionCount, teachers only: student submissions, drafts included, a delete would hide
    val submissionCount: Int? = null,
    val createdAt: Long,
    val updatedAt: Long
)