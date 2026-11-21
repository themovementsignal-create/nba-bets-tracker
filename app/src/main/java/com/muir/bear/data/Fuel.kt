package com.muir.bear.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Fuel (database v8): a few daily food rules, an afternoon snack prompt and a snack list.
 * v1 is a rules tracker, not a calorie counter, but items and logs already carry optional kcal and
 * protein so a later version can total them without a schema rewrite. Kinds are stored by name.
 */

/** How a rule gets ticked. */
object RuleKind {
    const val MANUAL = "MANUAL"
    /** Counted up to [FuelRule.target] (fruit: 0, 1, 2). */
    const val COUNTER = "COUNTER"
    /** Ticked when a workout is finished that day; doesn't apply on rest days. */
    const val AUTO_WORKOUT = "AUTO_WORKOUT"
    /** Ticked when a snack is logged that day; can also be ticked by hand. */
    const val AUTO_SNACK = "AUTO_SNACK"
    /** Ticked unless you untick it (chips: one small bag, after dinner, or none). */
    const val DEFAULT_ON = "DEFAULT_ON"
}

object CheckSource {
    const val MANUAL = "MANUAL"
    const val AUTO = "AUTO"
}

object FoodCategory {
    const val SAVOURY = "SAVOURY"
    const val SWEET = "SWEET"
    const val TRAINING = "TRAINING"
    /** Dinners and other meals (v2). */
    const val MEAL = "MEAL"

    val snacks = listOf(SAVOURY, SWEET, TRAINING)

    fun label(c: String) = when (c) {
        SAVOURY -> "Savoury"
        SWEET -> "Sweet"
        TRAINING -> "Training"
        MEAL -> "Meal"
        else -> c
    }
}

object MealSlot {
    const val SNACK = "SNACK"
    const val BREAKFAST = "BREAKFAST"
    const val LUNCH = "LUNCH"
    const val DINNER = "DINNER"
}

object LogSource {
    const val PROMPT = "PROMPT"
    const val LIST = "LIST"
    const val QUICK = "QUICK"
}

@Entity(tableName = "fuel_rule")
data class FuelRule(
    /** Stable id, e.g. "fruit_two". */
    @PrimaryKey val id: String,
    val label: String,
    val kind: String,
    /** Ticks needed: 2 for fruit, otherwise 1. */
    val target: Int = 1,
    val enabled: Boolean = true,
    val position: Int,
)

/** One rule on one day. No row = not ticked (or, for a DEFAULT_ON rule, ticked). */
@Entity(tableName = "fuel_check", primaryKeys = ["date", "ruleId"], indices = [Index("date")])
data class FuelCheck(
    /** ISO local date, "2026-11-21". */
    val date: String,
    val ruleId: String,
    /** Ticks or count; 0 = deliberately unticked. */
    val value: Int,
    val source: String,
)

/** The snack list now; the food library later. Never hard-deleted: archive instead. */
@Entity(tableName = "food_item")
data class FoodItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** "50 g", "1 bar". */
    val portion: String,
    val category: String,
    val kcal: Int? = null,
    val proteinG: Double? = null,
    /** Unused in v1. */
    val carbsG: Double? = null,
    /** Unused in v1. */
    val fatG: Double? = null,
    val favourite: Boolean = false,
    val archived: Boolean = false,
    /** Order in the snack list. */
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
)

/** Every eaten item: snacks in v1, all meals in v2. */
@Entity(tableName = "food_log", indices = [Index("date"), Index("foodItemId")])
data class FoodLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loggedAt: Long,
    /** ISO local date. */
    val date: String,
    val slot: String,
    val foodItemId: Long?,
    /** The name and portion when logged, so edits to the item don't rewrite history. */
    val nameSnapshot: String,
    val kcal: Int? = null,
    val proteinG: Double? = null,
    val source: String,
)
