package com.muir.bear.fuel

import com.muir.bear.Graph
import com.muir.bear.data.Activity
import com.muir.bear.data.CheckSource
import com.muir.bear.data.FoodItem
import com.muir.bear.data.FoodLog
import com.muir.bear.data.FuelCheck
import com.muir.bear.data.FuelRule
import com.muir.bear.data.MealSlot
import com.muir.bear.data.SessionType
import com.muir.bear.data.Workout
import com.muir.bear.domain.FuelAuto
import com.muir.bear.domain.FuelCheckIn
import com.muir.bear.domain.FuelRuleIn
import com.muir.bear.domain.FuelRules
import com.muir.bear.domain.FuelScore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Reads and writes for Fuel: ticks, the fruit count, snack logs and the automatic ticks. */
object FuelStore {
    private val dao get() = Graph.dao

    fun iso(d: LocalDate): String = d.toString()
    fun todayIso(): String = iso(LocalDate.now())

    private fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    fun ruleIn(r: FuelRule) = FuelRuleIn(r.id, r.kind, r.target, r.enabled)
    fun checkIn(c: FuelCheck) = FuelCheckIn(c.ruleId, c.value, c.source)

    /** Days with training: a finished workout, or a conditioning / basketball session. */
    fun trainingDays(workouts: List<Workout>, activities: List<Activity>): Set<LocalDate> =
        workouts.filter { it.endedAt != null }.map { dayOf(it.startedAt) }.toSet() +
            activities.filter { it.type == SessionType.CONDITIONING || it.type == SessionType.BASKETBALL }.map { dayOf(it.startedAt) }

    fun score(rules: List<FuelRule>, checks: List<FuelCheck>, hadWorkout: Boolean): FuelScore.Day =
        FuelScore.forDay(rules.map(::ruleIn), checks.map(::checkIn), hadWorkout)

    /** Tap on a rule: tick it if it isn't, untick it if it is. Always a manual choice. */
    suspend fun toggle(rule: FuelRule, date: String) {
        val existing = dao.fuelCheck(date, rule.id)
        val hit = FuelScore.isHit(ruleIn(rule), existing?.let(::checkIn))
        dao.putFuelCheck(FuelCheck(date, rule.id, if (hit) 0 else rule.target.coerceAtLeast(1), CheckSource.MANUAL))
    }

    /** The fruit stepper. */
    suspend fun setCount(rule: FuelRule, date: String, value: Int) {
        dao.putFuelCheck(FuelCheck(date, rule.id, value.coerceIn(0, rule.target.coerceAtLeast(1)), CheckSource.MANUAL))
    }

    /** Logs a snack (name and numbers copied, so later edits don't change history) and ticks the snack rule. */
    suspend fun logSnack(item: FoodItem, source: String, at: Long = System.currentTimeMillis()) {
        val date = iso(dayOf(at))
        dao.insertFoodLog(
            FoodLog(
                loggedAt = at, date = date, slot = MealSlot.SNACK, foodItemId = item.id,
                nameSnapshot = "${item.name}, ${item.portion}".trimEnd(',', ' '),
                kcal = item.kcal, proteinG = item.proteinG, source = source,
            )
        )
        autoTick(date, FuelRules.PLANNED_SNACK)
    }

    suspend fun removeLog(log: FoodLog) {
        dao.deleteFoodLog(log.id)
        val existing = dao.fuelCheck(log.date, FuelRules.PLANNED_SNACK)
        val left = dao.foodLogsOnce(log.date).count { it.slot == MealSlot.SNACK }
        if (FuelAuto.clearSnackTick(existing?.let(::checkIn), left)) dao.deleteFuelCheck(log.date, FuelRules.PLANNED_SNACK)
    }

    /** Called when a workout is finished: ticks "Shake after training" unless you've set it by hand today. */
    suspend fun onWorkoutFinished(at: Long = System.currentTimeMillis()) {
        autoTick(iso(dayOf(at)), FuelRules.SHAKE_AFTER_GYM)
    }

    private suspend fun autoTick(date: String, ruleId: String) {
        val existing = dao.fuelCheck(date, ruleId)
        val next = FuelAuto.autoTick(ruleId, existing?.let(::checkIn)) ?: return
        dao.putFuelCheck(FuelCheck(date, next.ruleId, next.value, next.source))
    }
}
