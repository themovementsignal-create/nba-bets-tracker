package com.muir.bear.domain

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * A plain daily readiness call built from simple, explainable signals. Each signal is good,
 * neutral or poor, with its reason; the result is never a made-up score.
 *  - Last night's sleep vs your own typical (median of the previous 14 nights) and an absolute floor.
 *  - Sleep debt over the week.
 *  - Morning check-in (energy, soreness).
 *  - Form from the fitness/fatigue model.
 *  - Active niggles.
 *  - HRV (when measured): today's ln(rMSSD) vs your 30-day baseline, in standard deviations
 *    (the approach used in HRV-guided training studies, e.g. Plews et al.).
 */
object Readiness {
    enum class Level { GOOD, NEUTRAL, POOR }
    data class Signal(val level: Level, val reason: String)
    data class Result(val call: String, val signals: List<Signal>)

    data class Inputs(
        val lastNightHours: Double? = null,
        val typicalHours: Double? = null,
        val debtHours: Double? = null,
        val energy: Int? = null,
        val soreness: Int? = null,
        val formRatio: Double? = null,
        val niggles: List<Pair<String, Int>> = emptyList(),
        val hrvToday: Double? = null,
        val hrvBaseline: List<Double> = emptyList(),
    )

    fun median(xs: List<Double>): Double? = if (xs.isEmpty()) null else xs.sorted().let { s ->
        if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    fun signals(i: Inputs): List<Signal> {
        val out = mutableListOf<Signal>()
        i.lastNightHours?.let { h ->
            val t = i.typicalHours
            out += when {
                h < 6.0 -> Signal(Level.POOR, "Short night: %.1f h".format(h))
                t != null && h < t - 1.0 -> Signal(Level.POOR, "%.1f h sleep, an hour+ under your usual %.1f h".format(h, t))
                t != null && h >= t - 0.25 -> Signal(Level.GOOD, "%.1f h sleep, at or above your usual".format(h))
                else -> Signal(Level.NEUTRAL, "%.1f h sleep".format(h))
            }
        }
        i.debtHours?.let { d -> if (d >= 5.0) out += Signal(Level.POOR, "Sleep debt this week: %.1f h".format(d)) }
        i.energy?.let { e ->
            out += when {
                e <= 2 -> Signal(Level.POOR, "Energy $e/5")
                e >= 4 -> Signal(Level.GOOD, "Energy $e/5")
                else -> Signal(Level.NEUTRAL, "Energy $e/5")
            }
        }
        i.soreness?.let { s -> if (s >= 4) out += Signal(Level.POOR, "Soreness $s/5") else if (s <= 2) out += Signal(Level.GOOD, "Little soreness ($s/5)") }
        i.formRatio?.let { r ->
            when {
                r < -0.3 -> out += Signal(Level.POOR, "Heavy recent training load")
                r > 0.15 -> out += Signal(Level.GOOD, "Fatigue has cleared")
            }
        }
        i.niggles.filter { it.second >= 5 }.forEach { (where, sev) -> out += Signal(Level.POOR, "$where niggle $sev/10") }
        hrvSignal(i.hrvToday, i.hrvBaseline)?.let { out += it }
        return out
    }

    /** HRV vs baseline: needs 5+ earlier readings. Below mean − 1 SD of ln(rMSSD) is poor. */
    fun hrvSignal(today: Double?, baseline: List<Double>): Signal? {
        if (today == null || today <= 0 || baseline.size < 5) return null
        val logs = baseline.filter { it > 0 }.map { ln(it) }
        if (logs.size < 5) return null
        val mean = logs.average()
        val sd = sqrt(logs.sumOf { (it - mean) * (it - mean) } / (logs.size - 1)).coerceAtLeast(0.05)
        val z = (ln(today) - mean) / sd
        return when {
            z < -1.0 -> Signal(Level.POOR, "HRV below your normal range")
            z > 1.0 -> Signal(Level.NEUTRAL, "HRV unusually high (can follow hard days)")
            else -> Signal(Level.GOOD, "HRV in your normal range")
        }
    }

    fun assess(i: Inputs): Result? {
        val s = signals(i)
        if (s.size < 2) return null
        val poor = s.count { it.level == Level.POOR }
        val good = s.count { it.level == Level.GOOD }
        val call = when {
            poor >= 2 -> "Go easy"
            poor == 1 -> "Steady"
            good >= 1 -> "Ready"
            else -> "Steady"
        }
        return Result(call, s.sortedBy { it.level.ordinal.let { o -> if (o == 2) -1 else o } })
    }
}
