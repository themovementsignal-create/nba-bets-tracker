package io.github.themovementsignal.training.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.roundToLong

/** Pure calculations with no Android dependencies, so they can be unit-tested on any JVM. */
object Calc {

    /** Estimated one-rep max (Epley). Returns 0 for reps <= 0. */
    fun epley(weightKg: Double, reps: Int): Double = when {
        reps <= 0 || weightKg <= 0 -> 0.0
        reps == 1 -> weightKg
        else -> weightKg * (1 + reps / 30.0)
    }

    data class PlateResult(
        /** Plates for ONE side, heaviest first. */
        val perSide: List<Double>,
        /** Weight actually loaded (bar + plates). */
        val achieved: Double,
        /** target − achieved (≥ 0 when the target can't be hit exactly). */
        val remainder: Double,
    )

    /** Greedy plate loading. Assumes unlimited pairs of each plate size. */
    fun plates(targetKg: Double, barKg: Double, available: List<Double>): PlateResult {
        val sizes = available.filter { it > 0 }.sortedDescending()
        var perSideLeft = (targetKg - barKg) / 2.0
        val result = mutableListOf<Double>()
        if (perSideLeft > 0) {
            for (p in sizes) {
                while (perSideLeft + 1e-9 >= p) {
                    result += p
                    perSideLeft -= p
                }
            }
        }
        val achieved = barKg + 2 * result.sum()
        return PlateResult(result, round2(achieved), round2(targetKg - achieved))
    }

    fun parsePlates(csv: String): List<Double> =
        csv.split(',', ';', ' ').mapNotNull { parseNumber(it) }.filter { it > 0 }

    /** Parses "102.5" or "102,5"; returns null for blanks or junk. */
    fun parseNumber(s: String?): Double? {
        if (s == null) return null
        val t = s.trim().replace(" ", "")
        if (t.isEmpty()) return null
        val normalised = if (t.contains(',') && !t.contains('.')) t.replace(',', '.') else t.replace(",", "")
        return normalised.toDoubleOrNull()
    }

    fun round2(d: Double): Double = (d * 100).roundToLong() / 100.0

    /** 102.5 → "102.5", 100.0 → "100". */
    fun fmt(d: Double?): String {
        if (d == null) return ""
        val r = round2(d)
        return if (abs(r - r.toLong()) < 1e-9) r.toLong().toString() else r.toString()
    }

    fun mondayOf(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    data class Week(val start: LocalDate, val value: Double, val spike: Boolean, val ratio: Double?)

    /**
     * Sums [items] (epoch day → value) into Monday-based weeks, covering the [weeks] weeks up to and
     * including the week of [today]. A week is flagged as a spike when it is more than [spikeRatio]×
     * the average of the four weeks before it (acute:chronic style).
     */
    fun weekly(
        items: List<Pair<Long, Double>>,
        today: LocalDate,
        weeks: Int = 8,
        spikeRatio: Double = 1.3,
    ): List<Week> {
        val thisMonday = mondayOf(today)
        val totalWeeks = weeks + 4
        val starts = (totalWeeks - 1 downTo 0).map { thisMonday.minusWeeks(it.toLong()) }
        val sums = DoubleArray(totalWeeks)
        val firstEpoch = starts.first().toEpochDay()
        for ((day, v) in items) {
            val idx = ((day - firstEpoch).floorDiv(7L)).toInt()
            if (idx in 0 until totalWeeks) sums[idx] += v
        }
        return (4 until totalWeeks).map { i ->
            val chronic = (i - 4 until i).map { sums[it] }.average()
            val ratio = if (chronic > 0) sums[i] / chronic else null
            Week(starts[i], sums[i], ratio != null && ratio > spikeRatio, ratio)
        }
    }

    /** For each entry (epoch day, value), the mean of entries within the 7 days ending that day. */
    fun rollingAverage(points: List<Pair<Long, Double>>, windowDays: Int = 7): List<Pair<Long, Double>> {
        val sorted = points.sortedBy { it.first }
        return sorted.map { (day, _) ->
            val window = sorted.filter { it.first in (day - windowDays + 1)..day }
            day to window.map { it.second }.average()
        }
    }

    /** Jump height in cm from flight time in seconds: h = g·t²/8. */
    fun jumpHeightCm(flightSeconds: Double): Double = 9.80665 * flightSeconds * flightSeconds / 8.0 * 100.0

    fun tags(csv: String): Set<String> = csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    /** True when every equipment tag needed is available. */
    fun hasEquipment(needed: String, available: Set<String>): Boolean = tags(needed).all { it in available }

    /**
     * Picks the exercise to do at a venue: the original if possible, otherwise the first alternative
     * whose equipment is available, otherwise the original (so the user can swap by hand).
     */
    fun <T> substitute(original: T, alternatives: List<T>, equipmentOf: (T) -> String, available: Set<String>): T {
        if (hasEquipment(equipmentOf(original), available)) return original
        return alternatives.firstOrNull { hasEquipment(equipmentOf(it), available) } ?: original
    }
}
