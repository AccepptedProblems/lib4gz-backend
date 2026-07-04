package com.example.lib4gz.courses.model.entity

import com.example.lib4gz.auth.model.entity.User
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.Where
import org.hibernate.type.SqlTypes
import java.time.Instant
import com.example.lib4gz.common.utils.IdGenerator

@Entity
@Table(name = "courses")
@SQLDelete(sql = "UPDATE courses SET deleted_at = (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
data class Course(
    @Id
    @Column(length = 50)
    val id: String = IdGenerator.generate("crs"),

    @Column(nullable = false, unique = true, length = 10)
    val code: String = IdGenerator.generateNumericCode(10),

    @Column(nullable = false, length = 500)
    var title: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var visibility: Visibility = Visibility.PRIVATE,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    val createdBy: User,

    @OneToMany(mappedBy = "course", cascade = [CascadeType.ALL], orphanRemoval = true)
    @Where(clause = "deleted_at IS NULL")
    val modules: MutableList<Module> = mutableListOf(),

    @OneToMany(mappedBy = "course", cascade = [CascadeType.ALL], orphanRemoval = true)
    val enrollments: MutableList<Enrollment> = mutableListOf(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var settings: Map<String, Any> = emptyMap(),

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
    name = "enrollments",
    uniqueConstraints = [UniqueConstraint(columnNames = ["course_id", "user_id"])]
)
data class Enrollment(
    @Id
    @Column(length = 50)
    val id: String = IdGenerator.generate("enr"),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    val course: Course,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var role: EnrollmentRole,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var status: EnrollmentStatus = EnrollmentStatus.PENDING,

    @Column(name = "joined_at")
    var joinedAt: Long? = null,

    // Resume state: the last lesson this user opened in this course.
    // Stored as a plain id (not a relation) so a deleted lesson never breaks the row.
    @Column(name = "last_visited_lesson_id", length = 50)
    var lastVisitedLessonId: String? = null,

    @Column(name = "last_visited_at")
    var lastVisitedAt: Long? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Long = Instant.now().toEpochMilli(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Long = Instant.now().toEpochMilli()
) {
    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now().toEpochMilli()
    }

    fun recordVisit(lessonId: String) {
        lastVisitedLessonId = lessonId
        lastVisitedAt = Instant.now().toEpochMilli()
    }

    fun approve() {
        status = EnrollmentStatus.ACTIVE
        joinedAt = Instant.now().toEpochMilli()
    }

    fun reject() {
        status = EnrollmentStatus.REJECTED
    }

    fun isActive(): Boolean = status == EnrollmentStatus.ACTIVE
    fun isTeacher(): Boolean = role == EnrollmentRole.TEACHER
    fun isLearner(): Boolean = role == EnrollmentRole.LEARNER
}


@Entity
@Table(
    name = "modules",
    uniqueConstraints = [UniqueConstraint(columnNames = ["course_id", "order_index"])]
)
@SQLDelete(sql = "UPDATE modules SET deleted_at = (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
data class Module(
    @Id
    @Column(length = 50)
    val id: String = IdGenerator.generate("mod"),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    val course: Course,

    @Column(nullable = false, length = 500)
    var title: String,

    @Column(name = "order_index", nullable = false)
    var orderIndex: Int,

    @OneToMany(mappedBy = "module", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Where(clause = "deleted_at IS NULL")
    val lessons: MutableList<Lesson> = mutableListOf(),

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
    name = "lessons",
    uniqueConstraints = [UniqueConstraint(columnNames = ["module_id", "order_index"])]
)
@SQLDelete(sql = "UPDATE lessons SET deleted_at = (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
data class Lesson(
    @Id
    @Column(length = 50)
    val id: String = IdGenerator.generate("les"),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id", nullable = false)
    val module: Module,

    @Column(nullable = false, length = 500)
    var title: String,

    @Column(name = "order_index", nullable = false)
    var orderIndex: Int,

    @OneToOne(mappedBy = "lesson", cascade = [CascadeType.ALL], orphanRemoval = true)
    val summary: Summary? = null,

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

enum class EnrollmentRole {
    LEARNER,
    TEACHER
}

enum class EnrollmentStatus {
    PENDING,
    ACTIVE,
    REJECTED,
    INACTIVE
}

enum class Visibility {
    PUBLIC,
    PRIVATE,
    UNLISTED
}

@Entity
@Table(name = "summaries")
data class Summary(
    @Id
    @Column(length = 50)
    val id: String = IdGenerator.generate("sum"),

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false, unique = true)
    val lesson: Lesson,

    @Column(columnDefinition = "TEXT", nullable = false)
    var content: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "edited_by", nullable = false)
    var editedBy: User,

    @Column(nullable = false)
    var version: Int = 1,

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
