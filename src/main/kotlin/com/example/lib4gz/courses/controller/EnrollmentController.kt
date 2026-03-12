package com.example.lib4gz.courses.controller

import com.example.lib4gz.common.config.common.PZRequestHeader
import com.example.lib4gz.courses.model.payload.EnrollmentRequest
import com.example.lib4gz.courses.model.payload.EnrollmentResponse
import com.example.lib4gz.courses.model.payload.UpdateEnrollmentRequest
import com.example.lib4gz.courses.service.EnrollmentService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/v1")
class EnrollmentController(
    private val enrollmentService: EnrollmentService
) {

    @PostMapping("/courses/{courseId}/enroll")
    @ResponseStatus(HttpStatus.CREATED)
    fun requestEnrollment(
        @PathVariable courseId: String,
        @RequestBody(required = false) enrollRequest: EnrollmentRequest?,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<EnrollmentResponse> {
        return enrollmentService.requestEnrollment(courseId, userId, enrollRequest ?: EnrollmentRequest())
    }

    @GetMapping("/courses/{courseId}/enrollments")
    fun listCourseEnrollments(
        @PathVariable courseId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Flux<EnrollmentResponse> {
        return enrollmentService.listCourseEnrollments(courseId, userId)
    }

    @GetMapping("/courses/{courseId}/my-enrollment")
    fun getMyEnrollment(
        @PathVariable courseId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<EnrollmentResponse> {
        return enrollmentService.getUserEnrollment(courseId, userId)
    }

    @PutMapping("/enrollments/{enrollmentId}")
    fun updateEnrollment(
        @PathVariable enrollmentId: String,
        @RequestBody updateRequest: UpdateEnrollmentRequest,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<EnrollmentResponse> {
        return enrollmentService.updateEnrollment(enrollmentId, userId, updateRequest)
    }

    @PostMapping("/enrollments/{enrollmentId}/approve")
    fun approveEnrollment(
        @PathVariable enrollmentId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<EnrollmentResponse> {
        return enrollmentService.approveEnrollment(enrollmentId, userId)
    }

    @PostMapping("/enrollments/{enrollmentId}/reject")
    fun rejectEnrollment(
        @PathVariable enrollmentId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<EnrollmentResponse> {
        return enrollmentService.rejectEnrollment(enrollmentId, userId)
    }

    @DeleteMapping("/enrollments/{enrollmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeEnrollment(
        @PathVariable enrollmentId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<Void> {
        return enrollmentService.removeEnrollment(enrollmentId, userId)
    }
}
