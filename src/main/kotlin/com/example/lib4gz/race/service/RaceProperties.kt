package com.example.lib4gz.race.service

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * `application.race`: the numbers behind the finish-time floor, copied from the game's constants
 * in the frontend, and the most questions one race may deal.
 */
@ConfigurationProperties("application.race")
class RaceProperties(
    val laps: Int,
    /** Units per lap. */
    val lapLength: Double,
    /** Units per second with no boost. */
    val topSpeed: Double,
    val boostSeconds: Double,
    /** A Double Boost's speed multiplier, the fastest the kart goes. */
    val doubleMultiplier: Double,
    val maxQuestions: Int
) {
    init {
        require(laps > 0 && lapLength > 0 && topSpeed > 0 && boostSeconds >= 0 && maxQuestions > 0) {
            "application.race: laps, lap-length, top-speed and max-questions must be above zero, boost-seconds not below"
        }
        require(doubleMultiplier >= 1) { "application.race.double-multiplier must be at least 1" }
    }

    /**
     * The fastest finish [correct] right answers allow, in ms: the no-boost time less what one
     * Double Boost saves per right answer, and never under the no-boost time at Double Boost speed
     * the whole way.
     */
    fun floorMs(correct: Int): Double {
        val noBoost = lapLength * laps / topSpeed
        val savedPerBoost = boostSeconds * (doubleMultiplier - 1)
        return maxOf(noBoost - correct * savedPerBoost, noBoost / doubleMultiplier) * 1000
    }
}
