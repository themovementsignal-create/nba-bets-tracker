package com.muir.bear.domain

import kotlin.math.roundToInt

/**
 * Steps for a treadmill / walking (NEAT) session the phone may not have counted, e.g. with the
 * phone on the console. The estimate is minutes × your walking cadence; only what the step
 * counter didn't already count during the session is credited, so nothing is counted twice.
 */
object NeatSteps {
    const val DEFAULT_PER_MIN = 100

    fun estimate(minutes: Int, perMin: Int): Int = (minutes.coerceAtLeast(0) * perMin.coerceIn(40, 200))

    /** Steps to add: the estimate less what the phone counted during it ([counted] null = unknown, assume none). */
    fun credit(minutes: Int, perMin: Int, counted: Int?): Int = (estimate(minutes, perMin) - (counted ?: 0).coerceAtLeast(0)).coerceAtLeast(0)

    /** Daily totals with credited session steps added: (day, phone steps) + (day, credited) → day → total. */
    fun totals(daily: List<Pair<Long, Int>>, credited: List<Pair<Long, Int>>): Map<Long, Int> {
        val out = daily.toMap().toMutableMap()
        credited.forEach { (day, s) -> if (s > 0) out[day] = (out[day] ?: 0) + s }
        return out
    }

    /** Rough walking speed for a cadence, for the label ("about 4.5 km/h"), assuming a 0.75 m step. */
    fun kmh(perMin: Int): Double = (perMin * 0.75 * 60 / 1000.0 * 10).roundToInt() / 10.0
}
