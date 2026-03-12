package com.example.lib4gz.exercise.controller

import com.example.lib4gz.common.config.common.PZRequestHeader
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

    @GetMapping("/lessons/{lessonId}/exercises")
    fun listLessonExercises(
        @PathVariable lessonId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Flux<ExerciseResponse> {
        return exerciseService.listLessonExercises(lessonId, userId)
    }

    @GetMapping("/exercises/{exerciseId}")
    fun getExercise(
        @PathVariable exerciseId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<ExerciseResponse> {
        return exerciseService.getExerciseById(exerciseId, userId)
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
