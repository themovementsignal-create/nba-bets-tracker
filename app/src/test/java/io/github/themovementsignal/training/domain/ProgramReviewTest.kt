package io.github.themovementsignal.training.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramReviewTest {
    @Test fun noBlockYet() = assertNull(ProgramReview.status(null, null, 100, null, null))

    @Test fun dueAfterSixWeeksByDefault() {
        assertFalse(ProgramReview.status(100, null, 141, null, null)!!.due)
        val s = ProgramReview.status(100, null, 142, null, null)!!
        assertTrue(s.due)
        assertEquals(6, s.weeksDone)
    }

    @Test fun fallsBackToFirstWorkout() {
        val s = ProgramReview.status(null, 10, 80, 8, null)!!
        assertEquals(10, s.startDay)
        assertTrue(s.due)
    }

    @Test fun customPeriodAndSnooze() {
        assertFalse(ProgramReview.status(0, null, 40, 8, null)!!.due)
        assertFalse(ProgramReview.status(0, null, 60, 8, 63)!!.due)
        assertTrue(ProgramReview.status(0, null, 63, 8, 63)!!.due)
    }
}
