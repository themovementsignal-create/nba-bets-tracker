package com.muir.bear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalcTest {

    @Test fun epley() {
        assertEquals(0.0, Calc.epley(100.0, 0), 1e-9)
        assertEquals(100.0, Calc.epley(100.0, 1), 1e-9)
        assertEquals(116.6667, Calc.epley(100.0, 5), 1e-3)
    }

    @Test fun platesExact() {
        val r = Calc.plates(140.0, 20.0, listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25))
        assertEquals(listOf(25.0, 25.0, 10.0), r.perSide)
        assertEquals(140.0, r.achieved, 1e-9)
        assertEquals(0.0, r.remainder, 1e-9)
    }

    @Test fun platesTrapBarAndRemainder() {
        val r = Calc.plates(101.0, 25.0, listOf(20.0, 10.0, 5.0, 2.5, 1.25))
        assertEquals(listOf(20.0, 10.0, 5.0, 2.5), r.perSide)
        assertEquals(100.0, r.achieved, 1e-9)
        assertEquals(1.0, r.remainder, 1e-9)
    }

    @Test fun platesBelowBar() {
        val r = Calc.plates(15.0, 20.0, listOf(20.0))
        assertTrue(r.perSide.isEmpty())
    }

    @Test fun parseNumber() {
        assertEquals(102.5, Calc.parseNumber("102,5")!!, 1e-9)
        assertEquals(1000.5, Calc.parseNumber("1,000.5")!!, 1e-9)
        assertEquals(null, Calc.parseNumber(" "))
    }

    @Test fun fmt() {
        assertEquals("100", Calc.fmt(100.0))
        assertEquals("102.5", Calc.fmt(102.5))
    }

    @Test fun weeklySpike() {
        val today = LocalDate.of(2026, 10, 1) // Thursday
        val monday = Calc.mondayOf(today)
        val items = mutableListOf<Pair<Long, Double>>()
        for (w in 1..6) items += monday.minusWeeks(w.toLong()).toEpochDay() to 1000.0
        items += monday.toEpochDay() to 2000.0
        val weeks = Calc.weekly(items, today, weeks = 4)
        assertEquals(4, weeks.size)
        assertEquals(monday, weeks.last().start)
        assertTrue(weeks.last().spike)
        assertFalse(weeks[weeks.size - 2].spike)
    }

    @Test fun rollingAverage() {
        val pts = listOf(1L to 100.0, 2L to 98.0, 10L to 96.0)
        val avg = Calc.rollingAverage(pts)
        assertEquals(99.0, avg[1].second, 1e-9)
        assertEquals(96.0, avg[2].second, 1e-9)
    }

    @Test fun jumpHeight() {
        // 0.5 s flight ≈ 30.6 cm
        assertEquals(30.65, Calc.jumpHeightCm(0.5), 0.05)
    }

    @Test fun substitute() {
        data class E(val name: String, val eq: String)
        val trap = E("Trap", "trap_bar")
        val rdl = E("RDL", "barbell")
        val db = E("DB RDL", "dumbbell")
        val hotel = setOf("dumbbell", "bench")
        assertEquals(db, Calc.substitute(trap, listOf(rdl, db), { it.eq }, hotel))
        assertEquals(trap, Calc.substitute(trap, listOf(rdl), { it.eq }, setOf("trap_bar")))
        assertEquals(trap, Calc.substitute(trap, listOf(rdl), { it.eq }, emptySet()))
    }
}
