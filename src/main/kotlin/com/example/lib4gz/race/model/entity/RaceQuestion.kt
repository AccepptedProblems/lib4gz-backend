package com.example.lib4gz.race.model.entity

import com.example.lib4gz.common.utils.IdGenerator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.Where
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "race_questions")
@SQLDelete(sql = "UPDATE race_questions SET deleted_at = (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
data class RaceQuestion(
    @Id
    @Column(length = 50)
    val id: String = IdGenerator.generate("rqn"),

    @Column(name = "course_id", nullable = false, length = 50)
    val courseId: String,

    @Column(name = "module_id", nullable = false, length = 50)
    var moduleId: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String,

    /** `[{"text": "..."}]`, in display order. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var options: List<Map<String, Any>>,

    @Column(name = "correct_index", nullable = false)
    var correctIndex: Int,

    @Column(name = "order_index", nullable = false)
    var orderIndex: Int,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Long = Instant.now().toEpochMilli(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Long = Instant.now().toEpochMilli(),

    @Column(name = "deleted_at")
    var deletedAt: Long? = null
) {
    fun optionTexts(): List<String> = options.map { it["text"]?.toString() ?: "" }

    @PreUpdate
    fun preUpdate() {
        updatedAt = Instant.now().toEpochMilli()
    }
}
