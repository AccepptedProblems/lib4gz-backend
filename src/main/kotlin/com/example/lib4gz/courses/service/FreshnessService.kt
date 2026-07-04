package com.example.lib4gz.courses.service

import com.example.lib4gz.common.exception.ResourceNotFoundException
import com.example.lib4gz.common.exception.UnauthorizedException
import com.example.lib4gz.courses.model.entity.Course
import com.example.lib4gz.courses.model.entity.EnrollmentStatus
import com.example.lib4gz.courses.model.entity.FreshnessResourceType
import com.example.lib4gz.courses.model.entity.Visibility
import com.example.lib4gz.courses.model.payload.LastUpdatesResponse
import com.example.lib4gz.courses.repo.CourseRepo
import com.example.lib4gz.courses.repo.CourseResourceFreshnessRepo
import com.example.lib4gz.courses.repo.EnrollmentRepo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * Read side of the client-cache freshness feature: one cheap call returning the
 * lastUpdate token for every course-scoped resource type. Clients compare tokens by
 * strict equality against the value stored with their cached payloads and refetch
 * only on mismatch.
 *
 * Writes are not exposed here — tokens are maintained automatically by
 * [com.example.lib4gz.common.freshness.FreshnessEntityListener].
 */
interface FreshnessService {
    fun getLastUpdates(courseId: String, userId: String): Mono<LastUpdatesResponse>
}

@Service
@Transactional(readOnly = true)
class FreshnessServiceImpl(
    private val courseRepo: CourseRepo,
    private val enrollmentRepo: EnrollmentRepo,
    private val freshnessRepo: CourseResourceFreshnessRepo
) : FreshnessService {

    override fun getLastUpdates(courseId: String, userId: String): Mono<LastUpdatesResponse> {
        return Mono.fromCallable {
            val course = courseRepo.findById(courseId).orElseThrow {
                ResourceNotFoundException("Course not found with id: $courseId")
            }

            val myEnrollment = enrollmentRepo.findByCourse_IdAndUser_Id(courseId, userId)
            authorizeReadAccess(course, userId, myEnrollment != null && myEnrollment.status == EnrollmentStatus.ACTIVE)

            val byType = freshnessRepo.findByCourseId(courseId)
                .associate { it.resourceType to it.lastUpdate }

            LastUpdatesResponse(
                courseId = courseId,
                // Always all types; 0 = "never bumped" is a deterministic server answer
                // (see LastUpdatesResponse docs for the bootstrap semantics).
                lastUpdates = FreshnessResourceType.entries.associateWith { byType[it] ?: 0L }
            )
        }.subscribeOn(Schedulers.boundedElastic())
    }

    /**
     * Mirrors SyllabusServiceImpl.authorizeReadAccess — this endpoint must be readable
     * by exactly the audience that can read the cached endpoints, or those users would
     * be designed into a permanent cache bypass.
     */
    private fun authorizeReadAccess(course: Course, userId: String, isActiveEnrolled: Boolean) {
        if (course.createdBy.id == userId) return
        if (course.visibility == Visibility.PUBLIC) return
        if (isActiveEnrolled) return
        throw UnauthorizedException("You do not have access to this course")
    }
}
