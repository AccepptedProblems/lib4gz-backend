package com.example.lib4gz.courses.service

import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.UnauthorizedException
import com.example.lib4gz.courses.model.entity.Module
import com.example.lib4gz.courses.model.mapper.ModuleMapper
import com.example.lib4gz.courses.model.payload.CreateModuleRequest
import com.example.lib4gz.courses.model.payload.ModuleResponse
import com.example.lib4gz.courses.model.payload.UpdateModuleRequest
import com.example.lib4gz.courses.repo.CourseRepo
import com.example.lib4gz.courses.repo.LessonRepo
import com.example.lib4gz.courses.repo.ModuleRepo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

interface ModuleService {

    fun createModule(courseId: String, userId: String, request: CreateModuleRequest): Mono<ModuleResponse>

    fun getModuleById(moduleId: String, userId: String): Mono<ModuleResponse>

    fun updateModule(moduleId: String, userId: String, request: UpdateModuleRequest): Mono<ModuleResponse>

    fun deleteModule(moduleId: String, userId: String): Mono<Void>

    fun listCourseModules(courseId: String, userId: String): Flux<ModuleResponse>
}

@Service
@Transactional
class ModuleServiceImpl(
    private val moduleRepo: ModuleRepo,
    private val courseRepo: CourseRepo,
    private val lessonRepo: LessonRepo,
    private val enrollmentService: EnrollmentService,
    private val moduleMapper: ModuleMapper
) : ModuleService {

    override fun createModule(courseId: String, userId: String, request: CreateModuleRequest): Mono<ModuleResponse> {
        return Mono.fromCallable {
            val course = courseRepo.findById(courseId).orElseThrow {
                ResourceNotFoundException("Course not found with id: $courseId")
            }

            // Only teachers can create modules
            if (!enrollmentService.isTeacherInCourse(courseId, userId)) {
                throw UnauthorizedException("Only teachers can create modules")
            }

            // Auto-assign order index if not provided
            val orderIndex = request.orderIndex ?: ((moduleRepo.findMaxOrderIndexByCourseId(courseId) ?: -1) + 1)

            val module = Module(
                course = course,
                title = request.title,
                orderIndex = orderIndex
            )

            val savedModule = moduleRepo.save(module)
            moduleMapper.toResponse(savedModule, 0)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun getModuleById(moduleId: String, userId: String): Mono<ModuleResponse> {
        return Mono.fromCallable {
            val module = moduleRepo.findById(moduleId).orElseThrow {
                ResourceNotFoundException("Module not found with id: $moduleId")
            }

            // Check if user has access to this course
            if (!enrollmentService.isEnrolledInCourse(module.course.id, userId)) {
                throw UnauthorizedException("You do not have access to this module")
            }

            val lessonCount = lessonRepo.countByModule_Id(moduleId).toInt()
            moduleMapper.toResponse(module, lessonCount)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun updateModule(moduleId: String, userId: String, request: UpdateModuleRequest): Mono<ModuleResponse> {
        return Mono.fromCallable {
            val module = moduleRepo.findById(moduleId).orElseThrow {
                ResourceNotFoundException("Module not found with id: $moduleId")
            }

            // Only teachers can update modules
            if (!enrollmentService.isTeacherInCourse(module.course.id, userId)) {
                throw UnauthorizedException("Only teachers can update modules")
            }

            request.title?.let { module.title = it }
            request.orderIndex?.let { module.orderIndex = it }

            val savedModule = moduleRepo.save(module)
            val lessonCount = lessonRepo.countByModule_Id(moduleId).toInt()
            moduleMapper.toResponse(savedModule, lessonCount)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun deleteModule(moduleId: String, userId: String): Mono<Void> {
        return Mono.fromCallable {
            val module = moduleRepo.findById(moduleId).orElseThrow {
                ResourceNotFoundException("Module not found with id: $moduleId")
            }

            // Only teachers can delete modules
            if (!enrollmentService.isTeacherInCourse(module.course.id, userId)) {
                throw UnauthorizedException("Only teachers can delete modules")
            }

            // Soft delete is handled by @SQLDelete annotation
            moduleRepo.delete(module)
        }.subscribeOn(Schedulers.boundedElastic()).then()
    }

    override fun listCourseModules(courseId: String, userId: String): Flux<ModuleResponse> {
        return Mono.fromCallable {
            // Check if course exists
            if (!courseRepo.existsById(courseId)) {
                throw ResourceNotFoundException("Course not found with id: $courseId")
            }

            // Check if user has access to this course
            if (!enrollmentService.isEnrolledInCourse(courseId, userId)) {
                throw UnauthorizedException("You do not have access to this course")
            }

            val modules = moduleRepo.findByCourse_IdOrderByOrderIndexAsc(courseId)
            modules.map { module ->
                val lessonCount = lessonRepo.countByModule_Id(module.id).toInt()
                moduleMapper.toResponse(module, lessonCount)
            }
        }.flatMapMany { Flux.fromIterable(it) }
         .subscribeOn(Schedulers.boundedElastic())
    }
}
