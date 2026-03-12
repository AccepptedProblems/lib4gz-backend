package com.example.lib4gz.courses.service

import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.UnauthorizedException
import com.example.lib4gz.courses.model.entity.Lesson
import com.example.lib4gz.courses.model.mapper.LessonMapper
import com.example.lib4gz.courses.model.payload.CreateLessonRequest
import com.example.lib4gz.courses.model.payload.LessonResponse
import com.example.lib4gz.courses.model.payload.UpdateLessonRequest
import com.example.lib4gz.courses.repo.LessonRepo
import com.example.lib4gz.courses.repo.ModuleRepo
import com.example.lib4gz.courses.repo.SummaryRepo
import com.example.lib4gz.exercise.repo.ExerciseRepo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

interface LessonService {

    fun createLesson(moduleId: String, userId: String, request: CreateLessonRequest): Mono<LessonResponse>

    fun getLessonById(lessonId: String, userId: String): Mono<LessonResponse>

    fun updateLesson(lessonId: String, userId: String, request: UpdateLessonRequest): Mono<LessonResponse>

    fun deleteLesson(lessonId: String, userId: String): Mono<Void>

    fun listModuleLessons(moduleId: String, userId: String): Flux<LessonResponse>
}

@Service
@Transactional
class LessonServiceImpl(
    private val lessonRepo: LessonRepo,
    private val moduleRepo: ModuleRepo,
    private val summaryRepo: SummaryRepo,
    private val exerciseRepo: ExerciseRepo,
    private val enrollmentService: EnrollmentService,
    private val lessonMapper: LessonMapper
) : LessonService {

    override fun createLesson(moduleId: String, userId: String, request: CreateLessonRequest): Mono<LessonResponse> {
        return Mono.fromCallable {
            val module = moduleRepo.findById(moduleId).orElseThrow {
                ResourceNotFoundException("Module not found with id: $moduleId")
            }

            // Only teachers can create lessons
            if (!enrollmentService.isTeacherInCourse(module.course.id, userId)) {
                throw UnauthorizedException("Only teachers can create lessons")
            }

            // Auto-assign order index if not provided
            val orderIndex = request.orderIndex ?: ((lessonRepo.findMaxOrderIndexByModuleId(moduleId) ?: -1) + 1)

            val lesson = Lesson(
                module = module,
                title = request.title,
                orderIndex = orderIndex
            )

            val savedLesson = lessonRepo.save(lesson)
            lessonMapper.toResponse(savedLesson, hasSummary = false, exerciseCount = 0)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun getLessonById(lessonId: String, userId: String): Mono<LessonResponse> {
        return Mono.fromCallable {
            val lesson = lessonRepo.findById(lessonId).orElseThrow {
                ResourceNotFoundException("Lesson not found with id: $lessonId")
            }

            // Check if user has access to this course
            if (!enrollmentService.isEnrolledInCourse(lesson.module.course.id, userId)) {
                throw UnauthorizedException("You do not have access to this lesson")
            }

            val hasSummary = summaryRepo.existsByLesson_Id(lessonId)
            val exerciseCount = exerciseRepo.countByLesson_Id(lessonId).toInt()
            lessonMapper.toResponse(lesson, hasSummary, exerciseCount)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun updateLesson(lessonId: String, userId: String, request: UpdateLessonRequest): Mono<LessonResponse> {
        return Mono.fromCallable {
            val lesson = lessonRepo.findById(lessonId).orElseThrow {
                ResourceNotFoundException("Lesson not found with id: $lessonId")
            }

            // Only teachers can update lessons
            if (!enrollmentService.isTeacherInCourse(lesson.module.course.id, userId)) {
                throw UnauthorizedException("Only teachers can update lessons")
            }

            request.title?.let { lesson.title = it }
            request.orderIndex?.let { lesson.orderIndex = it }

            val savedLesson = lessonRepo.save(lesson)
            val hasSummary = summaryRepo.existsByLesson_Id(lessonId)
            val exerciseCount = exerciseRepo.countByLesson_Id(lessonId).toInt()
            lessonMapper.toResponse(savedLesson, hasSummary, exerciseCount)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun deleteLesson(lessonId: String, userId: String): Mono<Void> {
        return Mono.fromCallable {
            val lesson = lessonRepo.findById(lessonId).orElseThrow {
                ResourceNotFoundException("Lesson not found with id: $lessonId")
            }

            // Only teachers can delete lessons
            if (!enrollmentService.isTeacherInCourse(lesson.module.course.id, userId)) {
                throw UnauthorizedException("Only teachers can delete lessons")
            }

            // Soft delete is handled by @SQLDelete annotation
            lessonRepo.delete(lesson)
        }.subscribeOn(Schedulers.boundedElastic()).then()
    }

    override fun listModuleLessons(moduleId: String, userId: String): Flux<LessonResponse> {
        return Mono.fromCallable {
            val module = moduleRepo.findById(moduleId).orElseThrow {
                ResourceNotFoundException("Module not found with id: $moduleId")
            }

            // Check if user has access to this course
            if (!enrollmentService.isEnrolledInCourse(module.course.id, userId)) {
                throw UnauthorizedException("You do not have access to this module")
            }

            val lessons = lessonRepo.findByModule_IdOrderByOrderIndexAsc(moduleId)
            lessons.map { lesson ->
                val hasSummary = summaryRepo.existsByLesson_Id(lesson.id)
                val exerciseCount = exerciseRepo.countByLesson_Id(lesson.id).toInt()
                lessonMapper.toResponse(lesson, hasSummary, exerciseCount)
            }
        }.flatMapMany { Flux.fromIterable(it) }
         .subscribeOn(Schedulers.boundedElastic())
    }
}
