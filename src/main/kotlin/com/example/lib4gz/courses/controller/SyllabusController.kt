package com.example.lib4gz.courses.controller

import com.example.lib4gz.common.config.common.PZRequestHeader
import com.example.lib4gz.courses.model.payload.SyllabusResponse
import com.example.lib4gz.courses.service.SyllabusService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * Aggregate endpoint serving the course shell screens.
 *
 * The endpoint is intentionally narrow: one GET. Mutations stay on the per-resource
 * controllers (course, module, lesson). This composite endpoint exists to collapse a
 * 4-RTT fan-out (course + modules + my-enrollment + lessons[0]) into a single call.
 */
@RestController
@RequestMapping("/v1")
class SyllabusController(
    private val syllabusService: SyllabusService
) {

    @GetMapping("/courses/{courseId}/syllabus")
    fun getSyllabus(
        @PathVariable courseId: String,
        @RequestHeader(PZRequestHeader.USER_ID) userId: String
    ): Mono<SyllabusResponse> {
        return syllabusService.getSyllabus(courseId, userId)
    }
}
