package com.example.lib4gz.courses.model.payload

/**
 * Course "table-of-contents" composite response.
 *
 * One round-trip replaces the chained `course → modules → lessons[0]` load that course
 * shell screens (CourseLayout, CourseHome, CourseMaterial) previously performed.
 *
 * Authorization: caller must be the course creator, ACTIVE-enrolled, or the course must
 * be PUBLIC. The `course.myEnrollment` field reflects the caller's relationship.
 */
data class SyllabusResponse(
    val course: CourseResponse,
    val modules: List<SyllabusModule>
)

data class SyllabusModule(
    val id: String,
    val courseId: String,
    val title: String,
    val orderIndex: Int,
    val lessonCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val lessons: List<LessonResponse>
)
