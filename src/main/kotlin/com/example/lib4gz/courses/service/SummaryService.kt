package com.example.lib4gz.courses.service

import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.UnauthorizedException
import com.example.lib4gz.courses.model.entity.Summary
import com.example.lib4gz.courses.model.mapper.SummaryMapper
import com.example.lib4gz.courses.model.payload.CreateSummaryRequest
import com.example.lib4gz.courses.model.payload.SummaryResponse
import com.example.lib4gz.courses.model.payload.UpdateSummaryRequest
import com.example.lib4gz.courses.repo.LessonRepo
import com.example.lib4gz.courses.repo.SummaryRepo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

interface SummaryService {

    fun getSummary(lessonId: String, userId: String): Mono<SummaryResponse>

    fun createSummary(lessonId: String, userId: String, request: CreateSummaryRequest): Mono<SummaryResponse>

    fun updateSummary(lessonId: String, userId: String, request: UpdateSummaryRequest): Mono<SummaryResponse>

    fun createOrUpdateSummary(lessonId: String, userId: String, content: String): Mono<SummaryResponse>
}

@Service
@Transactional
class SummaryServiceImpl(
    private val summaryRepo: SummaryRepo,
    private val lessonRepo: LessonRepo,
    private val userRepo: UserRepo,
    private val enrollmentService: EnrollmentService,
    private val summaryMapper: SummaryMapper
) : SummaryService {

    override fun getSummary(lessonId: String, userId: String): Mono<SummaryResponse> {
        return Mono.fromCallable {
            val lesson = lessonRepo.findById(lessonId).orElseThrow {
                ResourceNotFoundException("Lesson not found with id: $lessonId")
            }

            // Check if user has access to this course
            if (!enrollmentService.isEnrolledInCourse(lesson.module.course.id, userId)) {
                throw UnauthorizedException("You do not have access to this lesson")
            }

            val summary = summaryRepo.findByLesson_Id(lessonId)
            summary?.let { summaryMapper.toResponse(it) }
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun createSummary(lessonId: String, userId: String, request: CreateSummaryRequest): Mono<SummaryResponse> {
        return Mono.fromCallable {
            val lesson = lessonRepo.findById(lessonId).orElseThrow {
                ResourceNotFoundException("Lesson not found with id: $lessonId")
            }

            // Only teachers can create summaries
            if (!enrollmentService.isTeacherInCourse(lesson.module.course.id, userId)) {
                throw UnauthorizedException("Only teachers can create summaries")
            }

            val user = userRepo.findById(userId).orElseThrow {
                ResourceNotFoundException("User not found with id: $userId")
            }

            val summary = Summary(
                lesson = lesson,
                content = request.content,
                editedBy = user
            )

            val savedSummary = summaryRepo.save(summary)
            summaryMapper.toResponse(savedSummary)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun updateSummary(lessonId: String, userId: String, request: UpdateSummaryRequest): Mono<SummaryResponse> {
        return Mono.fromCallable {
            val lesson = lessonRepo.findById(lessonId).orElseThrow {
                ResourceNotFoundException("Lesson not found with id: $lessonId")
            }

            // Only teachers can update summaries
            if (!enrollmentService.isTeacherInCourse(lesson.module.course.id, userId)) {
                throw UnauthorizedException("Only teachers can update summaries")
            }

            val summary = summaryRepo.findByLesson_Id(lessonId) ?: throw ResourceNotFoundException("Summary not found for lesson: $lessonId")

            val user = userRepo.findById(userId).orElseThrow {
                ResourceNotFoundException("User not found with id: $userId")
            }

            summary.content = request.content
            summary.editedBy = user
            summary.version += 1

            val savedSummary = summaryRepo.save(summary)
            summaryMapper.toResponse(savedSummary)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun createOrUpdateSummary(lessonId: String, userId: String, content: String): Mono<SummaryResponse> {
        return Mono.fromCallable {
            val lesson = lessonRepo.findById(lessonId).orElseThrow {
                ResourceNotFoundException("Lesson not found with id: $lessonId")
            }

            if (!enrollmentService.isTeacherInCourse(lesson.module.course.id, userId)) {
                throw UnauthorizedException("Only teachers can create or update summaries")
            }

            val user = userRepo.findById(userId).orElseThrow {
                ResourceNotFoundException("User not found with id: $userId")
            }

            val existingSummary = summaryRepo.findByLesson_Id(lessonId)
            if (existingSummary != null) {
                existingSummary.content = content
                existingSummary.editedBy = user
                existingSummary.version += 1
                summaryMapper.toResponse(summaryRepo.save(existingSummary))
            } else {
                val summary = Summary(
                    lesson = lesson,
                    content = content,
                    editedBy = user
                )
                summaryMapper.toResponse(summaryRepo.save(summary))
            }
        }.subscribeOn(Schedulers.boundedElastic())
    }
}
