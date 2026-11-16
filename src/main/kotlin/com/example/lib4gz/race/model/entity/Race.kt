package com.example.lib4gz.race.model.entity

import com.example.lib4gz.common.utils.IdGenerator
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * One dealt race. Inserted by start; after that only `RaceRepo.answer` and `RaceRepo.markFinished`
 * change it, in SQL.
 */
@Entity
@Table(name = "races")
data class Race(
    @Id
    @Column(length = 50)
    val id: String = IdGenerator.generate("rce"),

    @Column(name = "user_id", nullable = false, length = 50)
    val userId: String,

    @Column(name = "course_id", nullable = false, length = 50)
    val courseId: String,

    /** Null when the race is over the whole course. */
    @Column(name = "module_id", length = 50)
    val moduleId: String? = null,

    @Column(nullable = false)
    val seed: Long,

    /** The question at each question number. A question may appear more than once. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_ids", nullable = false, columnDefinition = "jsonb")
    val questionIds: List<String>,

    /** The right shown option at each question number. Only a finish sends any of it, the first wrong ones. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer_key", nullable = false, columnDefinition = "jsonb")
    val answerKey: List<Int>,

    /** Every question number judged wrong, a timeout included, in race order. Appended by `RaceRepo.answer`. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "wrong_seqs", nullable = false, columnDefinition = "jsonb")
    val wrongSeqs: List<Int> = emptyList(),

    @Column(name = "next_seq", nullable = false)
    val nextSeq: Int = 0,

    @Column(name = "correct_count", nullable = false)
    val correctCount: Int = 0,

    @Column(name = "wrong_count", nullable = false)
    val wrongCount: Int = 0,

    @Column(name = "last_correct")
    val lastCorrect: Boolean? = null,

    @Column(name = "started_at", nullable = false, updatable = false)
    val startedAt: Long = Instant.now().toEpochMilli(),

    @Column(name = "finished_at")
    val finishedAt: Long? = null
)
