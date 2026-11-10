package com.example.lib4gz.race.repo

import com.example.lib4gz.race.model.entity.RaceResult
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface RaceResultRepo : JpaRepository<RaceResult, String> {

    // The leaderboard: each user's best result in the scope, ten fastest users. Two queries rather
    // than one with a nullable parameter, because `:moduleId IS NULL` in a native query leaves
    // Postgres unable to type the parameter.

    @Query(
        "SELECT * FROM (" +
            "SELECT DISTINCT ON (user_id) * FROM race_results " +
            "WHERE course_id = :courseId AND module_id IS NULL " +
            "ORDER BY user_id, finish_ms, created_at" +
            ") best ORDER BY finish_ms, created_at LIMIT 10",
        nativeQuery = true
    )
    fun findBestPerUserInCourse(courseId: String): List<RaceResult>

    @Query(
        "SELECT * FROM (" +
            "SELECT DISTINCT ON (user_id) * FROM race_results " +
            "WHERE course_id = :courseId AND module_id = :moduleId " +
            "ORDER BY user_id, finish_ms, created_at" +
            ") best ORDER BY finish_ms, created_at LIMIT 10",
        nativeQuery = true
    )
    fun findBestPerUserInModule(courseId: String, moduleId: String): List<RaceResult>

    fun findTopByUserIdAndCourseIdAndModuleIdIsNullOrderByFinishMsAscCreatedAtAsc(
        userId: String, courseId: String
    ): RaceResult?

    fun findTopByUserIdAndCourseIdAndModuleIdOrderByFinishMsAscCreatedAtAsc(
        userId: String, courseId: String, moduleId: String
    ): RaceResult?
}
