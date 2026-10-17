package com.muir.bear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class SuppRemindersTest {
    @Test fun roundTripAndIgnoresJunk() {
        val list = SuppReminders.parse("1@480, 2@1260,bad,3@9999,1@500")
        assertEquals(listOf(SuppReminders.Daily(1, 480), SuppReminders.Daily(2, 1260)), list)
        assertEquals("1@480,2@1260", SuppReminders.serialize(list))
        assertEquals(emptyList<SuppReminders.Daily>(), SuppReminders.parse(null))
    }

    @Test fun setAndRemove() {
        var list = SuppReminders.set(emptyList(), 5, 480)
        list = SuppReminders.set(list, 5, 540)
        assertEquals(540, SuppReminders.timeOf(list, 5))
        list = SuppReminders.set(list, 5, null)
        assertNull(SuppReminders.timeOf(list, 5))
    }

    @Test fun nextIsLaterTodayOrTomorrow() {
        val list = listOf(SuppReminders.Daily(1, 8 * 60), SuppReminders.Daily(2, 21 * 60))
        val morning = LocalDateTime.of(2026, 10, 17, 7, 0)
        assertEquals(LocalDateTime.of(2026, 10, 17, 8, 0), SuppReminders.next(list, morning))
        // Exactly at 08:00 the 08:00 one has just fired: next is 21:00.
        assertEquals(LocalDateTime.of(2026, 10, 17, 21, 0), SuppReminders.next(list, LocalDateTime.of(2026, 10, 17, 8, 0)))
        assertEquals(LocalDateTime.of(2026, 10, 18, 8, 0), SuppReminders.next(list, LocalDateTime.of(2026, 10, 17, 22, 0)))
        assertNull(SuppReminders.next(emptyList(), morning))
    }

    @Test fun dueGroupsSameTime() {
        val list = listOf(SuppReminders.Daily(1, 480), SuppReminders.Daily(2, 480), SuppReminders.Daily(3, 1260))
        assertEquals(listOf(1L, 2L), SuppReminders.dueAt(list, 480))
        assertEquals("08:00", SuppReminders.fmt(480))
    }
}
