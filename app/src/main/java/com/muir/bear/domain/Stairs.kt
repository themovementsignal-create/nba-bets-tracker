package com.muir.bear.domain

import kotlin.math.roundToInt

/**
 * Stairmaster (stepmill) numbers. The console counts floors; that is the number entered.
 * Steps and height are derived, using the machine's own convention of 16 steps a floor and a
 * typical 20 cm step, so one floor is about 3.2 m. Both are shown as "about".
 */
object Stairs {
    const val KIND = "Stairmaster"
    const val STEPS_PER_FLOOR = 16
    const val STEP_HEIGHT_M = 0.2

    fun steps(floors: Int): Int = floors.coerceAtLeast(0) * STEPS_PER_FLOOR

    /** Vertical metres climbed, rounded to the metre. */
    fun metres(floors: Int): Int = (steps(floors) * STEP_HEIGHT_M).roundToInt()

    /** Floors a minute to one decimal, or null without a duration. */
    fun perMin(floors: Int, minutes: Int): Double? =
        if (minutes <= 0 || floors <= 0) null else (floors * 10.0 / minutes).roundToInt() / 10.0

    /** "120 floors · about 384 m" for a history row. */
    fun label(floors: Int): String = "$floors ${if (floors == 1) "floor" else "floors"} · about ${metres(floors)} m"

    data class Session(val day: Long, val floors: Int, val minutes: Int)

    data class Summary(val weekFloors: Int, val weekSessions: Int, val bestFloors: Int, val bestPerMin: Double?)

    /** This week's totals (days on or after [weekStart]) and all-time bests, over sessions with floors logged. */
    fun summary(sessions: List<Session>, weekStart: Long): Summary {
        val logged = sessions.filter { it.floors > 0 }
        val week = logged.filter { it.day >= weekStart }
        return Summary(
            weekFloors = week.sumOf { it.floors },
            weekSessions = week.size,
            bestFloors = logged.maxOfOrNull { it.floors } ?: 0,
            bestPerMin = logged.mapNotNull { perMin(it.floors, it.minutes) }.maxOrNull(),
        )
    }
}
