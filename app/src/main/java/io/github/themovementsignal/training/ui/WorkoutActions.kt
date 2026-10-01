package io.github.themovementsignal.training.ui

import androidx.room.withTransaction
import io.github.themovementsignal.training.Graph
import io.github.themovementsignal.training.data.Exercise
import io.github.themovementsignal.training.data.ExerciseType
import io.github.themovementsignal.training.data.GearUsage
import io.github.themovementsignal.training.data.SessionType
import io.github.themovementsignal.training.data.Settings
import io.github.themovementsignal.training.data.Venue
import io.github.themovementsignal.training.data.Workout
import io.github.themovementsignal.training.data.WorkoutSet
import io.github.themovementsignal.training.domain.Calc

/** Database actions shared by several screens. All are suspend and run off the main thread via Room. */
object Actions {
    private val dao get() = Graph.dao

    suspend fun activeVenue(): Venue? {
        val id = dao.setting(Settings.ACTIVE_VENUE)?.toLongOrNull() ?: return null
        return dao.venue(id)
    }

    /** Creates a workout from a template (or empty when [templateId] is null) and returns its id. */
    suspend fun startWorkout(templateId: Long?): Long = Graph.db.withTransaction {
        val template = templateId?.let { dao.template(it) }
        val venue = activeVenue()
        val equipment = venue?.let { Calc.tags(it.equipment) }
        val name = buildString {
            append(template?.name ?: "Workout")
            if (venue?.isTravel == true) append(" · ").append(venue.name)
        }
        val wid = dao.insertWorkout(
            Workout(templateId = templateId, name = name, startedAt = System.currentTimeMillis(), venueId = venue?.id)
        )
        if (template != null) {
            val items = dao.templateExercisesList(template.id)
            val sets = mutableListOf<WorkoutSet>()
            items.forEachIndexed { order, te ->
                val ex = dao.exercise(te.exerciseId) ?: return@forEachIndexed
                val chosen = if (equipment != null) {
                    Calc.substitute(ex, dao.alternatives(ex.id), { it.equipment }, equipment)
                } else ex
                for (i in 0 until te.sets.coerceAtLeast(1)) {
                    sets += WorkoutSet(
                        workoutId = wid, exerciseId = chosen.id, exerciseOrder = order, setIndex = i, target = te.target,
                    )
                }
            }
            dao.insertSets(sets)
        }
        wid
    }

    suspend fun addExercise(workoutId: Long, exercise: Exercise, sets: Int = 3) {
        val existing = dao.sets(workoutId)
        val order = (existing.maxOfOrNull { it.exerciseOrder } ?: -1) + 1
        dao.insertSets((0 until sets).map { WorkoutSet(workoutId = workoutId, exerciseId = exercise.id, exerciseOrder = order, setIndex = it) })
    }

    suspend fun addSet(workoutId: Long, exerciseOrder: Int) {
        val group = dao.sets(workoutId).filter { it.exerciseOrder == exerciseOrder }
        val last = group.maxByOrNull { it.setIndex } ?: return
        dao.insertSet(
            WorkoutSet(
                workoutId = workoutId, exerciseId = last.exerciseId, exerciseOrder = exerciseOrder,
                setIndex = last.setIndex + 1, target = last.target,
            )
        )
    }

    suspend fun removeExercise(workoutId: Long, exerciseOrder: Int) {
        dao.sets(workoutId).filter { it.exerciseOrder == exerciseOrder }.forEach { dao.deleteSet(it) }
    }

    suspend fun swapExercise(workoutId: Long, exerciseOrder: Int, newExerciseId: Long) {
        dao.sets(workoutId).filter { it.exerciseOrder == exerciseOrder }.forEach {
            dao.updateSet(it.copy(exerciseId = newExerciseId))
        }
    }

    suspend fun moveExercise(workoutId: Long, exerciseOrder: Int, delta: Int) {
        val all = dao.sets(workoutId)
        val orders = all.map { it.exerciseOrder }.distinct().sorted()
        val idx = orders.indexOf(exerciseOrder)
        val otherIdx = idx + delta
        if (idx < 0 || otherIdx !in orders.indices) return
        val other = orders[otherIdx]
        Graph.db.withTransaction {
            all.forEach { s ->
                when (s.exerciseOrder) {
                    exerciseOrder -> dao.updateSet(s.copy(exerciseOrder = other))
                    other -> dao.updateSet(s.copy(exerciseOrder = exerciseOrder))
                }
            }
        }
    }

    suspend fun finishWorkout(workoutId: Long, rpe: Int?, notes: String) {
        Graph.db.withTransaction {
            val w = dao.workout(workoutId) ?: return@withTransaction
            dao.deleteIncompleteSets(workoutId)
            val now = System.currentTimeMillis()
            dao.updateWorkout(w.copy(endedAt = now, rpe = rpe, notes = notes))
            recordGearUsage(SessionType.STRENGTH, workoutId, now)
        }
    }

    suspend fun discardWorkout(workoutId: Long) = deleteWorkout(workoutId)

    suspend fun deleteWorkout(workoutId: Long) {
        Graph.db.withTransaction {
            dao.deleteSetsForWorkout(workoutId)
            dao.deleteWorkoutRow(workoutId)
            dao.deleteGearUsageForSession(SessionType.STRENGTH, workoutId)
        }
    }

    /** Counts a session against every active gear item that defaults to this session type. */
    suspend fun recordGearUsage(sessionType: String, sessionId: Long, at: Long) {
        dao.allGear()
            .filter { !it.retired && sessionType in Calc.tags(it.defaultFor) }
            .forEach { dao.insertGearUsage(GearUsage(gearId = it.id, sessionType = sessionType, sessionId = sessionId, at = at)) }
    }

    /** Sets from the most recent finished workout containing this exercise (excluding [workoutId]). */
    suspend fun previousSets(exerciseId: Long, workoutId: Long): List<WorkoutSet> {
        val h = dao.history(exerciseId, workoutId)
        val last = h.firstOrNull()?.set?.workoutId ?: return emptyList()
        return h.filter { it.set.workoutId == last }.map { it.set }.filter { it.kind != "W" }
    }
}

/** "100 × 5", "+10 × 6", "45s", "80 kg × 20 m" etc. */
fun describeSet(type: String, s: WorkoutSet): String {
    val w = s.weightKg
    return when (type) {
        ExerciseType.TIMED -> buildString {
            append("${s.seconds ?: 0}s")
            if (w != null && w != 0.0) append(" @ ${Calc.fmt(w)} kg")
        }
        ExerciseType.LOAD_DISTANCE -> "${Calc.fmt(w ?: 0.0)} kg × ${Calc.fmt(s.distanceM ?: 0.0)} m"
        ExerciseType.BODYWEIGHT -> {
            val load = when {
                w == null || w == 0.0 -> "BW"
                w > 0 -> "+${Calc.fmt(w)}"
                else -> Calc.fmt(w)
            }
            "$load × ${s.reps ?: 0}"
        }
        else -> "${Calc.fmt(w ?: 0.0)} × ${s.reps ?: 0}"
    }
}
