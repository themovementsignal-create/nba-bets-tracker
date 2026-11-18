package com.muir.bear.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Timed sets run work → rest → next set's work on their own (e.g. 15 s on the bike, 45 s rest, ×3). */
class IntervalTest {
    @Test fun workRestWorkThenStop() {
        var s: RestTimer.State? = RestTimer.State(endAt = 15_000, totalSeconds = 15, setId = 1, work = true, restSeconds = 45, upNext = listOf(2L to 15, 3L to 20))
        val seen = mutableListOf<Triple<Long?, Boolean, Int>>()
        var now = 0L
        while (s != null) {
            seen += Triple(s.setId, s.work, s.totalSeconds)
            now = s.endAt
            s = RestTimer.after(s, now)?.also { assertEquals(now + it.totalSeconds * 1000L, it.endAt) }
        }
        assertEquals(
            listOf(Triple(1L, true, 15), Triple(1L, false, 45), Triple(2L, true, 15), Triple(2L, false, 45), Triple(3L, true, 20), Triple(3L, false, 45)),
            seen,
        )
    }

    @Test fun noRestGoesStraightToNextSet() {
        val s = RestTimer.State(0, 15, 1, work = true, restSeconds = 0, upNext = listOf(2L to 15))
        val n = RestTimer.after(s, 1_000)!!
        assertTrue(n.work)
        assertEquals(2L, n.setId)
        assertNull(RestTimer.after(n, 2_000))
    }

    @Test fun plainRestJustEnds() {
        assertNull(RestTimer.after(RestTimer.State(0, 90, 5), 90_000))
        val rest = RestTimer.after(RestTimer.State(0, 30, 5, work = true, restSeconds = 60), 30_000)!!
        assertFalse(rest.work)
        assertNull(RestTimer.after(rest, 90_000))
    }
}
