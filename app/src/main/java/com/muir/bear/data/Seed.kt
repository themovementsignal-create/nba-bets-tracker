package com.muir.bear.data

import androidx.room.withTransaction
import com.muir.bear.data.ExerciseType.BODYWEIGHT
import com.muir.bear.data.ExerciseType.LOAD_DISTANCE
import com.muir.bear.data.ExerciseType.TIMED
import com.muir.bear.data.ExerciseType.WEIGHT_REPS
import java.time.LocalDate

object Equipment {
    const val BARBELL = "barbell"
    const val TRAP_BAR = "trap_bar"
    const val DUMBBELL = "dumbbell"
    const val KETTLEBELL = "kettlebell"
    const val BENCH = "bench"
    const val CABLE = "cable"
    const val MACHINE = "machine"
    const val SLED = "sled"
    const val PULLUP_BAR = "pullup_bar"
    const val BAND = "band"

    val all = listOf(BARBELL, TRAP_BAR, DUMBBELL, KETTLEBELL, BENCH, CABLE, MACHINE, SLED, PULLUP_BAR, BAND)

    fun label(tag: String) = when (tag) {
        TRAP_BAR -> "Trap bar"
        PULLUP_BAR -> "Pull-up bar"
        else -> tag.replaceFirstChar { it.uppercase() }
    }
}

object Settings {
    const val SEEDED = "seeded_v1"
    const val PLATES = "plates"
    const val ACTIVE_VENUE = "active_venue"
    const val NEAT_TIMER_START = "neat_timer_start"
    const val BODYWEIGHT_TARGET = "bodyweight_target"
    const val PROTEIN_MIN = "protein_min"
    const val PROTEIN_MAX = "protein_max"
    const val MONTHLY_SESSION_TARGET = "monthly_session_target"
    const val NEAT_WEEKLY_SESSIONS = "neat_weekly_sessions"
    const val NEAT_SESSION_MIN = "neat_session_min"
    const val ALARM_TIME = "alarm_time"
    const val ALARM_ON = "alarm_on"
    const val ALARM_WINDOW = "alarm_window"
    const val SNORE_ON = "snore_on"
    const val ALARM_SOUND = "alarm_sound"
    /** Hours of sleep you need, for sleep debt. */
    const val SLEEP_NEED = "sleep_need"
    /** Today layout: order and on/off of cards (see domain/Dashboard). */
    const val DASH_LAYOUT = "dash_layout"
    const val DASH_TICKS = "dash_ticks"
    /** Exercise ids for the Key lifts chart; empty = your two most-trained. */
    const val DASH_LIFTS = "dash_lifts"
    /** Epoch day the current training block (program) started; unset = first workout. */
    const val PROGRAM_START = "program_start"
    const val PROGRAM_REVIEW_WEEKS = "program_review_weeks"
    /** Epoch day before which the review reminder stays hidden ("remind me next week"). */
    const val PROGRAM_REVIEW_SNOOZE = "program_review_snooze"

    const val DEFAULT_PLATES = "25,20,15,10,5,2.5,1.25"
}

private data class SeedExercise(
    val name: String,
    val type: String,
    val equipment: String = "",
    val bar: String? = null,
    val rest: Int = 120,
    val alts: List<String> = emptyList(),
)

private val seedExercises = listOf(
    // Session 1: Athletic Lower
    SeedExercise("Trap Bar Deadlift", WEIGHT_REPS, Equipment.TRAP_BAR, bar = "Trap bar", rest = 180,
        alts = listOf("Romanian Deadlift", "DB Romanian Deadlift")),
    SeedExercise("Bulgarian Split Squat", WEIGHT_REPS, "${Equipment.DUMBBELL},${Equipment.BENCH}", rest = 90,
        alts = listOf("Goblet Squat", "Reverse Lunge")),
    SeedExercise("Goblet Squat", WEIGHT_REPS, Equipment.DUMBBELL, rest = 90),
    SeedExercise("Sled Push", LOAD_DISTANCE, Equipment.SLED, rest = 120,
        alts = listOf("DB Walking Lunge", "Farmer Carry")),
    SeedExercise("Step-Down Isometric", TIMED, rest = 60),
    SeedExercise("Seated Calf Raise", WEIGHT_REPS, Equipment.MACHINE, rest = 60,
        alts = listOf("Single-Leg Calf Raise")),
    // Session 2: Push/Pull Upper
    SeedExercise("Dead Hang", TIMED, Equipment.PULLUP_BAR, rest = 60, alts = listOf("Farmer Hold")),
    SeedExercise("Chin-Up", BODYWEIGHT, Equipment.PULLUP_BAR, rest = 120,
        alts = listOf("Lat Pulldown", "DB Row")),
    SeedExercise("Horizontal Press", WEIGHT_REPS, "${Equipment.DUMBBELL},${Equipment.BENCH}", rest = 120,
        alts = listOf("Bench Press", "Push-Up")),
    SeedExercise("Row", WEIGHT_REPS, Equipment.CABLE, rest = 90, alts = listOf("DB Row")),
    SeedExercise("Overhead Press", WEIGHT_REPS, Equipment.DUMBBELL, rest = 120, alts = listOf("Pike Push-Up")),
    SeedExercise("Face Pull", WEIGHT_REPS, Equipment.CABLE, rest = 60, alts = listOf("Band Face Pull", "Reverse Fly")),
    SeedExercise("Cable Crossover", WEIGHT_REPS, Equipment.CABLE, rest = 60, alts = listOf("DB Fly", "Push-Up")),
    // Alternatives and general library
    SeedExercise("Romanian Deadlift", WEIGHT_REPS, Equipment.BARBELL, bar = "Olympic bar", rest = 150,
        alts = listOf("DB Romanian Deadlift")),
    SeedExercise("DB Romanian Deadlift", WEIGHT_REPS, Equipment.DUMBBELL, rest = 120),
    SeedExercise("Reverse Lunge", WEIGHT_REPS, Equipment.DUMBBELL, rest = 90),
    SeedExercise("DB Walking Lunge", WEIGHT_REPS, Equipment.DUMBBELL, rest = 90),
    SeedExercise("Farmer Carry", LOAD_DISTANCE, Equipment.DUMBBELL, rest = 90),
    SeedExercise("Farmer Hold", TIMED, Equipment.DUMBBELL, rest = 60),
    SeedExercise("Single-Leg Calf Raise", WEIGHT_REPS, Equipment.DUMBBELL, rest = 60),
    SeedExercise("Lat Pulldown", WEIGHT_REPS, Equipment.CABLE, rest = 90, alts = listOf("DB Row")),
    SeedExercise("DB Row", WEIGHT_REPS, "${Equipment.DUMBBELL},${Equipment.BENCH}", rest = 90),
    SeedExercise("Bench Press", WEIGHT_REPS, "${Equipment.BARBELL},${Equipment.BENCH}", bar = "Olympic bar", rest = 150,
        alts = listOf("Horizontal Press", "Push-Up")),
    SeedExercise("Push-Up", BODYWEIGHT, rest = 60),
    SeedExercise("Pike Push-Up", BODYWEIGHT, rest = 60),
    SeedExercise("Band Face Pull", WEIGHT_REPS, Equipment.BAND, rest = 60),
    SeedExercise("Reverse Fly", WEIGHT_REPS, Equipment.DUMBBELL, rest = 60),
    SeedExercise("DB Fly", WEIGHT_REPS, "${Equipment.DUMBBELL},${Equipment.BENCH}", rest = 60),
    SeedExercise("Back Squat", WEIGHT_REPS, Equipment.BARBELL, bar = "Olympic bar", rest = 180,
        alts = listOf("Goblet Squat")),
    SeedExercise("Front Squat", WEIGHT_REPS, Equipment.BARBELL, bar = "Olympic bar", rest = 180,
        alts = listOf("Goblet Squat")),
    SeedExercise("Deadlift", WEIGHT_REPS, Equipment.BARBELL, bar = "Olympic bar", rest = 180,
        alts = listOf("Trap Bar Deadlift", "DB Romanian Deadlift")),
    SeedExercise("Hip Thrust", WEIGHT_REPS, "${Equipment.BARBELL},${Equipment.BENCH}", bar = "Olympic bar", rest = 120),
    SeedExercise("Pull-Up", BODYWEIGHT, Equipment.PULLUP_BAR, rest = 120, alts = listOf("Lat Pulldown", "DB Row")),
    SeedExercise("Dip", BODYWEIGHT, rest = 120, alts = listOf("Push-Up")),
    SeedExercise("Nordic Curl", BODYWEIGHT, rest = 120),
    SeedExercise("Plank", TIMED, rest = 60),
    SeedExercise("Copenhagen Plank", TIMED, Equipment.BENCH, rest = 60),
    SeedExercise("Sled Pull", LOAD_DISTANCE, Equipment.SLED, rest = 120, alts = listOf("Farmer Carry")),
    SeedExercise("Box Jump", WEIGHT_REPS, rest = 90),
    SeedExercise("Kettlebell Swing", WEIGHT_REPS, Equipment.KETTLEBELL, rest = 90),
)

private data class SeedTemplateItem(val exercise: String, val sets: Int, val target: String)

private val lowerSession = listOf(
    SeedTemplateItem("Trap Bar Deadlift", 3, "5"),
    SeedTemplateItem("Bulgarian Split Squat", 3, "8"),
    SeedTemplateItem("Sled Push", 4, "20m"),
    SeedTemplateItem("Step-Down Isometric", 3, "30s"),
    SeedTemplateItem("Seated Calf Raise", 3, "12"),
)

private val upperSession = listOf(
    SeedTemplateItem("Dead Hang", 2, "45s"),
    SeedTemplateItem("Chin-Up", 3, "6"),
    SeedTemplateItem("Horizontal Press", 3, "8"),
    SeedTemplateItem("Row", 3, "10"),
    SeedTemplateItem("Overhead Press", 3, "8"),
    SeedTemplateItem("Face Pull", 2, "15"),
    SeedTemplateItem("Cable Crossover", 2, "12"),
)

/** Inserts starter data the first time the app runs. Safe to call on every launch. */
suspend fun seedIfNeeded(db: AppDatabase) {
    val dao = db.dao()
    if (dao.setting(Settings.SEEDED) != null) return
    db.withTransaction {
        if (dao.setting(Settings.SEEDED) != null) return@withTransaction

        val olympic = dao.insertBar(Bar(name = "Olympic bar", weightKg = 20.0))
        val trap = dao.insertBar(Bar(name = "Trap bar", weightKg = 25.0))
        dao.insertBar(Bar(name = "Women's bar", weightKg = 15.0))
        dao.insertBar(Bar(name = "EZ bar", weightKg = 10.0))
        val barIds = mapOf("Olympic bar" to olympic, "Trap bar" to trap)

        val ids = mutableMapOf<String, Long>()
        for (e in seedExercises) {
            ids[e.name] = dao.insertExercise(
                Exercise(
                    name = e.name,
                    type = e.type,
                    equipment = e.equipment,
                    barId = e.bar?.let { barIds[it] },
                    restSeconds = e.rest,
                )
            )
        }
        for (e in seedExercises) {
            e.alts.forEachIndexed { i, alt ->
                val altId = ids[alt] ?: return@forEachIndexed
                dao.insertAlt(ExerciseAlt(exerciseId = ids.getValue(e.name), altExerciseId = altId, priority = i))
            }
        }

        suspend fun template(name: String, order: Int, items: List<SeedTemplateItem>) {
            val tId = dao.insertTemplate(Template(name = name, sortOrder = order))
            items.forEachIndexed { i, item ->
                dao.insertTemplateExercise(
                    TemplateExercise(
                        templateId = tId,
                        exerciseId = ids.getValue(item.exercise),
                        position = i,
                        sets = item.sets,
                        target = item.target,
                    )
                )
            }
        }
        template("Session 1 · Athletic Lower", 0, lowerSession)
        template("Session 2 · Push/Pull Upper", 1, upperSession)
        dao.insertTemplate(
            Template(name = "Wednesday · Conditioning", kind = TemplateKind.ACTIVITY, sortOrder = 2)
        )

        val all = Equipment.all.joinToString(",")
        val mainGym = dao.insertVenue(Venue(name = "Main gym", equipment = all))
        dao.insertVenue(
            Venue(
                name = "Hotel gym",
                equipment = listOf(Equipment.DUMBBELL, Equipment.BENCH, Equipment.BAND).joinToString(","),
                isTravel = true,
            )
        )

        listOf("Protein shake" to 30, "Chicken breast" to 40, "Greek yoghurt" to 20, "3 eggs" to 18, "Tuna can" to 25)
            .forEachIndexed { i, (label, g) -> dao.insertProteinPreset(ProteinPreset(label = label, grams = g, sortOrder = i)) }

        listOf("Creatine", "Vitamin D", "Omega-3", "Magnesium")
            .forEachIndexed { i, name -> dao.insertSupplement(Supplement(name = name, sortOrder = i)) }

        val today = LocalDate.now().toEpochDay()
        dao.insertGear(
            Gear(
                name = "Training shoes", category = "Shoes", startDay = today,
                lifespanSessions = 150, lifespanDays = 365, defaultFor = SessionType.STRENGTH,
            )
        )
        dao.insertGear(
            Gear(
                name = "Basketball shoes", category = "Shoes", startDay = today,
                lifespanSessions = 80, lifespanDays = 270, defaultFor = SessionType.BASKETBALL,
            )
        )

        dao.putSetting(Setting(Settings.PLATES, Settings.DEFAULT_PLATES))
        dao.putSetting(Setting(Settings.ACTIVE_VENUE, mainGym.toString()))
        dao.putSetting(Setting(Settings.BODYWEIGHT_TARGET, "98"))
        dao.putSetting(Setting(Settings.PROTEIN_MIN, "160"))
        dao.putSetting(Setting(Settings.PROTEIN_MAX, "175"))
        dao.putSetting(Setting(Settings.MONTHLY_SESSION_TARGET, "7"))
        dao.putSetting(Setting(Settings.NEAT_WEEKLY_SESSIONS, "3"))
        dao.putSetting(Setting(Settings.NEAT_SESSION_MIN, "60"))
        dao.putSetting(Setting(Settings.SEEDED, "1"))
    }
}

/** One-off data updates for schema v2 (safe to call every launch). */
suspend fun migrateDataV2(db: AppDatabase) {
    val dao = db.dao()
    if (dao.setting("data_v2") != null) return
    dao.allTemplates()
        .filter { it.kind == TemplateKind.ACTIVITY && it.name == "Wednesday · Basketball or conditioning" }
        .forEach { dao.updateTemplate(it.copy(name = "Wednesday · Conditioning")) }
    dao.putSetting(Setting("data_v2", "1"))
}
