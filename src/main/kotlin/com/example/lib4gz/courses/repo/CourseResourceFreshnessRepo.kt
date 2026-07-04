package com.example.lib4gz.courses.repo

import com.example.lib4gz.courses.model.entity.CourseResourceFreshness
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Read side of the freshness table. Writes happen in
 * [com.example.lib4gz.common.freshness.FreshnessEntityListener] via a native
 * `INSERT ... ON CONFLICT` on the transaction's JDBC connection, because they must run
 * inside Hibernate's before-transaction-completion phase (after the commit-time flush).
 */
@Repository
interface CourseResourceFreshnessRepo : JpaRepository<CourseResourceFreshness, String> {

    fun findByCourseId(courseId: String): List<CourseResourceFreshness>
}
