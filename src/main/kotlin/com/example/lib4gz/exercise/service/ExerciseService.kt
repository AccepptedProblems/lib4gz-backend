package com.example.lib4gz.exercise.service

import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.UnauthorizedException
import com.example.lib4gz.courses.repo.LessonRepo
import com.example.lib4gz.courses.service.EnrollmentService
import com.example.lib4gz.exercise.model.entity.Exercise
import com.example.lib4gz.exercise.model.entity.Question
import com.example.lib4gz.exercise.model.mapper.ExerciseMapper
import com.example.lib4gz.exercise.model.payload.CreateExerciseRequest
import com.example.lib4gz.exercise.model.payload.ExerciseResponse
import com.example.lib4gz.exercise.model.payload.UpdateExerciseRequest
import com.example.lib4gz.exercise.repo.ExerciseRepo
import com.example.lib4gz.exercise.repo.QuestionRepo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

interface ExerciseService {

    fun createExercise(lessonId: String, userId: String, request: CreateExerciseRequest): Mono<ExerciseResponse>

    fun getExerciseById(exerciseId: String, userId: String): Mono<ExerciseResponse>

    fun updateExercise(exerciseId: String, userId: String, request: UpdateExerciseRequest): Mono<ExerciseResponse>

    fun deleteExercise(exerciseId: String, userId: String): Mono<Void>

    fun listLessonExercises(lessonId: String, userId: String): Flux<ExerciseResponse>

    // This remains synchronous as it's used internally by other services
    fun getCourseIdByExercise(exerciseId: String): String
}

@Service
@Transactional
class ExerciseServiceImpl(
    private val exerciseRepo: ExerciseRepo,
    private val lessonRepo: LessonRepo,
    private val questionRepo: QuestionRepo,
    private val enrollmentService: EnrollmentService,
    private val exerciseMapper: ExerciseMapper
) : ExerciseService {

    override fun createExercise(lessonId: String, userId: String, request: CreateExerciseRequest): Mono<ExerciseResponse> {
        return Mono.fromCallable {
            val lesson = lessonRepo.findById(lessonId).orElseThrow {
                ResourceNotFoundException("Lesson not found with id: $lessonId")
            }

            val courseId = lesson.module.course.id

            // Only teachers can create exercises
            if (!enrollmentService.isTeacherInCourse(courseId, userId)) {
                throw UnauthorizedException("Only teachers can create exercises")
            }

            // Auto-assign order index if not provided
            val orderIndex = request.orderIndex ?: ((exerciseRepo.findMaxOrderIndexByLessonId(lessonId) ?: -1) + 1)

            val exercise = Exercise(
                lesson = lesson,
                title = request.title,
                type = request.type,
                settings = request.settings,
                orderIndex = orderIndex
            )

            val savedExercise = exerciseRepo.save(exercise)

            // Create inline questions if provided
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

    override fun getExerciseById(exerciseId: String, userId: String): Mono<ExerciseResponse> {
        return Mono.fromCallable {
            val exercise = exerciseRepo.findById(exerciseId).orElseThrow {
                ResourceNotFoundException("Exercise not found with id: $exerciseId")
            }

            val courseId = exercise.lesson.module.course.id

            // Check if user has access to this course
            if (!enrollmentService.isEnrolledInCourse(courseId, userId)) {
                throw UnauthorizedException("You do not have access to this exercise")
            }

            val questionCount = questionRepo.countByExerciseId(exerciseId).toInt()
            exerciseMapper.toResponse(exercise, questionCount)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun updateExercise(exerciseId: String, userId: String, request: UpdateExerciseRequest): Mono<ExerciseResponse> {
        return Mono.fromCallable {
            val exercise = exerciseRepo.findById(exerciseId).orElseThrow {
                ResourceNotFoundException("Exercise not found with id: $exerciseId")
            }

            val courseId = exercise.lesson.module.course.id

            // Only teachers can update exercises
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

            // Only teachers can delete exercises
            if (!enrollmentService.isTeacherInCourse(courseId, userId)) {
                throw UnauthorizedException("Only teachers can delete exercises")
            }

            // Soft delete is handled by @SQLDelete annotation
            exerciseRepo.delete(exercise)
        }.subscribeOn(Schedulers.boundedElastic()).then()
    }

    override fun listLessonExercises(lessonId: String, userId: String): Flux<ExerciseResponse> {
        return Mono.fromCallable {
            val lesson = lessonRepo.findById(lessonId).orElseThrow {
                ResourceNotFoundException("Lesson not found with id: $lessonId")
            }

            val courseId = lesson.module.course.id

            // Check if user has access to this course
            if (!enrollmentService.isEnrolledInCourse(courseId, userId)) {
                throw UnauthorizedException("You do not have access to this lesson")
            }

            val exercises = exerciseRepo.findByLesson_IdOrderByOrderIndexAsc(lessonId)
            exercises.map { exercise ->
                val questionCount = questionRepo.countByExerciseId(exercise.id).toInt()
                exerciseMapper.toResponse(exercise, questionCount)
            }
        }.flatMapMany { Flux.fromIterable(it) }
         .subscribeOn(Schedulers.boundedElastic())
    }

    override fun getCourseIdByExercise(exerciseId: String): String {
        val exercise = exerciseRepo.findById(exerciseId).orElseThrow {
            ResourceNotFoundException("Exercise not found with id: $exerciseId")
        }
        return exercise.lesson.module.course.id
    }
}
