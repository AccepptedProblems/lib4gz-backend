package com.example.lib4gz.exercise.controller

import com.example.lib4gz.common.config.common.PZRequestHeader
import com.example.lib4gz.exercise.model.payload.*
import com.example.lib4gz.exercise.service.QuestionService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/v1")
class QuestionController(
    private val questionService: QuestionService
) {

    @GetMapping("/exercises/{exerciseId}/questions")
    fun getQuestions(
        @PathVariable exerciseId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Flux<QuestionResponse> {
        return questionService.getQuestionsByExercise(exerciseId, userId)
    }

    @PostMapping("/exercises/{exerciseId}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    fun createQuestions(
        @PathVariable exerciseId: String,
        @RequestBody createRequest: CreateQuestionsRequest,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Flux<QuestionResponse> {
        return questionService.createQuestions(exerciseId, userId, createRequest)
    }

    @PatchMapping("/exercises/{exerciseId}/questions")
    fun updateQuestions(
        @PathVariable exerciseId: String,
        @RequestBody updateRequest: UpdateQuestionsRequest,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<QuestionsResponse> {
        return questionService.updateQuestions(exerciseId, userId, updateRequest)
    }
}
