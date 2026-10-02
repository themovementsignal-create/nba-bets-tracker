package com.muir.bear.domain

import kotlin.math.sqrt

/**
 * Training and recovery analytics from data Bear already has. All days are epoch days.
 * Methods are deliberately simple and published:
 *  - Hard sets per muscle: working sets taken reasonably close to failure (RPE 7+, or unrated);
 *    main muscles count 1, "also works" counts ½ (common convention in volume research).
 *  - Fitness/fatigue: Banister impulse-response, as exponentially weighted averages of daily
 *    load (session RPE × minutes) with 42-day (fitness) and 7-day (fatigue) time constants,
 *    the same constants TrainingPeaks uses. Form = fitness − fatigue.
 *  - Sleep: debt against your stated need over 7 nights; regularity as the spread (standard
 *    deviation) of bed and wake times.
 *  - Plateau: best estimated 1RM in the last 4 weeks vs the 4 weeks before.
 */
object Insights {

    // ---------- Hard sets per muscle ----------

    data class SetForVolume(val day: Long, val muscles: List<String>, val secondary: List<String>, val rpe: Double?, val warmUp: Boolean)

    fun isHard(s: SetForVolume): Boolean = !s.warmUp && (s.rpe == null || s.rpe >= 7.0)

    /** Hard sets per muscle for days in [from, to]. */
    fun setsPerMuscle(sets: List<SetForVolume>, from: Long, to: Long): Map<String, Double> {
        val out = mutableMapOf<String, Double>()
        for (s in sets) {
            if (s.day < from || s.day > to || !isHard(s)) continue
            s.muscles.forEach { out[it] = (out[it] ?: 0.0) + 1.0 }
            s.secondary.filter { it !in s.muscles }.forEach { out[it] = (out[it] ?: 0.0) + 0.5 }
        }
        return out
    }

    // ---------- Fitness / fatigue ----------

    data class LoadDay(val day: Long, val load: Double, val fitness: Double, val fatigue: Double) {
        val form: Double get() = fitness - fatigue
    }

    /**
     * Daily fitness and fatigue from [loads] (day to session RPE × minutes; several sessions on a
     * day add up), from the first day with load up to [today].
     */
    fun fitnessFatigue(loads: List<Pair<Long, Double>>, today: Long, fitnessDays: Double = 42.0, fatigueDays: Double = 7.0): List<LoadDay> {
        if (loads.isEmpty()) return emptyList()
        val byDay = loads.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        val start = byDay.keys.min()
        var fit = 0.0
        var fat = 0.0
        val out = ArrayList<LoadDay>()
        for (d in start..today) {
            val l = byDay[d] ?: 0.0
            fit += (l - fit) / fitnessDays
            fat += (l - fat) / fatigueDays
            out += LoadDay(d, l, fit, fat)
        }
        return out
    }

    /** Plain-language reading of form relative to fitness (thresholds as ratios, so they scale). */
    fun formLabel(day: LoadDay): String {
        if (day.fitness < 1.0) return "Building a baseline"
        val r = day.form / day.fitness
        return when {
            r < -0.3 -> "Heavy fatigue: a lighter few days would help"
            r < -0.1 -> "Training hard: fatigue above fitness"
            r <= 0.15 -> "Balanced"
            else -> "Fresh: fatigue has cleared"
        }
    }

    // ---------- Sleep ----------

    data class Night(val bedAt: Long, val wakeAt: Long)

    data class SleepStats(val nights: Int, val avgHours: Double, val debtHours: Double, val bedSpreadMin: Double?, val wakeSpreadMin: Double?)

    /** [nights] newest first or any order; uses the last [days] nights. Times are epoch millis; [zoneOffsetMin] for clock times. */
    fun sleepStats(nights: List<Night>, needHours: Double, days: Int = 7, zoneOffsetMin: Int = 0): SleepStats? {
        val recent = nights.filter { it.wakeAt > it.bedAt }.sortedByDescending { it.wakeAt }.take(days)
        if (recent.isEmpty()) return null
        val hours = recent.map { (it.wakeAt - it.bedAt) / 3_600_000.0 }
        val debt = hours.sumOf { (needHours - it).coerceAtLeast(0.0) }
        fun clockMin(t: Long, pivotHour: Int): Double {
            // Minutes after a pivot hour so a bedtime of 23:30 and 00:30 are 60 min apart, not 23 h.
            val m = ((t / 60_000 + zoneOffsetMin) % 1440 + 1440) % 1440
            return ((m - pivotHour * 60 + 1440) % 1440).toDouble()
        }
        fun spread(xs: List<Double>): Double? {
            if (xs.size < 3) return null
            val mean = xs.average()
            return sqrt(xs.sumOf { (it - mean) * (it - mean) } / (xs.size - 1))
        }
        return SleepStats(
            nights = recent.size,
            avgHours = hours.average(),
            debtHours = debt,
            bedSpreadMin = spread(recent.map { clockMin(it.bedAt, 12) }),
            wakeSpreadMin = spread(recent.map { clockMin(it.wakeAt, 0) }),
        )
    }

    // ---------- Strength trend ----------

    data class Trend(val recentBest: Double, val priorBest: Double?, val changePct: Double?, val plateau: Boolean, val sessions: Int)

    /** [sessions]: day to best e1RM that session. Compares the last 4 weeks with the 4 before. */
    fun strengthTrend(sessions: List<Pair<Long, Double>>, today: Long): Trend? {
        val recent = sessions.filter { it.first > today - 28 && it.first <= today }
        if (recent.isEmpty()) return null
        val prior = sessions.filter { it.first > today - 56 && it.first <= today - 28 }
        val recentBest = recent.maxOf { it.second }
        val priorBest = prior.maxOfOrNull { it.second }
        val change = priorBest?.takeIf { it > 0 }?.let { (recentBest - it) / it * 100 }
        // Flag only with enough evidence: 3+ sessions in each window and under 1% gain.
        val plateau = change != null && recent.size >= 3 && prior.size >= 3 && change < 1.0
        return Trend(recentBest, priorBest, change, plateau, recent.size)
    }
}
