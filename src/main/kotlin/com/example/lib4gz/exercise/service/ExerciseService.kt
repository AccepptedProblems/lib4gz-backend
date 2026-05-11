package com.example.lib4gz.exercise.service

import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.UnauthorizedException
import com.example.lib4gz.common.utils.ExpandTokens
import com.example.lib4gz.courses.repo.LessonRepo
import com.example.lib4gz.courses.service.EnrollmentService
import com.example.lib4gz.exercise.model.entity.Exercise
import com.example.lib4gz.exercise.model.entity.Question
import com.example.lib4gz.exercise.model.entity.StudentAnswer
import com.example.lib4gz.exercise.model.entity.Submission
import com.example.lib4gz.exercise.model.mapper.ExerciseMapper
import com.example.lib4gz.exercise.model.mapper.QuestionMapper
import com.example.lib4gz.exercise.model.mapper.SubmissionMapper
import com.example.lib4gz.exercise.model.payload.CreateExerciseRequest
import com.example.lib4gz.exercise.model.payload.ExerciseResponse
import com.example.lib4gz.exercise.model.payload.QuestionResponse
import com.example.lib4gz.exercise.model.payload.SubmissionResponse
import com.example.lib4gz.exercise.model.payload.UpdateExerciseRequest
import com.example.lib4gz.exercise.repo.ExerciseRepo
import com.example.lib4gz.exercise.repo.QuestionRepo
import com.example.lib4gz.exercise.repo.StudentAnswerRepo
import com.example.lib4gz.exercise.repo.SubmissionRepo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

interface ExerciseService {

    fun createExercise(lessonId: String, userId: String, request: CreateExerciseRequest): Mono<ExerciseResponse>

    fun getExerciseById(exerciseId: String, userId: String, expand: ExpandTokens = ExpandTokens.EMPTY): Mono<ExerciseResponse>

    fun updateExercise(exerciseId: String, userId: String, request: UpdateExerciseRequest): Mono<ExerciseResponse>

    fun deleteExercise(exerciseId: String, userId: String): Mono<Void>

    fun listLessonExercises(lessonId: String, userId: String, expand: ExpandTokens = ExpandTokens.EMPTY): Flux<ExerciseResponse>

    // Synchronous helper used by other services.
    fun getCourseIdByExercise(exerciseId: String): String
}

/**
 * Implements [ExerciseService] with SOLID separation:
 * - Repositories perform I/O.
 * - This service orchestrates and assembles via mappers.
 * - The [ExerciseMapper] / [QuestionMapper] / [SubmissionMapper] remain pure.
 *
 * Open/Closed: new expansions are added by passing additional tokens — no controller
 * or interface change is required.
 */
@Service
@Transactional
class ExerciseServiceImpl(
    private val exerciseRepo: ExerciseRepo,
    private val lessonRepo: LessonRepo,
    private val questionRepo: QuestionRepo,
    private val submissionRepo: SubmissionRepo,
    private val studentAnswerRepo: StudentAnswerRepo,
    private val enrollmentService: EnrollmentService,
    private val exerciseMapper: ExerciseMapper,
    private val questionMapper: QuestionMapper,
    private val submissionMapper: SubmissionMapper
) : ExerciseService {

    override fun createExercise(lessonId: String, userId: String, request: CreateExerciseRequest): Mono<ExerciseResponse> {
        return Mono.fromCallable {
            val lesson = lessonRepo.findById(lessonId).orElseThrow {
                ResourceNotFoundException("Lesson not found with id: $lessonId")
            }

            val courseId = lesson.module.course.id

            if (!enrollmentService.isTeacherInCourse(courseId, userId)) {
                throw UnauthorizedException("Only teachers can create exercises")
            }

            val orderIndex = request.orderIndex ?: ((exerciseRepo.findMaxOrderIndexByLessonId(lessonId) ?: -1) + 1)

            val exercise = Exercise(
                lesson = lesson,
                title = request.title,
                type = request.type,
                settings = request.settings,
                orderIndex = orderIndex
            )

            val savedExercise = exerciseRepo.save(exercise)

            if (request.questions.isNotEmpty()) {
                var currentMaxIndex = -1
                val questions = request.questions.map { item ->
                    val questionOrderIndex = item.orderIndex ?: ++currentMaxIndex
                    Question(
                        exerciseId = savedExercise.id,
                        content = item.content,
                        orderIndex = questionOrderIndex,
                        meta = item.meta,
                        visibility = item.visibility
                    )
                }
                questionRepo.saveAll(questions)
            }

            val questionCount = request.questions.size
            exerciseMapper.toResponse(savedExercise, questionCount)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun getExerciseById(exerciseId: String, userId: String, expand: ExpandTokens): Mono<ExerciseResponse> {
        return Mono.fromCallable {
            val exercise = exerciseRepo.findById(exerciseId).orElseThrow {
                ResourceNotFoundException("Exercise not found with id: $exerciseId")
            }

            val courseId = exercise.lesson.module.course.id

            if (!enrollmentService.isEnrolledInCourse(courseId, userId)) {
                throw UnauthorizedException("You do not have access to this exercise")
            }

            val questionCount = questionRepo.countByExerciseId(exerciseId).toInt()
            val mySub = submissionRepo.findByExercise_IdAndUser_Id(exerciseId, userId)

            val questions = if (expand.has(EXPAND_QUESTIONS)) {
                questionRepo.findByExerciseIdOrderByOrderIndexAsc(exerciseId).map(questionMapper::toResponse)
            } else null

            val mySubmission = if (expand.has(EXPAND_MY_SUBMISSION) && mySub != null) {
                buildSubmissionResponse(mySub)
            } else null

            exerciseMapper.toResponse(
                exercise = exercise,
                questionCount = questionCount,
                mySubmissionStatus = mySub?.status,
                mySubmissionId = mySub?.id,
                questions = questions,
                mySubmission = mySubmission
            )
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun updateExercise(exerciseId: String, userId: String, request: UpdateExerciseRequest): Mono<ExerciseResponse> {
        return Mono.fromCallable {
            val exercise = exerciseRepo.findById(exerciseId).orElseThrow {
                ResourceNotFoundException("Exercise not found with id: $exerciseId")
            }

            val courseId = exercise.lesson.module.course.id

            if (!enrollmentService.isTeacherInCourse(courseId, userId)) {
                throw UnauthorizedException("Only teachers can update exercises")
            }

            request.title?.let { exercise.title = it }
            request.type?.let { exercise.type = it }
            request.settings?.let { exercise.settings = it }
            request.orderIndex?.let { exercise.orderIndex = it }

            val savedExercise = exerciseRepo.save(exercise)
            val questionCount = questionRepo.countByExerciseId(exerciseId).toInt()
            exerciseMapper.toResponse(savedExercise, questionCount)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun deleteExercise(exerciseId: String, userId: String): Mono<Void> {
        return Mono.fromCallable {
            val exercise = exerciseRepo.findById(exerciseId).orElseThrow {
                ResourceNotFoundException("Exercise not found with id: $exerciseId")
            }

            val courseId = exercise.lesson.module.course.id

            if (!enrollmentService.isTeacherInCourse(courseId, userId)) {
                throw UnauthorizedException("Only teachers can delete exercises")
            }

            exerciseRepo.delete(exercise)
        }.subscribeOn(Schedulers.boundedElastic()).then()
    }

    override fun listLessonExercises(lessonId: String, userId: String, expand: ExpandTokens): Flux<ExerciseResponse> {
        return Mono.fromCallable {
            val lesson = lessonRepo.findById(lessonId).orElseThrow {
                ResourceNotFoundException("Lesson not found with id: $lessonId")
            }

            val courseId = lesson.module.course.id

            if (!enrollmentService.isEnrolledInCourse(courseId, userId)) {
                throw UnauthorizedException("You do not have access to this lesson")
            }

            val exercises = exerciseRepo.findByLesson_IdOrderByOrderIndexAsc(lessonId)
            if (exercises.isEmpty()) return@fromCallable emptyList<ExerciseResponse>()

            assembleExerciseResponses(exercises, userId, expand)
        }.flatMapMany { Flux.fromIterable(it) }
            .subscribeOn(Schedulers.boundedElastic())
    }

    override fun getCourseIdByExercise(exerciseId: String): String {
        val exercise = exerciseRepo.findById(exerciseId).orElseThrow {
            ResourceNotFoundException("Exercise not found with id: $exerciseId")
        }
        return exercise.lesson.module.course.id
    }

    /**
     * Batched assembly of [ExerciseResponse] for a list of exercises owned by one caller.
     * Single Responsibility: this method exists to keep `listLessonExercises` linear and
     * to centralise the N+1-avoidance logic.
     */
    private fun assembleExerciseResponses(
        exercises: List<Exercise>,
        userId: String,
        expand: ExpandTokens
    ): List<ExerciseResponse> {
        val exerciseIds = exercises.map { it.id }

        val questionCountsById: Map<String, Int> = countQuestionsByExercise(exerciseIds)
        val mySubmissionsById: Map<String, Submission> = submissionRepo
            .findByExercise_IdInAndUser_Id(exerciseIds, userId)
            .associateBy { it.exercise.id }

        val questionsByExercise: Map<String, List<QuestionResponse>>? = if (expand.has(EXPAND_QUESTIONS)) {
            questionRepo.findByExerciseIdInOrderByOrderIndexAsc(exerciseIds)
                .groupBy { it.exerciseId }
                .mapValues { entry -> entry.value.map(questionMapper::toResponse) }
        } else null

        val mySubmissionsExpanded: Map<String, SubmissionResponse>? = if (expand.has(EXPAND_MY_SUBMISSION)) {
            buildSubmissionResponses(mySubmissionsById.values.toList())
                .associateBy { it.exerciseId }
        } else null

        return exercises.map { exercise ->
            val sub = mySubmissionsById[exercise.id]
            exerciseMapper.toResponse(
                exercise = exercise,
                questionCount = questionCountsById[exercise.id] ?: 0,
                mySubmissionStatus = sub?.status,
                mySubmissionId = sub?.id,
                questions = questionsByExercise?.get(exercise.id),
                mySubmission = mySubmissionsExpanded?.get(exercise.id)
            )
        }
    }

    private fun countQuestionsByExercise(exerciseIds: List<String>): Map<String, Int> {
        if (exerciseIds.isEmpty()) return emptyMap()
        // One batched query; group/count in memory keeps the repo interface small.
        return questionRepo.findByExerciseIdInOrderByOrderIndexAsc(exerciseIds)
            .groupingBy { it.exerciseId }
            .eachCount()
    }

    /**
     * Build a [SubmissionResponse] for one submission. Two queries: one for answers,
     * one for the answers' questions. Used by single-resource paths.
     */
    private fun buildSubmissionResponse(submission: Submission): SubmissionResponse {
        val answers = studentAnswerRepo.findBySubmissionIdOrderedByQuestion(submission.id)
        val questions = questionsByIdFor(answers)
        return submissionMapper.toResponse(submission, answers, questions)
    }

    /**
     * Build [SubmissionResponse]s for a batch of submissions in two batched queries.
     */
    private fun buildSubmissionResponses(submissions: List<Submission>): List<SubmissionResponse> {
        if (submissions.isEmpty()) return emptyList()
        val submissionIds = submissions.map { it.id }
        val allAnswers = studentAnswerRepo.findBySubmissionIdInOrdered(submissionIds)
        val answersBySubmission: Map<String, List<StudentAnswer>> =
            allAnswers.groupBy { it.submission.id }
        val questions = questionsByIdFor(allAnswers)
        return submissions.map { sub ->
            submissionMapper.toResponse(sub, answersBySubmission[sub.id] ?: emptyList(), questions)
        }
    }

    private fun questionsByIdFor(answers: List<StudentAnswer>): Map<String, Question> {
        val questionIds = answers.map { it.question.id }.distinct()
        if (questionIds.isEmpty()) return emptyMap()
        return questionRepo.findAllById(questionIds).associateBy { it.id }
    }

    companion object {
        const val EXPAND_QUESTIONS = "questions"
        const val EXPAND_MY_SUBMISSION = "mysubmission"
    }
}
