package io.github.themovementsignal.training

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.themovementsignal.training.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Builds a database exactly as Build 8–20 created it (schema v1), fills it with data, upgrades it to
 * the current schema and checks that nothing was lost.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate1To2KeepsData() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO activity (id, type, startedAt, durationMin, rpe, notes) VALUES (1, 'BASKETBALL', 1000, 60, 7, 'hoops')")
            execSQL("INSERT INTO sleep (id, bedAt, wakeAt, quality) VALUES (1, 1000, 2000, 4)")
            execSQL("INSERT INTO workout (id, name, startedAt, endedAt, notes, source) VALUES (1, 'Session 1', 1000, 5000, '', '')")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 2, true)
        db.query("SELECT type, notes, kind FROM activity WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("BASKETBALL", c.getString(0))
            assertEquals("hoops", c.getString(1))
            assertEquals("", c.getString(2))
        }
        db.query("SELECT quality, tracked, score FROM sleep WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(4, c.getInt(0))
            assertEquals(0, c.getInt(1))
            assertTrue(c.isNull(2))
        }
        db.query("SELECT COUNT(*) FROM workout").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM sleep_sample").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM daily_steps").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        db.close()
    }
}
