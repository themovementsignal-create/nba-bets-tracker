package io.github.themovementsignal.training.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// All weights in kg, distances in metres, temperatures in °C.
// Dates that are "a day" are stored as epoch days (LocalDate.toEpochDay); moments as epoch millis.

/** Exercise types, stored as their name. */
object ExerciseType {
    const val WEIGHT_REPS = "WEIGHT_REPS"
    /** Bodyweight with added (+kg) or assisted (−kg) load. */
    const val BODYWEIGHT = "BODYWEIGHT"
    /** Timed hold in seconds, optional load. */
    const val TIMED = "TIMED"
    /** Load × distance (sled push, carries). */
    const val LOAD_DISTANCE = "LOAD_DISTANCE"

    val all = listOf(WEIGHT_REPS, BODYWEIGHT, TIMED, LOAD_DISTANCE)

    fun label(type: String) = when (type) {
        WEIGHT_REPS -> "Weight × reps"
        BODYWEIGHT -> "Bodyweight ± load"
        TIMED -> "Timed hold"
        LOAD_DISTANCE -> "Load × distance"
        else -> type
    }
}

/** Session types used for gear defaults, load and counting. */
object SessionType {
    const val STRENGTH = "STRENGTH"
    const val BASKETBALL = "BASKETBALL"
    const val CONDITIONING = "CONDITIONING"
    const val NEAT = "NEAT"
    const val SAUNA = "SAUNA"

    val all = listOf(STRENGTH, BASKETBALL, CONDITIONING, NEAT, SAUNA)

    fun label(type: String) = when (type) {
        STRENGTH -> "Strength"
        BASKETBALL -> "Basketball"
        CONDITIONING -> "Conditioning"
        NEAT -> "NEAT / treadmill"
        SAUNA -> "Sauna"
        else -> type
    }
}

@Entity(tableName = "exercise", indices = [Index(value = ["name"], unique = true)])
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    /** Comma-separated equipment tags, e.g. "barbell,bench". Empty = no equipment. */
    val equipment: String = "",
    /** Bar used by the plate calculator (bar id), or null if not a plated lift. */
    val barId: Long? = null,
    val restSeconds: Int = 120,
    val notes: String = "",
    val isCustom: Boolean = false,
    val archived: Boolean = false,
)

/** Ordered alternatives for an exercise (used for swaps and venue substitutions). */
@Entity(
    tableName = "exercise_alt",
    primaryKeys = ["exerciseId", "altExerciseId"],
    indices = [Index("altExerciseId")],
)
data class ExerciseAlt(
    val exerciseId: Long,
    val altExerciseId: Long,
    val priority: Int = 0,
)

object TemplateKind {
    const val STRENGTH = "STRENGTH"
    /** Opens the basketball / conditioning log instead of a workout. */
    const val ACTIVITY = "ACTIVITY"
}

@Entity(tableName = "template")
data class Template(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String = TemplateKind.STRENGTH,
    val sortOrder: Int = 0,
    val notes: String = "",
)

@Entity(tableName = "template_exercise", indices = [Index("templateId"), Index("exerciseId")])
data class TemplateExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val exerciseId: Long,
    val position: Int,
    val sets: Int = 3,
    /** Free text target, e.g. "5", "8-10", "30s", "20m". */
    val target: String = "",
)

@Entity(tableName = "workout", indices = [Index("startedAt")])
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long? = null,
    val name: String,
    val startedAt: Long,
    /** Null while the workout is in progress. */
    val endedAt: Long? = null,
    /** Session RPE 1–10, asked at the end. */
    val rpe: Int? = null,
    val notes: String = "",
    val venueId: Long? = null,
    /** Set when imported (e.g. "strong") so re-imports can skip duplicates. */
    val source: String = "",
)

@Entity(tableName = "workout_set", indices = [Index("workoutId"), Index("exerciseId")])
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    /** Order of the exercise within the workout. */
    val exerciseOrder: Int,
    /** Order of the set within the exercise, from 0. */
    val setIndex: Int,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val seconds: Int? = null,
    val distanceM: Double? = null,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    /** "" normal, "W" warm-up, "D" drop, "F" failure. */
    val kind: String = "",
    val target: String = "",
)

/** Basketball, conditioning and NEAT/treadmill sessions. */
@Entity(tableName = "activity", indices = [Index("startedAt")])
data class Activity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val startedAt: Long,
    val durationMin: Int,
    val rpe: Int? = null,
    val notes: String = "",
    /** For conditioning: what was done (Rower, Bike, Sled…). Added in schema v2. */
    @ColumnInfo(defaultValue = "") val kind: String = "",
)

/** Conditioning options shown in the dropdown. */
object ConditioningKind {
    val all = listOf("Rower", "Bike", "Sled", "Circuit", "Run", "Other")
}

@Entity(tableName = "sauna", indices = [Index("at")])
data class SaunaSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val rounds: Int,
    val minutesPerRound: Int,
    val tempC: Int,
    val coldContrast: Boolean,
    val coldMinutes: Int = 0,
    val notes: String = "",
)

@Entity(tableName = "gear")
data class Gear(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    /** Epoch day it went into use. */
    val startDay: Long,
    val lifespanSessions: Int? = null,
    val lifespanDays: Int? = null,
    /** Comma-separated SessionType values this gear is used for by default. */
    val defaultFor: String = "",
    val retired: Boolean = false,
    val notes: String = "",
)

@Entity(tableName = "gear_usage", indices = [Index("gearId"), Index(value = ["sessionType", "sessionId"])])
data class GearUsage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gearId: Long,
    val sessionType: String,
    val sessionId: Long,
    val at: Long,
)

@Entity(tableName = "sleep", indices = [Index("bedAt")])
data class Sleep(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bedAt: Long,
    val wakeAt: Long? = null,
    val quality: Int? = null,
    // ---- Added in schema v2: tracked nights (phone on the bed) ----
    @ColumnInfo(defaultValue = "0") val tracked: Boolean = false,
    val alarmAt: Long? = null,
    val score: Int? = null,
    val deepMin: Int? = null,
    val lightMin: Int? = null,
    val remMin: Int? = null,
    val awakeMin: Int? = null,
    val snoreMin: Int? = null,
)

/** One minute of a tracked night: movement from the accelerometer, loudness and snoring from the microphone. */
@Entity(tableName = "sleep_sample", indices = [Index("sleepId")])
data class SleepSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sleepId: Long,
    val at: Long,
    val movement: Float,
    val noiseDb: Float,
    val snoreSec: Int,
)

/** Steps per day from the phone's step counter. */
@Entity(tableName = "daily_steps")
data class DailySteps(
    @PrimaryKey val day: Long,
    val steps: Int,
)

@Entity(tableName = "checkin", indices = [Index(value = ["day"], unique = true)])
data class CheckIn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val sleep: Int,
    val soreness: Int,
    val energy: Int,
)

@Entity(tableName = "niggle", indices = [Index("at")])
data class Niggle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val region: String,
    val side: String,
    val severity: Int,
    val notes: String = "",
)

@Entity(tableName = "bodyweight", indices = [Index(value = ["day"], unique = true)])
data class BodyWeight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val kg: Double,
)

@Entity(tableName = "protein", indices = [Index("day")])
data class ProteinEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val at: Long,
    val grams: Int,
    val label: String = "",
)

@Entity(tableName = "protein_preset")
data class ProteinPreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val grams: Int,
    val sortOrder: Int = 0,
)

@Entity(tableName = "supplement")
data class Supplement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val active: Boolean = true,
)

@Entity(
    tableName = "supplement_log",
    primaryKeys = ["supplementId", "day"],
    indices = [Index("day")],
)
data class SupplementLog(
    val supplementId: Long,
    val day: Long,
)

/** Bars for the plate calculator. */
@Entity(tableName = "bar")
data class Bar(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val weightKg: Double,
)

/** A gym and the equipment it has. */
@Entity(tableName = "venue")
data class Venue(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Comma-separated equipment tags. */
    val equipment: String,
    val isTravel: Boolean = false,
)

@Entity(tableName = "jump_test", indices = [Index("at")])
data class JumpTest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val flightMs: Int,
    val heightCm: Double,
    val notes: String = "",
)

/** Simple key/value settings. */
@Entity(tableName = "setting")
data class Setting(
    @PrimaryKey val key: String,
    val value: String,
)
