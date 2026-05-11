package com.example.lib4gz.exercise.controller

import com.example.lib4gz.common.config.common.PZRequestHeader
import com.example.lib4gz.common.utils.ExpandTokens
import com.example.lib4gz.exercise.model.payload.CreateExerciseRequest
import com.example.lib4gz.exercise.model.payload.ExerciseResponse
import com.example.lib4gz.exercise.model.payload.UpdateExerciseRequest
import com.example.lib4gz.exercise.service.ExerciseService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/v1")
class ExerciseController(
    private val exerciseService: ExerciseService
) {

    @PostMapping("/lessons/{lessonId}/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    fun createExercise(
        @PathVariable lessonId: String,
        @RequestBody createRequest: CreateExerciseRequest,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<ExerciseResponse> {
        return exerciseService.createExercise(lessonId, userId, createRequest)
    }

    /**
     * GET /lessons/{id}/exercises?expand=questions,mySubmission
     *
     * Powers the Exercise Attempt screen in one round-trip. Without `expand`, the
     * response is the legacy shape plus the denormalized `mySubmissionStatus` and
     * `mySubmissionId` on each row.
     */
    @GetMapping("/lessons/{lessonId}/exercises")
    fun listLessonExercises(
        @PathVariable lessonId: String,
        @RequestParam(required = false) expand: String?,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Flux<ExerciseResponse> {
        return exerciseService.listLessonExercises(lessonId, userId, ExpandTokens.parse(expand))
    }

    @GetMapping("/exercises/{exerciseId}")
    fun getExercise(
        @PathVariable exerciseId: String,
        @RequestParam(required = false) expand: String?,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<ExerciseResponse> {
        return exerciseService.getExerciseById(exerciseId, userId, ExpandTokens.parse(expand))
    }

    @PatchMapping("/exercises/{exerciseId}")
    fun updateExercise(
        @PathVariable exerciseId: String,
        @RequestBody updateRequest: UpdateExerciseRequest,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<ExerciseResponse> {
        return exerciseService.updateExercise(exerciseId, userId, updateRequest)
    }

    @DeleteMapping("/exercises/{exerciseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteExercise(
        @PathVariable exerciseId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<Void> {
        return exerciseService.deleteExercise(exerciseId, userId)
    }
}
