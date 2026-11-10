package com.example.lib4gz.race.controller

import com.example.lib4gz.common.config.common.PZRequestHeader
import com.example.lib4gz.race.model.payload.CreateRaceResultRequest
import com.example.lib4gz.race.model.payload.LeaderboardResponse
import com.example.lib4gz.race.model.payload.RaceQuestionResponse
import com.example.lib4gz.race.model.payload.RaceResultResponse
import com.example.lib4gz.race.model.payload.ReplaceRaceQuestionsRequest
import com.example.lib4gz.race.service.RaceService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/v1")
class RaceController(
    private val raceService: RaceService
) {

    @GetMapping("/courses/{courseId}/race-questions")
    fun listQuestions(
        @PathVariable courseId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Flux<RaceQuestionResponse> {
        return raceService.listQuestions(courseId, userId)
    }

    @PutMapping("/courses/{courseId}/race-questions")
    fun replaceQuestions(
        @PathVariable courseId: String,
        @RequestBody request: ReplaceRaceQuestionsRequest,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Flux<RaceQuestionResponse> {
        return raceService.replaceQuestions(courseId, userId, request)
    }

    @PostMapping("/courses/{courseId}/race-results")
    @ResponseStatus(HttpStatus.CREATED)
    fun saveResult(
        @PathVariable courseId: String,
        @RequestBody request: CreateRaceResultRequest,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<RaceResultResponse> {
        return raceService.saveResult(courseId, userId, request)
    }

    @GetMapping("/courses/{courseId}/race-leaderboard")
    fun leaderboard(
        @PathVariable courseId: String,
        @RequestParam(required = false) moduleId: String?,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<LeaderboardResponse> {
        return raceService.leaderboard(courseId, moduleId, userId)
    }
}
