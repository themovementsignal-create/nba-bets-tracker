package io.github.themovementsignal.training.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.themovementsignal.training.Graph
import io.github.themovementsignal.training.data.Exercise
import io.github.themovementsignal.training.data.ExerciseType
import io.github.themovementsignal.training.data.WorkoutSet
import io.github.themovementsignal.training.domain.Calc
import io.github.themovementsignal.training.timer.RestTimer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WorkoutScreen(workoutId: Long, nav: Nav) {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val workout by dao.workoutFlow(workoutId).collectAsState(initial = null)
    val sets by dao.setsFlow(workoutId).collectAsState(initial = emptyList())
    val exercises by dao.exercises().collectAsState(initial = emptyList())
    var allExercises by remember { mutableStateOf<Map<Long, Exercise>>(emptyMap()) }
    LaunchedEffect(exercises) { allExercises = dao.allExercises().associateBy { it.id } }

    val exerciseIds = sets.map { it.exerciseId }.distinct()
    var previous by remember { mutableStateOf<Map<Long, List<WorkoutSet>>>(emptyMap()) }
    LaunchedEffect(exerciseIds) {
        previous = exerciseIds.associateWith { Actions.previousSets(it, workoutId) }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    var showFinish by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var pickerFor by remember { mutableStateOf<PickerTarget?>(null) }
    var plateTarget by remember { mutableStateOf<Pair<Exercise, Double?>?>(null) }

    val w = workout
    Scaffold(
        topBar = {
            AppTopBar(w?.name ?: "Workout", onBack = { nav.back() }) {
                TextButton(onClick = { showFinish = true }) {
                    Text("Finish", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        bottomBar = { RestTimerBar() },
    ) { padding ->
        if (w == null) {
            Box(Modifier.fillMaxSize().padding(padding)) { Muted("Loading…", Modifier.padding(16.dp)) }
            return@Scaffold
        }
        val groups = sets.groupBy { it.exerciseOrder }.toSortedMap()
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val done = sets.count { it.completed }
                Muted("${fmtClock((now - w.startedAt) / 1000)} elapsed · $done/${sets.size} sets done")
            }
            items(groups.entries.toList(), key = { it.key }) { (order, groupSets) ->
                val ex = allExercises[groupSets.first().exerciseId]
                if (ex != null) {
                    ExerciseBlock(
                        exercise = ex,
                        sets = groupSets.sortedBy { it.setIndex },
                        previous = previous[ex.id].orEmpty(),
                        onAddSet = { scope.launch { Actions.addSet(workoutId, order) } },
                        onSwap = { pickerFor = PickerTarget.Swap(order, ex.id) },
                        onRemove = { scope.launch { Actions.removeExercise(workoutId, order) } },
                        onMove = { delta -> scope.launch { Actions.moveExercise(workoutId, order, delta) } },
                        onPlates = { weight -> plateTarget = ex to weight },
                        onHistory = { nav.go(Screen.ExerciseDetail(ex.id)) },
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton("+ Add exercise", onClick = { pickerFor = PickerTarget.Add }, secondary = true)
                    BigButton("Finish workout", onClick = { showFinish = true })
                    DangerTextButton("Discard workout") { showDiscard = true }
                }
            }
        }
    }

    when (val p = pickerFor) {
        PickerTarget.Add -> ExercisePickerDialog(
            title = "Add exercise",
            onDismiss = { pickerFor = null },
            onPick = { e -> pickerFor = null; scope.launch { Actions.addExercise(workoutId, e) } },
        )
        is PickerTarget.Swap -> SwapDialog(
            exerciseId = p.exerciseId,
            onDismiss = { pickerFor = null },
            onPick = { e -> pickerFor = null; scope.launch { Actions.swapExercise(workoutId, p.order, e.id) } },
        )
        null -> {}
    }

    plateTarget?.let { (ex, weight) ->
        PlateCalcDialog(initialWeight = weight, initialBarId = ex.barId, onDismiss = { plateTarget = null })
    }

    if (showFinish) {
        FinishDialog(
            unfinished = sets.count { !it.completed },
            onDismiss = { showFinish = false },
            onFinish = { rpe, notes ->
                showFinish = false
                RestTimer.stop(context)
                scope.launch { Actions.finishWorkout(workoutId, rpe, notes) }
                nav.replace(Screen.WorkoutDetail(workoutId))
            },
        )
    }
    if (showDiscard) {
        ConfirmDialog(
            title = "Discard workout?",
            text = "This deletes this workout and all its sets.",
            confirm = "Discard",
            onConfirm = { scope.launch { Actions.discardWorkout(workoutId) }; nav.back() },
            onDismiss = { showDiscard = false },
        )
    }
}

private sealed interface PickerTarget {
    data object Add : PickerTarget
    data class Swap(val order: Int, val exerciseId: Long) : PickerTarget
}

@Composable
private fun ExerciseBlock(
    exercise: Exercise,
    sets: List<WorkoutSet>,
    previous: List<WorkoutSet>,
    onAddSet: () -> Unit,
    onSwap: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    onPlates: (Double?) -> Unit,
    onHistory: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { menu = true }) {
                Text(exercise.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Muted(ExerciseType.label(exercise.type) + if (exercise.restSeconds > 0) " · rest ${fmtClock(exercise.restSeconds.toLong())}" else "")
            }
            Box {
                TextButton(onClick = { menu = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Swap exercise") }, onClick = { menu = false; onSwap() })
                    if (exercise.type == ExerciseType.WEIGHT_REPS || exercise.barId != null) {
                        DropdownMenuItem(
                            text = { Text("Plate calculator") },
                            onClick = { menu = false; onPlates(sets.lastOrNull { it.weightKg != null }?.weightKg ?: previous.firstOrNull()?.weightKg) },
                        )
                    }
                    DropdownMenuItem(text = { Text("History & PRs") }, onClick = { menu = false; onHistory() })
                    DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; onMove(-1) })
                    DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; onMove(1) })
                    DropdownMenuItem(text = { Text("Remove exercise") }, onClick = { menu = false; onRemove() })
                }
            }
        }
        Gap(8)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Set", Modifier.width(40.dp), style = MaterialTheme.typography.labelMedium)
            Text("Previous", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Text(fieldLabels(exercise.type).joinToString("   "), style = MaterialTheme.typography.labelMedium)
            Box(Modifier.width(64.dp))
        }
        var workingNumber = 0
        sets.forEach { s ->
            val label = if (s.kind == "W") "W" else { workingNumber++; if (s.kind.isNotEmpty()) "$workingNumber${s.kind}" else "$workingNumber" }
            val prev = previous.getOrNull(s.setIndex) ?: previous.lastOrNull()
            key(s.id) { SetRow(exercise, s, label, prev) }
        }
        TextButton(onClick = onAddSet, modifier = Modifier.fillMaxWidth()) { Text("+ Add set") }
    }
}

private fun fieldLabels(type: String) = when (type) {
    ExerciseType.TIMED -> listOf("kg", "sec")
    ExerciseType.LOAD_DISTANCE -> listOf("kg", "m")
    ExerciseType.BODYWEIGHT -> listOf("±kg", "reps")
    else -> listOf("kg", "reps")
}

/** Parses a template target like "5", "8-10", "30s", "20m" into a placeholder for the second field. */
private fun targetPlaceholder(type: String, target: String): String {
    val n = Regex("\\d+(\\.\\d+)?").find(target)?.value ?: return ""
    return when (type) {
        ExerciseType.TIMED -> if (target.contains("s") || target.contains("sec")) n else n
        ExerciseType.LOAD_DISTANCE -> n
        else -> n
    }
}

@Composable
private fun SetRow(exercise: Exercise, set: WorkoutSet, label: String, prev: WorkoutSet?) {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val type = exercise.type

    var weight by remember(set.id) { mutableStateOf(Calc.fmt(set.weightKg)) }
    var second by remember(set.id) {
        mutableStateOf(
            when (type) {
                ExerciseType.TIMED -> set.seconds?.toString().orEmpty()
                ExerciseType.LOAD_DISTANCE -> Calc.fmt(set.distanceM)
                else -> set.reps?.toString().orEmpty()
            }
        )
    }
    val weightHint = prev?.weightKg?.let { Calc.fmt(it) } ?: if (type == ExerciseType.BODYWEIGHT) "0" else ""
    val secondHint = when (type) {
        ExerciseType.TIMED -> prev?.seconds?.toString()
        ExerciseType.LOAD_DISTANCE -> prev?.distanceM?.let { Calc.fmt(it) }
        else -> prev?.reps?.toString()
    } ?: targetPlaceholder(type, set.target)

    fun build(completed: Boolean, w: String, s: String): WorkoutSet {
        val wVal = Calc.parseNumber(w)
        val sNum = Calc.parseNumber(s)
        return when (type) {
            ExerciseType.TIMED -> set.copy(weightKg = wVal, seconds = sNum?.toInt(), completed = completed)
            ExerciseType.LOAD_DISTANCE -> set.copy(weightKg = wVal, distanceM = sNum, completed = completed)
            else -> set.copy(weightKg = wVal, reps = sNum?.toInt(), completed = completed)
        }
    }

    fun save(w: String, s: String) {
        scope.launch { dao.updateSet(build(set.completed, w, s)) }
    }

    var menu by remember { mutableStateOf(false) }
    val rowColor = if (set.completed) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainer
    Surface(color = rowColor, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton(onClick = { menu = true }, modifier = Modifier.width(40.dp), contentPadding = PaddingValues(0.dp)) {
                    Text(label, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOf("" to "Normal set", "W" to "Warm-up", "D" to "Drop set", "F" to "To failure").forEach { (k, name) ->
                        DropdownMenuItem(text = { Text(name) }, onClick = { menu = false; scope.launch { dao.updateSet(set.copy(kind = k)) } })
                    }
                    DropdownMenuItem(text = { Text("Delete set") }, onClick = { menu = false; scope.launch { dao.deleteSet(set) } })
                }
            }
            Text(
                prev?.let { describeSet(type, it) } ?: if (set.target.isNotEmpty()) "target ${set.target}" else "—",
                Modifier.weight(1f).padding(end = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            NumberField(
                value = weight,
                onValueChange = { weight = it; save(it, second) },
                label = "",
                placeholder = weightHint,
                modifier = Modifier.width(84.dp),
            )
            Box(Modifier.width(4.dp))
            NumberField(
                value = second,
                onValueChange = { second = it; save(weight, it) },
                label = "",
                placeholder = secondHint,
                decimal = type == ExerciseType.LOAD_DISTANCE,
                modifier = Modifier.width(68.dp),
            )
            Box(Modifier.width(4.dp))
            val onCheck = {
                if (set.completed) {
                    scope.launch { dao.updateSet(build(false, weight, second).copy(completedAt = null)) }
                } else {
                    // One tap: empty fields take the previous / target values.
                    if (weight.isBlank()) weight = weightHint
                    if (second.isBlank()) second = secondHint
                    scope.launch { dao.updateSet(build(true, weight, second).copy(completedAt = System.currentTimeMillis())) }
                    if (set.kind != "W") RestTimer.start(context, exercise.restSeconds)
                }
                Unit
            }
            if (set.completed) {
                Button(onClick = onCheck, modifier = Modifier.size(56.dp), contentPadding = PaddingValues(0.dp)) {
                    Text("✓", style = MaterialTheme.typography.titleLarge)
                }
            } else {
                OutlinedButton(onClick = onCheck, modifier = Modifier.size(56.dp), contentPadding = PaddingValues(0.dp)) {
                    Text("✓", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

/** Countdown bar shown at the bottom of the workout screen while resting. */
@Composable
fun RestTimerBar() {
    val state by RestTimer.state.collectAsState()
    val context = LocalContext.current
    val s = state ?: return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s) { while (true) { now = System.currentTimeMillis(); delay(250) } }
    val left = ((s.endAt - now) / 1000).coerceAtLeast(0)
    Surface(color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 4.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Muted("Rest")
                Text(fmtClock(left), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            FilledTonalButton(onClick = { RestTimer.adjust(context, -15) }, modifier = Modifier.heightIn(min = 52.dp)) { Text("−15") }
            Box(Modifier.width(6.dp))
            FilledTonalButton(onClick = { RestTimer.adjust(context, 15) }, modifier = Modifier.heightIn(min = 52.dp)) { Text("+15") }
            Box(Modifier.width(6.dp))
            Button(onClick = { RestTimer.stop(context) }, modifier = Modifier.heightIn(min = 52.dp)) { Text("Skip") }
        }
    }
}

@Composable
private fun FinishDialog(unfinished: Int, onDismiss: () -> Unit, onFinish: (Int?, String) -> Unit) {
    var rpe by remember { mutableStateOf<Int?>(null) }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finish workout") },
        text = {
            Column {
                if (unfinished > 0) {
                    Text("$unfinished unticked set(s) will be removed.", color = MaterialTheme.colorScheme.error)
                    Gap(8)
                }
                Text("Session RPE (how hard was it overall?)")
                Gap(4)
                RatingRow(rpe, { rpe = it }, 1..5)
                Gap(4)
                RatingRow(rpe, { rpe = it }, 6..10)
                Gap(8)
                TextInput(notes, { notes = it }, "Notes (optional)", singleLine = false)
            }
        },
        confirmButton = { TextButton(onClick = { onFinish(rpe, notes) }) { Text("Finish") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep going") } },
    )
}

/** Swap dialog: alternatives first, then everything else. */
@Composable
private fun SwapDialog(exerciseId: Long, onDismiss: () -> Unit, onPick: (Exercise) -> Unit) {
    var alts by remember { mutableStateOf<List<Exercise>>(emptyList()) }
    LaunchedEffect(exerciseId) { alts = Graph.dao.alternatives(exerciseId) }
    ExercisePickerDialog(title = "Swap for…", onDismiss = onDismiss, onPick = onPick, pinned = alts)
}

/** Searchable list of exercises. [pinned] are shown first under "Suggested". */
@Composable
fun ExercisePickerDialog(
    title: String,
    onDismiss: () -> Unit,
    onPick: (Exercise) -> Unit,
    pinned: List<Exercise> = emptyList(),
) {
    val all by Graph.dao.exercises().collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    val filtered = all.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                TextInput(query, { query = it }, "Search")
                Gap(8)
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    if (pinned.isNotEmpty() && query.isBlank()) {
                        item { Text("Suggested", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                        items(pinned, key = { "p" + it.id }) { e -> PickRow(e, onPick) }
                        item { HorizontalDivider(Modifier.padding(vertical = 6.dp)) }
                    }
                    items(filtered, key = { it.id }) { e -> PickRow(e, onPick) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PickRow(e: Exercise, onPick: (Exercise) -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onPick(e) }.padding(vertical = 10.dp)) {
        Text(e.name, style = MaterialTheme.typography.bodyLarge)
        Muted(ExerciseType.label(e.type))
    }
}
