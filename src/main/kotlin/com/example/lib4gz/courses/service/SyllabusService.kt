package com.example.lib4gz.courses.service

import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.UnauthorizedException
import com.example.lib4gz.courses.model.entity.Course
import com.example.lib4gz.courses.model.entity.EnrollmentStatus
import com.example.lib4gz.courses.model.entity.Lesson
import com.example.lib4gz.courses.model.entity.Visibility
import com.example.lib4gz.courses.model.mapper.CourseMapper
import com.example.lib4gz.courses.model.mapper.EnrollmentMapper
import com.example.lib4gz.courses.model.mapper.LessonMapper
import com.example.lib4gz.courses.model.payload.CourseProgress
import com.example.lib4gz.courses.model.payload.SyllabusModule
import com.example.lib4gz.courses.model.payload.SyllabusResponse
import com.example.lib4gz.courses.repo.CourseRepo
import com.example.lib4gz.courses.repo.EnrollmentRepo
import com.example.lib4gz.courses.repo.LessonRepo
import com.example.lib4gz.courses.repo.ModuleRepo
import com.example.lib4gz.courses.repo.SummaryRepo
import com.example.lib4gz.exercise.repo.ExerciseRepo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * Composite read for the course "table of contents" — what every course shell screen
 * (`CourseLayout`, `CourseHome`, `CourseMaterial`) needs in one round-trip.
 *
 * SOLID:
 *  - Single Responsibility: assembles one aggregate view. CRUD lives elsewhere.
 *  - Dependency Inversion: depends on repository abstractions (Spring Data interfaces)
 *    and mapper components.
 *  - Open/Closed: new fields are added in the response DTO; this service composes them
 *    without leaking JPA entities outward.
 *
 * The implementation issues a fixed, small number of queries (6) regardless of course
 * size — no per-module or per-lesson fan-out.
 */
interface SyllabusService {
    fun getSyllabus(courseId: String, userId: String): Mono<SyllabusResponse>
}

@Service
@Transactional(readOnly = true)
class SyllabusServiceImpl(
    private val courseRepo: CourseRepo,
    private val moduleRepo: ModuleRepo,
    private val lessonRepo: LessonRepo,
    private val summaryRepo: SummaryRepo,
    private val exerciseRepo: ExerciseRepo,
    private val enrollmentRepo: EnrollmentRepo,
    private val lessonMapper: LessonMapper,
    private val enrollmentMapper: EnrollmentMapper,
    private val progressService: ProgressService
) : SyllabusService {

    override fun getSyllabus(courseId: String, userId: String): Mono<SyllabusResponse> {
        return Mono.fromCallable {
            val course = courseRepo.findById(courseId).orElseThrow {
                ResourceNotFoundException("Course not found with id: $courseId")
            }

            val myEnrollment = enrollmentRepo.findByCourse_IdAndUser_Id(courseId, userId)
            authorizeReadAccess(course, userId, myEnrollment != null && myEnrollment.status == EnrollmentStatus.ACTIVE)

            val modules = moduleRepo.findByCourse_IdOrderByOrderIndexAsc(courseId)
            val lessonsByModuleId: Map<String, List<Lesson>> =
                lessonRepo.findByCourseId(courseId)
                    .sortedBy { it.orderIndex }
                    .groupBy { it.module.id }

            val lessonIdsWithSummary: Set<String> =
                summaryRepo.findLessonIdsWithSummaryByCourseId(courseId).toSet()

            val exerciseCountByLesson: Map<String, Int> =
                exerciseRepo.findByCourseId(courseId)
                    .groupingBy { it.lesson.id }
                    .eachCount()

            val enrollmentCount =
                enrollmentRepo.countByCourse_IdAndStatus(courseId, EnrollmentStatus.ACTIVE).toInt()

            // lessonId → done, only for exercise-bearing lessons; a lesson absent
            // from this map has no exercises and therefore no completion state.
            val completionByLesson = progressService.computeLessonCompletion(courseId, userId)

            val syllabusModules = modules.map { module ->
                val moduleLessons = lessonsByModuleId[module.id].orEmpty()
                SyllabusModule(
                    id = module.id,
                    courseId = module.course.id,
                    title = module.title,
                    orderIndex = module.orderIndex,
                    lessonCount = moduleLessons.size,
                    createdAt = module.createdAt,
                    updatedAt = module.updatedAt,
                    lessons = moduleLessons.map { lesson ->
                        val completion = completionByLesson[lesson.id]
                        lessonMapper.toResponse(
                            lesson = lesson,
                            hasSummary = lesson.id in lessonIdsWithSummary,
                            exerciseCount = exerciseCountByLesson[lesson.id] ?: 0,
                            completed = completion?.completed,
                            completedExerciseCount = completion?.doneExercises,
                            moduleTitle = module.title
                        )
                    }
                )
            }

            val mySummary = myEnrollment?.let(enrollmentMapper::toSummary)
            val progress = myEnrollment?.let {
                val visitedTitle = it.lastVisitedLessonId?.let { id ->
                    lessonsByModuleId.values.flatten().firstOrNull { l -> l.id == id }?.title
                }
                CourseProgress(
                    completedLessons = completionByLesson.count { entry -> entry.value.completed },
                    totalLessons = completionByLesson.size,
                    lastVisitedLessonId = if (visitedTitle != null) it.lastVisitedLessonId else null,
                    lastVisitedLessonTitle = visitedTitle,
                    lastVisitedAt = if (visitedTitle != null) it.lastVisitedAt else null
                )
            }
            val courseResponse = CourseMapper.toResponse(
                course = course,
                moduleCount = modules.size,
                enrollmentCount = enrollmentCount,
                myEnrollment = mySummary,
                progress = progress
            )

            SyllabusResponse(course = courseResponse, modules = syllabusModules)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    /**
     * Read access: creator OR ACTIVE-enrolled OR PUBLIC course.
     * Pending and rejected enrollments do not grant access.
     */
    private fun authorizeReadAccess(course: Course, userId: String, isActiveEnrolled: Boolean) {
        if (course.createdBy.id == userId) return
        if (course.visibility == Visibility.PUBLIC) return
        if (isActiveEnrolled) return
        throw UnauthorizedException("You do not have access to this course")
    }
}
