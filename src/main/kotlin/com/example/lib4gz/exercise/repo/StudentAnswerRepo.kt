package com.example.lib4gz.exercise.repo

import com.example.lib4gz.exercise.model.entity.StudentAnswer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface StudentAnswerRepo : JpaRepository<StudentAnswer, String> {

    fun findBySubmission_Id(submissionId: String): List<StudentAnswer>

    fun findByQuestion_Id(questionId: String): List<StudentAnswer>

    fun findBySubmission_IdAndQuestion_Id(submissionId: String, questionId: String): StudentAnswer?

    fun existsBySubmission_IdAndQuestion_Id(submissionId: String, questionId: String): Boolean

    fun deleteBySubmission_Id(submissionId: String)

    @Query("SELECT sa FROM StudentAnswer sa WHERE sa.submission.id = :submissionId ORDER BY sa.question.orderIndex ASC")
    fun findBySubmissionIdOrderedByQuestion(submissionId: String): List<StudentAnswer>

    fun countBySubmission_Id(submissionId: String): Long

    /**
     * Batched fetch of all answers across many submissions, ordered by question.
     * Used by aggregate endpoints to load review-queue data in one query.
     */
    @Query(
        "SELECT sa FROM StudentAnswer sa WHERE sa.submission.id IN :submissionIds " +
                "ORDER BY sa.submission.id ASC, sa.question.orderIndex ASC"
    )
    fun findBySubmissionIdInOrdered(submissionIds: Collection<String>): List<StudentAnswer>

    /**
     * Same as [findBySubmissionIdInOrdered] but eagerly fetches each answer's question,
     * so callers can read `answer.question.content` without firing a separate
     * questions-by-id query.
     */
    @Query(
        "SELECT sa FROM StudentAnswer sa JOIN FETCH sa.question " +
                "WHERE sa.submission.id IN :submissionIds " +
                "ORDER BY sa.submission.id ASC, sa.question.orderIndex ASC"
    )
    fun findBySubmissionIdInOrderedFetchQuestion(submissionIds: Collection<String>): List<StudentAnswer>
}