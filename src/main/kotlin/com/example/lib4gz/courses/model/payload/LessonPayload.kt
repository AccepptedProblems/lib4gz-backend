package com.example.lib4gz.courses.model.payload

import com.example.lib4gz.exercise.model.payload.ExerciseResponse

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
    // Caller's completion state: true/false for exercise-bearing lessons
    // (done = all exercises SUBMITTED/APPROVED), null when the lesson has no
    // exercises or the context carries no caller progress.
    val completed: Boolean? = null,
    // Caller's SUBMITTED/APPROVED exercise count in this lesson (syllabus only).
    // Together with exerciseCount this yields the full/partial/none marker:
    // == exerciseCount → full, in between → partial, 0 → none.
    val completedExerciseCount: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
    // Denormalized: parent module title — removes the lesson→module trivial lookup.
    val moduleTitle: String? = null,
    // ?expand=module → owning module's minimal view
    val module: ModuleSummary? = null,
    // ?expand=summary → the lesson's summary (null if hasSummary == false)
    val summary: SummaryResponse? = null,
    // ?expand=exercises → ordered exercise list (each can carry mySubmissionStatus)
    val exercises: List<ExerciseResponse>? = null
)

/**
 * Minimal module view used when expanded inside a lesson response.
 */
data class ModuleSummary(
    val id: String,
    val title: String,
    val orderIndex: Int
)