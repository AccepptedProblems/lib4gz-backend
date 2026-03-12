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
import com.example.lib4gz.courses.model.payload.CreateCourseRequest
import com.example.lib4gz.courses.model.payload.UpdateCourseRequest
import com.example.lib4gz.courses.model.payload.CourseResponse
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
    private val moduleRepo: ModuleRepo
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

            createFirstEnrollment(savedCourse, user)

            CourseMapper.toResponse(savedCourse)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    private fun createFirstEnrollment(
        savedCourse: Course,
        user: User
    ) {
        val teacherEnrollment = Enrollment(
            course = savedCourse,
            user = user,
            role = EnrollmentRole.TEACHER,
            status = EnrollmentStatus.ACTIVE,
            joinedAt = Instant.now().toEpochMilli()
        )
        enrollmentRepo.save(teacherEnrollment)
    }

    override fun getCourseById(courseId: String, userId: String): Mono<CourseResponse> {
        return Mono.fromCallable {
            val course = courseRepo.findById(courseId).orElseThrow {
                ResourceNotFoundException("Course not found with id: $courseId")
            }

            // Check if user has access to this course
            if (!hasAccessToCourse(course, userId)) {
                throw UnauthorizedException("You do not have access to this course")
            }

            val moduleCount = moduleRepo.countByCourse_Id(courseId).toInt()
            val enrollmentCount = enrollmentRepo.countByCourse_IdAndStatus(courseId, EnrollmentStatus.ACTIVE).toInt()

            CourseMapper.toResponse(course, moduleCount, enrollmentCount)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun getCourseByCode(code: String, userId: String): Mono<CourseResponse> {
        return Mono.fromCallable {
            val course = courseRepo.findByCode(code)
                ?: throw ResourceNotFoundException("Course not found with code: $code")

            if (!hasAccessToCourse(course, userId)) {
                throw UnauthorizedException("You do not have access to this course")
            }

            val moduleCount = moduleRepo.countByCourse_Id(course.id).toInt()
            val enrollmentCount = enrollmentRepo.countByCourse_IdAndStatus(course.id, EnrollmentStatus.ACTIVE).toInt()

            CourseMapper.toResponse(course, moduleCount, enrollmentCount)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun updateCourse(courseId: String, userId: String, request: UpdateCourseRequest): Mono<CourseResponse> {
        return Mono.fromCallable {
            val course = courseRepo.findById(courseId).orElseThrow {
                ResourceNotFoundException("Course not found with id: $courseId")
            }

            // Only course creator can update
            if (course.createdBy.id != userId) {
                throw UnauthorizedException("Only the course creator can update the course")
            }

            request.title?.let { course.title = it }
            request.description?.let { course.description = it }
            request.visibility?.let { course.visibility = it }
            request.settings?.let { course.settings = it }

            val updatedCourse = courseRepo.save(course)
            CourseMapper.toResponse(updatedCourse)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun deleteCourse(courseId: String, userId: String): Mono<Void> {
        return Mono.fromCallable {
            val course = courseRepo.findById(courseId).orElseThrow {
                ResourceNotFoundException("Course not found with id: $courseId")
            }

            // Only course creator can delete
            if (course.createdBy.id != userId) {
                throw UnauthorizedException("Only the course creator can delete the course")
            }

            // Soft delete is handled by @SQLDelete annotation
            courseRepo.delete(course)
        }.subscribeOn(Schedulers.boundedElastic()).then()
    }

    override fun listUserCreatedCourses(userId: String): Flux<CourseResponse> {
        return Mono.fromCallable {
            courseRepo.findByCreatedBy_Id(userId)
        }.flatMapMany { courses ->
            Flux.fromIterable(courses).map { course ->
                val moduleCount = moduleRepo.countByCourse_Id(course.id).toInt()
                val enrollmentCount = enrollmentRepo.countByCourse_IdAndStatus(course.id, EnrollmentStatus.ACTIVE).toInt()
                CourseMapper.toResponse(course, moduleCount, enrollmentCount)
            }
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun listEnrolledCourses(userId: String): Flux<CourseResponse> {
        return Mono.fromCallable {
            courseRepo.findByEnrolledUser(userId)
        }.flatMapMany { courses ->
            Flux.fromIterable(courses).map { course ->
                val moduleCount = moduleRepo.countByCourse_Id(course.id).toInt()
                val enrollmentCount = enrollmentRepo.countByCourse_IdAndStatus(course.id, EnrollmentStatus.ACTIVE).toInt()
                CourseMapper.toResponse(course, moduleCount, enrollmentCount)
            }
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun listPublicCourses(): Flux<CourseResponse> {
        return Mono.fromCallable {
            courseRepo.findPublicCourses()
        }.flatMapMany { courses ->
            Flux.fromIterable(courses).map { course ->
                val moduleCount = moduleRepo.countByCourse_Id(course.id).toInt()
                val enrollmentCount = enrollmentRepo.countByCourse_IdAndStatus(course.id, EnrollmentStatus.ACTIVE).toInt()
                CourseMapper.toResponse(course, moduleCount, enrollmentCount)
            }
        }.subscribeOn(Schedulers.boundedElastic())
    }

    private fun hasAccessToCourse(course: Course, userId: String): Boolean {
        // Course creator has access
        if (course.createdBy.id == userId) {
            return true
        }

        // Public courses are accessible to all
        if (course.visibility == Visibility.PUBLIC) {
            return true
        }

        // Check if user is enrolled with ACTIVE status
        val enrollment = enrollmentRepo.findByCourse_IdAndUser_Id(course.id, userId)
        return enrollment?.status == EnrollmentStatus.ACTIVE
    }
}
