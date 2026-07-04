package com.example.lib4gz.courses.controller

import com.example.lib4gz.common.config.common.PZRequestHeader
import com.example.lib4gz.courses.model.payload.LastUpdatesResponse
import com.example.lib4gz.courses.service.FreshnessService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * Freshness check for the client-side cache: returns the lastUpdate token per resource
 * type for one course in a single tiny payload. Clients call this (at most once per
 * 30s, memoized) instead of refetching data, and hit the real endpoints only when a
 * token changed.
 */
@RestController
@RequestMapping("/v1")
class FreshnessController(
    private val freshnessService: FreshnessService
) {

    @GetMapping("/courses/{courseId}/last-updates")
    fun getLastUpdates(
        @PathVariable courseId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<LastUpdatesResponse> {
        return freshnessService.getLastUpdates(courseId, userId)
    }
}
