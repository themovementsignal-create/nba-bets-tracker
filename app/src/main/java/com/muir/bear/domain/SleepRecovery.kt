package com.muir.bear.domain

/**
 * What to do with a tracked night that is still "open" (no wake time) when Bear isn't tracking,
 * e.g. because the phone closed the app overnight. Times are epoch millis.
 */
object SleepRecovery {
    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN

    sealed interface Action
    /** Probably still the night: offer to resume (or the tracker restarts itself). */
    data object Resume : Action
    /** The night is clearly over: close it at [wakeAt] (the last moment we have data for). */
    data class Close(val wakeAt: Long) : Action

    fun decide(bedAt: Long, alarmAt: Long?, lastSampleAt: Long?, now: Long): Action {
        val lastData = maxOf(bedAt, lastSampleAt ?: bedAt)
        val over = when {
            now - bedAt > 16 * HOUR -> true
            alarmAt != null && now > alarmAt + 2 * HOUR -> true
            else -> false
        }
        return if (over) Close(lastData + MIN) else Resume
    }

    /** True when tracking looks interrupted: no data for more than [graceMin] minutes. */
    fun interrupted(bedAt: Long, lastSampleAt: Long?, now: Long, graceMin: Int = 5): Boolean =
        now - maxOf(bedAt, lastSampleAt ?: bedAt) > (graceMin + 1) * MIN

    /** Gaps longer than [minGapMin] minutes between samples, as (from, to) pairs, for the morning report. */
    fun gaps(sampleTimes: List<Long>, minGapMin: Int = 5): List<Pair<Long, Long>> {
        val sorted = sampleTimes.sorted()
        return (1 until sorted.size).mapNotNull { i ->
            val a = sorted[i - 1]
            val b = sorted[i]
            if (b - a > (minGapMin + 1) * MIN) (a + MIN) to b else null
        }
    }
}
