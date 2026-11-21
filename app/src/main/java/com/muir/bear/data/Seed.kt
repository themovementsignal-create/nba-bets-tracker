package com.muir.bear.data

import androidx.room.withTransaction
import com.muir.bear.data.ExerciseType.BODYWEIGHT
import com.muir.bear.data.ExerciseType.FLOORS
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
    /** Your walking cadence, for estimating NEAT steps the phone didn't count (default 100). */
    const val NEAT_STEPS_PER_MIN = "neat_steps_per_min"
    /** Today's phone step count when the NEAT timer started (to see what it counted during the walk). */
    const val NEAT_TIMER_STEPS0 = "neat_timer_steps0"
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
    /** "1" (default): turning the alarm off opens the morning check-ins. */
    const val MORNING_AFTER_ALARM = "morning_after_alarm"
    /** Daily supplement reminder times (see domain/SuppReminders). */
    const val SUPP_REMINDERS = "supp_reminders"
    /** Epoch millis of a one-off "remind me in…" supplement reminder; empty = none. */
    const val SUPP_REMIND_ONCE = "supp_remind_once"
    /** What plays when rest ends: horn (default), beep or silent (see RestTimer.Sound). */
    const val REST_SOUND = "rest_sound"
    /** Epoch day the current training block (program) started; unset = first workout. */
    const val PROGRAM_START = "program_start"
    const val PROGRAM_REVIEW_WEEKS = "program_review_weeks"
    /** Epoch day before which the review reminder stays hidden ("remind me next week"). */
    const val PROGRAM_REVIEW_SNOOZE = "program_review_snooze"

    /** Fuel snack prompt: "1" on (default), "0" off; times in minutes after midnight (default 15:30). */
    const val FUEL_PROMPT_ON = "fuel_prompt_on"
    const val FUEL_PROMPT_WEEKDAY = "fuel_prompt_weekday"
    const val FUEL_PROMPT_WEEKEND = "fuel_prompt_weekend"
    /** ISO date "Skip today" was pressed. */
    const val FUEL_SKIP_DAY = "fuel_skip_day"
    /** Fuel phase: "MAINTENANCE" (default) or "CUT", with optional ISO start and end dates. */
    const val FUEL_PHASE = "fuel_phase"
    const val FUEL_PHASE_START = "fuel_phase_start"
    const val FUEL_PHASE_END = "fuel_phase_end"

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
    SeedExercise("Stairmaster", FLOORS, rest = 60),
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

/** Fuel defaults: the six rules and the snack list. Added once; never changes what you've edited. */
suspend fun seedFuel(db: AppDatabase) {
    val dao = db.dao()
    if (dao.setting("data_fuel") != null) return
    db.withTransaction {
        if (dao.setting("data_fuel") != null) return@withTransaction
        dao.insertFuelRules(
            listOf(
                FuelRule("protein_each_meal", "Protein at every meal", RuleKind.MANUAL, position = 0),
                FuelRule("fruit_two", "Two pieces of fruit", RuleKind.COUNTER, target = 2, position = 1),
                FuelRule("veg_half_dinner", "Half of dinner is veg", RuleKind.MANUAL, position = 2),
                FuelRule("chips_small_bag", "Chips: one small bag after dinner, or none", RuleKind.DEFAULT_ON, position = 3),
                FuelRule("planned_snack", "One planned afternoon snack", RuleKind.AUTO_SNACK, position = 4),
                FuelRule("shake_after_gym", "Shake after training", RuleKind.AUTO_WORKOUT, position = 5),
            )
        )
        if (dao.allFoodItems().isEmpty()) {
            val S = FoodCategory.SAVOURY; val W = FoodCategory.SWEET; val T = FoodCategory.TRAINING
            listOf(
                FoodItem(name = "Prosciutto + Babybel", portion = "×2", category = S),
                FoodItem(name = "Tuna + crackers", portion = "1 tin + 4 crackers", category = S),
                FoodItem(name = "Vintage cheddar + crackers", portion = "30 g + 4 crackers", category = S),
                FoodItem(name = "Boiled eggs", portion = "2", category = S),
                FoodItem(name = "Edamame", portion = "1 cup", category = S),
                FoodItem(name = "Leftovers", portion = "small bowl", category = S),
                FoodItem(name = "Barebells bar", portion = "1 bar", category = W),
                FoodItem(name = "YoPro", portion = "1 tub", category = W),
                FoodItem(name = "Greek yoghurt + berries + honey", portion = "200 g", category = W),
                FoodItem(name = "Protein shake + dark chocolate", portion = "1 shake + 2 squares", category = W),
                FoodItem(name = "Date balls + shake", portion = "2 balls + 1 shake", category = W),
                FoodItem(name = "Paul's protein smoothie", portion = "1 smoothie", category = T, kcal = 272, proteinG = 30.0),
            ).forEachIndexed { i, f -> dao.insertFoodItem(f.copy(position = i)) }
        }
        dao.putSetting(Setting("data_fuel", "1"))
    }
}

/** Adds exercises shipped after the first install (never touches existing ones). Safe every launch. */
suspend fun addNewExercises(db: AppDatabase) {
    val dao = db.dao()
    if (dao.setting("data_stairmaster") != null) return
    if (dao.exerciseByName("Stairmaster") == null) {
        dao.insertExercise(Exercise(name = "Stairmaster", type = FLOORS, restSeconds = 60))
    }
    dao.putSetting(Setting("data_stairmaster", "1"))
}
