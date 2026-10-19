package com.muir.bear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateImportTest {
    @Test fun json() {
        val r = TemplateImport.parse(
            """
            {"templates": [
              {"name": "Session 3 · Full Body", "notes": "Week 1–4", "exercises": [
                {"name": "Back Squat", "sets": 4, "reps": "5", "rest": "3:00"},
                {"exercise": "Plank", "sets": "3", "target": "45s", "rest": 60, "type": "hold"},
                "Face Pull"
              ]},
              {"name": "", "exercises": ["x"]}
            ]}
            """.trimIndent(),
        )
        assertEquals(1, r.templates.size)
        val t = r.templates[0]
        assertEquals("Session 3 · Full Body", t.name)
        assertEquals("Week 1–4", t.notes)
        assertEquals(TemplateImport.Item("Back Squat", 4, "5", 180, null), t.items[0])
        assertEquals(TemplateImport.Item("Plank", 3, "45s", 60, TemplateImport.TIMED), t.items[1])
        assertEquals(TemplateImport.Item("Face Pull", 3, "", null, null), t.items[2])
        assertEquals(1, r.problems.size) // the nameless template
    }

    @Test fun jsonBareListAndSingle() {
        assertEquals(2, TemplateImport.parse("""[{"name":"A","exercises":["Row"]},{"title":"B","items":["Dip"]}]""").templates.size)
        assertEquals("C", TemplateImport.parse("""{"name":"C","exercises":[{"name":"Curl","sets":"3x10-12"}]}""").templates.single().name)
        assertEquals("10-12", TemplateImport.parse("""{"name":"C","exercises":[{"name":"Curl","sets":"3x10-12"}]}""").templates.single().items.single().target)
    }

    @Test fun badJsonSaysWhy() {
        val r = TemplateImport.parse("""{"templates": [ {"name": "A", ]}""")
        assertTrue(r.templates.isEmpty())
        assertTrue(r.problems.single().startsWith("Not valid JSON"))
    }

    @Test fun csv() {
        val r = TemplateImport.parse(
            """
            Template,Exercise,Sets,Reps,Rest,Type,Notes
            Session 3 · Full Body,Back Squat,4,5,3:00,,Heavy day
            ,"Dumbbell Press, Incline",3x8-10,,90,,
            ,Farmer Carry,3,20m,2m,distance,
            Session 4,Chin-up,3,6-8,,bodyweight,
            """.trimIndent(),
        )
        assertEquals(listOf("Session 3 · Full Body", "Session 4"), r.templates.map { it.name })
        val a = r.templates[0]
        assertEquals("Heavy day", a.notes)
        assertEquals(TemplateImport.Item("Back Squat", 4, "5", 180, null), a.items[0])
        assertEquals(TemplateImport.Item("Dumbbell Press, Incline", 3, "8-10", 90, null), a.items[1])
        assertEquals(TemplateImport.Item("Farmer Carry", 3, "20m", 120, TemplateImport.LOAD_DISTANCE), a.items[2])
        assertEquals(TemplateImport.BODYWEIGHT, r.templates[1].items.single().type)
        assertTrue(r.problems.isEmpty())
    }

    @Test fun csvNeedsHeader() {
        val r = TemplateImport.parse("Back Squat,4,5\n")
        assertTrue(r.templates.isEmpty())
        assertTrue(r.problems.single().contains("template"))
    }

    @Test fun fields() {
        assertEquals(90, TemplateImport.restSeconds("90s"))
        assertEquals(150, TemplateImport.restSeconds("2:30"))
        assertEquals(90, TemplateImport.restSeconds("1.5 min"))
        assertEquals(null, TemplateImport.restSeconds("soon"))
        assertEquals(5 to "", TemplateImport.setsAndTarget("5"))
        assertEquals(3 to "", TemplateImport.setsAndTarget(""))
        assertEquals(4 to "6", TemplateImport.setsAndTarget("4 × 6"))
    }

    @Test fun programWithRoutines() {
        val r = TemplateImport.parse(
            """
            {"format": "bear-program/1",
             "program": {"name": "Summer Block", "weeks": 12},
             "routines": [
              {"name": "A · Lower + Power", "day": "Saturday", "duration_min": 60, "exercises": [
                {"order": 2, "name": "Trap Bar Deadlift", "type": "reps", "sets": 3, "reps_min": 5, "reps_max": 5,
                 "per_side": false, "load_kg": 100, "rest_s": 150, "notes": "Cap at RPE 7.", "progression": "+5 kg a week."},
                {"order": 1, "name": "Supported Hip Airplane", "type": "reps", "sets": 2, "reps_min": 4, "reps_max": 4,
                 "per_side": true, "load_kg": null, "rest_s": 30, "warmup": true, "notes": "Left glute soft."},
                {"order": 3, "name": "Copenhagen Plank", "type": "time", "sets": 3, "seconds": 20, "per_side": true, "rest_s": 45},
                {"order": 4, "name": "Suitcase Carry", "type": "distance", "sets": 3, "metres": 30, "per_side": true, "rest_s": 60},
                {"order": 5, "name": "Hamstring Curl", "type": "reps", "sets": 3, "reps_min": 10, "reps_max": 12, "rest_s": 60}
              ]}
             ]}
            """.trimIndent(),
        )
        assertTrue(r.problems.toString(), r.problems.isEmpty())
        val t = r.templates.single()
        assertEquals("A · Lower + Power", t.name)
        assertEquals("Summer Block · Saturday · ~60 min", t.notes)
        assertEquals(listOf("Supported Hip Airplane", "Trap Bar Deadlift", "Copenhagen Plank", "Suitcase Carry", "Hamstring Curl"), t.items.map { it.exercise })
        assertEquals(TemplateImport.Item("Supported Hip Airplane", 2, "4/side", 30, null, "Warm-up. Left glute soft."), t.items[0])
        assertEquals(TemplateImport.Item("Trap Bar Deadlift", 3, "5 · 100 kg", 150, null, "Cap at RPE 7. Progression: +5 kg a week."), t.items[1])
        assertEquals(TemplateImport.Item("Copenhagen Plank", 3, "20s/side", 45, TemplateImport.TIMED), t.items[2])
        assertEquals(TemplateImport.Item("Suitcase Carry", 3, "30m/side", 60, TemplateImport.LOAD_DISTANCE), t.items[3])
        assertEquals("10-12", t.items[4].target)
    }
}
