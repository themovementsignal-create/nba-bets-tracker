package com.muir.bear.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/**
 * The end-of-rest buzzer: an arena-style horn made in code (no sound file, nothing to license).
 * A slightly dissonant stack of bright, buzzy tones, gently overdriven, with a quick attack and a
 * short tail, the way a basketball shot-clock horn sounds.
 */
object Horn {
    const val SAMPLE_RATE = 44_100

    /** 16-bit mono samples for one blast of [seconds]. */
    fun samples(seconds: Double = 1.1, volume: Double = 0.6): ShortArray {
        val n = (seconds * SAMPLE_RATE).toInt()
        // Close, clashing pitches give the horn its buzz (roughly A#3, C#4, F4 and a low root).
        val freqs = doubleArrayOf(233.1, 277.2, 349.2, 116.5)
        val weights = doubleArrayOf(1.0, 0.8, 0.6, 0.5)
        val phase = DoubleArray(freqs.size)
        val out = ShortArray(n)
        val attack = 0.015 * SAMPLE_RATE
        val release = 0.09 * SAMPLE_RATE
        for (i in 0 until n) {
            var s = 0.0
            for (k in freqs.indices) {
                phase[k] += freqs[k] / SAMPLE_RATE
                if (phase[k] >= 1.0) phase[k] -= 1.0
                // Sawtooth with a touch of sine: bright, brassy.
                val saw = 2.0 * phase[k] - 1.0
                s += weights[k] * (0.75 * saw + 0.25 * sin(2 * PI * phase[k]))
            }
            s /= weights.sum()
            // Overdrive like a stadium speaker, then shape the start and the end.
            s = tanh(2.2 * s) / tanh(2.2)
            val env = min(1.0, min(i / attack, (n - i) / release))
            out[i] = (s * env * volume * Short.MAX_VALUE).toInt().coerceIn(-Short.MAX_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    /** Peak level as a share of full scale (for tests). */
    fun peak(x: ShortArray): Double = (x.maxOfOrNull { abs(it.toInt()) } ?: 0) / Short.MAX_VALUE.toDouble()
}
