package com.muir.bear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RpeTest {
    @Test fun chartValues() {
        assertEquals(1.0, Rpe.fraction(1, 10.0)!!, 1e-9)
        assertEquals(0.811, Rpe.fraction(5, 8.0)!!, 1e-9) // 5 @ 8 = 7 effective reps
        assertEquals(0.863, Rpe.fraction(5, 10.0)!!, 1e-9)
        assertEquals(0.572, Rpe.fraction(12, 6.0)!!, 1e-9)
        assertEquals(0.907, Rpe.fraction(1, 7.5)!!, 1e-9)
    }

    @Test fun offChartIsNull() {
        assertNull(Rpe.fraction(5, 5.0))
        assertNull(Rpe.fraction(0, 8.0))
        assertNull(Rpe.fraction(20, 6.0))
    }

    @Test fun e1rmUsesRpeOrFallsBack() {
        assertEquals(100.0 / 0.811, Rpe.e1rm(100.0, 5, 8.0), 1e-6)
        assertEquals(Calc.epley(100.0, 5), Rpe.e1rm(100.0, 5, null), 1e-9)
        assertEquals(0.0, Rpe.e1rm(0.0, 5, 8.0), 1e-9)
    }

    @Test fun suggestedLoadRoundsToPlates() {
        // e1RM 140: 5 @ 8 = 81.1% = 113.5 -> 112.5
        assertEquals(112.5, Rpe.suggestedLoad(140.0, 5, 8.0)!!, 1e-9)
        assertEquals(114.0, Rpe.suggestedLoad(140.0, 5, 8.0, step = 2.0)!!, 1e-9)
    }
}
