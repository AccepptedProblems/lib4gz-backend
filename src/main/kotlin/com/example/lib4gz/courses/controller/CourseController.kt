package com.example.lib4gz.courses.controller

import com.example.lib4gz.common.config.common.PZRequestHeader
import com.example.lib4gz.courses.model.payload.CourseResponse
import com.example.lib4gz.courses.model.payload.CreateCourseRequest
import com.example.lib4gz.courses.model.payload.UpdateCourseRequest
import com.example.lib4gz.courses.service.CourseService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/v1/courses")
class CourseController(
    private val courseService: CourseService
) {

    @GetMapping
    fun listCourses(
        @RequestParam(required = false) type: String?,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Flux<CourseResponse> {
        return when (type) {
            "created" -> courseService.listUserCreatedCourses(userId)
            "enrolled" -> courseService.listEnrolledCourses(userId)
            "public" -> courseService.listPublicCourses()
            else -> courseService.listEnrolledCourses(userId)
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createCourse(
        @RequestBody createRequest: CreateCourseRequest,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<CourseResponse> {
        return courseService.createCourse(userId, createRequest)
    }

    @GetMapping("/{id}")
    fun getCourse(
        @PathVariable id: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<CourseResponse> {
        return courseService.getCourseById(id, userId)
    }

    @GetMapping("/code/{code}")
    fun getCourseByCode(
        @PathVariable code: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<CourseResponse> {
        return courseService.getCourseByCode(code, userId)
    }

    @PatchMapping("/{id}")
    fun updateCourse(
        @PathVariable id: String,
        @RequestBody updateRequest: UpdateCourseRequest,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<CourseResponse> {
        return courseService.updateCourse(id, userId, updateRequest)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCourse(
        @PathVariable id: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<Void> {
        return courseService.deleteCourse(id, userId)
    }
}
