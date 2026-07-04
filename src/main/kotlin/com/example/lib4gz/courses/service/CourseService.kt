package com.example.lib4gz.courses.service

import com.example.lib4gz.auth.model.entity.User
import com.example.lib4gz.auth.model.entity.UserRole
import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.UnauthorizedException
import com.example.lib4gz.courses.model.entity.Course
import com.example.lib4gz.courses.model.entity.Enrollment
import com.example.lib4gz.courses.model.entity.EnrollmentRole
import com.example.lib4gz.courses.model.entity.EnrollmentStatus
import com.example.lib4gz.courses.model.entity.Visibility
import com.example.lib4gz.courses.model.mapper.CourseMapper
import com.example.lib4gz.courses.model.mapper.EnrollmentMapper
import com.example.lib4gz.courses.model.payload.CreateCourseRequest
import com.example.lib4gz.courses.model.payload.UpdateCourseRequest
import com.example.lib4gz.courses.model.payload.CourseResponse
import com.example.lib4gz.courses.model.payload.EnrollmentSummary
import com.example.lib4gz.courses.repo.CourseRepo
import com.example.lib4gz.courses.repo.EnrollmentRepo
import com.example.lib4gz.courses.repo.ModuleRepo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant

interface CourseService {

    fun createCourse(userId: String, request: CreateCourseRequest): Mono<CourseResponse>

    fun getCourseById(courseId: String, userId: String): Mono<CourseResponse>

    fun getCourseByCode(code: String, userId: String): Mono<CourseResponse>

    fun updateCourse(courseId: String, userId: String, request: UpdateCourseRequest): Mono<CourseResponse>

    fun deleteCourse(courseId: String, userId: String): Mono<Void>

    fun listUserCreatedCourses(userId: String): Flux<CourseResponse>

    fun listEnrolledCourses(userId: String): Flux<CourseResponse>

    fun listPublicCourses(): Flux<CourseResponse>
}

@Service
@Transactional
class CourseServiceImpl(
    private val courseRepo: CourseRepo,
    private val userRepo: UserRepo,
    private val enrollmentRepo: EnrollmentRepo,
    private val moduleRepo: ModuleRepo,
    private val enrollmentMapper: EnrollmentMapper,
    private val progressService: ProgressService
) : CourseService {

    override fun createCourse(userId: String, request: CreateCourseRequest): Mono<CourseResponse> {
        return Mono.fromCallable {
            val user = userRepo.findById(userId).orElseThrow {
                ResourceNotFoundException("User not found with id: $userId")
            }

            if (!user.hasRole(UserRole.TEACHER)) {
                throw UnauthorizedException("Only users with TEACHER role can create courses")
            }

            val course = Course(
                title = request.title,
                description = request.description,
                visibility = request.visibility,
                createdBy = user,
                settings = request.settings
            )

            val savedCourse = courseRepo.save(course)

            val teacherEnrollment = createFirstEnrollment(savedCourse, user)
            CourseMapper.toResponse(
                course = savedCourse,
                moduleCount = 0,
                enrollmentCount = 1,
                myEnrollment = enrollmentMapper.toSummary(teacherEnrollment)
            )
        }.subscribeOn(Schedulers.boundedElastic())
    }

    private fun createFirstEnrollment(savedCourse: Course, user: User): Enrollment {
        val teacherEnrollment = Enrollment(
            course = savedCourse,
            user = user,
            role = EnrollmentRole.TEACHER,
            status = EnrollmentStatus.ACTIVE,
            joinedAt = Instant.now().toEpochMilli()
        )
        return enrollmentRepo.save(teacherEnrollment)
    }

    override fun getCourseById(courseId: String, userId: String): Mono<CourseResponse> {
        return Mono.fromCallable {
            val course = courseRepo.findById(courseId).orElseThrow {
                ResourceNotFoundException("Course not found with id: $courseId")
            }

            if (!hasAccessToCourse(course, userId)) {
                throw UnauthorizedException("You do not have access to this course")
            }

            buildCourseResponse(course, userId)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun getCourseByCode(code: String, userId: String): Mono<CourseResponse> {
        return Mono.fromCallable {
            val course = courseRepo.findByCode(code)
                ?: throw ResourceNotFoundException("Course not found with code: $code")

            if (!hasAccessToCourse(course, userId)) {
                throw UnauthorizedException("You do not have access to this course")
            }

            buildCourseResponse(course, userId)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun updateCourse(courseId: String, userId: String, request: UpdateCourseRequest): Mono<CourseResponse> {
        return Mono.fromCallable {
            val course = courseRepo.findById(courseId).orElseThrow {
                ResourceNotFoundException("Course not found with id: $courseId")
            }

            if (course.createdBy.id != userId) {
                throw UnauthorizedException("Only the course creator can update the course")
            }

            request.title?.let { course.title = it }
            request.description?.let { course.description = it }
            request.visibility?.let { course.visibility = it }
            request.settings?.let { course.settings = it }

            val updatedCourse = courseRepo.save(course)
            buildCourseResponse(updatedCourse, userId)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun deleteCourse(courseId: String, userId: String): Mono<Void> {
        return Mono.fromCallable {
            val course = courseRepo.findById(courseId).orElseThrow {
                ResourceNotFoundException("Course not found with id: $courseId")
            }

            if (course.createdBy.id != userId) {
                throw UnauthorizedException("Only the course creator can delete the course")
            }

            courseRepo.delete(course)
        }.subscribeOn(Schedulers.boundedElastic()).then()
    }

    override fun listUserCreatedCourses(userId: String): Flux<CourseResponse> {
        return Mono.fromCallable {
            assembleCourseList(courseRepo.findByCreatedBy_Id(userId), userId)
        }.flatMapMany { Flux.fromIterable(it) }
            .subscribeOn(Schedulers.boundedElastic())
    }

    override fun listEnrolledCourses(userId: String): Flux<CourseResponse> {
        return Mono.fromCallable {
            // Enrolled listing is the dashboard read — include the caller's progress.
            assembleCourseList(courseRepo.findByEnrolledUser(userId), userId, withProgress = true)
        }.flatMapMany { Flux.fromIterable(it) }
            .subscribeOn(Schedulers.boundedElastic())
    }

    override fun listPublicCourses(): Flux<CourseResponse> {
        return Mono.fromCallable {
            // Anonymous public listing: no caller-specific enrollment join.
            courseRepo.findPublicCourses().map { course ->
                CourseMapper.toResponse(
                    course = course,
                    moduleCount = moduleRepo.countByCourse_Id(course.id).toInt(),
                    enrollmentCount = enrollmentRepo
                        .countByCourse_IdAndStatus(course.id, EnrollmentStatus.ACTIVE).toInt(),
                    myEnrollment = null
                )
            }
        }.flatMapMany { Flux.fromIterable(it) }
            .subscribeOn(Schedulers.boundedElastic())
    }

    /**
     * Builds a single-course response with the caller's enrollment denormalized.
     */
    private fun buildCourseResponse(course: Course, userId: String): CourseResponse {
        val moduleCount = moduleRepo.countByCourse_Id(course.id).toInt()
        val enrollmentCount = enrollmentRepo
            .countByCourse_IdAndStatus(course.id, EnrollmentStatus.ACTIVE).toInt()
        val mySummary = enrollmentRepo.findByCourse_IdAndUser_Id(course.id, userId)
            ?.let(enrollmentMapper::toSummary)
        return CourseMapper.toResponse(course, moduleCount, enrollmentCount, mySummary)
    }

    /**
     * Batched list assembly. Issues exactly one enrollment query for the caller's
     * relationship across every course in the list — avoids the per-row lookup.
     * With [withProgress], adds two grouped queries (exercise totals + the
     * caller's done submissions) covering all courses at once.
     */
    private fun assembleCourseList(
        courses: List<Course>,
        userId: String,
        withProgress: Boolean = false
    ): List<CourseResponse> {
        if (courses.isEmpty()) return emptyList()
        val courseIds = courses.map { it.id }
        val myEnrollmentsByCourseId: Map<String, Enrollment> =
            enrollmentRepo.findByCourse_IdInAndUser_Id(courseIds, userId)
                .associateBy { it.course.id }

        val progressByCourseId =
            if (withProgress) progressService.computeProgressForCourses(courseIds, userId, myEnrollmentsByCourseId)
            else emptyMap()

        return courses.map { course ->
            CourseMapper.toResponse(
                course = course,
                moduleCount = moduleRepo.countByCourse_Id(course.id).toInt(),
                enrollmentCount = enrollmentRepo
                    .countByCourse_IdAndStatus(course.id, EnrollmentStatus.ACTIVE).toInt(),
                myEnrollment = myEnrollmentsByCourseId[course.id]?.let(enrollmentMapper::toSummary),
                progress = progressByCourseId[course.id]
            )
        }
    }

    private fun hasAccessToCourse(course: Course, userId: String): Boolean {
        if (course.createdBy.id == userId) return true
        if (course.visibility == Visibility.PUBLIC) return true
        val enrollment = enrollmentRepo.findByCourse_IdAndUser_Id(course.id, userId)
        return enrollment?.status == EnrollmentStatus.ACTIVE
    }
}
