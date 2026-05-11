package com.example.lib4gz.exercise.model.mapper

import com.example.lib4gz.courses.model.payload.UserSummary
import com.example.lib4gz.exercise.model.entity.*
import com.example.lib4gz.exercise.model.payload.*
import org.springframework.stereotype.Component

/**
 * Maps Exercise entity to ExerciseResponse.
 *
 * Single Responsibility: pure entity→DTO mapping. Counts and submission state are
 * supplied by the caller (service) — the mapper never performs I/O. New denormalized
 * fields are optional, so existing call sites compile unchanged.
 */
@Component
class ExerciseMapper {

    fun toResponse(
        exercise: Exercise,
        questionCount: Int? = null,
        mySubmissionStatus: SubmissionStatus? = null,
        mySubmissionId: String? = null,
        questions: List<QuestionResponse>? = null,
        mySubmission: SubmissionResponse? = null
    ): ExerciseResponse {
        return ExerciseResponse(
            id = exercise.id,
            lessonId = exercise.lesson.id,
            title = exercise.title,
            type = exercise.type,
            settings = exercise.settings,
            orderIndex = exercise.orderIndex,
            questionCount = questionCount,
            createdAt = exercise.createdAt,
            updatedAt = exercise.updatedAt,
            mySubmissionStatus = mySubmissionStatus,
            mySubmissionId = mySubmissionId,
            questions = questions,
            mySubmission = mySubmission
        )
    }
}

@Component
class QuestionMapper {

    fun toResponse(question: Question): QuestionResponse {
        return QuestionResponse(
            id = question.id,
            exerciseId = question.exerciseId,
            content = question.content,
            orderIndex = question.orderIndex,
            meta = question.meta,
            visibility = question.visibility,
            createdAt = question.createdAt,
            updatedAt = question.updatedAt
        )
    }
}

@Component
class SubmissionMapper {

    fun toResponse(
        submission: Submission,
        answers: List<StudentAnswer>? = null,
        questions: Map<String, Question>? = null
    ): SubmissionResponse {
        return SubmissionResponse(
            id = submission.id,
            exerciseId = submission.exercise.id,
            user = UserSummary(
                id = submission.user.id,
                name = submission.user.name,
                email = submission.user.email,
                avatarUrl = submission.user.avatarUrl
            ),
            status = submission.status,
            answers = answers?.map { answer ->
                val question = questions?.get(answer.question.id)
                StudentAnswerResponse(
                    id = answer.id,
                    questionId = answer.question.id,
                    questionContent = question?.content ?: "",
                    answer = answer.answer,
                    teacherComment = answer.teacherComment,
                    createdAt = answer.createdAt,
                    updatedAt = answer.updatedAt
                )
            },
            createdAt = submission.createdAt,
            updatedAt = submission.updatedAt,
            submittedAt = submission.submittedAt,
            approvedAt = submission.approvedAt
        )
    }
}
