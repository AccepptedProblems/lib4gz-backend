package com.example.lib4gz.exercise.service

import com.example.lib4gz.common.exception.BadRequestException
import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.UnauthorizedException
import com.example.lib4gz.courses.service.EnrollmentService
import com.example.lib4gz.exercise.model.entity.Question
import com.example.lib4gz.exercise.model.entity.QuestionVisibility
import com.example.lib4gz.exercise.model.mapper.QuestionMapper
import com.example.lib4gz.exercise.model.payload.*
import com.example.lib4gz.exercise.repo.ExerciseRepo
import com.example.lib4gz.exercise.repo.QuestionRepo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

interface QuestionService {

    fun createQuestions(exerciseId: String, userId: String, request: CreateQuestionsRequest): Flux<QuestionResponse>

    fun updateQuestions(exerciseId: String, userId: String, request: UpdateQuestionsRequest): Mono<QuestionsResponse>

    fun getQuestionsByExercise(exerciseId: String, userId: String): Flux<QuestionResponse>

    fun getVisibleQuestionsByExercise(exerciseId: String, userId: String): Flux<QuestionResponse>
}

@Service
@Transactional
class QuestionServiceImpl(
    private val questionRepo: QuestionRepo,
    private val exerciseRepo: ExerciseRepo,
    private val exerciseService: ExerciseService,
    private val enrollmentService: EnrollmentService,
    private val questionMapper: QuestionMapper
) : QuestionService {

    override fun createQuestions(exerciseId: String, userId: String, request: CreateQuestionsRequest): Flux<QuestionResponse> {
        return Mono.fromCallable {
            // Verify exercise exists
            if (!exerciseRepo.existsById(exerciseId)) {
                throw ResourceNotFoundException("Exercise not found with id: $exerciseId")
            }

            val courseId = exerciseService.getCourseIdByExercise(exerciseId)

            // Only teachers can create questions
            if (!enrollmentService.isTeacherInCourse(courseId, userId)) {
                throw UnauthorizedException("Only teachers can create questions")
            }

            // Get current max order index
            var currentMaxIndex = questionRepo.findMaxOrderIndexByExerciseId(exerciseId) ?: -1

            val questions = request.questions.map { item ->
                val orderIndex = item.orderIndex ?: ++currentMaxIndex
                Question(
                    exerciseId = exerciseId,
                    content = item.content,
                    orderIndex = orderIndex,
                    meta = item.meta,
                    visibility = item.visibility
                )
            }

            val savedQuestions = questionRepo.saveAll(questions)
            savedQuestions.map { questionMapper.toResponse(it) }
        }.flatMapMany { Flux.fromIterable(it) }
         .subscribeOn(Schedulers.boundedElastic())
    }

    override fun updateQuestions(exerciseId: String, userId: String, request: UpdateQuestionsRequest): Mono<QuestionsResponse> {
        return Mono.fromCallable {
            // Verify exercise exists
            if (!exerciseRepo.existsById(exerciseId)) {
                throw ResourceNotFoundException("Exercise not found with id: $exerciseId")
            }

            val courseId = exerciseService.getCourseIdByExercise(exerciseId)

            // Only teachers can update questions
            if (!enrollmentService.isTeacherInCourse(courseId, userId)) {
                throw UnauthorizedException("Only teachers can update questions")
            }

            val created = mutableListOf<QuestionResponse>()
            val updated = mutableListOf<QuestionResponse>()
            val deleted = mutableListOf<String>()

            var currentMaxIndex = questionRepo.findMaxOrderIndexByExerciseId(exerciseId) ?: -1

            for (item in request.questions) {
                when (item.action) {
                    QuestionAction.CREATE -> {
                        if (item.content.isNullOrBlank()) {
                            throw BadRequestException("Content is required for CREATE action")
                        }
                        val orderIndex = item.orderIndex ?: ++currentMaxIndex
                        val question = Question(
                            exerciseId = exerciseId,
                            content = item.content,
                            orderIndex = orderIndex,
                            meta = item.meta ?: emptyMap(),
                            visibility = item.visibility ?: QuestionVisibility.VISIBLE
                        )
                        val savedQuestion = questionRepo.save(question)
                        created.add(questionMapper.toResponse(savedQuestion))
                    }

                    QuestionAction.UPDATE -> {
                        if (item.questionId == null) {
                            throw BadRequestException("questionId is required for UPDATE action")
                        }
                        val question = questionRepo.findById(item.questionId).orElseThrow {
                            ResourceNotFoundException("Question not found with id: ${item.questionId}")
                        }
                        // Verify question belongs to this exercise
                        if (question.exerciseId != exerciseId) {
                            throw BadRequestException("Question ${item.questionId} does not belong to exercise $exerciseId")
                        }
                        item.content?.let { question.content = it }
                        item.orderIndex?.let { question.orderIndex = it }
                        item.meta?.let { question.meta = it }
                        item.visibility?.let { question.visibility = it }
                        val savedQuestion = questionRepo.save(question)
                        updated.add(questionMapper.toResponse(savedQuestion))
                    }

                    QuestionAction.DELETE -> {
                        if (item.questionId == null) {
                            throw BadRequestException("questionId is required for DELETE action")
                        }
                        val question = questionRepo.findById(item.questionId).orElseThrow {
                            ResourceNotFoundException("Question not found with id: ${item.questionId}")
                        }
                        // Verify question belongs to this exercise
                        if (question.exerciseId != exerciseId) {
                            throw BadRequestException("Question ${item.questionId} does not belong to exercise $exerciseId")
                        }
                        // Soft delete handled by @SQLDelete
                        questionRepo.delete(question)
                        deleted.add(item.questionId)
                    }
                }
            }

            QuestionsResponse(
                created = created,
                updated = updated,
                deleted = deleted
            )
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun getQuestionsByExercise(exerciseId: String, userId: String): Flux<QuestionResponse> {
        return Mono.fromCallable {
            // Verify exercise exists
            if (!exerciseRepo.existsById(exerciseId)) {
                throw ResourceNotFoundException("Exercise not found with id: $exerciseId")
            }

            val courseId = exerciseService.getCourseIdByExercise(exerciseId)

            // Check if user has access to this course
            if (!enrollmentService.isEnrolledInCourse(courseId, userId)) {
                throw UnauthorizedException("You do not have access to this exercise")
            }

            // Teachers see all questions, learners only see visible ones
            val isTeacher = enrollmentService.isTeacherInCourse(courseId, userId)
            val questions = if (isTeacher) {
                questionRepo.findByExerciseIdOrderByOrderIndexAsc(exerciseId)
            } else {
                questionRepo.findByExerciseIdAndVisibility(exerciseId, QuestionVisibility.VISIBLE)
                    .sortedBy { it.orderIndex }
            }

            questions.map { questionMapper.toResponse(it) }
        }.flatMapMany { Flux.fromIterable(it) }
         .subscribeOn(Schedulers.boundedElastic())
    }

    override fun getVisibleQuestionsByExercise(exerciseId: String, userId: String): Flux<QuestionResponse> {
        return Mono.fromCallable {
            // Verify exercise exists
            if (!exerciseRepo.existsById(exerciseId)) {
                throw ResourceNotFoundException("Exercise not found with id: $exerciseId")
            }

            val courseId = exerciseService.getCourseIdByExercise(exerciseId)

            // Check if user has access to this course
            if (!enrollmentService.isEnrolledInCourse(courseId, userId)) {
                throw UnauthorizedException("You do not have access to this exercise")
            }

            val questions = questionRepo.findByExerciseIdAndVisibility(exerciseId, QuestionVisibility.VISIBLE)
                .sortedBy { it.orderIndex }

            questions.map { questionMapper.toResponse(it) }
        }.flatMapMany { Flux.fromIterable(it) }
         .subscribeOn(Schedulers.boundedElastic())
    }
}
