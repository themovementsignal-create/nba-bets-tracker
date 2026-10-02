package com.muir.bear.domain

import com.muir.bear.domain.SleepAnalysis.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepAnalysisTest {

    /** A synthetic 8-hour night: restless start, deep stretches, lighter/REM later, waking at the end. */
    private fun night(): List<Float> = buildList {
        repeat(20) { add(3f) }                 // falling asleep
        repeat(4) { cycle ->
            repeat(40) { add(0.05f) }          // deep
            repeat(30) { add(0.6f) }           // light
            repeat(20) { add(if (it % 3 == 0) 0.4f else 0.1f) } // REM-ish twitches
            repeat(5) { add(2.5f) }            // brief stir
        }
        repeat(80) { add(0.5f) }
        repeat(10) { add(4f) }                 // waking
    }

    @Test fun stagesCoverEveryMinute() {
        val m = night()
        val st = SleepAnalysis.stages(m)
        assertEquals(m.size, st.size)
        assertTrue(st.take(10).all { it == Stage.AWAKE })
        assertTrue(st.count { it == Stage.DEEP } > 60)
        assertTrue(st.count { it == Stage.AWAKE } < m.size / 4)
    }

    @Test fun lightSleepDetection() {
        val calm = List(60) { 0.1f }
        assertFalse(SleepAnalysis.isLightNow(calm))
        assertTrue(SleepAnalysis.isLightNow(calm + listOf(1f, 1f, 1f)))
        assertFalse(SleepAnalysis.isLightNow(List(10) { 1f }))
    }

    @Test fun scoreBounds() {
        assertEquals(0, SleepAnalysis.score(0, 0, 0, 0, 0))
        val good = SleepAnalysis.score(480, 120, 100, 10, 0)
        val poor = SleepAnalysis.score(300, 20, 10, 90, 60)
        assertTrue(good in 85..100)
        assertTrue(poor < good)
    }

    @Test fun summaryCountsSnoring() {
        val s = SleepAnalysis.summarise(night(), List(night().size) { if (it in 100..159) 30 else 0 })
        assertEquals(30, s.snoreMin)
        assertEquals(s.minutes, s.awake + s.rem + s.light + s.deep)
    }
}
