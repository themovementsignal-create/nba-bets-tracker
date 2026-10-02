package com.muir.bear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadinessTest {
    @Test fun needsTwoSignals() {
        assertNull(Readiness.assess(Readiness.Inputs(energy = 4)))
    }

    @Test fun readyWhenAllGood() {
        val r = Readiness.assess(Readiness.Inputs(lastNightHours = 8.0, typicalHours = 7.5, energy = 4, soreness = 1))!!
        assertEquals("Ready", r.call)
    }

    @Test fun goEasyWithTwoPoorSignals() {
        val r = Readiness.assess(Readiness.Inputs(lastNightHours = 5.5, typicalHours = 7.5, energy = 2, soreness = 2))!!
        assertEquals("Go easy", r.call)
        assertEquals(Readiness.Level.POOR, r.signals.first().level) // reasons that matter come first
    }

    @Test fun oneNiggleMakesItSteady() {
        val r = Readiness.assess(Readiness.Inputs(energy = 4, soreness = 1, niggles = listOf("Left knee" to 6)))!!
        assertEquals("Steady", r.call)
    }

    @Test fun hrvAgainstBaseline() {
        val base = listOf(60.0, 62.0, 58.0, 61.0, 59.0, 63.0, 60.0)
        assertEquals(Readiness.Level.POOR, Readiness.hrvSignal(45.0, base)!!.level)
        assertEquals(Readiness.Level.GOOD, Readiness.hrvSignal(60.0, base)!!.level)
        assertNull(Readiness.hrvSignal(60.0, base.take(3)))
    }
}
