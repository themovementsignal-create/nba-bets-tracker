package com.muir.bear.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Heart rate and HRV from a fingertip-on-camera brightness signal (photoplethysmography).
 * Classic signal processing, as in the published smartphone-PPG studies:
 *  1. Remove slow drift (subtract a ~1 s moving average) and light smoothing.
 *  2. Each heartbeat briefly darkens the image, so beats are minima of the signal: find them with
 *     a refractory period (max 200 bpm) and an adaptive threshold, refined with parabolic
 *     interpolation for better than frame-rate timing.
 *  3. Beat-to-beat (RR) intervals; reject implausible ones (outside 300–2000 ms or more than 20%
 *     away from the local median), and report the share rejected as signal quality.
 *  4. RMSSD (the standard short-term HRV measure) from successive valid intervals; heart rate
 *     from the median interval.
 */
object Ppg {
    enum class Quality { GOOD, OK, POOR }

    data class Result(
        val heartRate: Double,
        val rmssdMs: Double,
        val beats: Int,
        val artifactPct: Double,
        val quality: Quality,
    )

    private fun movingAverage(x: DoubleArray, half: Int): DoubleArray {
        val n = x.size
        val prefix = DoubleArray(n + 1)
        for (i in 0 until n) prefix[i + 1] = prefix[i] + x[i]
        return DoubleArray(n) { i ->
            val a = max(0, i - half)
            val b = minOf(n - 1, i + half)
            (prefix[b + 1] - prefix[a]) / (b - a + 1)
        }
    }

    /** Detrended, smoothed and inverted signal (beats become peaks). [fs] = samples per second. */
    fun clean(values: DoubleArray, fs: Double): DoubleArray {
        if (values.isEmpty()) return values
        val trend = movingAverage(values, max(1, (fs * 0.5).toInt()))
        val detrended = DoubleArray(values.size) { values[it] - trend[it] }
        val smooth = movingAverage(detrended, max(1, (fs * 0.05).toInt()))
        return DoubleArray(smooth.size) { -smooth[it] }
    }

    /** Peak times in seconds. [t] is seconds, roughly evenly spaced. */
    fun peaks(t: DoubleArray, y: DoubleArray): List<Double> {
        val n = y.size
        if (n < 5) return emptyList()
        val fs = (n - 1) / (t.last() - t.first())
        val refractory = 0.3 // s, i.e. up to 200 bpm
        // Adaptive threshold: a fraction of the local amplitude over a 2 s window.
        val absAvg = movingAverage(DoubleArray(n) { abs(y[it]) }, max(1, fs.toInt()))
        val out = mutableListOf<Double>()
        var lastPeak = Double.NEGATIVE_INFINITY
        for (i in 1 until n - 1) {
            if (y[i] <= y[i - 1] || y[i] < y[i + 1]) continue
            if (y[i] < 0.5 * absAvg[i]) continue
            // Local maximum over ±refractory/2 so small wiggles on a beat don't count twice.
            val w = max(1, (fs * refractory / 2).toInt())
            var isMax = true
            for (j in max(0, i - w)..minOf(n - 1, i + w)) if (y[j] > y[i]) { isMax = false; break }
            if (!isMax) continue
            // Parabolic interpolation for sub-sample timing.
            val denom = y[i - 1] - 2 * y[i] + y[i + 1]
            val offset = if (denom != 0.0) (0.5 * (y[i - 1] - y[i + 1]) / denom).coerceIn(-0.5, 0.5) else 0.0
            val dt = (t[i + 1] - t[i - 1]) / 2
            val time = t[i] + offset * dt
            if (time - lastPeak < refractory) continue
            out += time
            lastPeak = time
        }
        return out
    }

    /** Analyse a recording: [t] in seconds, [values] the mean red brightness per frame. */
    fun analyze(t: DoubleArray, values: DoubleArray): Result? {
        if (t.size < 60 || t.size != values.size) return null
        val duration = t.last() - t.first()
        if (duration < 20) return null
        val fs = (t.size - 1) / duration
        val beatTimes = peaks(t, clean(values, fs))
        if (beatTimes.size < 10) return null
        val rr = (1 until beatTimes.size).map { (beatTimes[it] - beatTimes[it - 1]) * 1000 }
        val valid = BooleanArray(rr.size) { i ->
            val x = rr[i]
            if (x < 300 || x > 2000) return@BooleanArray false
            val window = rr.subList(max(0, i - 3), minOf(rr.size, i + 4)).sorted()
            val med = window[window.size / 2]
            abs(x - med) <= 0.2 * med
        }
        val good = rr.indices.filter { valid[it] }.map { rr[it] }
        if (good.size < 8) return null
        val diffs = (1 until rr.size).filter { valid[it] && valid[it - 1] }.map { rr[it] - rr[it - 1] }
        if (diffs.size < 5) return null
        val rmssd = sqrt(diffs.sumOf { it * it } / diffs.size)
        val medianRr = good.sorted()[good.size / 2]
        val artifact = 100.0 * (rr.size - good.size) / rr.size
        val quality = when {
            artifact < 5 -> Quality.GOOD
            artifact < 15 -> Quality.OK
            else -> Quality.POOR
        }
        return Result(60_000.0 / medianRr, rmssd, beatTimes.size, artifact, quality)
    }
}
