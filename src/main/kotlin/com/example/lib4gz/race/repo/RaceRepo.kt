package com.example.lib4gz.race.repo

import com.example.lib4gz.race.model.entity.Race
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface RaceRepo : JpaRepository<Race, String> {

    fun findByIdAndUserId(id: String, userId: String): Race?

    /** A race's counters as an UPDATE on it left them. */
    interface Counts {
        val correctCount: Int
        val wrongCount: Int
    }

    interface Verdict : Counts {
        val lastCorrect: Boolean
    }

    /**
     * Records the answer to question [seq] and returns the verdict, or null when the race refuses it:
     * not this user's or course's, finished, [seq] below `next_seq`, or [seq] past the deck. A [seq]
     * ahead of `next_seq` is taken; the numbers between count neither way and are not recorded.
     * [choice] -1 matches no key. A wrong [seq] is appended to `wrong_seqs`, in this same statement.
     */
    @Query(
        "UPDATE races SET " +
            "next_seq = CAST(:seq AS integer) + 1, " +
            "last_correct = (CAST(answer_key ->> CAST(:seq AS integer) AS integer) = CAST(:choice AS integer)), " +
            "correct_count = correct_count + CASE WHEN CAST(answer_key ->> CAST(:seq AS integer) AS integer) = CAST(:choice AS integer) THEN 1 ELSE 0 END, " +
            "wrong_count = wrong_count + CASE WHEN CAST(answer_key ->> CAST(:seq AS integer) AS integer) = CAST(:choice AS integer) THEN 0 ELSE 1 END, " +
            "wrong_seqs = CASE WHEN CAST(answer_key ->> CAST(:seq AS integer) AS integer) = CAST(:choice AS integer) THEN wrong_seqs ELSE wrong_seqs || to_jsonb(CAST(:seq AS integer)) END " +
            "WHERE id = :raceId AND user_id = :userId AND course_id = :courseId AND finished_at IS NULL " +
            "AND next_seq <= CAST(:seq AS integer) AND CAST(:seq AS integer) < jsonb_array_length(answer_key) " +
            "RETURNING correct_count AS \"correctCount\", wrong_count AS \"wrongCount\", last_correct AS \"lastCorrect\"",
        nativeQuery = true
    )
    fun answer(raceId: String, userId: String, courseId: String, seq: Int, choice: Int): Verdict?

    interface Finished : Counts {
        /** `wrong_seqs` as JSON text, e.g. `[0, 3]`: a projection cannot map jsonb to a list. */
        val wrongSeqs: String
    }

    /**
     * Finishes the race and returns its counters and wrong question numbers at that moment, or null
     * when it was already finished. Run it in the transaction that inserts the result: the row stays
     * locked until then, so no answer lands between these values and the result.
     */
    @Query(
        "UPDATE races SET finished_at = :now WHERE id = :raceId AND finished_at IS NULL " +
            "RETURNING correct_count AS \"correctCount\", wrong_count AS \"wrongCount\", " +
            "CAST(wrong_seqs AS text) AS \"wrongSeqs\"",
        nativeQuery = true
    )
    fun markFinished(raceId: String, now: Long): Finished?
}
