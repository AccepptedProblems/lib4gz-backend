package com.example.lib4gz.exercise.model.payload

import com.example.lib4gz.exercise.model.entity.ExerciseType

/**
 * Teacher review queue scoped to a single lesson.
 *
 * Replaces the 1 + 3N + M fan-out in the Lesson Submissions tab (exercises list,
 * per-exercise submissions, per-exercise questions, per-submission re-fetch).
 *
 * Authorization: caller must be a TEACHER on the lesson's course.
 */
data class LessonSubmissionsResponse(
    val exercises: List<ExerciseSubmissionsGroup>
)

data class ExerciseSubmissionsGroup(
    val id: String,
    val title: String,
    val type: ExerciseType,
    val orderIndex: Int,
    // ?expand=questions → ordered question list for this exercise
    val questions: List<QuestionResponse>? = null,
    // Submissions always included. Each carries answers when ?expand=answers.
    val submissions: List<SubmissionResponse>
)
