package com.muir.bear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelTest {

    private val rules = listOf(
        FuelRuleIn("protein_each_meal", FuelKind.MANUAL),
        FuelRuleIn("fruit_two", FuelKind.COUNTER, target = 2),
        FuelRuleIn("veg_half_dinner", FuelKind.MANUAL),
        FuelRuleIn("chips_small_bag", FuelKind.DEFAULT_ON),
        FuelRuleIn("planned_snack", FuelKind.AUTO_SNACK),
        FuelRuleIn("shake_after_gym", FuelKind.AUTO_WORKOUT),
    )

    private fun manual(id: String, v: Int = 1) = FuelCheckIn(id, v, FuelSource.MANUAL)

    @Test
    fun restDayDropsTheShakeRule() {
        val day = FuelScore.forDay(rules, emptyList(), hadWorkout = false)
        assertEquals(5, day.applicable)
        assertEquals(1, day.hit) // chips are ticked unless unticked
    }

    @Test
    fun trainingDayCountsAllSix() {
        val checks = listOf(manual("protein_each_meal"), manual("fruit_two", 2), manual("veg_half_dinner"), FuelCheckIn("shake_after_gym", 1, FuelSource.AUTO))
        val day = FuelScore.forDay(rules, checks, hadWorkout = true)
        assertEquals(6, day.applicable)
        assertEquals(5, day.hit)
        assertTrue(day.goalMet)
    }

    @Test
    fun shakeTickedOnARestDayStillCounts() {
        val day = FuelScore.forDay(rules, listOf(manual("shake_after_gym")), hadWorkout = false)
        assertEquals(6, day.applicable)
    }

    @Test
    fun fruitCounterTicksAtTwo() {
        val fruit = rules[1]
        assertFalse(FuelScore.isHit(fruit, null))
        assertFalse(FuelScore.isHit(fruit, manual("fruit_two", 1)))
        assertTrue(FuelScore.isHit(fruit, manual("fruit_two", 2)))
    }

    @Test
    fun untickedChipsAreABrokenRule() {
        val chips = rules[3]
        assertTrue(FuelScore.isHit(chips, null))
        assertFalse(FuelScore.isHit(chips, manual("chips_small_bag", 0)))
    }

    @Test
    fun disabledRulesDontCount() {
        val day = FuelScore.forDay(rules.map { if (it.id == "veg_half_dinner") it.copy(enabled = false) else it }, emptyList(), hadWorkout = true)
        assertEquals(5, day.applicable)
    }

    @Test
    fun goalIsAllButOne() {
        assertTrue(FuelScore.Day(6, 5).goalMet)
        assertFalse(FuelScore.Day(6, 4).goalMet)
        assertTrue(FuelScore.Day(5, 4).goalMet)
        assertFalse(FuelScore.Day(0, 0).goalMet)
        assertEquals(2, FuelScore.goodDays(listOf(FuelScore.Day(6, 6), FuelScore.Day(6, 3), FuelScore.Day(5, 4))))
    }

    @Test
    fun autoTickNeverOverwritesAManualUntick() {
        assertNull(FuelAuto.onWorkoutFinished(manual("shake_after_gym", 0)))
        assertNull(FuelAuto.onSnackLogged(manual("planned_snack", 1)))
        val fresh = FuelAuto.onWorkoutFinished(null)
        assertNotNull(fresh)
        assertEquals(FuelSource.AUTO, fresh!!.source)
        assertEquals(1, fresh.value)
        // A second workout the same day just re-ticks.
        assertNotNull(FuelAuto.onWorkoutFinished(fresh))
    }

    @Test
    fun removingTheLastSnackClearsOnlyAnAutoTick() {
        assertTrue(FuelAuto.clearSnackTick(FuelCheckIn("planned_snack", 1, FuelSource.AUTO), snacksLeft = 0))
        assertFalse(FuelAuto.clearSnackTick(FuelCheckIn("planned_snack", 1, FuelSource.AUTO), snacksLeft = 1))
        assertFalse(FuelAuto.clearSnackTick(manual("planned_snack"), snacksLeft = 0))
    }

    private val items = (1L..6L).map { SnackPicker.Item(it) }

    @Test
    fun pickerNeverRepeatsYesterday() {
        for (seed in 0L until 500L) assertNotEquals(3L, SnackPicker.next(items, setOf(3L), seed)!!.id)
    }

    @Test
    fun pickerNeverPicksArchived() {
        val list = items.map { if (it.id % 2 == 0L) it.copy(archived = true) else it }
        for (seed in 0L until 500L) assertTrue(SnackPicker.next(list, emptySet(), seed)!!.id % 2 == 1L)
    }

    @Test
    fun pickerIsDeterministicAndFavouritesComeUpMore() {
        assertEquals(SnackPicker.next(items, emptySet(), 42)?.id, SnackPicker.next(items, emptySet(), 42)?.id)
        val list = listOf(SnackPicker.Item(1, favourite = true), SnackPicker.Item(2))
        val ones = (0L until 3000L).count { SnackPicker.next(list, emptySet(), it)!!.id == 1L }
        assertTrue("favourite picked $ones of 3000", ones in 1800..2200) // about 2 in 3
    }

    @Test
    fun pickerFallsBackWhenOnlyYesterdaysIsLeft() {
        assertEquals(1L, SnackPicker.next(listOf(SnackPicker.Item(1)), setOf(1L), 7)!!.id)
        assertNull(SnackPicker.next(listOf(SnackPicker.Item(1, archived = true)), emptySet(), 7))
    }
}

class SnackPromptTest {
    private val fri = java.time.LocalDateTime.of(2026, 11, 20, 10, 0) // a Friday

    @Test
    fun laterTodayOnAWeekday() {
        assertEquals(fri.withHour(15).withMinute(30), SnackPrompt.next(fri, 930, 600, doneToday = false))
    }

    @Test
    fun doneTodayMovesToTomorrowsWeekendTime() {
        assertEquals(java.time.LocalDateTime.of(2026, 11, 21, 10, 0), SnackPrompt.next(fri, 930, 600, doneToday = true))
    }

    @Test
    fun afterTodaysTimeGoesToTheNextDay() {
        val sun = java.time.LocalDateTime.of(2026, 11, 22, 16, 0)
        assertEquals(java.time.LocalDateTime.of(2026, 11, 23, 15, 30), SnackPrompt.next(sun, 930, 600, doneToday = false))
    }

    @Test
    fun copy() {
        assertEquals("Afternoon snack: Biltong, 50 g. Log it?", SnackPrompt.text("Biltong", "50 g"))
        assertEquals(930, SnackPrompt.parse(null))
        assertEquals(600, SnackPrompt.parse("600"))
        assertEquals("15:30", SnackPrompt.fmt(930))
    }
}
