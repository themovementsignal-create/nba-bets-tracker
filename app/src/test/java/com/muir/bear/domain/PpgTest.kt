package com.muir.bear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class PpgTest {
    /**
     * Synthetic fingertip signal at 30 fps: each beat is a dip in brightness, plus slow drift
     * (breathing, exposure) and noise. RR intervals alternate around [meanRr] so the true RMSSD is known.
     */
    private fun synth(meanRr: Double, swing: Double, seconds: Double, noise: Double, seed: Int = 1): Triple<DoubleArray, DoubleArray, Double> {
        val rnd = Random(seed)
        val beats = mutableListOf<Double>()
        var tb = 0.5
        var k = 0
        val rrs = mutableListOf<Double>()
        while (tb < seconds) {
            beats += tb
            val rr = meanRr + if (k % 2 == 0) swing else -swing
            rrs += rr
            tb += rr / 1000
            k++
        }
        val n = (seconds * 30).toInt()
        val t = DoubleArray(n) { it / 30.0 }
        val v = DoubleArray(n) { i ->
            val x = t[i]
            var s = 180.0 + 4 * sin(2 * PI * 0.25 * x) // drift
            for (b in beats) {
                val d = x - b
                if (d > -0.3 && d < 0.6) s -= 3.0 * exp(-(d * d) / (2 * 0.06 * 0.06))
            }
            s + noise * (rnd.nextDouble() - 0.5)
        }
        // RMSSD of the RR series actually used (consecutive differences).
        val diffs = (1 until rrs.size - 1).map { rrs[it] - rrs[it - 1] }
        val rmssd = sqrt(diffs.sumOf { it * it } / diffs.size)
        return Triple(t, v, rmssd)
    }

    @Test fun recoversHeartRateAndRmssd() {
        val (t, v, trueRmssd) = synth(meanRr = 1000.0, swing = 25.0, seconds = 120.0, noise = 0.4)
        val r = Ppg.analyze(t, v)!!
        assertEquals(60.0, r.heartRate, 2.0)
        assertEquals(trueRmssd, r.rmssdMs, 15.0) // 30 fps limits timing; interpolation keeps it close
        assertEquals(Ppg.Quality.GOOD, r.quality)
        assertTrue(r.beats in 115..122)
    }

    @Test fun fasterHeart() {
        val (t, v, _) = synth(meanRr = 700.0, swing = 10.0, seconds = 60.0, noise = 0.4, seed = 3)
        assertEquals(60_000.0 / 700, Ppg.analyze(t, v)!!.heartRate, 3.0)
    }

    @Test fun noiseOnlyIsRejectedOrPoor() {
        val rnd = Random(7)
        val t = DoubleArray(1800) { it / 30.0 }
        val v = DoubleArray(1800) { 180 + 3 * (rnd.nextDouble() - 0.5) }
        val r = Ppg.analyze(t, v)
        assertTrue(r == null || r.quality == Ppg.Quality.POOR)
    }

    @Test fun tooShort() {
        assertNull(Ppg.analyze(DoubleArray(10) { it.toDouble() }, DoubleArray(10)))
    }
}
