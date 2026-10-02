package com.muir.bear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepRecoveryTest {
    private val m = 60_000L
    private val h = 60 * m
    private val bed = 1_000_000_000L

    @Test fun duringTheNightResume() {
        assertEquals(SleepRecovery.Resume, SleepRecovery.decide(bed, bed + 8 * h, bed + 3 * h, bed + 4 * h))
    }

    @Test fun wellAfterTheAlarmClose() {
        val a = SleepRecovery.decide(bed, bed + 8 * h, bed + 3 * h, bed + 11 * h)
        assertEquals(SleepRecovery.Close(bed + 3 * h + m), a)
    }

    @Test fun noAlarmClosesAfter16Hours() {
        assertEquals(SleepRecovery.Resume, SleepRecovery.decide(bed, null, null, bed + 15 * h))
        assertEquals(SleepRecovery.Close(bed + m), SleepRecovery.decide(bed, null, null, bed + 17 * h))
    }

    @Test fun interruptedAfterSilence() {
        assertFalse(SleepRecovery.interrupted(bed, bed + 2 * h, bed + 2 * h + 3 * m))
        assertTrue(SleepRecovery.interrupted(bed, bed + 2 * h, bed + 2 * h + 10 * m))
    }

    @Test fun findsGaps() {
        val times = listOf(0L, m, 2 * m, 30 * m, 31 * m)
        assertEquals(listOf((3 * m) to (30 * m)), SleepRecovery.gaps(times))
    }
}
