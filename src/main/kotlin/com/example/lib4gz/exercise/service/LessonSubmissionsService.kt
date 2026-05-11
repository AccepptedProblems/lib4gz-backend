package com.example.lib4gz.exercise.service

import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.UnauthorizedException
import com.example.lib4gz.common.utils.ExpandTokens
import com.example.lib4gz.courses.model.entity.EnrollmentRole
import com.example.lib4gz.courses.repo.EnrollmentRepo
import com.example.lib4gz.courses.repo.LessonRepo
import com.example.lib4gz.exercise.model.entity.StudentAnswer
import com.example.lib4gz.exercise.model.entity.Submission
import com.example.lib4gz.exercise.model.mapper.QuestionMapper
import com.example.lib4gz.exercise.model.mapper.SubmissionMapper
import com.example.lib4gz.exercise.model.payload.ExerciseSubmissionsGroup
import com.example.lib4gz.exercise.model.payload.LessonSubmissionsResponse
import com.example.lib4gz.exercise.model.payload.QuestionResponse
import com.example.lib4gz.exercise.model.payload.SubmissionResponse
import com.example.lib4gz.exercise.repo.ExerciseRepo
import com.example.lib4gz.exercise.repo.QuestionRepo
import com.example.lib4gz.exercise.repo.StudentAnswerRepo
import com.example.lib4gz.exercise.repo.SubmissionRepo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * Teacher review queue scoped to a single lesson.
 *
 * Replaces the worst N+1 pattern in the app (1 + 3N + M):
 *   - list exercises
 *   - per exercise: list submissions
 *   - on expand: per exercise: list questions
 *   - on drawer open: per submission: full re-fetch
 *
 * With this aggregate the screen loads in a fixed 3–5 queries regardless of the
 * number of exercises or submissions:
 *   1. lesson auth projection (lesson exists + courseId + creatorId)
 *   2. active enrollment lookup (skipped when the caller is the course creator)
 *   3. exercises in the lesson
 *   4. submissions (JOIN FETCH user)
 *   5. answers (JOIN FETCH question) — only when expand=answers
 *   6. questions per exercise — only when expand=questions
 */
interface LessonSubmissionsService {
    fun getLessonSubmissions(
        lessonId: String,
        userId: String,
        expand: ExpandTokens
    ): Mono<LessonSubmissionsResponse>
}

@Service
@Transactional(readOnly = true)
class LessonSubmissionsServiceImpl(
    private val lessonRepo: LessonRepo,
    private val exerciseRepo: ExerciseRepo,
    private val submissionRepo: SubmissionRepo,
    private val questionRepo: QuestionRepo,
    private val studentAnswerRepo: StudentAnswerRepo,
    private val enrollmentRepo: EnrollmentRepo,
    private val questionMapper: QuestionMapper,
    private val submissionMapper: SubmissionMapper
) : LessonSubmissionsService {

    override fun getLessonSubmissions(
        lessonId: String,
        userId: String,
        expand: ExpandTokens
    ): Mono<LessonSubmissionsResponse> {
        return Mono.fromCallable {
            // One projection query resolves lesson existence + course id + creator id
            // without hydrating the lesson, module, course, or creator entities.
            val auth = lessonRepo.findCourseAuthByLessonId(lessonId)
                ?: throw ResourceNotFoundException("Lesson not found with id: $lessonId")

            val isTeacher = auth.creatorId == userId ||
                enrollmentRepo.findActiveEnrollment(auth.courseId, userId)?.role == EnrollmentRole.TEACHER
            if (!isTeacher) {
                throw UnauthorizedException("Only teachers can view lesson submissions")
            }

            val exercises = exerciseRepo.findByLesson_IdOrderByOrderIndexAsc(lessonId)
            if (exercises.isEmpty()) {
                return@fromCallable LessonSubmissionsResponse(exercises = emptyList())
            }

            val exerciseIds = exercises.map { it.id }
            val submissions = submissionRepo.findByExerciseIdInFetchUser(exerciseIds)
            val submissionsByExerciseId: Map<String, List<Submission>> =
                submissions.groupBy { it.exercise.id }

            val questionsByExerciseId: Map<String, List<QuestionResponse>>? =
                if (expand.has(EXPAND_QUESTIONS)) {
                    questionRepo.findByExerciseIdInOrderByOrderIndexAsc(exerciseIds)
                        .groupBy { it.exerciseId }
                        .mapValues { entry -> entry.value.map(questionMapper::toResponse) }
                } else null

            val submissionResponses = assembleSubmissionResponses(submissions, expand)
            val responsesById: Map<String, SubmissionResponse> = submissionResponses.associateBy { it.id }

            LessonSubmissionsResponse(
                exercises = exercises.map { exercise ->
                    ExerciseSubmissionsGroup(
                        id = exercise.id,
                        title = exercise.title,
                        type = exercise.type,
                        orderIndex = exercise.orderIndex,
                        questions = questionsByExerciseId?.get(exercise.id),
                        submissions = submissionsByExerciseId[exercise.id]
                            .orEmpty()
                            .mapNotNull { responsesById[it.id] }
                    )
                }
            )
        }.subscribeOn(Schedulers.boundedElastic())
    }

    /**
     * Builds [SubmissionResponse]s for a batch of submissions.
     * - 1 batched answer query when `expand=answers`, else no answer query
     * - The answer query joins its question, so no separate question-by-id query is needed
     */
    private fun assembleSubmissionResponses(
        submissions: List<Submission>,
        expand: ExpandTokens
    ): List<SubmissionResponse> {
        if (submissions.isEmpty()) return emptyList()
        if (!expand.has(EXPAND_ANSWERS)) {
            return submissions.map { submissionMapper.toResponse(it) }
        }

        val submissionIds = submissions.map { it.id }
        val allAnswers = studentAnswerRepo.findBySubmissionIdInOrderedFetchQuestion(submissionIds)
        val answersBySubmission: Map<String, List<StudentAnswer>> =
            allAnswers.groupBy { it.submission.id }

        // Questions were eagerly fetched as part of the answers query above,
        // so build the lookup map in memory rather than issuing another SELECT.
        val questionsById = allAnswers.asSequence()
            .map { it.question }
            .distinctBy { it.id }
            .associateBy { it.id }

        return submissions.map { submission ->
            submissionMapper.toResponse(
                submission = submission,
                answers = answersBySubmission[submission.id] ?: emptyList(),
                questions = questionsById
            )
        }
    }

    companion object {
        const val EXPAND_QUESTIONS = "questions"
        const val EXPAND_ANSWERS = "answers"
    }
}
