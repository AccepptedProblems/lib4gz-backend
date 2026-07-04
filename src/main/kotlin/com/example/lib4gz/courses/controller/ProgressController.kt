package com.example.lib4gz.courses.controller

import com.example.lib4gz.common.config.common.PZRequestHeader
import com.example.lib4gz.courses.service.ProgressService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/v1")
class ProgressController(
    private val progressService: ProgressService
) {

    /**
     * POST /lessons/{lessonId}/visit — fire-and-forget resume marker.
     *
     * Called by the client whenever a lesson is opened. Updates the caller's
     * enrollment resume position (last visited lesson). Idempotent; a no-op for
     * callers without an active enrollment. Deliberately "visit", not
     * "complete": completion is always derived server-side from submissions and
     * can never be asserted by the client.
     */
    @PostMapping("/lessons/{lessonId}/visit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun recordVisit(
        @PathVariable lessonId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<Void> {
        return progressService.recordVisit(lessonId, userId)
    }
}
