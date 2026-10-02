package com.muir.bear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsTest {
    private fun set(day: Long, rpe: Double?, warm: Boolean = false) =
        Insights.SetForVolume(day, listOf("quadriceps", "glutes"), listOf("hamstrings", "glutes"), rpe, warm)

    @Test fun hardSetsCountMainAsOneAndAlsoAsHalf() {
        val sets = listOf(set(10, 8.0), set(10, null), set(10, 6.0), set(10, 9.0, warm = true), set(20, 8.0))
        val m = Insights.setsPerMuscle(sets, 7, 13)
        assertEquals(2.0, m["quadriceps"]!!, 1e-9)
        assertEquals(2.0, m["glutes"]!!, 1e-9) // listed as main and also: counted once as main
        assertEquals(1.0, m["hamstrings"]!!, 1e-9)
    }

    @Test fun fitnessFatigueRespondsAtDifferentSpeeds() {
        val loads = (0L until 28L).map { it to 300.0 }
        val days = Insights.fitnessFatigue(loads, 27)
        val last = days.last()
        assertTrue(last.fatigue > last.fitness) // fatigue catches up faster
        // A week off: fatigue drops much faster than fitness.
        val rested = Insights.fitnessFatigue(loads, 34).last()
        assertTrue(rested.fatigue < last.fatigue / 2)
        assertTrue(rested.fitness > last.fitness * 0.8)
        assertTrue(rested.form > 0)
        assertEquals("Fresh: fatigue has cleared", Insights.formLabel(Insights.fitnessFatigue(loads, 34)))
        // One session isn't enough history for a reading.
        val one = Insights.fitnessFatigue(listOf(0L to 300.0), 0)
        assertEquals("Building a baseline: about 13 more days", Insights.formLabel(one))
        assertEquals(null, Insights.formRatio(one))
    }

    @Test fun sleepDebtAndSpread() {
        val h = 3_600_000L
        val nights = (0 until 7).map { i ->
            val bed = i * 24 * h + 23 * h + (if (i % 2 == 0) 0 else h) // 23:00 or 00:00
            Insights.Night(bed, bed + 7 * h)
        }
        val s = Insights.sleepStats(nights, needHours = 8.0)!!
        assertEquals(7, s.nights)
        assertEquals(7.0, s.avgHours, 1e-9)
        assertEquals(7.0, s.debtHours, 1e-9)
        assertTrue(s.bedSpreadMin!! in 25.0..35.0) // alternating by an hour, not 23 hours
        assertNull(Insights.sleepStats(emptyList(), 8.0))
        // A 2-minute test "night" is ignored.
        assertNull(Insights.sleepStats(listOf(Insights.Night(0, 120_000)), 8.0))
    }

    @Test fun plateauNeedsEvidence() {
        val flat = listOf(1L, 8L, 15L, 30L, 37L, 44L).map { it to 140.0 }
        val t = Insights.strengthTrend(flat, 50)!!
        assertTrue(t.plateau)
        val rising = listOf(1L to 130.0, 8L to 131.0, 15L to 132.0, 30L to 136.0, 37L to 138.0, 44L to 140.0)
        assertFalse(Insights.strengthTrend(rising, 50)!!.plateau)
        val sparse = listOf(30L to 140.0, 44L to 140.0)
        assertFalse(Insights.strengthTrend(sparse, 50)!!.plateau)
    }
}
