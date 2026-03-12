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
}