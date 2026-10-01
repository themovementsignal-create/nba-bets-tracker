package io.github.themovementsignal.training.domain

import kotlin.math.roundToInt

/**
 * Sleep-stage estimation from per-minute movement (phone on the mattress), in the spirit of
 * Sleep Cycle-style actigraphy. These are estimates for trends, not medical measurements.
 */
object SleepAnalysis {

    enum class Stage { AWAKE, REM, LIGHT, DEEP }

    data class Summary(
        val minutes: Int,
        val awake: Int,
        val rem: Int,
        val light: Int,
        val deep: Int,
        val snoreMin: Int,
        val score: Int,
    )

    private fun quantile(sorted: List<Float>, q: Double): Float {
        if (sorted.isEmpty()) return 0f
        val idx = ((sorted.size - 1) * q).roundToInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }

    /** Rolling mean over [window] minutes centred on each minute. */
    fun smooth(values: List<Float>, window: Int = 5): List<Float> {
        if (values.isEmpty()) return values
        val half = window / 2
        return values.indices.map { i ->
            val from = (i - half).coerceAtLeast(0)
            val to = (i + half).coerceAtMost(values.lastIndex)
            (from..to).map { values[it] }.average().toFloat()
        }
    }

    /**
     * One stage per minute. Thresholds are relative to the night itself, so it works whatever the
     * mattress or phone: most movement = awake, least = deep, low-but-restless in the later part of
     * the night = REM, the rest = light. The first 10 minutes count as falling asleep (awake).
     */
    fun stages(movement: List<Float>): List<Stage> {
        if (movement.isEmpty()) return emptyList()
        val s = smooth(movement)
        val sorted = s.sorted()
        val qAwake = quantile(sorted, 0.88)
        val qLight = quantile(sorted, 0.40)
        val qDeep = quantile(sorted, 0.22)
        val n = movement.size
        return s.mapIndexed { i, v ->
            when {
                i < 10 -> Stage.AWAKE
                v >= qAwake && movement[i] > 0f -> Stage.AWAKE
                v <= qDeep -> Stage.DEEP
                v < qLight && i > n * 0.35 && movement[i] > v -> Stage.REM
                else -> Stage.LIGHT
            }
        }
    }

    /**
     * Smart alarm check: true when the last few minutes look like light sleep (movement above the
     * night's median), i.e. a gentle moment to wake. Needs at least 30 minutes of data.
     */
    fun isLightNow(movement: List<Float>): Boolean {
        if (movement.size < 30) return false
        val median = quantile(movement.sorted(), 0.5)
        val recent = movement.takeLast(3).average().toFloat()
        return recent > median * 1.5f && recent > 0f
    }

    /** 0–100: duration (50), deep+REM share (25), little time awake (15), little snoring (10). */
    fun score(minutes: Int, deep: Int, rem: Int, awake: Int, snoreMin: Int): Int {
        if (minutes <= 0) return 0
        val duration = (minutes / 480.0).coerceAtMost(1.0) * 50
        val restorative = (((deep + rem).toDouble() / minutes) / 0.45).coerceAtMost(1.0) * 25
        val calm = (1 - (awake.toDouble() / minutes) * 4).coerceIn(0.0, 1.0) * 15
        val quiet = (1 - snoreMin / 60.0).coerceIn(0.0, 1.0) * 10
        return (duration + restorative + calm + quiet).roundToInt().coerceIn(0, 100)
    }

    fun summarise(movement: List<Float>, snoreSeconds: List<Int>): Summary {
        val st = stages(movement)
        val awake = st.count { it == Stage.AWAKE }
        val rem = st.count { it == Stage.REM }
        val light = st.count { it == Stage.LIGHT }
        val deep = st.count { it == Stage.DEEP }
        val snoreMin = (snoreSeconds.sum() / 60.0).roundToInt()
        return Summary(st.size, awake, rem, light, deep, snoreMin, score(st.size, deep, rem, awake, snoreMin))
    }
}
