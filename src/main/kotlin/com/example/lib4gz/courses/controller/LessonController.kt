package com.example.lib4gz.courses.controller

import com.example.lib4gz.common.config.common.PZRequestHeader
import com.example.lib4gz.common.utils.ExpandTokens
import com.example.lib4gz.courses.model.payload.CreateLessonRequest
import com.example.lib4gz.courses.model.payload.LessonResponse
import com.example.lib4gz.courses.model.payload.ReorderLessonsRequest
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

    /**
     * Rewrites the order of a module's lessons. The body lists every visible lesson once, in the
     * new order; anything else is a 400. Returns the lessons as the list endpoint does.
     */
    @PutMapping("/modules/{moduleId}/lessons/order")
    fun reorderLessons(
        @PathVariable moduleId: String,
        @RequestBody request: ReorderLessonsRequest,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Flux<LessonResponse> {
        return lessonService.reorderLessons(moduleId, userId, request.lessonIds)
    }

    /**
     * GET /lessons/{id}?expand=summary,exercises,module
     *
     * Without `expand`, behaves identically to the legacy endpoint. With expand:
     *   - `summary`   includes the lesson's summary (or null if none)
     *   - `exercises` includes the ordered exercise list (each row may also carry
     *                 `mySubmissionStatus`; combine with `questions`/`mySubmission`
     *                 to fully populate the Exercise Attempt screen in one call)
     *   - `module`    embeds the owning module summary
     *   - `submissionCount` adds, for a teacher, how many student submissions the lesson holds
     */
    @GetMapping("/lessons/{lessonId}")
    fun getLesson(
        @PathVariable lessonId: String,
        @RequestParam(required = false) expand: String?,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<LessonResponse> {
        return lessonService.getLessonById(lessonId, userId, ExpandTokens.parse(expand))
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
