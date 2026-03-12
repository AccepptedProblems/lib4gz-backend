package com.example.lib4gz.exercise.controller

import com.example.lib4gz.common.config.common.PZRequestHeader
import com.example.lib4gz.exercise.model.payload.CreateSubmissionRequest
import com.example.lib4gz.exercise.model.payload.SubmissionResponse
import com.example.lib4gz.exercise.service.SubmissionService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/v1")
class SubmissionController(
    private val submissionService: SubmissionService
) {

    @GetMapping("/exercises/{exerciseId}/submissions")
    fun listExerciseSubmissions(
        @PathVariable exerciseId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Flux<SubmissionResponse> {
        return submissionService.listExerciseSubmissions(exerciseId, userId)
    }

    @GetMapping("/exercises/{exerciseId}/my-submission")
    fun getMySubmission(
        @PathVariable exerciseId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<SubmissionResponse> {
        return submissionService.getUserSubmissionForExercise(exerciseId, userId)
    }

    @PostMapping("/exercises/{exerciseId}/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    fun createOrUpdateSubmission(
        @PathVariable exerciseId: String,
        @RequestBody createRequest: CreateSubmissionRequest,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<SubmissionResponse> {
        return submissionService.createOrUpdateSubmission(exerciseId, userId, createRequest)
    }

    @GetMapping("/submissions/{submissionId}")
    fun getSubmission(
        @PathVariable submissionId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<SubmissionResponse> {
        return submissionService.getSubmission(submissionId, userId)
    }

    @PostMapping("/submissions/{submissionId}/submit")
    fun submitForReview(
        @PathVariable submissionId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<SubmissionResponse> {
        return submissionService.submitForReview(submissionId, userId)
    }

    @PostMapping("/submissions/{submissionId}/approve")
    fun approveSubmission(
        @PathVariable submissionId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<SubmissionResponse> {
        return submissionService.approveSubmission(submissionId, userId)
    }

    @PostMapping("/submissions/{submissionId}/revision")
    fun requestRevision(
        @PathVariable submissionId: String,
        @RequestBody(required = false) body: RevisionRequest?,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<SubmissionResponse> {
        return submissionService.requestRevision(submissionId, userId, body?.feedback)
    }

    @PostMapping("/answers/{answerId}/comment")
    fun addTeacherComment(
        @PathVariable answerId: String,
        @RequestBody body: CommentRequest,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<SubmissionResponse> {
        return submissionService.addTeacherComment(answerId, userId, body.comment)
    }
}

data class RevisionRequest(
    val feedback: String? = null
)

data class CommentRequest(
    val comment: String
)
