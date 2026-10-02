package com.muir.bear.domain

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Effort-based (RPE) strength maths, using the RTS / Tuchscherer RPE chart.
 *
 * The chart only depends on "effective reps" = reps done + reps left in the tank (10 − RPE):
 * 5 reps @ RPE 8 is like a 7-rep max, so it's 81.1% of 1RM. Values every half rep from 1 to 16.
 */
object Rpe {
    private val percent = doubleArrayOf(
        100.0, 97.8, 95.5, 93.9, 92.2, 90.7, 89.2, 87.8, 86.3, 85.0, 83.7, 82.4, 81.1, 79.9, 78.6, 77.4,
        76.2, 75.1, 73.9, 72.3, 70.7, 69.4, 68.0, 66.7, 65.3, 64.0, 62.6, 61.3, 59.9, 58.6, 57.2,
    )

    /** Fraction of 1RM for [reps] at [rpe], or null outside the chart (RPE 6–10, up to 16 effective reps). */
    fun fraction(reps: Int, rpe: Double): Double? {
        if (reps < 1 || rpe < 6.0 || rpe > 10.0) return null
        val effective = reps + (10.0 - rpe)
        val idx = (effective - 1.0) * 2.0
        if (idx < 0 || idx > percent.lastIndex) return null
        // Interpolate in case of odd values like RPE 8.25.
        val lo = floor(idx).toInt()
        val hi = minOf(lo + 1, percent.lastIndex)
        val t = idx - lo
        return (percent[lo] * (1 - t) + percent[hi] * t) / 100.0
    }

    /** Estimated 1RM from a set with an RPE; falls back to Epley when the RPE is missing or off-chart. */
    fun e1rm(weightKg: Double, reps: Int, rpe: Double?): Double {
        if (weightKg <= 0 || reps <= 0) return 0.0
        val f = rpe?.let { fraction(reps, it) } ?: return Calc.epley(weightKg, reps)
        return weightKg / f
    }

    /** Load for [reps] at [targetRpe] given an estimated 1RM, rounded to [step] kg. */
    fun suggestedLoad(e1rm: Double, reps: Int, targetRpe: Double, step: Double = 2.5): Double? {
        val f = fraction(reps, targetRpe) ?: return null
        val raw = e1rm * f
        return (raw / step).roundToInt() * step
    }
}
