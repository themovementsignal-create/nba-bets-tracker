package com.muir.bear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class StrongCsvTest {

    private val standard = listOf(
        "Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Reps,Distance,Seconds,Notes,Workout Notes,RPE",
        "2024-03-04 07:30:00,\"Session 1, Lower\",1h 5m,Deadlift (Trap bar),1,120,5,0,0,,,",
        "2024-03-04 07:30:00,\"Session 1, Lower\",1h 5m,Deadlift (Trap bar),2,120,5,0,0,\"felt \"\"fast\"\"\",,8",
        "2024-03-04 07:30:00,\"Session 1, Lower\",1h 5m,Sled Push,1,80,0,0.02,0,,,",
        "2024-03-04 07:30:00,\"Session 1, Lower\",1h 5m,Dead Hang,1,0,0,0,45,,,",
        "2024-03-06 18:00:00,Upper,45m,Chin Up,W,0,5,0,0,,\"multi\nline note\",",
        "2024-03-06 18:00:00,Upper,45m,Chin Up,Rest Timer,0,0,0,90,,,",
    ).joinToString("\n")

    @Test fun parsesStandardExport() {
        val r = StrongCsv.parse(standard)
        assertEquals(2, r.workouts.size)
        val lower = r.workouts[0]
        assertEquals("Session 1, Lower", lower.name)
        assertEquals(65, lower.durationMin)
        assertEquals(LocalDateTime.of(2024, 3, 4, 7, 30), lower.start)
        assertEquals(4, lower.rows.size)
        assertEquals("felt \"fast\"", lower.rows[1].notes)
        assertEquals(8.0, lower.rows[1].rpe!!, 1e-9)
        assertEquals(20.0, lower.rows[2].distanceM!!, 1e-9)
        assertEquals(45, lower.rows[3].seconds)
        val upper = r.workouts[1]
        assertEquals(1, upper.rows.size)
        assertEquals("W", upper.rows[0].kind)
        assertEquals("multi\nline note", upper.notes)
        assertEquals(1, r.skippedRows)
    }

    @Test fun semicolonLbsAndBom() {
        val csv = "﻿date;workout name;exercise name;set order;weight;weight unit;reps\r\n" +
            "04/03/2024 07:30;Legs;Squat;1;225;lbs;5\r\n" +
            "04/03/2024 07:30;Legs;Squat;2;102,5;kg;5\r\n"
        val r = StrongCsv.parse(csv)
        assertEquals(1, r.workouts.size)
        val rows = r.workouts[0].rows
        assertEquals(102.058, rows[0].weightKg!!, 1e-3)
        assertEquals(102.5, rows[1].weightKg!!, 1e-9)
        assertEquals(LocalDateTime.of(2024, 3, 4, 7, 30), r.workouts[0].start)
    }

    @Test fun newerStrongExportWithWorkoutNumberAndSeconds() {
        val csv = listOf(
            "Workout #;Date;Workout Name;Duration (sec);Exercise Name;Set Order;Weight (kg);Reps;RPE;Distance (meters);Seconds;Notes;Workout Notes",
            "12;2025-06-01 18:00:00;Upper;3720;Chin Up;1;10;6;;;;;",
            "12;2025-06-01 18:00:00;Upper;3720;Sled Push;1;80;;;20;;;",
            "12;2025-06-01 18:00:00;Upper;3720;Chin Up;Rest Timer;;;;;90;;",
        ).joinToString("\n")
        val r = StrongCsv.parse(csv)
        assertEquals(1, r.workouts.size)
        val w = r.workouts[0]
        assertEquals("Upper", w.name)
        assertEquals(62, w.durationMin)
        assertEquals(2, w.rows.size)
        assertEquals(10.0, w.rows[0].weightKg!!, 1e-9)
        assertEquals(20.0, w.rows[1].distanceM!!, 1e-9)
        assertEquals(1, r.skippedRows)
    }

    @Test fun missingColumns() {
        val r = StrongCsv.parse("foo,bar\n1,2\n")
        assertEquals(0, r.workouts.size)
        assertEquals(2, r.problems.size)
    }

    @Test fun durations() {
        assertEquals(65, StrongCsv.parseDurationMinutes("1h 5m"))
        assertEquals(45, StrongCsv.parseDurationMinutes("45m"))
        assertEquals(60, StrongCsv.parseDurationMinutes("1h"))
        assertEquals(65, StrongCsv.parseDurationMinutes("01:05:00"))
        assertEquals(30, StrongCsv.parseDurationMinutes("30"))
        assertNull(StrongCsv.parseDurationMinutes(""))
    }

    @Test fun dates() {
        assertNotNull(StrongCsv.parseDate("2024-03-04 07:30:00"))
        assertNotNull(StrongCsv.parseDate("3/4/2024 7:30 AM"))
        assertNotNull(StrongCsv.parseDate("2024-03-04T07:30:00.000Z"))
        assertNotNull(StrongCsv.parseDate("2024-03-04"))
        assertNull(StrongCsv.parseDate("yesterday"))
    }
}
