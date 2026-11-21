package com.muir.bear.domain

import kotlin.random.Random

/**
 * Fuel: daily food rules and the afternoon snack pick. Pure logic; the screens map database rows
 * to these small types. Kinds match the names stored in the database (data/Fuel.kt).
 */
object FuelKind {
    const val MANUAL = "MANUAL"
    const val COUNTER = "COUNTER"
    const val AUTO_WORKOUT = "AUTO_WORKOUT"
    const val AUTO_SNACK = "AUTO_SNACK"
    const val DEFAULT_ON = "DEFAULT_ON"
}

object FuelSource {
    const val MANUAL = "MANUAL"
    const val AUTO = "AUTO"
}

object FuelRules {
    const val PLANNED_SNACK = "planned_snack"
    const val SHAKE_AFTER_GYM = "shake_after_gym"
}

data class FuelRuleIn(val id: String, val kind: String, val target: Int = 1, val enabled: Boolean = true)

data class FuelCheckIn(val ruleId: String, val value: Int, val source: String)

object FuelScore {

    data class Day(val applicable: Int, val hit: Int) {
        /** "5 of 6 most days": all but one rule counts as a good day. */
        val goalMet: Boolean get() = applicable > 0 && hit >= applicable - 1
    }

    /** Whether [rule] counts today. The shake rule doesn't apply on a rest day unless you ticked it. */
    fun applies(rule: FuelRuleIn, check: FuelCheckIn?, hadWorkout: Boolean): Boolean = when {
        !rule.enabled -> false
        rule.kind == FuelKind.AUTO_WORKOUT -> hadWorkout || (check?.value ?: 0) > 0
        else -> true
    }

    /** The value shown for a rule: a DEFAULT_ON rule with no row counts as ticked. */
    fun value(rule: FuelRuleIn, check: FuelCheckIn?): Int =
        check?.value ?: if (rule.kind == FuelKind.DEFAULT_ON) rule.target else 0

    fun isHit(rule: FuelRuleIn, check: FuelCheckIn?): Boolean = value(rule, check) >= rule.target.coerceAtLeast(1)

    fun forDay(rules: List<FuelRuleIn>, checks: List<FuelCheckIn>, hadWorkout: Boolean): Day {
        val byRule = checks.associateBy { it.ruleId }
        val counted = rules.filter { applies(it, byRule[it.id], hadWorkout) }
        return Day(counted.size, counted.count { isHit(it, byRule[it.id]) })
    }

    /** Days in [days] where the goal was met. */
    fun goodDays(days: List<Day>): Int = days.count { it.goalMet }
}

object FuelAuto {
    /**
     * The check to write when something should tick a rule automatically, or null to leave it:
     * anything you set by hand that day (a tick or an untick) wins.
     */
    fun autoTick(ruleId: String, existing: FuelCheckIn?): FuelCheckIn? =
        if (existing?.source == FuelSource.MANUAL) null else FuelCheckIn(ruleId, 1, FuelSource.AUTO)

    fun onWorkoutFinished(existing: FuelCheckIn?): FuelCheckIn? = autoTick(FuelRules.SHAKE_AFTER_GYM, existing)

    fun onSnackLogged(existing: FuelCheckIn?): FuelCheckIn? = autoTick(FuelRules.PLANNED_SNACK, existing)

    /** After a snack log is removed: clear an automatic tick if no snacks are left that day. */
    fun clearSnackTick(existing: FuelCheckIn?, snacksLeft: Int): Boolean =
        existing?.source == FuelSource.AUTO && snacksLeft == 0
}

object SnackPicker {
    data class Item(val id: Long, val favourite: Boolean = false, val archived: Boolean = false)

    /**
     * Today's suggestion: never archived, never yesterday's snack (unless it's the only one left),
     * favourites twice as likely. The same [seed] always gives the same pick, so use the day.
     */
    fun next(items: List<Item>, yesterday: Set<Long>, seed: Long): Item? {
        val live = items.filter { !it.archived }
        if (live.isEmpty()) return null
        val pool = live.filter { it.id !in yesterday }.ifEmpty { live }
        val weighted = pool.flatMap { if (it.favourite) listOf(it, it) else listOf(it) }
        return weighted[Random(seed).nextInt(weighted.size)]
    }
}

/** When the afternoon snack prompt fires next. Times are minutes after midnight. */
object SnackPrompt {
    const val DEFAULT_MINUTE = 15 * 60 + 30

    /**
     * The next prompt strictly after [now]: today's time if it's still ahead and today isn't done
     * (a snack logged, or "Skip today"), otherwise the next day's. Weekends have their own time.
     */
    fun next(now: java.time.LocalDateTime, weekdayMinute: Int, weekendMinute: Int, doneToday: Boolean): java.time.LocalDateTime {
        for (offset in 0L..7L) {
            if (offset == 0L && doneToday) continue
            val day = now.toLocalDate().plusDays(offset)
            val weekend = day.dayOfWeek == java.time.DayOfWeek.SATURDAY || day.dayOfWeek == java.time.DayOfWeek.SUNDAY
            val m = (if (weekend) weekendMinute else weekdayMinute).coerceIn(0, 24 * 60 - 1)
            val at = day.atTime(m / 60, m % 60)
            if (at.isAfter(now)) return at
        }
        return now.toLocalDate().plusDays(1).atTime(weekdayMinute / 60, weekdayMinute % 60)
    }

    fun parse(saved: String?): Int = saved?.toIntOrNull()?.takeIf { it in 0 until 24 * 60 } ?: DEFAULT_MINUTE

    fun fmt(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

    /** "Afternoon snack: Biltong, 50 g. Log it?" (names as you wrote them: brands keep their capitals) */
    fun text(name: String, portion: String): String =
        "Afternoon snack: $name${if (portion.isBlank()) "" else ", $portion"}. Log it?"
}
