package com.example.lib4gz.auth.service

import com.example.lib4gz.auth.model.payload.MeCoursesResponse
import com.example.lib4gz.courses.service.CourseService
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

/**
 * Self-scoped composite reads.
 *
 * Currently exposes the dashboard's `enrolled` + `created` course lists as a single
 * response. Future additions (recent activity, pending approvals, etc.) belong here.
 *
 * SOLID: This service composes — it owns no CRUD logic and depends only on existing
 * service abstractions. Adding a field is an Open/Closed extension.
 */
interface MeService {
    fun getMyCourses(userId: String): Mono<MeCoursesResponse>
}

@Service
class MeServiceImpl(
    private val courseService: CourseService
) : MeService {

    override fun getMyCourses(userId: String): Mono<MeCoursesResponse> {
        // The two lists are independent — issue both in parallel, then combine.
        return Mono.zip(
            courseService.listEnrolledCourses(userId).collectList(),
            courseService.listUserCreatedCourses(userId).collectList()
        ).map { tuple ->
            MeCoursesResponse(enrolled = tuple.t1, created = tuple.t2)
        }
    }
}
