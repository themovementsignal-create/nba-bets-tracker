package com.muir.bear.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NeatStepsTest {
    @Test fun creditsOnlyWhatThePhoneMissed() {
        // Phone on the console: counted almost nothing.
        assertEquals(4_480, NeatSteps.credit(45, 100, 20))
        // Phone in the pocket: counted it all, nothing to add.
        assertEquals(0, NeatSteps.credit(45, 100, 4_700))
        // No step counter: the whole estimate.
        assertEquals(3_000, NeatSteps.credit(30, 100, null))
    }

    @Test fun totalsAddCreditedSteps() {
        val t = NeatSteps.totals(listOf(10L to 5_000, 11L to 2_000), listOf(10L to 4_000, 12L to 3_000, 11L to 0))
        assertEquals(mapOf(10L to 9_000, 11L to 2_000, 12L to 3_000), t)
    }

    @Test fun cadenceIsClampedAndSpeedLabel() {
        assertEquals(40 * 10, NeatSteps.estimate(10, 5))
        assertEquals(4.5, NeatSteps.kmh(100), 0.0)
    }
}
