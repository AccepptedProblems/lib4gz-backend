package com.example.lib4gz.courses.controller

import com.example.lib4gz.common.config.common.PZRequestHeader
import com.example.lib4gz.courses.model.payload.CreateLessonRequest
import com.example.lib4gz.courses.model.payload.LessonResponse
import com.example.lib4gz.courses.model.payload.UpdateLessonRequest
import com.example.lib4gz.courses.service.LessonService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/v1")
class LessonController(
    private val lessonService: LessonService
) {

    @PostMapping("/modules/{moduleId}/lessons")
    @ResponseStatus(HttpStatus.CREATED)
    fun createLesson(
        @PathVariable moduleId: String,
        @RequestBody createRequest: CreateLessonRequest,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<LessonResponse> {
        return lessonService.createLesson(moduleId, userId, createRequest)
    }

    @GetMapping("/modules/{moduleId}/lessons")
    fun listModuleLessons(
        @PathVariable moduleId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Flux<LessonResponse> {
        return lessonService.listModuleLessons(moduleId, userId)
    }

    @GetMapping("/lessons/{lessonId}")
    fun getLesson(
        @PathVariable lessonId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<LessonResponse> {
        return lessonService.getLessonById(lessonId, userId)
    }

    @PatchMapping("/lessons/{lessonId}")
    fun updateLesson(
        @PathVariable lessonId: String,
        @RequestBody updateRequest: UpdateLessonRequest,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<LessonResponse> {
        return lessonService.updateLesson(lessonId, userId, updateRequest)
    }

    @DeleteMapping("/lessons/{lessonId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteLesson(
        @PathVariable lessonId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<Void> {
        return lessonService.deleteLesson(lessonId, userId)
    }
}
