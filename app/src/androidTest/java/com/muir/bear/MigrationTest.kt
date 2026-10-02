package com.muir.bear

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.muir.bear.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Builds a database exactly as an older schema created it, from the committed schema JSON, fills
 * it with data, then opens it with the current app database. Room runs the migrations and
 * validates every table against the current entities; we then check the old data survived.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext

    /** Creates [name] as schema [version] had it, then runs [fill] on it. */
    private fun createOld(name: String, version: Int, fill: (SQLiteDatabase) -> Unit) {
        target.deleteDatabase(name)
        val json = instrumentation.context.assets
            .open("com.muir.bear.data.AppDatabase/$version.json")
            .bufferedReader().use { it.readText() }
        val schema = JSONObject(json).getJSONObject("database")
        val file = target.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = e.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = version
            fill(db)
        }
    }

    /** Opens [name] with the current database (migrations + schema validation run here). */
    private fun <T> openCurrent(name: String, check: suspend (AppDatabase) -> T) {
        val db = Room.databaseBuilder(target, AppDatabase::class.java, name).build()
        try {
            runBlocking { check(db) }
        } finally {
            db.close()
            target.deleteDatabase(name)
        }
    }

    @Test
    fun upgradeFromV1KeepsData() {
        val name = "migration-test-v1.db"
        createOld(name, 1) { db ->
            db.execSQL("INSERT INTO activity (id, type, startedAt, durationMin, rpe, notes) VALUES (1, 'BASKETBALL', 1000, 60, 7, 'hoops')")
            db.execSQL("INSERT INTO sleep (id, bedAt, wakeAt, quality) VALUES (1, 1000, 2000, 4)")
            db.execSQL("INSERT INTO workout (id, name, startedAt, endedAt, rpe, notes, source) VALUES (1, 'Session 1', 1000, 5000, 8, '', '')")
            db.execSQL("INSERT INTO setting (`key`, value) VALUES ('seeded_v1', '1')")
        }
        openCurrent(name) { db ->
            val dao = db.dao()
            val activity = dao.allActivities().single()
            assertEquals("BASKETBALL", activity.type)
            assertEquals("hoops", activity.notes)
            assertEquals("", activity.kind)
            val sleep = dao.allSleeps().single()
            assertEquals(4, sleep.quality)
            assertEquals(false, sleep.tracked)
            assertNull(sleep.score)
            assertEquals(1, dao.allWorkouts().size)
            assertEquals(0, dao.sleepSamples(1).size)
            assertNull(dao.stepsOn(0))
        }
    }

    @Test
    fun upgradeFromV2KeepsData() {
        val name = "migration-test-v2.db"
        createOld(name, 2) { db ->
            db.execSQL("INSERT INTO exercise (id, name, type, equipment, barId, restSeconds, notes, isCustom, archived) VALUES (1, 'Trap Bar Deadlift', 'WEIGHT_REPS', 'trap_bar', NULL, 180, '', 0, 0)")
            db.execSQL("INSERT INTO workout (id, name, startedAt, endedAt, rpe, notes, source) VALUES (1, 'Session 1', 1000, 5000, 8, '', '')")
            db.execSQL("INSERT INTO workout_set (id, workoutId, exerciseId, exerciseOrder, setIndex, weightKg, reps, completed, completedAt, kind, target) VALUES (1, 1, 1, 0, 0, 140.0, 5, 1, 2000, '', '5')")
            db.execSQL("INSERT INTO activity (id, type, startedAt, durationMin, rpe, notes, kind) VALUES (1, 'CONDITIONING', 1000, 30, 7, '', 'Rower')")
        }
        openCurrent(name) { db ->
            val dao = db.dao()
            val e = dao.allExercises().single()
            assertEquals("Trap Bar Deadlift", e.name)
            assertEquals("", e.muscles)
            assertEquals("", e.secondaryMuscles)
            val set = dao.allSets().single()
            assertEquals(140.0, set.weightKg!!, 0.0)
            assertEquals(5, set.reps)
            assertNull(set.rpe)
            assertEquals("Rower", dao.allActivities().single().kind)
        }
    }
}
