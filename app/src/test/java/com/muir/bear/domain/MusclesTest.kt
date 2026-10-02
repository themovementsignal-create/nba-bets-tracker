package com.muir.bear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusclesTest {
    private val db = listOf(
        Triple("Barbell Deadlift", listOf("lower back"), listOf("glutes", "hamstrings")),
        Triple("Dumbbell Bicep Curl", listOf("biceps"), listOf("forearms")),
        Triple("Pullups", listOf("lats"), listOf("biceps", "middle back")),
        Triple("Barbell Hip Thrust", listOf("glutes"), listOf("hamstrings")),
    )

    @Test fun seededNamesWin() {
        assertEquals(listOf("quadriceps", "glutes", "hamstrings"), Muscles.lookup("Trap Bar Deadlift", db)!!.primary)
        assertEquals(listOf("hamstrings"), Muscles.lookup("nordic curl", db)!!.primary)
    }

    @Test fun matchesTheDatabaseLoosely() {
        assertEquals(listOf("biceps"), Muscles.lookup("DB Bicep Curls", db)!!.primary)
        assertEquals(listOf("lats"), Muscles.lookup("Pull-ups", db)!!.primary)
    }

    @Test fun unknownStaysUnknown() {
        assertNull(Muscles.lookup("Turkish Get-Up", db))
        assertNull(Muscles.lookup("", db))
    }
}
