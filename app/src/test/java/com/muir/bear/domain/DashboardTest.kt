package com.muir.bear.domain

import com.muir.bear.domain.Dashboard.Item
import com.muir.bear.domain.Dashboard.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardTest {
    @Test fun defaultsAndRoundTrip() {
        assertEquals(Dashboard.default, Dashboard.parse(null))
        val custom = Dashboard.move(Dashboard.default, 2, -2).map { if (it.item == Item.GEAR) it.copy(shown = false) else it }
        assertEquals(custom, Dashboard.parse(Dashboard.serialize(custom)))
        assertEquals(Item.TICKS, custom.first().item)
        assertFalse(custom.first { it.item == Item.GEAR }.shown)
    }

    @Test fun newItemsAppearAndJunkIsIgnored() {
        val e = Dashboard.parse("sleep,-readiness,nonsense,sleep")
        assertEquals(Item.SLEEP, e[0].item)
        assertEquals(Dashboard.Entry(Item.READINESS, false), e[1])
        assertEquals(Item.entries.size, e.size) // everything else appended, shown
        assertEquals(true, e.last().shown)
    }

    @Test fun moveStaysInBounds() {
        assertEquals(Dashboard.default, Dashboard.move(Dashboard.default, 0, -1))
        assertEquals(Dashboard.default, Dashboard.move(Dashboard.default, Dashboard.default.lastIndex, 1))
    }

    @Test fun ticksDefaultWithoutProtein() {
        assertEquals(Tick.entries.toSet() - Tick.PROTEIN, Dashboard.parseTicks(null))
        assertEquals(emptySet<Tick>(), Dashboard.parseTicks(""))
        assertEquals(setOf(Tick.SAUNA, Tick.PROTEIN), Dashboard.parseTicks(Dashboard.serializeTicks(setOf(Tick.PROTEIN, Tick.SAUNA))))
    }

    @Test fun rotation() {
        val t = listOf(1L, 2L, 3L)
        assertEquals(1L, Dashboard.nextTemplate(t, emptyList()))
        assertEquals(2L, Dashboard.nextTemplate(t, listOf(1L to 100L)))
        assertEquals(1L, Dashboard.nextTemplate(t, listOf(1L to 100L, 3L to 200L))) // wraps
        assertEquals(1L, Dashboard.nextTemplate(t, listOf(9L to 500L))) // deleted template ignored
        assertNull(Dashboard.nextTemplate(emptyList(), emptyList()))
    }
}
