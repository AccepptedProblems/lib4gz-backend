package com.example.lib4gz.courses.service

import com.example.lib4gz.auth.repo.UserRepo
import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.UnauthorizedException
import com.example.lib4gz.courses.model.entity.Enrollment
import com.example.lib4gz.courses.model.entity.EnrollmentRole
import com.example.lib4gz.courses.model.entity.EnrollmentStatus
import com.example.lib4gz.courses.model.mapper.EnrollmentMapper
import com.example.lib4gz.courses.model.payload.EnrollmentRequest
import com.example.lib4gz.courses.model.payload.EnrollmentResponse
import com.example.lib4gz.courses.model.payload.UpdateEnrollmentRequest
import com.example.lib4gz.courses.repo.CourseRepo
import com.example.lib4gz.courses.repo.EnrollmentRepo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant

interface EnrollmentService {

    fun requestEnrollment(courseId: String, userId: String, request: EnrollmentRequest): Mono<EnrollmentResponse>

    fun approveEnrollment(enrollmentId: String, teacherId: String): Mono<EnrollmentResponse>

    fun rejectEnrollment(enrollmentId: String, teacherId: String): Mono<EnrollmentResponse>

    fun updateEnrollment(enrollmentId: String, userId: String, request: UpdateEnrollmentRequest): Mono<EnrollmentResponse>

    fun removeEnrollment(enrollmentId: String, userId: String): Mono<Void>

    fun listCourseEnrollments(courseId: String, userId: String): Flux<EnrollmentResponse>

    fun getUserEnrollment(courseId: String, userId: String): Mono<EnrollmentResponse>

    // These remain synchronous as they're used internally by other services
    fun isTeacherInCourse(courseId: String, userId: String): Boolean

    fun isEnrolledInCourse(courseId: String, userId: String): Boolean
}

@Service
@Transactional
class EnrollmentServiceImpl(
    private val enrollmentRepo: EnrollmentRepo,
    private val courseRepo: CourseRepo,
    private val userRepo: UserRepo,
    private val enrollmentMapper: EnrollmentMapper
) : EnrollmentService {

    override fun requestEnrollment(courseId: String, userId: String, request: EnrollmentRequest): Mono<EnrollmentResponse> {
        return Mono.fromCallable {
            val course = courseRepo.findById(courseId).orElseThrow {
                ResourceNotFoundException("Course not found with id: $courseId")
            }

            val user = userRepo.findById(userId).orElseThrow {
                ResourceNotFoundException("User not found with id: $userId")
            }

            // Idempotent: if already enrolled, return existing enrollment
            val existingEnrollment = enrollmentRepo.findByCourse_IdAndUser_Id(courseId, userId)
            if (existingEnrollment != null) {
                return@fromCallable enrollmentMapper.toResponse(existingEnrollment, course.title)
            }

            // Course creator automatically becomes TEACHER with ACTIVE status
            val isCreator = course.createdBy.id == userId
            val enrollment = Enrollment(
                course = course,
                user = user,
                role = if (isCreator) EnrollmentRole.TEACHER else request.role,
                status = if (isCreator) EnrollmentStatus.ACTIVE else EnrollmentStatus.PENDING,
                joinedAt = if (isCreator) Instant.now().toEpochMilli() else null
            )

            val savedEnrollment = enrollmentRepo.save(enrollment)
            enrollmentMapper.toResponse(savedEnrollment, course.title)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun approveEnrollment(enrollmentId: String, teacherId: String): Mono<EnrollmentResponse> {
        return Mono.fromCallable {
            val enrollment = enrollmentRepo.findById(enrollmentId).orElseThrow {
                ResourceNotFoundException("Enrollment not found with id: $enrollmentId")
            }

            // Check if the approver is a teacher in this course
            if (!isTeacherInCourse(enrollment.course.id, teacherId)) {
                throw UnauthorizedException("Only teachers can approve enrollments")
            }

            enrollment.status = EnrollmentStatus.ACTIVE
            enrollment.joinedAt = Instant.now().toEpochMilli()

            val savedEnrollment = enrollmentRepo.save(enrollment)
            enrollmentMapper.toResponse(savedEnrollment, enrollment.course.title)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun rejectEnrollment(enrollmentId: String, teacherId: String): Mono<EnrollmentResponse> {
        return Mono.fromCallable {
            val enrollment = enrollmentRepo.findById(enrollmentId).orElseThrow {
                ResourceNotFoundException("Enrollment not found with id: $enrollmentId")
            }

            // Check if the rejecter is a teacher in this course
            if (!isTeacherInCourse(enrollment.course.id, teacherId)) {
                throw UnauthorizedException("Only teachers can reject enrollments")
            }

            enrollment.status = EnrollmentStatus.REJECTED

            val savedEnrollment = enrollmentRepo.save(enrollment)
            enrollmentMapper.toResponse(savedEnrollment, enrollment.course.title)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun updateEnrollment(enrollmentId: String, userId: String, request: UpdateEnrollmentRequest): Mono<EnrollmentResponse> {
        return Mono.fromCallable {
            val enrollment = enrollmentRepo.findById(enrollmentId).orElseThrow {
                ResourceNotFoundException("Enrollment not found with id: $enrollmentId")
            }

            // Only teachers can update enrollments
            if (!isTeacherInCourse(enrollment.course.id, userId)) {
                throw UnauthorizedException("Only teachers can update enrollments")
            }

            request.role?.let { enrollment.role = it }
            request.status?.let {
                enrollment.status = it
                if (it == EnrollmentStatus.ACTIVE && enrollment.joinedAt == null) {
                    enrollment.joinedAt = Instant.now().toEpochMilli()
                }
            }

            val savedEnrollment = enrollmentRepo.save(enrollment)
            enrollmentMapper.toResponse(savedEnrollment, enrollment.course.title)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun removeEnrollment(enrollmentId: String, userId: String): Mono<Void> {
        return Mono.fromCallable {
            val enrollment = enrollmentRepo.findById(enrollmentId).orElseThrow {
                ResourceNotFoundException("Enrollment not found with id: $enrollmentId")
            }

            // Users can remove their own enrollment, or teachers can remove others
            val isSelf = enrollment.user.id == userId
            val isTeacher = isTeacherInCourse(enrollment.course.id, userId)

            if (!isSelf && !isTeacher) {
                throw UnauthorizedException("You can only remove your own enrollment or be a teacher")
            }

            enrollmentRepo.delete(enrollment)
        }.subscribeOn(Schedulers.boundedElastic()).then()
    }

    override fun listCourseEnrollments(courseId: String, userId: String): Flux<EnrollmentResponse> {
        return Mono.fromCallable {
            val course = courseRepo.findById(courseId).orElseThrow {
                ResourceNotFoundException("Course not found with id: $courseId")
            }

            // Only teachers can list all enrollments
            if (!isTeacherInCourse(courseId, userId)) {
                throw UnauthorizedException("Only teachers can view all enrollments")
            }

            val enrollments = enrollmentRepo.findByCourse_Id(courseId)
            enrollments.map { enrollmentMapper.toResponse(it, course.title) }
        }.flatMapMany { Flux.fromIterable(it) }
         .subscribeOn(Schedulers.boundedElastic())
    }

    override fun getUserEnrollment(courseId: String, userId: String): Mono<EnrollmentResponse> {
        return Mono.fromCallable {
            val enrollment = enrollmentRepo.findByCourse_IdAndUser_Id(courseId, userId)
            enrollment?.let { enrollmentMapper.toResponse(it, it.course.title) }
        }.subscribeOn(Schedulers.boundedElastic())
    }

    override fun isTeacherInCourse(courseId: String, userId: String): Boolean {
        // Course creator is always a teacher
        val course = courseRepo.findById(courseId).orElse(null) ?: return false
        if (course.createdBy.id == userId) {
            return true
        }

        val enrollment = enrollmentRepo.findActiveEnrollment(courseId, userId) ?: return false
        return enrollment.role == EnrollmentRole.TEACHER
    }

    override fun isEnrolledInCourse(courseId: String, userId: String): Boolean {
        // Course creator is always enrolled
        val course = courseRepo.findById(courseId).orElse(null) ?: return false
        if (course.createdBy.id == userId) {
            return true
        }

        return enrollmentRepo.findActiveEnrollment(courseId, userId) != null
    }
}
