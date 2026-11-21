package com.muir.bear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StairsTest {

    @Test
    fun floorsToStepsAndMetres() {
        assertEquals(1600, Stairs.steps(100))
        assertEquals(320, Stairs.metres(100))
        assertEquals(0, Stairs.steps(-5))
        assertEquals("1 floor · about 3 m", Stairs.label(1))
    }

    @Test
    fun paceNeedsFloorsAndMinutes() {
        assertEquals(6.0, Stairs.perMin(120, 20)!!, 0.0)
        assertEquals(6.7, Stairs.perMin(100, 15)!!, 0.0)
        assertNull(Stairs.perMin(100, 0))
        assertNull(Stairs.perMin(0, 20))
    }

    @Test
    fun summaryCountsThisWeekAndAllTimeBests() {
        val s = Stairs.summary(
            listOf(
                Stairs.Session(day = 100, floors = 150, minutes = 30), // last week: best total
                Stairs.Session(day = 107, floors = 80, minutes = 10),  // this week: best pace (8.0)
                Stairs.Session(day = 108, floors = 0, minutes = 20),   // no floors logged: ignored
                Stairs.Session(day = 109, floors = 120, minutes = 20),
            ),
            weekStart = 107,
        )
        assertEquals(200, s.weekFloors)
        assertEquals(2, s.weekSessions)
        assertEquals(150, s.bestFloors)
        assertEquals(8.0, s.bestPerMin!!, 0.0)
    }

    @Test
    fun emptySummary() {
        val s = Stairs.summary(emptyList(), weekStart = 0)
        assertEquals(0, s.bestFloors)
        assertNull(s.bestPerMin)
    }
}
