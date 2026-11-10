package com.example.lib4gz.race.model.entity

import com.example.lib4gz.common.utils.IdGenerator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** One finished race. Written once, never updated or deleted. */
@Entity
@Table(name = "race_results")
data class RaceResult(
    @Id
    @Column(length = 50)
    val id: String = IdGenerator.generate("rrs"),

    @Column(name = "user_id", nullable = false, length = 50)
    val userId: String,

    @Column(name = "course_id", nullable = false, length = 50)
    val courseId: String,

    /** Null when the race was over the whole course. */
    @Column(name = "module_id", length = 50)
    val moduleId: String? = null,

    @Column(name = "finish_ms", nullable = false)
    val finishMs: Int,

    @Column(name = "correct_count", nullable = false)
    val correctCount: Int,

    @Column(name = "wrong_count", nullable = false)
    val wrongCount: Int,

    @Column(nullable = false)
    val seed: Long,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Long = Instant.now().toEpochMilli()
)
