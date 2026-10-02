package com.muir.bear.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.muir.bear.Graph
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private data class Section(val title: String, val lines: List<String>)

@Composable
fun WeeklySummaryScreen(nav: Nav) {
    val context = LocalContext.current
    val dao = Graph.dao
    val summaries by dao.workoutSummaries().collectAsState(initial = emptyList())
    val activities by dao.activities().collectAsState(initial = emptyList())
    val sleeps by dao.sleeps().collectAsState(initial = emptyList())
    val saunas by dao.saunas().collectAsState(initial = emptyList())
    val bodyweights by dao.bodyweights().collectAsState(initial = emptyList())
    val proteins by dao.proteins().collectAsState(initial = emptyList())
    val steps by dao.dailySteps().collectAsState(initial = emptyList())
    val niggles by dao.niggles().collectAsState(initial = emptyList())
    val hrv by dao.hrvReadings().collectAsState(initial = emptyList())
    val proteinMin = dao.settingFlow(Settings.PROTEIN_MIN).collectAsState(initial = null).value?.toIntOrNull() ?: 160
    val sleepNeed = Calc.parseNumber(dao.settingFlow(Settings.SLEEP_NEED).collectAsState(initial = null).value) ?: 8.0
    var weeksBack by remember { mutableIntStateOf(0) }
    var sets by remember { mutableStateOf<List<WorkoutSet>>(emptyList()) }
    var exercises by remember { mutableStateOf<Map<Long, Exercise>>(emptyMap()) }
    var workouts by remember { mutableStateOf<Map<Long, Workout>>(emptyMap()) }
    LaunchedEffect(summaries.size) {
        sets = dao.allSets(); exercises = dao.allExercises().associateBy { it.id }; workouts = dao.allWorkouts().associateBy { it.id }
    }

    val monday = Calc.mondayOf(LocalDate.now()).minusWeeks(weeksBack.toLong())
    val from = monday.toEpochDay()
    val to = minOf(from + 6, today())
    val prevFrom = from - 7
    fun inWeek(day: Long) = day in from..to
    fun inPrev(day: Long) = day in prevFrom until from

    val sections = buildList {
        // Training
        val strength = summaries.map { it.workout }.filter { it.endedAt != null }
        val sport = activities.filter { it.type == SessionType.CONDITIONING || it.type == SessionType.BASKETBALL }
        val sw = strength.filter { inWeek(it.startedAt.toLocalDate().toEpochDay()) }
        val cw = sport.filter { inWeek(it.startedAt.toLocalDate().toEpochDay()) }
        val loads = trainingLoads(strength, activities)
        val load = loads.filter { inWeek(it.first) }.sumOf { it.second }
        val prevLoad = loads.filter { inPrev(it.first) }.sumOf { it.second }
        val minutes = sw.sumOf { ((it.endedAt!! - it.startedAt) / 60_000).toInt() } + cw.sumOf { it.durationMin }
        val loadDays = Insights.fitnessFatigue(loads, to)
        add(Section("Training", buildList {
            add("${sw.size} strength ${if (sw.size == 1) "session" else "sessions"}, ${cw.size} conditioning, $minutes min in all.")
            if (prevLoad > 0) add("Load ${load.roundToInt()} (${"%+d".format(((load - prevLoad) / prevLoad * 100).roundToInt())}% vs the week before).")
            else if (load > 0) add("Load ${load.roundToInt()}.")
            if (Insights.formRatio(loadDays) != null) add("By the end of the week: ${Insights.formLabel(loadDays).lowercase()}.")
        }))

        // Strength: best e1RM this week vs everything before it
        val working = sets.filter { s ->
            s.completed && s.kind != "W" && (s.weightKg ?: 0.0) > 0 && (s.reps ?: 0) > 0 &&
                exercises[s.exerciseId]?.type == ExerciseType.WEIGHT_REPS && workouts[s.workoutId]?.endedAt != null
        }
        fun dayOf(s: WorkoutSet) = workouts.getValue(s.workoutId).startedAt.toLocalDate().toEpochDay()
        val lifts = working.filter { inWeek(dayOf(it)) }.groupBy { it.exerciseId }.mapNotNull { (id, ws) ->
            val best = ws.maxOf { Rpe.e1rm(it.weightKg!!, it.reps!!, it.rpe) }
            val before = working.filter { it.exerciseId == id && dayOf(it) < from }.maxOfOrNull { Rpe.e1rm(it.weightKg!!, it.reps!!, it.rpe) }
            val name = exercises[id]?.name ?: return@mapNotNull null
            when {
                before == null -> "$name: first time, est. 1RM ${(best * 2).roundToInt() / 2.0} kg"
                best > before * 1.005 -> "$name: new best est. 1RM ${(best * 2).roundToInt() / 2.0} kg (+${Calc.fmt(Calc.round2(best - before))})"
                else -> null
            }
        }
        if (lifts.isNotEmpty()) add(Section("Progress", lifts))

        // Volume gaps
        val vol = working.plus(sets.filter { it.completed && exercises[it.exerciseId]?.type == ExerciseType.BODYWEIGHT && workouts[it.workoutId]?.endedAt != null })
            .map { s -> exercises.getValue(s.exerciseId).let { e -> Insights.SetForVolume(dayOf(s), Calc.tags(e.muscles).toList(), Calc.tags(e.secondaryMuscles).toList(), s.rpe, s.kind == "W") } }
        val week = Insights.setsPerMuscle(vol, from, to)
        val before = Insights.setsPerMuscle(vol, from - 28, from - 1)
        val top = week.entries.sortedByDescending { it.value }.take(4).map { "${Muscles.label(it.key)} ${Calc.fmt(Calc.round2(it.value))}" }
        val missed = before.keys.filter { (week[it] ?: 0.0) == 0.0 }.map { Muscles.label(it) }
        if (top.isNotEmpty() || missed.isNotEmpty()) add(Section("Hard sets", buildList {
            if (top.isNotEmpty()) add("Most: ${top.joinToString(", ")}.")
            if (missed.isNotEmpty() && weeksBack > 0 || missed.isNotEmpty() && LocalDate.now().dayOfWeek.value >= 5) add("Not trained this week: ${missed.joinToString(", ")}.")
        }))

        // Recovery
        val offset = ZoneId.systemDefault().rules.getOffset(java.time.Instant.now()).totalSeconds / 60
        val nights = sleeps.filter { it.wakeAt != null && inWeek(it.wakeAt.toLocalDate().toEpochDay()) }.map { Insights.Night(it.bedAt, it.wakeAt!!) }
        val st = Insights.sleepStats(nights, sleepNeed, 7, offset)
        val hrvWeek = hrv.filter { inWeek(it.at.toLocalDate().toEpochDay()) }
        val hrvPrev = hrv.filter { inPrev(it.at.toLocalDate().toEpochDay()) }
        add(Section("Recovery", buildList {
            if (st != null) add("Sleep: %.1f h a night over %d %s, %.1f h short of your %s h need.".format(st.avgHours, st.nights, if (st.nights == 1) "night" else "nights", st.debtHours, Calc.fmt(sleepNeed)))
            else add("No sleep logged.")
            if (hrvWeek.isNotEmpty()) {
                val avg = hrvWeek.map { it.rmssdMs }.average()
                val rhr = hrvWeek.map { it.heartRate }.average()
                add("HRV ${avg.roundToInt()} ms, resting HR ${rhr.roundToInt()} bpm (${hrvWeek.size} readings)" +
                    (if (hrvPrev.isNotEmpty()) ", ${"%+d".format((avg - hrvPrev.map { it.rmssdMs }.average()).roundToInt())} ms vs the week before." else "."))
            }
            val nig = niggles.filter { inWeek(it.at.toLocalDate().toEpochDay()) && it.severity >= 3 }.distinctBy { it.region + it.side }
            if (nig.isNotEmpty()) add("Niggles: " + nig.joinToString(", ") { "${if (it.side == "Centre") "" else it.side.lowercase() + " "}${it.region.lowercase()} ${it.severity}/10" } + ".")
        }))

        // Body and habits
        add(Section("Body and habits", buildList {
            val bw = bodyweights.filter { inWeek(it.day) }
            val bwPrev = bodyweights.filter { inPrev(it.day) }
            if (bw.isNotEmpty()) add("Bodyweight %s kg average".format(Calc.fmt(Calc.round2(bw.map { it.kg }.average()))) +
                (if (bwPrev.isNotEmpty()) " (%+.1f kg).".format(bw.map { it.kg }.average() - bwPrev.map { it.kg }.average()) else "."))
            val days = (from..to)
            val proteinDays = days.count { d -> proteins.filter { it.day == d }.sumOf { it.grams } >= proteinMin }
            add("Protein target hit $proteinDays of ${days.count()} days.")
            val stepsWeek = steps.filter { inWeek(it.day) }
            if (stepsWeek.isNotEmpty()) add("%,d steps a day on average.".format(stepsWeek.sumOf { it.steps } / stepsWeek.size))
            val sauna = saunas.filter { inWeek(it.at.toLocalDate().toEpochDay()) }
            if (sauna.isNotEmpty()) add("Sauna ${sauna.size}×, ${sauna.sumOf { it.rounds * it.minutesPerRound }} min.")
            val neat = activities.filter { it.type == SessionType.NEAT && inWeek(it.startedAt.toLocalDate().toEpochDay()) }
            if (neat.isNotEmpty()) add("NEAT ${neat.size} ${if (neat.size == 1) "session" else "sessions"}, ${neat.sumOf { it.durationMin }} min.")
        }))
    }

    val title = "Week of " + monday.format(DateTimeFormatter.ofPattern("d MMMM"))
    LogScaffold("Weekly summary", nav) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("This week" to 0, "Last week" to 1).forEach { (label, w) ->
                    if (w == weeksBack) Button(onClick = {}, modifier = Modifier.weight(1f)) { Text(label) }
                    else OutlinedButton(onClick = { weeksBack = w }, modifier = Modifier.weight(1f)) { Text(label) }
                }
            }
        }
        item { Text(title, style = MaterialTheme.typography.titleLarge) }
        sections.forEach { s ->
            item {
                SectionCard(s.title) { s.lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) } }
            }
        }
        item {
            BigButton("Copy as text", onClick = {
                val text = "Bear · $title\n\n" + sections.joinToString("\n\n") { s -> s.title + "\n" + s.lines.joinToString("\n") { "- $it" } }
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Bear weekly summary", text))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }, secondary = true)
            Gap(4)
            Muted("Paste it anywhere, for example into a chat with Claude when you review your program.")
        }
    }
}
