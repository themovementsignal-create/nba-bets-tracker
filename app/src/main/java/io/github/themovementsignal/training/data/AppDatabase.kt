package io.github.themovementsignal.training.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The local database. DATA SAFETY: every change to the entities must bump [version] and add a
 * real Migration. Never use fallbackToDestructiveMigration.
 */
@Database(
    entities = [
        Exercise::class, ExerciseAlt::class, Template::class, TemplateExercise::class,
        Workout::class, WorkoutSet::class, Activity::class, SaunaSession::class,
        Gear::class, GearUsage::class, Sleep::class, CheckIn::class, Niggle::class,
        BodyWeight::class, ProteinEntry::class, ProteinPreset::class, Supplement::class,
        SupplementLog::class, Bar::class, Venue::class, JumpTest::class, Setting::class,
        SleepSample::class, DailySteps::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // v2: conditioning kind, tracked-sleep summary columns, sleep samples, daily steps (additions only).
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TrainingDao

    companion object {
        const val NAME = "training.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                // Add migrations here, e.g. .addMigrations(MIGRATION_1_2)
                .build()
    }
}
