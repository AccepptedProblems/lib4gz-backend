package com.example.lib4gz.courses.service

import com.example.lib4gz.common.exception.BadRequestException
import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.UnauthorizedException
import com.example.lib4gz.common.utils.ExpandTokens
import com.example.lib4gz.courses.model.entity.Module
import com.example.lib4gz.courses.model.mapper.ModuleMapper
import com.example.lib4gz.courses.model.payload.CreateModuleRequest
import com.example.lib4gz.courses.model.payload.ModuleResponse
import com.example.lib4gz.courses.model.payload.UpdateModuleRequest
import com.example.lib4gz.courses.repo.CourseRepo
import com.example.lib4gz.courses.repo.LessonRepo
import com.example.lib4gz.courses.repo.ModuleRepo
import com.example.lib4gz.exercise.repo.ExerciseRepo
import com.example.lib4gz.exercise.repo.QuestionRepo
import com.example.lib4gz.exercise.repo.SubmissionRepo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

interface ModuleService {

    fun createModule(courseId: String, userId: String, request: CreateModuleRequest): Mono<ModuleResponse>

    fun getModuleById(moduleId: String, userId: String, expand: ExpandTokens = ExpandTokens.EMPTY): Mono<ModuleResponse>

    fun updateModule(moduleId: String, userId: String, request: UpdateModuleRequest): Mono<ModuleResponse>

    fun deleteModule(moduleId: String, userId: String): Mono<Void>

    fun listCourseModules(courseId: String, userId: String): Flux<ModuleResponse>

    /** Rewrites every position from [moduleIds], which must list each visible module of the course once. */
    fun reorderModules(courseId: String, userId: String, moduleIds: List<String>): Flux<ModuleResponse>
}

@Service
@Transactional
class ModuleServiceImpl(
    private val moduleRepo: ModuleRepo,
    private val courseRepo: CourseRepo,
    private val lessonRepo: LessonRepo,
    private val exerciseRepo: ExerciseRepo,
    private val questionRepo: QuestionRepo,
    private val submissionRepo: SubmissionRepo,
    private val enrollmentService: EnrollmentService,
    private val moduleMapper: ModuleMapper,
    private val transactionTemplate: TransactionTemplate
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

    override fun getModuleById(moduleId: String, userId: String, expand: ExpandTokens): Mono<ModuleResponse> {
        return Mono.fromCallable {
            val module = moduleRepo.findById(moduleId).orElseThrow {
                ResourceNotFoundException("Module not found with id: $moduleId")
            }

            // Check if user has access to this course
            if (!enrollmentService.isEnrolledInCourse(module.course.id, userId)) {
                throw UnauthorizedException("You do not have access to this module")
            }

            val lessonCount = lessonRepo.countByModule_Id(moduleId).toInt()
            val submissionCount =
                if (expand.has(EXPAND_SUBMISSION_COUNT) && enrollmentService.isTeacherInCourse(module.course.id, userId)) {
                    submissionRepo.countByModuleId(moduleId).toInt()
                } else null
            moduleMapper.toResponse(module, lessonCount).copy(submissionCount = submissionCount)
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
            transactionTemplate.execute {
                val module = moduleRepo.findById(moduleId).orElseThrow {
                    ResourceNotFoundException("Module not found with id: $moduleId")
                }

                // Only teachers can delete modules
                if (!enrollmentService.isTeacherInCourse(module.course.id, userId)) {
                    throw UnauthorizedException("Only teachers can delete modules")
                }

                // The whole subtree is hidden with the module. Lessons go before the module's own
                // @SQLDelete, so its JPA cascade finds none left to remove one by one.
                questionRepo.hideByModuleId(moduleId)
                exerciseRepo.hideByModuleId(moduleId)
                lessonRepo.hideByModuleId(moduleId)
                moduleRepo.delete(module)
            }
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

            val modules = moduleRepo.findByCourse_IdOrderByOrderIndexAscCreatedAtAsc(courseId)
            modules.map { module ->
                val lessonCount = lessonRepo.countByModule_Id(module.id).toInt()
                moduleMapper.toResponse(module, lessonCount)
            }
        }.flatMapMany { Flux.fromIterable(it) }
         .subscribeOn(Schedulers.boundedElastic())
    }

    override fun reorderModules(courseId: String, userId: String, moduleIds: List<String>): Flux<ModuleResponse> {
        return Mono.fromCallable {
            transactionTemplate.execute {
                if (!courseRepo.existsById(courseId)) {
                    throw ResourceNotFoundException("Course not found with id: $courseId")
                }

                if (!enrollmentService.isTeacherInCourse(courseId, userId)) {
                    throw UnauthorizedException("Only teachers can reorder modules")
                }

                val modules = moduleRepo.findByCourse_IdOrderByOrderIndexAscCreatedAtAsc(courseId)
                if (moduleIds.size != modules.size || moduleIds.toSet() != modules.map { it.id }.toSet()) {
                    throw BadRequestException(
                        "The modules of this course changed since the page was loaded. Refresh the page and try again."
                    )
                }

                // Entity writes, not a bulk update, so the freshness listener sees each change.
                val byId = modules.associateBy { it.id }
                moduleIds.forEachIndexed { index, id -> byId.getValue(id).orderIndex = index }
            }
        }.subscribeOn(Schedulers.boundedElastic())
            .thenMany(listCourseModules(courseId, userId))
    }

    companion object {
        const val EXPAND_SUBMISSION_COUNT = "submissioncount"
    }
}
