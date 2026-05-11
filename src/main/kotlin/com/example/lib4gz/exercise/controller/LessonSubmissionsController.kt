package com.example.lib4gz.exercise.controller

import com.example.lib4gz.common.config.common.PZRequestHeader
import com.example.lib4gz.common.utils.ExpandTokens
import com.example.lib4gz.exercise.model.payload.LessonSubmissionsResponse
import com.example.lib4gz.exercise.service.LessonSubmissionsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * Aggregate teacher review endpoint for one lesson.
 *
 * GET /v1/lessons/{id}/submissions?expand=answers,questions
 *
 * Authorization is teacher-only and is enforced inside the service.
 * Mutations on submissions live on the existing SubmissionController.
 */
@RestController
@RequestMapping("/v1")
class LessonSubmissionsController(
    private val lessonSubmissionsService: LessonSubmissionsService
) {

    @GetMapping("/lessons/{lessonId}/submissions")
    fun listLessonSubmissions(
        @PathVariable lessonId: String,
        @RequestParam(required = false) expand: String?,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<LessonSubmissionsResponse> {
        return lessonSubmissionsService.getLessonSubmissions(lessonId, userId, ExpandTokens.parse(expand))
    }
}
