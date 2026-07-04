package com.example.lib4gz.courses.model.entity

import com.example.lib4gz.common.utils.IdGenerator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * Freshness token for course-scoped resources: one row per (courseId, resourceType),
 * bumped by [com.example.lib4gz.common.freshness.FreshnessEntityListener] whenever any
 * entity of that resource type changes. Clients compare the token by strict equality
 * to decide whether their IndexedDB cache is still valid.
 *
 * `courseId` is a plain column (not a @ManyToOne) on purpose: these rows are cache
 * metadata, not part of the domain aggregate, and must stay visible even for
 * soft-deleted courses so the native upsert never fights the @Where filter.
 */
@Entity
@Table(
    name = "course_resource_freshness",
    uniqueConstraints = [UniqueConstraint(columnNames = ["course_id", "resource_type"])]
)
data class CourseResourceFreshness(
    @Id
    @Column(length = 50)
    val id: String = IdGenerator.generate("frs"),

    @Column(name = "course_id", nullable = false, length = 50)
    val courseId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 50)
    val resourceType: FreshnessResourceType,

    @Column(name = "last_update", nullable = false)
    var lastUpdate: Long = Instant.now().toEpochMilli()
)

enum class FreshnessResourceType {
    SYLLABUS,
    LESSON,
    SUMMARY,
    EXERCISE,
    SUBMISSION
}
