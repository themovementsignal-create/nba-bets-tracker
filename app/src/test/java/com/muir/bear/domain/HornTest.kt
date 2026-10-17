package com.muir.bear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HornTest {
    @Test fun lengthLevelAndSoftEnds() {
        val x = Horn.samples(seconds = 1.0, volume = 0.6)
        assertEquals(Horn.SAMPLE_RATE, x.size)
        val peak = Horn.peak(x)
        assertTrue("loud but not clipping: $peak", peak in 0.4..0.61)
        // Starts and ends at silence (no click).
        assertTrue(kotlin.math.abs(x.first().toInt()) < 50)
        assertTrue(kotlin.math.abs(x.last().toInt()) < 50)
    }
}
