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
 * Builds a database exactly as schema v1 (Builds 8–20) created it, from the committed schema JSON,
 * fills it with data, then opens it with the current app database. Room runs the migration and
 * validates every table against the current entities; we then check the old data survived.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @Test
    fun upgradeFromV1KeepsData() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val name = "migration-test.db"
        target.deleteDatabase(name)

        // 1. Recreate the v1 database from the exported schema.
        val json = instrumentation.context.assets
            .open("com.muir.bear.data.AppDatabase/1.json")
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
            db.version = 1

            // 2. Data as a real user would have it.
            db.execSQL("INSERT INTO activity (id, type, startedAt, durationMin, rpe, notes) VALUES (1, 'BASKETBALL', 1000, 60, 7, 'hoops')")
            db.execSQL("INSERT INTO sleep (id, bedAt, wakeAt, quality) VALUES (1, 1000, 2000, 4)")
            db.execSQL("INSERT INTO workout (id, name, startedAt, endedAt, rpe, notes, source) VALUES (1, 'Session 1', 1000, 5000, 8, '', '')")
            db.execSQL("INSERT INTO setting (`key`, value) VALUES ('seeded_v1', '1')")
        }

        // 3. Open with the current database: migration + Room's schema validation run here.
        val db = Room.databaseBuilder(target, AppDatabase::class.java, name).build()
        try {
            runBlocking {
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
        } finally {
            db.close()
            target.deleteDatabase(name)
        }
    }
}
