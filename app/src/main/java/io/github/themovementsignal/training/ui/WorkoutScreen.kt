@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.themovementsignal.training.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.themovementsignal.training.Graph
import io.github.themovementsignal.training.data.Exercise
import io.github.themovementsignal.training.data.ExerciseType
import io.github.themovementsignal.training.data.WorkoutSet
import io.github.themovementsignal.training.domain.Calc
import io.github.themovementsignal.training.timer.RestTimer
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Column widths shared by the header row and the set rows so everything lines up.
private val SetCol = 36.dp
private val KgCol = 72.dp
private val SecondCol = 64.dp
private val CheckCol = 52.dp
private val ColGap = 6.dp

@Composable
fun WorkoutScreen(workoutId: Long, nav: Nav) {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val workout by dao.workoutFlow(workoutId).collectAsState(initial = null)
    val sets by dao.setsFlow(workoutId).collectAsState(initial = emptyList())
    val exercises by dao.exercises().collectAsState(initial = emptyList())
    val rest by RestTimer.state.collectAsState()
    var allExercises by remember { mutableStateOf<Map<Long, Exercise>>(emptyMap()) }
    LaunchedEffect(exercises) { allExercises = dao.allExercises().associateBy { it.id } }

    val exerciseIds = sets.map { it.exerciseId }.distinct()
    var previous by remember { mutableStateOf<Map<Long, List<WorkoutSet>>>(emptyMap()) }
    LaunchedEffect(exerciseIds) {
        previous = exerciseIds.associateWith { Actions.previousSets(it, workoutId) }
    }

    // Best score per exercise from earlier workouts, for live PR trophies.
    var bestBefore by remember { mutableStateOf<Map<Long, Double>>(emptyMap()) }
    LaunchedEffect(exerciseIds, allExercises.size) {
        bestBefore = exerciseIds.associateWith { id ->
            val type = allExercises[id]?.type ?: ExerciseType.WEIGHT_REPS
            dao.history(id, workoutId).filter { it.set.kind != "W" }.maxOfOrNull { setScore(type, it.set) } ?: 0.0
        }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(250) } }

    val listState = rememberLazyListState()
    // Drag-to-reorder: local order while dragging, saved to the database on release.
    var localOrder by remember { mutableStateOf<List<Long>?>(null) }
    var dragKey by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    var showFinish by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    var restPickerFor by remember { mutableStateOf<Exercise?>(null) }
    var pickerFor by remember { mutableStateOf<PickerTarget?>(null) }
    var plateTarget by remember { mutableStateOf<Pair<Exercise, Double?>?>(null) }

    val w = workout
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(w?.name ?: "Workout", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (w != null) Muted(fmtClock((now - w.startedAt) / 1000))
                    }
                },
                navigationIcon = { TextButton(onClick = { nav.back() }) { Text("‹", style = MaterialTheme.typography.headlineMedium) } },
                actions = {
                    RestPill(rest, now) { showTimer = true }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { showFinish = true }, contentPadding = PaddingValues(horizontal = 16.dp)) { Text("Finish") }
                    Spacer(Modifier.width(8.dp))
                },
            )
        },
    ) { padding ->
        if (w == null) {
            Box(Modifier.fillMaxSize().padding(padding)) { Muted("Loading…", Modifier.padding(16.dp)) }
            return@Scaffold
        }
        // Each exercise group is identified by its first set id, which stays stable when reordered.
        val groupsById = sets.groupBy { it.exerciseOrder }.toSortedMap().values.associateBy { g -> g.minOf { it.id } }
        val dbOrder = groupsById.keys.toList()
        LaunchedEffect(dbOrder) { if (localOrder == dbOrder) localOrder = null }
        val shown = localOrder?.let { local -> local.filter { it in groupsById } + dbOrder.filter { it !in local } } ?: dbOrder
        val currentShown by rememberUpdatedState(shown)
        val currentDbOrder by rememberUpdatedState(dbOrder)

        fun handleDrag(dy: Float) {
            val key = dragKey ?: return
            val order = localOrder ?: return
            dragOffset += dy
            val visible = listState.layoutInfo.visibleItemsInfo
            val info = visible.firstOrNull { it.key == key } ?: return
            val top = info.offset + dragOffset
            val bottom = top + info.size
            val idx = order.indexOf(key)
            if (dy > 0 && idx in 0 until order.lastIndex) {
                val next = visible.firstOrNull { it.key == order[idx + 1] }
                if (next != null && bottom > next.offset + next.size / 2f) {
                    localOrder = order.toMutableList().apply { add(idx + 1, removeAt(idx)) }
                    dragOffset -= (next.offset + next.size) - (info.offset + info.size)
                }
            } else if (dy < 0 && idx > 0) {
                val prev = visible.firstOrNull { it.key == order[idx - 1] }
                if (prev != null && top < prev.offset + prev.size / 2f) {
                    localOrder = order.toMutableList().apply { add(idx - 1, removeAt(idx)) }
                    dragOffset += info.offset - prev.offset
                }
            }
            // Auto-scroll when dragging near the top or bottom edge.
            val li = listState.layoutInfo
            if (dy > 0 && bottom > li.viewportEndOffset - 160) dragOffset += listState.dispatchRawDelta(24f)
            if (dy < 0 && top < li.viewportStartOffset + 160) dragOffset += listState.dispatchRawDelta(-24f)
        }

        fun endDrag() {
            val final = localOrder
            dragKey = null
            dragOffset = 0f
            if (final != null && final != currentDbOrder) {
                scope.launch { withContext(NonCancellable) { Actions.reorderExercises(workoutId, final) } }
            } else {
                localOrder = null
            }
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val done = sets.count { it.completed }
                Muted("$done of ${sets.size} sets done")
            }
            items(shown, key = { it }) { groupId ->
                val groupSets = groupsById.getValue(groupId)
                val order = groupSets.first().exerciseOrder
                val ex = allExercises[groupSets.first().exerciseId]
                val dragging = dragKey == groupId
                val itemModifier = if (dragging) {
                    Modifier.zIndex(1f).graphicsLayer { translationY = dragOffset; shadowElevation = 24f; scaleX = 1.02f; scaleY = 1.02f }
                } else {
                    Modifier.animateItem()
                }
                if (ex != null) Box(itemModifier) {
                    ExerciseBlock(
                        dragHandle = Modifier.pointerInput(groupId) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragKey = groupId; dragOffset = 0f; localOrder = currentShown },
                                onDrag = { change, amount -> change.consume(); handleDrag(amount.y) },
                                onDragEnd = { endDrag() },
                                onDragCancel = { endDrag() },
                            )
                        },
                        exercise = ex,
                        sets = groupSets.sortedBy { it.setIndex },
                        previous = previous[ex.id].orEmpty(),
                        prBest = bestBefore[ex.id] ?: 0.0,
                        rest = rest,
                        now = now,
                        onAddSet = { scope.launch { Actions.addSet(workoutId, order) } },
                        onSwap = { pickerFor = PickerTarget.Swap(order, ex.id) },
                        onRemove = { scope.launch { Actions.removeExercise(workoutId, order) } },
                        onMove = { delta -> scope.launch { Actions.moveExercise(workoutId, order, delta) } },
                        onPlates = { weight -> plateTarget = ex to weight },
                        onHistory = { nav.go(Screen.ExerciseDetail(ex.id)) },
                        onOpenTimer = { showTimer = true },
                        onPickRest = { restPickerFor = ex },
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
    if (showTimer) RestTimerDialog(onDismiss = { showTimer = false })
    restPickerFor?.let { ex -> RestPickerDialog(ex, onDismiss = { restPickerFor = null }) }

    if (showFinish) {
        FinishDialog(
            unfinished = sets.count { !it.completed },
            onDismiss = { showFinish = false },
            onFinish = { rpe, notes ->
                showFinish = false
                RestTimer.stop(context)
                // Finish first, then navigate (leaving the screen would cancel the work).
                scope.launch {
                    withContext(NonCancellable) { Actions.finishWorkout(workoutId, rpe, notes) }
                    nav.replace(Screen.WorkoutDetail(workoutId))
                }
            },
        )
    }
    if (showDiscard) {
        ConfirmDialog(
            title = "Discard workout?",
            text = "This deletes this workout and all its sets.",
            confirm = "Discard",
            onConfirm = {
                RestTimer.stop(context)
                scope.launch {
                    withContext(NonCancellable) { Actions.discardWorkout(workoutId) }
                    nav.back()
                }
            },
            onDismiss = { showDiscard = false },
        )
    }
}

private sealed interface PickerTarget {
    data object Add : PickerTarget
    data class Swap(val order: Int, val exerciseId: Long) : PickerTarget
}

/** Header pill: live countdown while resting, otherwise a clock to start a timer by hand. */
@Composable
private fun RestPill(rest: RestTimer.State?, now: Long, onClick: () -> Unit) {
    if (rest != null) {
        val left = ((rest.endAt - now + 999) / 1000).coerceAtLeast(0)
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.testTag("restPill"),
        ) {
            Text(
                "⏱ ${fmtClock(left)}",
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
    } else {
        TextButton(onClick = onClick, modifier = Modifier.testTag("restPill")) { Text("⏱", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun ExerciseBlock(
    dragHandle: Modifier,
    exercise: Exercise,
    sets: List<WorkoutSet>,
    previous: List<WorkoutSet>,
    prBest: Double,
    rest: RestTimer.State?,
    now: Long,
    onAddSet: () -> Unit,
    onSwap: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    onPlates: (Double?) -> Unit,
    onHistory: () -> Unit,
    onOpenTimer: () -> Unit,
    onPickRest: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    SectionCard {
        // Long-press anywhere on this header row to pick the exercise up and drag it.
        Row(dragHandle.testTag("exerciseHeader"), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "≡",
                Modifier.padding(end = 8.dp),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                exercise.name,
                Modifier.weight(1f).clickable { menu = true },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
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
                    DropdownMenuItem(
                        text = { Text("Rest timer: ${if (exercise.restSeconds > 0) fmtClock(exercise.restSeconds.toLong()) else "off"}") },
                        onClick = { menu = false; onPickRest() },
                    )
                    DropdownMenuItem(text = { Text("History & PRs") }, onClick = { menu = false; onHistory() })
                    DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; onMove(-1) })
                    DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; onMove(1) })
                    DropdownMenuItem(text = { Text("Remove exercise") }, onClick = { menu = false; onRemove() })
                }
            }
        }
        Gap(4)
        // Column headings (Strong-style).
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            HeaderCell("SET", SetCol)
            Text(
                "PREVIOUS", Modifier.weight(1f).padding(start = 4.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val (first, second) = fieldLabels(exercise.type)
            HeaderCell(first, KgCol)
            Spacer(Modifier.width(ColGap))
            HeaderCell(second, SecondCol)
            Spacer(Modifier.width(ColGap))
            Spacer(Modifier.width(CheckCol))
        }
        var workingNumber = 0
        var workingIndex = 0
        sets.forEachIndexed { i, s ->
            val label = if (s.kind == "W") "W" else { workingNumber++; if (s.kind.isNotEmpty()) "$workingNumber${s.kind}" else "$workingNumber" }
            // Warm-ups aren't matched against last session's working sets.
            val prev = if (s.kind == "W") null else (previous.getOrNull(workingIndex) ?: previous.lastOrNull())
            if (s.kind != "W") workingIndex++
            val above = sets.getOrNull(i - 1)
            key(s.id) {
                SetRow(exercise, s, label, prev, above, prBest)
                if (exercise.restSeconds > 0) {
                    RestDivider(exercise, s, rest, now, onActiveClick = onOpenTimer, onPlannedClick = onPickRest)
                }
            }
        }
        TextButton(onClick = onAddSet, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("+ Add set") }
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp) {
    Text(
        text, Modifier.width(width), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
    )
}

private fun fieldLabels(type: String) = when (type) {
    ExerciseType.TIMED -> "KG" to "SEC"
    ExerciseType.LOAD_DISTANCE -> "KG" to "M"
    ExerciseType.BODYWEIGHT -> "±KG" to "REPS"
    else -> "KG" to "REPS"
}

/** Parses a template target like "5", "8-10", "30s", "20m" into a placeholder for the second field. */
private fun targetPlaceholder(target: String): String = Regex("\\d+(\\.\\d+)?").find(target)?.value ?: ""

@Composable
private fun SetRow(exercise: Exercise, set: WorkoutSet, label: String, prev: WorkoutSet?, above: WorkoutSet?, prBest: Double) {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val type = exercise.type

    fun secondOf(x: WorkoutSet?) = when (type) {
        ExerciseType.TIMED -> x?.seconds?.toString()
        ExerciseType.LOAD_DISTANCE -> x?.distanceM?.let { Calc.fmt(it) }
        else -> x?.reps?.toString()
    }

    var weight by remember(set.id) { mutableStateOf(Calc.fmt(set.weightKg)) }
    var second by remember(set.id) { mutableStateOf(secondOf(set).orEmpty()) }

    // Placeholders: last session's numbers, else the set above in this workout, else the template target.
    val weightHint = (prev?.weightKg ?: above?.weightKg)?.let { Calc.fmt(it) } ?: if (type == ExerciseType.BODYWEIGHT) "0" else ""
    val secondHint = secondOf(prev) ?: secondOf(above) ?: targetPlaceholder(set.target)

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

    fun toggle() {
        if (set.completed) {
            scope.launch { dao.updateSet(build(false, weight, second).copy(completedAt = null)) }
            if (RestTimer.state.value?.setId == set.id) RestTimer.stop(context)
        } else {
            // One tap: empty fields take the previous / target values.
            if (weight.isBlank()) weight = weightHint
            if (second.isBlank()) second = secondHint
            scope.launch { dao.updateSet(build(true, weight, second).copy(completedAt = System.currentTimeMillis())) }
            RestTimer.start(context, exercise.restSeconds, set.id)
        }
    }

    var menu by remember { mutableStateOf(false) }
    val done = set.completed
    val rowColor = if (done) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent
    Row(
        Modifier.fillMaxWidth().background(rowColor, RoundedCornerShape(10.dp)).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(SetCol)) {
            Text(
                label,
                Modifier.fillMaxWidth().clickable { menu = true }.padding(vertical = 10.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = if (set.kind == "W") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                listOf("" to "Normal set", "W" to "Warm-up", "D" to "Drop set", "F" to "To failure").forEach { (k, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { menu = false; scope.launch { dao.updateSet(set.copy(kind = k)) } })
                }
                DropdownMenuItem(text = { Text("Delete set") }, onClick = { menu = false; scope.launch { dao.deleteSet(set) } })
            }
        }
        // Previous: tap to copy last session's numbers into this set.
        val copyPrev = if (prev != null) Modifier.clickable {
            weight = Calc.fmt(prev.weightKg)
            second = secondOf(prev).orEmpty()
            save(weight, second)
        } else Modifier
        val isPr = done && set.kind != "W" && prBest > 0 && setScore(type, set) > prBest
        if (isPr) {
            Text(
                "🏆 PR",
                Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        } else {
            Text(
                prev?.let { describeSet(type, it) } ?: if (set.target.isNotEmpty()) "target ${set.target}" else "—",
                Modifier.weight(1f).then(copyPrev).padding(horizontal = 4.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SetInput(weight, { weight = it; save(it, second) }, weightHint, KgCol, decimal = true, imeAction = ImeAction.Next, done = done)
        Spacer(Modifier.width(ColGap))
        SetInput(second, { second = it; save(weight, it) }, secondHint, SecondCol, decimal = type == ExerciseType.LOAD_DISTANCE, imeAction = ImeAction.Done, done = done)
        Spacer(Modifier.width(ColGap))
        Box(
            Modifier
                .size(CheckCol, 44.dp)
                .background(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(10.dp))
                .clickable { toggle() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "✓",
                style = MaterialTheme.typography.titleLarge,
                color = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Compact filled number box (like Strong's), with the previous value as a grey placeholder. */
@Composable
private fun SetInput(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    width: Dp,
    decimal: Boolean,
    imeAction: ImeAction,
    done: Boolean,
) {
    val textStyle = MaterialTheme.typography.titleMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.SemiBold,
    )
    val boxColor = if (done) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceContainerHighest
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    BasicTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' || it == ',' || it == '-' }) },
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number, imeAction = imeAction),
        modifier = Modifier.width(width).height(44.dp),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize().background(boxColor, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, style = textStyle.copy(color = hintColor))
                inner()
            }
        },
    )
}

/**
 * The slim row between sets. Shows the planned rest ("2:00"); after the set above is ticked it
 * becomes a live countdown with a draining gold line. Tap the countdown for −15/+15/Skip.
 */
@Composable
private fun RestDivider(
    exercise: Exercise,
    set: WorkoutSet,
    rest: RestTimer.State?,
    now: Long,
    onActiveClick: () -> Unit,
    onPlannedClick: () -> Unit,
) {
    val activeRest = rest?.takeIf { it.setId == set.id }
    val track = MaterialTheme.colorScheme.outlineVariant
    val gold = MaterialTheme.colorScheme.primary
    val remainingMs = activeRest?.let { (it.endAt - now).coerceAtLeast(0) } ?: 0L
    val fraction = activeRest?.let { (remainingMs / (it.totalSeconds * 1000f)).coerceIn(0f, 1f) } ?: 0f
    Box(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clickable { if (activeRest != null) onActiveClick() else onPlannedClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val y = size.height / 2
            drawLine(track, Offset(0f, y), Offset(size.width, y), strokeWidth = if (activeRest != null) 6f else 2f, cap = StrokeCap.Round)
            if (activeRest != null) drawLine(gold, Offset(0f, y), Offset(size.width * fraction, y), strokeWidth = 6f, cap = StrokeCap.Round)
        }
        if (activeRest != null) {
            Surface(shape = RoundedCornerShape(50), color = gold) {
                Text(
                    fmtClock((remainingMs + 999) / 1000),
                    Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(
                    fmtClock(exercise.restSeconds.toLong()),
                    Modifier.padding(horizontal = 10.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (set.completed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val restPresets = listOf(30, 45, 60, 90, 120, 150, 180, 240, 300)

/** Big rest timer: ring countdown with −15 / +15 / Skip, or presets to start one by hand. */
@Composable
fun RestTimerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val rest by RestTimer.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(100) } }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val gold = MaterialTheme.colorScheme.primary
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Rest timer", style = MaterialTheme.typography.titleLarge)
                Gap(16)
                val r = rest
                if (r != null) {
                    val remainingMs = (r.endAt - now).coerceAtLeast(0)
                    val fraction = (remainingMs / (r.totalSeconds * 1000f)).coerceIn(0f, 1f)
                    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val stroke = 16.dp.toPx()
                            val inset = stroke / 2
                            val arcSize = Size(size.width - stroke, size.height - stroke)
                            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                            drawArc(gold, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(fmtClock((remainingMs + 999) / 1000), style = MaterialTheme.typography.displayMedium)
                            Muted("of ${fmtClock(r.totalSeconds.toLong())}")
                        }
                    }
                    Gap(20)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = { RestTimer.adjust(context, -15) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("−15s") }
                        FilledTonalButton(onClick = { RestTimer.adjust(context, 15) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("+15s") }
                    }
                    Gap(12)
                    BigButton("Skip", onClick = { RestTimer.stop(context); onDismiss() })
                } else {
                    Muted("No rest running. Start one:")
                    Gap(12)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        restPresets.forEach { s ->
                            OutlinedButton(onClick = { RestTimer.start(context, s, null) }) { Text(fmtClock(s.toLong())) }
                        }
                    }
                }
                Gap(8)
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

/** Choose the rest time after each set of an exercise (saved on the exercise). */
@Composable
private fun RestPickerDialog(exercise: Exercise, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rest after each set") },
        text = {
            Column {
                Muted(exercise.name)
                Gap(12)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf(0) + restPresets).forEach { s ->
                        val label = if (s == 0) "Off" else fmtClock(s.toLong())
                        val pick: () -> Unit = {
                            Graph.scope.launch { Graph.dao.updateExercise(exercise.copy(restSeconds = s)) }
                            onDismiss()
                        }
                        if (exercise.restSeconds == s) Button(onClick = pick) { Text(label) } else OutlinedButton(onClick = pick) { Text(label) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
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
