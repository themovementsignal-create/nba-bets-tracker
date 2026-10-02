package com.muir.bear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muir.bear.Graph
import com.muir.bear.data.Activity
import com.muir.bear.data.Exercise
import com.muir.bear.data.ExerciseType
import com.muir.bear.data.SessionType
import com.muir.bear.data.Settings
import com.muir.bear.data.Workout
import com.muir.bear.data.WorkoutSet
import com.muir.bear.domain.Calc
import com.muir.bear.domain.Insights
import com.muir.bear.domain.Muscles
import com.muir.bear.domain.Rpe
import java.time.ZoneId
import kotlin.math.roundToInt

/** Daily training load (session RPE × minutes) from finished workouts and conditioning sessions. */
fun trainingLoads(finished: List<Workout>, activities: List<Activity>): List<Pair<Long, Double>> =
    finished.mapNotNull { w ->
        val rpe = w.rpe ?: return@mapNotNull null
        val end = w.endedAt ?: return@mapNotNull null
        w.startedAt.toLocalDate().toEpochDay() to rpe * (end - w.startedAt) / 60_000.0
    } + activities
        .filter { it.type == SessionType.BASKETBALL || it.type == SessionType.CONDITIONING }
        .mapNotNull { a -> a.rpe?.let { a.startedAt.toLocalDate().toEpochDay() to (it * a.durationMin).toDouble() } }

/** Everything the Insights screen needs from the database, loaded once when it opens. */
private class InsightData(
    val sets: List<WorkoutSet>,
    val exercises: Map<Long, Exercise>,
    val workouts: Map<Long, Workout>,
)

@Composable
fun InsightsScreen(nav: Nav) {
    val dao = Graph.dao
    val summaries by dao.workoutSummaries().collectAsState(initial = emptyList())
    val activities by dao.activities().collectAsState(initial = emptyList())
    val sleeps by dao.sleeps().collectAsState(initial = emptyList())
    val sleepNeed = Calc.parseNumber(dao.settingFlow(Settings.SLEEP_NEED).collectAsState(initial = null).value) ?: 8.0
    var data by remember { mutableStateOf<InsightData?>(null) }
    LaunchedEffect(Unit) {
        data = InsightData(dao.allSets(), dao.allExercises().associateBy { it.id }, dao.allWorkouts().associateBy { it.id })
    }
    val today = today()

    LogScaffold("Insights", nav) {
        item { Muted("Built from what you log. Estimates for spotting trends, not lab measurements.") }

        // ---------- Fitness and fatigue ----------
        item {
            val days = Insights.fitnessFatigue(trainingLoads(summaries.map { it.workout }, activities), today)
            SectionCard("Fitness and fatigue") {
                val last = days.lastOrNull()
                if (last == null) {
                    Muted("Finish a workout with a session RPE to start this.")
                } else {
                    Text(Insights.formLabel(days), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Gap(4)
                    val shown = days.filter { it.day > today - 56 }
                    LineChart(
                        listOf(
                            shown.map { it.day.toFloat() to it.fitness.toFloat() },
                            shown.map { it.day.toFloat() to it.fatigue.toFloat() },
                        ),
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurfaceVariant),
                        showPoints = false,
                    )
                    Gap(4)
                    StatLine("Fitness (6-week trend)", last.fitness.roundToInt().toString(), MaterialTheme.colorScheme.primary)
                    StatLine("Fatigue (last week or so)", last.fatigue.roundToInt().toString())
                    StatLine("Form (fitness − fatigue)", last.form.roundToInt().toString())
                    Muted("Load = session RPE × minutes. Fitness builds and fades slowly; fatigue rises and clears within days. Banister's model, as used by TrainingPeaks.")
                }
            }
        }

        // ---------- Hard sets per muscle ----------
        item {
            SectionCard("Hard sets per muscle") {
                val d = data
                if (d == null) {
                    Muted("Loading…")
                } else {
                    val volumeSets = d.sets.mapNotNull { s ->
                        val w = d.workouts[s.workoutId] ?: return@mapNotNull null
                        val e = d.exercises[s.exerciseId] ?: return@mapNotNull null
                        if (!s.completed || w.endedAt == null) return@mapNotNull null
                        Insights.SetForVolume(
                            w.startedAt.toLocalDate().toEpochDay(), Calc.tags(e.muscles).toList(), Calc.tags(e.secondaryMuscles).toList(), s.rpe, s.kind == "W",
                        )
                    }
                    val weekStart = Calc.mondayOf(java.time.LocalDate.now()).toEpochDay()
                    val thisWeek = Insights.setsPerMuscle(volumeSets, weekStart, today)
                    val fourWeeks = Insights.setsPerMuscle(volumeSets, weekStart - 28, weekStart - 1).mapValues { it.value / 4 }
                    val muscles = Muscles.all.filter { (thisWeek[it] ?: 0.0) > 0 || (fourWeeks[it] ?: 0.0) > 0 }
                    if (muscles.isEmpty()) {
                        Muted("Log some workouts to see this.")
                    } else {
                        Row(Modifier.fillMaxWidth()) {
                            Text("", Modifier.width(96.dp))
                            Text("This week", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("4-wk avg", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        muscles.forEach { m ->
                            val now = thisWeek[m] ?: 0.0
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(Muscles.label(m), Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium)
                                LinearProgressIndicator(
                                    progress = { (now / 20.0).toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                )
                                Text(Calc.fmt(Calc.round2(now)), Modifier.width(32.dp), style = MaterialTheme.typography.bodyMedium)
                                Text(Calc.fmt(Math.round((fourWeeks[m] ?: 0.0) * 10) / 10.0), Modifier.width(36.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Gap(4)
                        Muted("Working sets at RPE 7+ (or not rated). Main muscle = 1, also works = ½. Research suggests about 10–20 a week per muscle for growth; fewer still maintains strength. Bar full at 20.")
                    }
                }
            }
        }

        // ---------- Sleep ----------
        item {
            SectionCard("Sleep") {
                val offset = ZoneId.systemDefault().rules.getOffset(java.time.Instant.now()).totalSeconds / 60
                val nights = sleeps.filter { it.wakeAt != null }.map { Insights.Night(it.bedAt, it.wakeAt!!) }
                val s7 = Insights.sleepStats(nights, sleepNeed, 7, offset)
                val s14 = Insights.sleepStats(nights, sleepNeed, 14, offset)
                if (s7 == null) {
                    Muted("Track or log a few nights to see this.")
                } else {
                    StatLine("Average, last ${s7.nights} ${if (s7.nights == 1) "night" else "nights"}", "%.1f h".format(s7.avgHours))
                    StatLine("Sleep debt (need ${Calc.fmt(sleepNeed)} h)", "%.1f h".format(s7.debtHours),
                        if (s7.debtHours >= 5) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified)
                    s14?.bedSpreadMin?.let { StatLine("Bedtime varies by", "± ${it.roundToInt()} min") }
                    s14?.wakeSpreadMin?.let { StatLine("Wake time varies by", "± ${it.roundToInt()} min") }
                    Gap(4)
                    Muted("Debt = hours short of your need, summed over the week. Regular bed and wake times (within about ±30 min) support recovery as much as total hours. Set your need in Targets & settings.")
                }
            }
        }

        // ---------- Strength trends ----------
        item {
            SectionCard("Strength trends") {
                val d = data
                if (d == null) {
                    Muted("Loading…")
                } else {
                    // Best estimated 1RM per session for each weight × reps lift (RPE-adjusted when rated).
                    val perExercise = d.sets
                        .filter { it.completed && it.kind != "W" && (it.weightKg ?: 0.0) > 0 && (it.reps ?: 0) > 0 }
                        .filter { d.exercises[it.exerciseId]?.type == ExerciseType.WEIGHT_REPS }
                        .filter { d.workouts[it.workoutId]?.endedAt != null }
                        .groupBy { it.exerciseId }
                        .mapValues { (_, sets) ->
                            sets.groupBy { it.workoutId }.map { (wid, ws) ->
                                d.workouts.getValue(wid).startedAt.toLocalDate().toEpochDay() to ws.maxOf { Rpe.e1rm(it.weightKg!!, it.reps!!, it.rpe) }
                            }
                        }
                    val trends = perExercise.mapNotNull { (id, sessions) ->
                        Insights.strengthTrend(sessions, today)?.let { d.exercises.getValue(id) to it }
                    }.sortedByDescending { it.second.sessions }.take(8)
                    if (trends.isEmpty()) {
                        Muted("Lifts done in the last 4 weeks will show here.")
                    } else {
                        trends.forEach { (e, t) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(e.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text("${(t.recentBest * 2).roundToInt() / 2.0} kg", style = MaterialTheme.typography.bodyMedium)
                            }
                            val note = when {
                                t.plateau -> "Flat for 4 weeks. Worth a look at your next review: rep range, load or a lighter week."
                                t.changePct != null -> "%+.1f%% vs the 4 weeks before".format(t.changePct)
                                else -> "New in the last 4 weeks"
                            }
                            Text(
                                note, style = MaterialTheme.typography.bodySmall,
                                color = if (t.plateau) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Gap(4)
                        Muted("Best estimated 1RM in the last 4 weeks, using your RPE where you rated sets (Epley otherwise).")
                    }
                }
            }
        }
    }
}
