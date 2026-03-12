package com.example.lib4gz.exercise.model.entity

import com.example.lib4gz.auth.model.entity.User
import com.example.lib4gz.courses.model.entity.Lesson
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.Where
import org.hibernate.type.SqlTypes
import java.time.Instant
import com.example.lib4gz.common.utils.IdGenerator

@Entity
@Table(
    name = "exercises",
    uniqueConstraints = [UniqueConstraint(columnNames = ["lesson_id", "order_index"])]
)
@SQLDelete(sql = "UPDATE exercises SET deleted_at = (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
data class Exercise(
    @Id
    @Column(length = 50)
    val id: String = IdGenerator.generate("exc"),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false)
    val lesson: Lesson,

    @Column(nullable = false, length = 500)
    var title: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var type: ExerciseType,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var settings: Map<String, Any> = emptyMap(),

    @Column(name = "order_index", nullable = false)
    var orderIndex: Int,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Long = Instant.now().toEpochMilli(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Long = Instant.now().toEpochMilli(),

    @Column(name = "deleted_at")
    var deletedAt: Long? = null
) {
    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now().toEpochMilli()
    }
}

enum class ExerciseType {
    TEXT_ANSWER,
    CODE,
    FILE_UPLOAD,
    MULTIPLE_CHOICE,
    PROJECT_LINK
}

@Entity
@Table(name = "questions")
@SQLDelete(sql = "UPDATE questions SET deleted_at = (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
data class Question(
    @Id
    @Column(length = 50)
    val id: String = IdGenerator.generate("qst"),

    @Column(name = "exercise_id", nullable = false, length = 50)
    val exerciseId: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String,

    @Column(name = "order_index", nullable = false)
    var orderIndex: Int,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var meta: Map<String, Any> = emptyMap(),

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    var visibility: QuestionVisibility = QuestionVisibility.VISIBLE,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Long = Instant.now().toEpochMilli(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Long = Instant.now().toEpochMilli(),

    @Column(name = "deleted_at")
    var deletedAt: Long? = null
) {
    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now().toEpochMilli()
    }
}

@Entity
@Table(
    name = "submissions",
    uniqueConstraints = [UniqueConstraint(columnNames = ["exercise_id", "user_id"])]
)
data class Submission(
    @Id
    @Column(length = 50)
    val id: String = IdGenerator.generate("sub"),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id", nullable = false)
    val exercise: Exercise,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var status: SubmissionStatus = SubmissionStatus.DRAFT,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Long = Instant.now().toEpochMilli(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Long = Instant.now().toEpochMilli(),

    @Column(name = "submitted_at")
    var submittedAt: Long? = null,

    @Column(name = "approved_at")
    var approvedAt: Long? = null
) {
    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now().toEpochMilli()
    }

    fun submit() {
        status = SubmissionStatus.SUBMITTED
        if (submittedAt == null) {
            submittedAt = Instant.now().toEpochMilli()
        }
    }

    fun approve() {
        require(status == SubmissionStatus.SUBMITTED) {
            "Can only approve from SUBMITTED status"
        }
        status = SubmissionStatus.APPROVED
        approvedAt = Instant.now().toEpochMilli()
    }

    fun requestRevision() {
        require(status == SubmissionStatus.SUBMITTED) {
            "Can only request revision from SUBMITTED status"
        }
        status = SubmissionStatus.NEEDS_REVISION
    }

    fun isDraft(): Boolean = status == SubmissionStatus.DRAFT
    fun isSubmitted(): Boolean = status == SubmissionStatus.SUBMITTED
    fun isApproved(): Boolean = status == SubmissionStatus.APPROVED
    fun needsRevision(): Boolean = status == SubmissionStatus.NEEDS_REVISION
}

enum class SubmissionStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    NEEDS_REVISION
}

enum class QuestionVisibility {
    VISIBLE,
    HIDDEN
}

@Entity
@Table(name = "student_answers")
data class StudentAnswer(
    @Id
    @Column(length = 50)
    val id: String = IdGenerator.generate("ans"),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    val question: Question,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    val submission: Submission,

    @Column(columnDefinition = "TEXT")
    var answer: String? = null,

    @Column(name = "teacher_comment", columnDefinition = "TEXT")
    var teacherComment: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Long = Instant.now().toEpochMilli(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Long = Instant.now().toEpochMilli()
) {
    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now().toEpochMilli()
    }
}