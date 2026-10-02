@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.muir.bear.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muir.bear.Graph
import com.muir.bear.data.Equipment
import com.muir.bear.data.Exercise
import com.muir.bear.data.ExerciseAlt
import com.muir.bear.data.ExerciseType
import com.muir.bear.data.SetWithTime
import com.muir.bear.data.WorkoutSet
import com.muir.bear.domain.Calc
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlin.math.roundToLong
import kotlinx.coroutines.withContext

// ---------- Personal records ----------

/** The number tracked for progress and PRs for one set, by exercise type. */
fun setScore(type: String, s: WorkoutSet): Double = when (type) {
    ExerciseType.TIMED -> (s.seconds ?: 0).toDouble()
    ExerciseType.LOAD_DISTANCE -> (s.weightKg ?: 0.0) * (s.distanceM ?: 0.0)
    ExerciseType.BODYWEIGHT -> {
        val w = s.weightKg ?: 0.0
        if (w > 0) Calc.epley(w, s.reps ?: 0) else (s.reps ?: 0).toDouble()
    }
    else -> Calc.epley(s.weightKg ?: 0.0, s.reps ?: 0)
}

fun scoreLabel(type: String) = when (type) {
    ExerciseType.TIMED -> "Longest hold (s)"
    ExerciseType.LOAD_DISTANCE -> "Best load × distance (kg·m)"
    ExerciseType.BODYWEIGHT -> "Best e1RM of added load (or reps)"
    else -> "Estimated 1RM (Epley, kg)"
}

data class PrLine(val label: String, val value: String, val date: Long)

fun personalRecords(type: String, history: List<SetWithTime>): List<PrLine> {
    val working = history.filter { it.set.kind != "W" }
    if (working.isEmpty()) return emptyList()
    val out = mutableListOf<PrLine>()
    fun best(label: String, selector: (WorkoutSet) -> Double?, show: (SetWithTime) -> String) {
        val b = working.filter { (selector(it.set) ?: 0.0) > 0 }.maxByOrNull { selector(it.set) ?: 0.0 } ?: return
        out += PrLine(label, show(b), b.startedAt)
    }
    when (type) {
        ExerciseType.TIMED -> {
            best("Longest hold", { it.seconds?.toDouble() }) { "${it.set.seconds}s" }
            best("Heaviest load", { it.weightKg }) { describeSet(type, it.set) }
        }
        ExerciseType.LOAD_DISTANCE -> {
            best("Heaviest load", { it.weightKg }) { describeSet(type, it.set) }
            best("Longest distance", { it.distanceM }) { describeSet(type, it.set) }
            best("Best load × distance", { (it.weightKg ?: 0.0) * (it.distanceM ?: 0.0) }) { describeSet(type, it.set) }
        }
        else -> {
            best("Estimated 1RM", { Calc.epley(it.weightKg ?: 0.0, it.reps ?: 0) }) {
                "${Calc.fmt(Calc.epley(it.set.weightKg ?: 0.0, it.set.reps ?: 0))} kg (${describeSet(type, it.set)})"
            }
            best("Heaviest weight", { it.weightKg }) { describeSet(type, it.set) }
            best("Most reps", { it.reps?.toDouble() }) { describeSet(type, it.set) }
            best("Best set volume", { (it.weightKg ?: 0.0) * (it.reps ?: 0) }) {
                "${Calc.fmt((it.set.weightKg ?: 0.0) * (it.set.reps ?: 0))} kg (${describeSet(type, it.set)})"
            }
        }
    }
    return out
}

/** Volume in kg for sets with external load × reps (weight × reps and added-load bodyweight). */
fun setVolume(type: String, s: WorkoutSet): Double = when (type) {
    ExerciseType.WEIGHT_REPS, ExerciseType.BODYWEIGHT -> ((s.weightKg ?: 0.0).coerceAtLeast(0.0)) * (s.reps ?: 0)
    else -> 0.0
}

fun fmtVolume(kg: Double): String = "%,d kg".format(kg.roundToLong())

// ---------- History tab ----------

@Composable
fun HistoryScreen(nav: Nav) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Workouts", "Exercises").forEachIndexed { i, label ->
                if (i == tab) Button(onClick = { tab = i }, modifier = Modifier.weight(1f)) { Text(label) }
                else OutlinedButton(onClick = { tab = i }, modifier = Modifier.weight(1f)) { Text(label) }
            }
        }
        if (tab == 0) WorkoutList(nav) else ExerciseList(nav, showTitle = false)
    }
}

@Composable
private fun WorkoutList(nav: Nav) {
    val dao = Graph.dao
    val summaries by dao.workoutSummaries().collectAsState(initial = emptyList())
    val allSets by dao.allCompletedSets().collectAsState(initial = emptyList())
    var exercises by remember { mutableStateOf<Map<Long, Exercise>>(emptyMap()) }
    LaunchedEffect(allSets.size) { exercises = dao.allExercises().associateBy { it.id } }
    val setsByWorkout = allSets.groupBy { it.set.workoutId }
    if (summaries.isEmpty()) {
        Muted("No finished workouts yet. Start one from the Train tab, or import your Strong history in More → Backup & import.", Modifier.padding(16.dp))
        return
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(summaries, key = { it.workout.id }) { s ->
            val w = s.workout
            SectionCard(onClick = { nav.go(Screen.WorkoutDetail(w.id)) }) {
                Text(w.name, style = MaterialTheme.typography.titleLarge)
                val dur = w.endedAt?.let { (it - w.startedAt) / 60_000 } ?: 0
                Muted(
                    "${fmtDateTime(w.startedAt)} · ${fmtMinutes(dur)}" +
                        (s.volume?.takeIf { it > 0 }?.let { " · ${fmtVolume(it)}" } ?: "") +
                        (w.rpe?.let { " · RPE $it" } ?: "")
                )
                val groups = setsByWorkout[w.id].orEmpty().groupBy { it.set.exerciseOrder }.toSortedMap().values
                if (groups.isNotEmpty()) {
                    Gap(8)
                    Row {
                        Text("Exercise", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Best set", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    groups.take(6).forEach { g ->
                        val ex = exercises[g.first().set.exerciseId]
                        val type = ex?.type ?: ExerciseType.WEIGHT_REPS
                        val working = g.filter { it.set.kind != "W" }.ifEmpty { g }
                        val best = working.maxByOrNull { setScore(type, it.set) }
                        Row {
                            Text("${working.size} × ${ex?.name ?: "…"}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (best != null) Text(describeSet(type, best.set), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (groups.size > 6) Muted("+ ${groups.size - 6} more")
                }
            }
        }
    }
}

@Composable
fun WorkoutDetailScreen(workoutId: Long, nav: Nav) {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val workout by dao.workoutFlow(workoutId).collectAsState(initial = null)
    val sets by dao.setsFlow(workoutId).collectAsState(initial = emptyList())
    var exercises by remember { mutableStateOf<Map<Long, Exercise>>(emptyMap()) }
    var bestBefore by remember { mutableStateOf<Map<Long, Double>>(emptyMap()) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(sets, workout) {
        val w = workout ?: return@LaunchedEffect
        exercises = dao.allExercises().associateBy { it.id }
        bestBefore = sets.map { it.exerciseId }.distinct().associateWith { exId ->
            val type = exercises[exId]?.type ?: ExerciseType.WEIGHT_REPS
            dao.history(exId, workoutId).filter { it.startedAt < w.startedAt && it.set.kind != "W" }
                .maxOfOrNull { setScore(type, it.set) } ?: -1.0
        }
    }
    Scaffold(topBar = {
        AppTopBar(workout?.name ?: "Workout", onBack = { nav.back() }) {
            DangerTextButton("Delete") { confirmDelete = true }
        }
    }) { padding ->
        val w = workout
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (w != null) item {
                SectionCard {
                    Text(fmtDateTime(w.startedAt), style = MaterialTheme.typography.titleMedium)
                    val dur = (w.endedAt ?: System.currentTimeMillis()) - w.startedAt
                    Muted("Duration ${fmtMinutes(dur / 60_000)}" + (w.rpe?.let { " · session RPE $it · load ${it * (dur / 60_000)}" } ?: ""))
                    val done = sets.filter { it.completed }
                    val volume = done.sumOf { setVolume(exercises[it.exerciseId]?.type ?: ExerciseType.WEIGHT_REPS, it) }
                    val prCount = done.filter { it.kind != "W" }.groupBy { it.exerciseId }.count { (exId, g) ->
                        val type = exercises[exId]?.type ?: ExerciseType.WEIGHT_REPS
                        val bar = bestBefore[exId] ?: -1.0
                        bar > 0 && g.any { setScore(type, it) > bar }
                    }
                    Gap(8)
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        SummaryStat("Volume", fmtVolume(volume))
                        SummaryStat("Sets", done.size.toString())
                        SummaryStat("PRs", if (prCount > 0) "🏆 $prCount" else "0")
                    }
                    if (w.notes.isNotBlank()) { Gap(4); Text(w.notes) }
                    if (w.endedAt == null) {
                        Gap(8)
                        BigButton("Resume workout", onClick = { nav.replace(Screen.Workout(w.id)) })
                    }
                }
            }
            val groups = sets.filter { it.completed }.groupBy { it.exerciseOrder }.toSortedMap()
            items(groups.values.toList()) { group ->
                val ex = exercises[group.first().exerciseId]
                val type = ex?.type ?: ExerciseType.WEIGHT_REPS
                val prBar = ex?.let { bestBefore[it.id] } ?: -1.0
                val bestHere = group.filter { it.kind != "W" }.maxByOrNull { setScore(type, it) }
                SectionCard(ex?.name ?: "…", onClick = ex?.let { { nav.go(Screen.ExerciseDetail(it.id)) } }) {
                    group.sortedBy { it.setIndex }.forEachIndexed { i, s ->
                        val isPr = s == bestHere && prBar > 0 && setScore(type, s) > prBar
                        Row {
                            Text("${if (s.kind == "W") "W" else (i + 1).toString()}  ", fontWeight = FontWeight.Bold)
                            Text(describeSet(type, s), Modifier.weight(1f))
                            if (isPr) Text("🏆 PR", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog("Delete workout?", "This permanently deletes this workout and its sets.", onConfirm = {
            scope.launch {
                withContext(NonCancellable) { Actions.deleteWorkout(workoutId) }
                nav.back()
            }
        }, onDismiss = { confirmDelete = false })
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Muted(label)
    }
}

// ---------- Exercise library ----------

@Composable
fun ExercisesScreen(nav: Nav) {
    Scaffold(topBar = { AppTopBar("Exercise library", onBack = { nav.back() }) }) { padding ->
        Column(Modifier.padding(padding)) { ExerciseList(nav, showTitle = false) }
    }
}

@Composable
private fun ExerciseList(nav: Nav, showTitle: Boolean) {
    val exercises by Graph.dao.exercises().collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    val filtered = exercises.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showTitle) item { Text("Exercises", style = MaterialTheme.typography.headlineMedium) }
        item { TextInput(query, { query = it }, "Search exercises") }
        item { BigButton("+ New custom exercise", onClick = { nav.go(Screen.ExerciseEdit(null)) }, secondary = true) }
        items(filtered, key = { it.id }) { e ->
            Column(Modifier.fillMaxWidth().clickable { nav.go(Screen.ExerciseDetail(e.id)) }.padding(vertical = 10.dp)) {
                Text(e.name, style = MaterialTheme.typography.bodyLarge)
                Muted(ExerciseType.label(e.type) + if (e.isCustom) " · custom" else "")
            }
        }
    }
}

@Composable
fun ExerciseDetailScreen(exerciseId: Long, nav: Nav) {
    val dao = Graph.dao
    val exercise by dao.exerciseFlow(exerciseId).collectAsState(initial = null)
    val history by dao.historyFlow(exerciseId).collectAsState(initial = emptyList())
    val ex = exercise
    Scaffold(topBar = {
        AppTopBar(ex?.name ?: "Exercise", onBack = { nav.back() }) {
            TextButton(onClick = { nav.go(Screen.ExerciseEdit(exerciseId)) }) { Text("Edit") }
        }
    }) { padding ->
        if (ex == null) return@Scaffold
        val type = ex.type
        val bySession = history.groupBy { it.set.workoutId }
        val points = bySession.values.mapNotNull { sets ->
            val best = sets.filter { it.set.kind != "W" }.maxOfOrNull { setScore(type, it.set) } ?: return@mapNotNull null
            if (best <= 0) null else (sets.first().startedAt.toLocalDate().toEpochDay().toFloat() to best.toFloat())
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard("Personal records") {
                    val prs = personalRecords(type, history)
                    if (prs.isEmpty()) Muted("No completed sets yet.")
                    prs.forEach { StatLine(it.label, "${it.value} · ${fmtDate(it.date)}") }
                }
            }
            item {
                SectionCard("Progress") {
                    Muted(scoreLabel(type) + ", best set per session")
                    Gap(4)
                    LineChart(listOf(points), listOf(MaterialTheme.colorScheme.primary))
                }
            }
            item { Text("History", style = MaterialTheme.typography.titleMedium) }
            items(bySession.entries.toList(), key = { it.key }) { (wid, sets) ->
                SectionCard(onClick = { nav.go(Screen.WorkoutDetail(wid)) }) {
                    Text("${fmtDate(sets.first().startedAt)} · ${sets.first().workoutName}", style = MaterialTheme.typography.titleSmall)
                    Muted(sets.joinToString("   ") { (if (it.set.kind == "W") "W " else "") + describeSet(type, it.set) })
                }
            }
        }
    }
}

@Composable
fun ExerciseEditScreen(exerciseId: Long?, nav: Nav) {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(exerciseId == null) }
    var original by remember { mutableStateOf<Exercise?>(null) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ExerciseType.WEIGHT_REPS) }
    var equipment by remember { mutableStateOf(setOf<String>()) }
    var rest by remember { mutableStateOf("120") }
    var barId by remember { mutableStateOf<Long?>(null) }
    var archived by remember { mutableStateOf(false) }
    var alts by remember { mutableStateOf<List<Exercise>>(emptyList()) }
    var pickingAlt by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val bars by dao.bars().collectAsState(initial = emptyList())

    LaunchedEffect(exerciseId) {
        if (exerciseId != null) {
            dao.exercise(exerciseId)?.let { e ->
                original = e; name = e.name; type = e.type; equipment = Calc.tags(e.equipment)
                rest = e.restSeconds.toString(); barId = e.barId; archived = e.archived
            }
            alts = dao.alternatives(exerciseId)
            loaded = true
        }
    }

    Scaffold(topBar = { AppTopBar(if (exerciseId == null) "New exercise" else "Edit exercise", onBack = { nav.back() }) }) { padding ->
        if (!loaded) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TextInput(name, { name = it }, "Name") }
            item {
                SectionCard("Type") {
                    ExerciseType.all.forEach { t ->
                        Row(Modifier.fillMaxWidth().clickable { type = t }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(selected = type == t, onClick = { type = t })
                            Text(ExerciseType.label(t))
                        }
                    }
                }
            }
            item {
                SectionCard("Equipment needed") {
                    ChipGroup(Equipment.all, equipment, Equipment::label) { tag ->
                        equipment = if (tag in equipment) equipment - tag else equipment + tag
                    }
                    Muted("Used to swap exercises automatically at venues that lack this equipment.")
                }
            }
            item { NumberField(rest, { rest = it }, "Rest timer (seconds)", Modifier.fillMaxWidth(), decimal = false) }
            item {
                SectionCard("Bar for plate calculator") {
                    ChipGroup(listOf("none") + bars.map { it.id.toString() }, setOf(barId?.toString() ?: "none"),
                        { id -> if (id == "none") "None" else bars.firstOrNull { it.id.toString() == id }?.let { "${it.name} (${Calc.fmt(it.weightKg)} kg)" } ?: id }) { id ->
                        barId = id.toLongOrNull()
                    }
                }
            }
            if (exerciseId != null) item {
                SectionCard("Alternatives (for swaps and venues)") {
                    alts.forEach { a ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(a.name, Modifier.weight(1f))
                            DangerTextButton("Remove") {
                                scope.launch { dao.deleteAlt(exerciseId, a.id); alts = dao.alternatives(exerciseId) }
                            }
                        }
                    }
                    TextButton(onClick = { pickingAlt = true }) { Text("+ Add alternative") }
                }
            }
            if (exerciseId != null) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hide from library (keeps history)", Modifier.weight(1f))
                    Switch(checked = archived, onCheckedChange = { archived = it })
                }
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item {
                BigButton("Save", enabled = name.isNotBlank(), onClick = {
                    scope.launch {
                        val restSec = rest.toIntOrNull() ?: 120
                        try {
                            val o = original
                            if (o == null) {
                                dao.insertExercise(
                                    Exercise(name = name.trim(), type = type, equipment = equipment.joinToString(","), restSeconds = restSec, barId = barId, isCustom = true)
                                )
                            } else {
                                dao.updateExercise(o.copy(name = name.trim(), type = type, equipment = equipment.joinToString(","), restSeconds = restSec, barId = barId, archived = archived))
                            }
                            nav.back()
                        } catch (e: Exception) {
                            error = "Couldn't save: an exercise with that name probably exists already."
                        }
                    }
                })
            }
        }
    }
    if (pickingAlt && exerciseId != null) {
        ExercisePickerDialog("Add alternative", onDismiss = { pickingAlt = false }, onPick = { a ->
            pickingAlt = false
            scope.launch {
                dao.insertAlt(ExerciseAlt(exerciseId, a.id, alts.size))
                alts = dao.alternatives(exerciseId)
            }
        })
    }
}

@Composable
fun ChipGroup(options: List<String>, selected: Set<String>, label: (String) -> String, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(selected = o in selected, onClick = { onToggle(o) }, label = { Text(label(o)) })
        }
    }
}
