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
 *     from the median interval. One minute is enough: ultra-short RMSSD tracks longer recordings
 *     closely, and it's what the best-validated camera apps use.
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

    /** Analyse one continuous recording: [t] in seconds, [values] the mean red brightness per frame. */
    fun analyze(t: DoubleArray, values: DoubleArray): Result? {
        if (t.size < 60 || t.size != values.size) return null
        if (t.last() - t.first() < 20) return null
        return analyzeSegments(listOf(t to values))
    }

    /** One beat-to-beat interval: when it ended, its length (ms) and which stretch of recording it came from. */
    private class Interval(val time: Double, val rr: Double, val segment: Int)

    /**
     * Analyse a recording made of separate stretches (the finger slipped off in between). Beats are
     * found in each stretch on its own, so a gap never counts as one long heartbeat. If the whole
     * thing is too messy, the cleanest [rescueSeconds] stretch is used instead of giving up.
     */
    fun analyzeSegments(segments: List<Pair<DoubleArray, DoubleArray>>, rescueSeconds: Double = 45.0): Result? {
        val iv = mutableListOf<Interval>()
        segments.forEachIndexed { seg, (t, v) ->
            if (t.size < 60 || t.size != v.size || t.last() - t.first() < 4) return@forEachIndexed
            val fs = (t.size - 1) / (t.last() - t.first())
            val beats = peaks(t, clean(v, fs))
            for (i in 1 until beats.size) iv += Interval(beats[i], (beats[i] - beats[i - 1]) * 1000, seg)
        }
        if (iv.isEmpty()) return null
        // Plausible and within 20% of the local median (neighbours from the same stretch only).
        val valid = BooleanArray(iv.size) { i ->
            val x = iv[i].rr
            if (x < 300 || x > 2000) return@BooleanArray false
            val window = (max(0, i - 3)..minOf(iv.size - 1, i + 3)).filter { iv[it].segment == iv[i].segment }.map { iv[it].rr }.sorted()
            val med = window[window.size / 2]
            abs(x - med) <= 0.2 * med
        }
        val whole = summarize(iv, valid, iv.indices)
        if (whole != null && whole.quality != Quality.POOR) return whole
        // Rescue: slide a window along in 5 s steps and keep the cleanest stretch.
        var best = whole
        var start = iv.first().time
        while (start + rescueSeconds <= iv.last().time + 1e-9) {
            val first = iv.indexOfFirst { it.time >= start }
            val last = iv.indexOfLast { it.time < start + rescueSeconds }
            if (first in 0..last) {
                val r = summarize(iv, valid, first..last)
                if (r != null && (best == null || r.artifactPct < best.artifactPct)) best = r
            }
            start += 5.0
        }
        return best
    }

    private fun summarize(iv: List<Interval>, valid: BooleanArray, range: IntRange): Result? {
        val good = range.filter { valid[it] }.map { iv[it].rr }
        if (good.size < 8) return null
        val diffs = range.drop(1).filter { valid[it] && valid[it - 1] && iv[it].segment == iv[it - 1].segment }
            .map { iv[it].rr - iv[it - 1].rr }
        if (diffs.size < 5) return null
        val rmssd = sqrt(diffs.sumOf { it * it } / diffs.size)
        val medianRr = good.sorted()[good.size / 2]
        val total = range.count()
        val artifact = 100.0 * (total - good.size) / total
        val quality = when {
            artifact < 5 -> Quality.GOOD
            artifact < 15 -> Quality.OK
            else -> Quality.POOR
        }
        val beats = total + range.map { iv[it].segment }.distinct().size
        return Result(60_000.0 / medianRr, rmssd, beats, artifact, quality)
    }

    enum class Signal { NONE, WEAK, GOOD }

    /** What the last few seconds look like, for live feedback while measuring. */
    data class Live(val heartRate: Double?, val signal: Signal)

    /** Live heart rate and signal strength from the last few seconds (about 8 s works well). */
    fun live(t: DoubleArray, values: DoubleArray): Live {
        if (t.size < 60 || t.size != values.size || t.last() - t.first() < 4) return Live(null, Signal.NONE)
        val fs = (t.size - 1) / (t.last() - t.first())
        val beats = peaks(t, clean(values, fs))
        val rr = (1 until beats.size).map { (beats[it] - beats[it - 1]) * 1000 }.filter { it in 300.0..2000.0 }
        if (rr.size < 3) return Live(null, Signal.NONE)
        val med = rr.sorted()[rr.size / 2]
        val steady = rr.count { abs(it - med) <= 0.2 * med }.toDouble() / rr.size
        val signal = when {
            steady >= 0.8 -> Signal.GOOD
            steady >= 0.5 -> Signal.WEAK
            else -> Signal.NONE
        }
        return Live(if (signal == Signal.NONE) null else 60_000.0 / med, signal)
    }
}
