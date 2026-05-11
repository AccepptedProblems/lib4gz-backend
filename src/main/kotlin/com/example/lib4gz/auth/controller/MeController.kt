package com.example.lib4gz.auth.controller

import com.example.lib4gz.auth.model.payload.MeCoursesResponse
import com.example.lib4gz.auth.service.MeService
import com.example.lib4gz.common.config.common.PZRequestHeader
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * Self-scoped endpoints under `/v1/me`.
 *
 * Conventional shape: `/me/...` always reflects the authenticated caller, derived from
 * the X-User-Id header injected by JwtAuthFilter. No path parameter for user id —
 * impersonation by URL is a footgun.
 */
@RestController
@RequestMapping("/v1/me")
class MeController(
    private val meService: MeService
) {

    @GetMapping("/courses")
    fun getMyCourses(
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<MeCoursesResponse> {
        return meService.getMyCourses(userId)
    }
}
