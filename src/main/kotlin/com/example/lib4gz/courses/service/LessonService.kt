package com.example.lib4gz.courses.service

import com.example.lib4gz.common.exception.BadRequestException
import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.UnauthorizedException
import com.example.lib4gz.common.utils.ExpandTokens
import com.example.lib4gz.courses.model.entity.Lesson
import com.example.lib4gz.courses.model.mapper.LessonMapper
import com.example.lib4gz.courses.model.mapper.ModuleMapper
import com.example.lib4gz.courses.model.mapper.SummaryMapper
import com.example.lib4gz.courses.model.payload.CreateLessonRequest
import com.example.lib4gz.courses.model.payload.LessonResponse
import com.example.lib4gz.courses.model.payload.UpdateLessonRequest
import com.example.lib4gz.courses.repo.LessonRepo
import com.example.lib4gz.courses.repo.ModuleRepo
import com.example.lib4gz.courses.repo.SummaryRepo
import com.example.lib4gz.exercise.repo.ExerciseRepo
import com.example.lib4gz.exercise.repo.QuestionRepo
import com.example.lib4gz.exercise.repo.SubmissionRepo
import com.example.lib4gz.exercise.service.ExerciseService
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

interface LessonService {

    fun createLesson(moduleId: String, userId: String, request: CreateLessonRequest): Mono<LessonResponse>

    fun getLessonById(lessonId: String, userId: String, expand: ExpandTokens = ExpandTokens.EMPTY): Mono<LessonResponse>

    fun updateLesson(lessonId: String, userId: String, request: UpdateLessonRequest): Mono<LessonResponse>

    fun deleteLesson(lessonId: String, userId: String): Mono<Void>

    fun listModuleLessons(moduleId: String, userId: String): Flux<LessonResponse>

    /** Rewrites every position from [lessonIds], which must list each visible lesson of the module once. */
    fun reorderLessons(moduleId: String, userId: String, lessonIds: List<String>): Flux<LessonResponse>
}

/**
 * SOLID notes:
 *  - Service orchestrates; mappers are pure; repos do I/O.
 *  - Open/Closed: adding a new expand value (e.g. `expand=assignments`) is a one-line
 *    branch here; no controller signature change is required.
 *  - Dependency Inversion: depends on [ExerciseService] (abstraction) for the expensive
 *    `expand=exercises` join so the N+1-avoidance logic lives in one place. The
 *    `@Lazy` on the ExerciseService dependency breaks the bidirectional graph that
 *    would otherwise form if ExerciseService later depended on LessonService.
 */
@Service
@Transactional
class LessonServiceImpl(
    private val lessonRepo: LessonRepo,
    private val moduleRepo: ModuleRepo,
    private val summaryRepo: SummaryRepo,
    private val exerciseRepo: ExerciseRepo,
    private val questionRepo: QuestionRepo,
    private val submissionRepo: SubmissionRepo,
    @Lazy private val exerciseService: ExerciseService,
    private val enrollmentService: EnrollmentService,
    private val lessonMapper: LessonMapper,
    private val moduleMapper: ModuleMapper,
    private val summaryMapper: SummaryMapper,
    private val transactionTemplate: TransactionTemplate
) : LessonService {

    override fun createLesson(moduleId: String, userId: String, request: CreateLessonRequest): Mono<LessonResponse> {
        return Mono.fromCallable {
            val module = moduleRepo.findById(moduleId).orElseThrow {
                ResourceNotFoundException("Module not found with id: $moduleId")
            }

            if (!enrollmentService.isTeacherInCourse(module.course.id, userId)) {
                throw UnauthorizedException("Only teachers can create lessons")
            }

            val orderIndex = request.orderIndex ?: ((lessonRepo.findMaxOrderIndexByModuleId(moduleId) ?: -1) + 1)

            val lesson = Lesson(
                module = module,
                title = request.title,
                orderIndex = orderIndex
            )

            val savedLesson = lessonRepo.save(lesson)
            lessonMapper.toResponse(
                lesson = savedLesson,
                hasSummary = false,
                exerciseCount = 0,
                moduleTitle = module.title
            )
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun getLessonById(lessonId: String, userId: String, expand: ExpandTokens): Mono<LessonResponse> {
        return Mono.fromCallable {
            val lesson = lessonRepo.findById(lessonId).orElseThrow {
                ResourceNotFoundException("Lesson not found with id: $lessonId")
            }

            val courseId = lesson.module.course.id
            if (!enrollmentService.isEnrolledInCourse(courseId, userId)) {
                throw UnauthorizedException("You do not have access to this lesson")
            }

            val hasSummary = summaryRepo.existsByLesson_Id(lessonId)

            val moduleSummary = if (expand.has(EXPAND_MODULE)) moduleMapper.toSummary(lesson.module) else null

            val summary = if (expand.has(EXPAND_SUMMARY) && hasSummary) {
                summaryRepo.findByLesson_Id(lessonId)?.let(summaryMapper::toResponse)
            } else null

            val exercises = if (expand.has(EXPAND_EXERCISES)) {
                // Reuse ExerciseService's batched, N+1-safe loader. The same expand
                // tokens flow through, so callers can request `expand=exercises,questions,mySubmission`
                // on the lesson endpoint and get the exercise tree in one round-trip.
                exerciseService.listLessonExercises(lessonId, userId, expand)
                    .collectList()
                    .block()
            } else null

            val exerciseCount = exercises?.size ?: exerciseRepo.countByLesson_Id(lessonId).toInt()

            val submissionCount =
                if (expand.has(EXPAND_SUBMISSION_COUNT) && enrollmentService.isTeacherInCourse(courseId, userId)) {
                    submissionRepo.countByLessonId(lessonId).toInt()
                } else null

            lessonMapper.toResponse(
                lesson = lesson,
                hasSummary = hasSummary,
                exerciseCount = exerciseCount,
                moduleTitle = lesson.module.title,
                moduleSummary = moduleSummary,
                summary = summary,
                exercises = exercises
            ).copy(submissionCount = submissionCount)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun updateLesson(lessonId: String, userId: String, request: UpdateLessonRequest): Mono<LessonResponse> {
        return Mono.fromCallable {
            val lesson = lessonRepo.findById(lessonId).orElseThrow {
                ResourceNotFoundException("Lesson not found with id: $lessonId")
            }

            if (!enrollmentService.isTeacherInCourse(lesson.module.course.id, userId)) {
                throw UnauthorizedException("Only teachers can update lessons")
            }

            request.title?.let { lesson.title = it }
            request.orderIndex?.let { lesson.orderIndex = it }

            val savedLesson = lessonRepo.save(lesson)
            val hasSummary = summaryRepo.existsByLesson_Id(lessonId)
            lessonMapper.toResponse(
                lesson = savedLesson,
                hasSummary = hasSummary,
                exerciseCount = exerciseRepo.countByLesson_Id(lessonId).toInt(),
                moduleTitle = lesson.module.title
            )
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun deleteLesson(lessonId: String, userId: String): Mono<Void> {
        return Mono.fromCallable {
            transactionTemplate.execute {
                val lesson = lessonRepo.findById(lessonId).orElseThrow {
                    ResourceNotFoundException("Lesson not found with id: $lessonId")
                }

                if (!enrollmentService.isTeacherInCourse(lesson.module.course.id, userId)) {
                    throw UnauthorizedException("Only teachers can delete lessons")
                }

                // The lesson's exercises and their questions are hidden with it. Its summary is
                // removed by the lesson's JPA cascade.
                questionRepo.hideByLessonId(lessonId)
                exerciseRepo.hideByLessonId(lessonId)
                lessonRepo.delete(lesson)
            }
        }.subscribeOn(Schedulers.boundedElastic()).then()
    }

    override fun listModuleLessons(moduleId: String, userId: String): Flux<LessonResponse> {
        return Mono.fromCallable {
            val module = moduleRepo.findById(moduleId).orElseThrow {
                ResourceNotFoundException("Module not found with id: $moduleId")
            }

            if (!enrollmentService.isEnrolledInCourse(module.course.id, userId)) {
                throw UnauthorizedException("You do not have access to this module")
            }

            val lessons = lessonRepo.findByModule_IdOrderByOrderIndexAscCreatedAtAsc(moduleId)
            lessons.map { lesson ->
                lessonMapper.toResponse(
                    lesson = lesson,
                    hasSummary = summaryRepo.existsByLesson_Id(lesson.id),
                    exerciseCount = exerciseRepo.countByLesson_Id(lesson.id).toInt(),
                    moduleTitle = module.title
                )
            }
        }.flatMapMany { Flux.fromIterable(it) }
            .subscribeOn(Schedulers.boundedElastic())
    }

    override fun reorderLessons(moduleId: String, userId: String, lessonIds: List<String>): Flux<LessonResponse> {
        return Mono.fromCallable {
            transactionTemplate.execute {
                val module = moduleRepo.findById(moduleId).orElseThrow {
                    ResourceNotFoundException("Module not found with id: $moduleId")
                }

                if (!enrollmentService.isTeacherInCourse(module.course.id, userId)) {
                    throw UnauthorizedException("Only teachers can reorder lessons")
                }

                val lessons = lessonRepo.findByModule_IdOrderByOrderIndexAscCreatedAtAsc(moduleId)
                if (lessonIds.size != lessons.size || lessonIds.toSet() != lessons.map { it.id }.toSet()) {
                    throw BadRequestException(
                        "The lessons of this module changed since the page was loaded. Refresh the page and try again."
                    )
                }

                // Entity writes, not a bulk update, so the freshness listener sees each change.
                val byId = lessons.associateBy { it.id }
                lessonIds.forEachIndexed { index, id -> byId.getValue(id).orderIndex = index }
            }
        }.subscribeOn(Schedulers.boundedElastic())
            .thenMany(listModuleLessons(moduleId, userId))
    }

    companion object {
        const val EXPAND_SUMMARY = "summary"
        const val EXPAND_EXERCISES = "exercises"
        const val EXPAND_MODULE = "module"
        const val EXPAND_SUBMISSION_COUNT = "submissioncount"
    }
}
