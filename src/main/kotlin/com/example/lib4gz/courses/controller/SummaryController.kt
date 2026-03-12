package com.example.lib4gz.courses.controller

import com.example.lib4gz.common.config.common.PZRequestHeader
import com.example.lib4gz.courses.model.payload.CreateSummaryRequest
import com.example.lib4gz.courses.model.payload.SummaryResponse
import com.example.lib4gz.courses.service.SummaryService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/v1")
class SummaryController(
    private val summaryService: SummaryService
) {

    @GetMapping("/lessons/{lessonId}/summary")
    fun getSummary(
        @PathVariable lessonId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<SummaryResponse> {
        return summaryService.getSummary(lessonId, userId)
    }

    @PostMapping("/lessons/{lessonId}/summary")
    @ResponseStatus(HttpStatus.CREATED)
    fun createOrUpdateSummary(
        @PathVariable lessonId: String,
        @RequestBody createRequest: CreateSummaryRequest,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<SummaryResponse> {
        return summaryService.createOrUpdateSummary(lessonId, userId, createRequest.content)
    }
}
