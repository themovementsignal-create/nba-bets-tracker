@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.muir.bear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
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
import androidx.compose.material3.FilterChip
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
import com.muir.bear.Graph
import com.muir.bear.data.Exercise
import com.muir.bear.data.ExerciseType
import com.muir.bear.data.WorkoutSet
import com.muir.bear.domain.Calc
import com.muir.bear.domain.Rpe
import com.muir.bear.timer.RestTimer
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
    val finished by RestTimer.lastFinished.collectAsState()
    // Finished exercises fold to one line; tap one to open it again.
    val reopened = remember { mutableStateListOf<Long>() }
    var allExercises by remember { mutableStateOf<Map<Long, Exercise>>(emptyMap()) }
    LaunchedEffect(exercises) { allExercises = dao.allExercises().associateBy { it.id } }

    val exerciseIds = sets.map { it.exerciseId }.distinct()
    var previous by remember { mutableStateOf<Map<Long, List<WorkoutSet>>>(emptyMap()) }
    LaunchedEffect(exerciseIds) {
        previous = exerciseIds.associateWith { Actions.previousSets(it, workoutId) }
    }

    // Best score per exercise from earlier workouts, for live PR trophies.
    var bestBefore by remember { mutableStateOf<Map<Long, Double>>(emptyMap()) }
    // Effort-adjusted 1RM from the most recent session (last 6 weeks) where sets had an RPE.
    var formE1rm by remember { mutableStateOf<Map<Long, Double>>(emptyMap()) }
    LaunchedEffect(exerciseIds, allExercises.size) {
        val history = exerciseIds.associateWith { dao.history(it, workoutId) }
        bestBefore = exerciseIds.associateWith { id ->
            val type = allExercises[id]?.type ?: ExerciseType.WEIGHT_REPS
            history[id].orEmpty().filter { it.set.kind != "W" }.maxOfOrNull { setScore(type, it.set) } ?: 0.0
        }
        val since = System.currentTimeMillis() - 42 * 86_400_000L
        formE1rm = exerciseIds.mapNotNull { id ->
            if (allExercises[id]?.type != ExerciseType.WEIGHT_REPS) return@mapNotNull null
            val rated = history[id].orEmpty().filter {
                it.startedAt >= since && it.set.kind != "W" && it.set.rpe != null && (it.set.weightKg ?: 0.0) > 0 && (it.set.reps ?: 0) > 0
            }
            val last = rated.firstOrNull()?.set?.workoutId ?: return@mapNotNull null
            val e1rm = rated.filter { it.set.workoutId == last }.maxOf { Rpe.e1rm(it.set.weightKg!!, it.set.reps!!, it.set.rpe) }
            id to e1rm
        }.toMap()
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
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(w?.name ?: "Workout", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (w != null) {
                            val lifted = sets.filter { it.completed }.sumOf { setVolume(allExercises[it.exerciseId]?.type ?: ExerciseType.WEIGHT_REPS, it) }
                            Muted(fmtClock((now - w.startedAt) / 1000) + if (lifted > 0) " · ${fmtVolume(lifted)} lifted" else "")
                        }
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
                val progress by animateFloatAsState(if (sets.isEmpty()) 0f else done.toFloat() / sets.size, tween(500), label = "progress")
                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                    }
                    HGap(10)
                    Text("$done/${sets.size} sets", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // The exercise you're on: the first with a set still to do. It's the bright card.
            val currentGroup = shown.firstOrNull { gid -> groupsById.getValue(gid).any { !it.completed } }
            items(shown, key = { it }) { groupId ->
                val groupSets = groupsById.getValue(groupId)
                val ids = groupSets.map { it.id }
                // Folds once its last rest (and the buzzer) is over, so the shot clock stays in view.
                val allDone = groupSets.isNotEmpty() && groupSets.all { it.completed } &&
                    rest?.setId?.let { it in ids } != true &&
                    finished?.let { f -> f.setId?.let { it in ids } == true && now - f.at < 2_500 } != true
                // While resting, everything but the exercise you're on steps back.
                val dim by animateFloatAsState(if (rest != null && groupId != currentGroup) 0.45f else 1f, tween(400), label = "dim")
                val order = groupSets.first().exerciseOrder
                val ex = allExercises[groupSets.first().exerciseId]
                val dragging = dragKey == groupId
                val itemModifier = if (dragging) {
                    Modifier.zIndex(1f).graphicsLayer { translationY = dragOffset; shadowElevation = 24f; scaleX = 1.02f; scaleY = 1.02f }
                } else if (Motion.animateLists) {
                    Modifier.animateItem()
                } else {
                    Modifier
                }
                if (ex != null && allDone && groupId !in reopened && dragKey == null) Box(itemModifier.alpha(dim)) {
                    FoldedExercise(ex, groupSets, previous[ex.id].orEmpty()) { reopened += groupId }
                } else if (ex != null) Box(itemModifier.alpha(dim)) {
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
                        formE1rm = formE1rm[ex.id],
                        rest = rest,
                        finished = finished,
                        highlight = groupId == currentGroup,
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
    // Rest over: the screen edge glows bronze for a moment, with the horn and a long buzz.
    BuzzerGlow(finished)
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
    formE1rm: Double?,
    rest: RestTimer.State?,
    finished: RestTimer.Finished?,
    highlight: Boolean,
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
    val primary = MaterialTheme.colorScheme.primary
    val shape = MaterialTheme.shapes.medium
    Column(
        Modifier.fillMaxWidth()
            .bentoSurface(if (highlight) primary else glowFor(exercise.name), glowAlpha = if (highlight) 0.20f else 0.10f)
            .then(if (highlight) Modifier.border(1.dp, primary.copy(alpha = 0.25f), shape) else Modifier)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        // Long-press anywhere on this header row to pick the exercise up and drag it.
        Row(dragHandle.testTag("exerciseHeader"), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "≡",
                Modifier.padding(end = 8.dp),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f).clickable { menu = true }) {
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (highlight) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                )
                val working = sets.count { it.kind != "W" }
                Text(
                    "$working sets" + if (exercise.restSeconds > 0) " · rest ${fmtClock(exercise.restSeconds.toLong())}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
        // Suggested load from your recent effort ratings: reps from the plan, aiming for RPE 8.
        if (formE1rm != null) {
            val reps = sets.firstOrNull { it.kind != "W" && !it.completed }?.let { s ->
                targetPlaceholder(s.target).toDoubleOrNull()?.toInt()
            } ?: previous.firstOrNull()?.reps
            val load = reps?.let { Rpe.suggestedLoad(formE1rm, it, 8.0) }
            if (reps != null && load != null && load > 0) {
                Text(
                    "Suggested: ${Calc.fmt(load)} kg × $reps @ 8",
                    Modifier.padding(start = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
        // Just after the buzzer, the next set to do is outlined for a few seconds.
        val buzzedIdx = finished?.takeIf { now - it.at < 6_000 }?.let { f -> sets.indexOfFirst { it.id == f.setId } } ?: -1
        val nextUpId = if (buzzedIdx >= 0) sets.drop(buzzedIdx + 1).firstOrNull { !it.completed }?.id else null
        val labels = run {
            var n = 0
            sets.map { s -> if (s.kind == "W") "W" else { n++; if (s.kind.isNotEmpty()) "$n${s.kind}" else "$n" } }
        }
        sets.forEachIndexed { i, s ->
            val label = if (s.kind == "W") "W" else { workingNumber++; if (s.kind.isNotEmpty()) "$workingNumber${s.kind}" else "$workingNumber" }
            // Warm-ups aren't matched against last session's working sets.
            val prev = if (s.kind == "W") null else (previous.getOrNull(workingIndex) ?: previous.lastOrNull())
            if (s.kind != "W") workingIndex++
            val above = sets.getOrNull(i - 1)
            key(s.id) {
                SetRow(exercise, s, label, prev, above, prBest, isNext = s.id == nextUpId)
                // Optional effort, asked right after ticking a working set; gone once you move on.
                val askRpe = s.completed && s.rpe == null && s.kind != "W" &&
                    (exercise.type == ExerciseType.WEIGHT_REPS || exercise.type == ExerciseType.BODYWEIGHT) &&
                    sets.getOrNull(i + 1)?.completed != true &&
                    now - (s.completedAt ?: 0L) < 10 * 60_000L
                if (askRpe) RpePrompt(s)
                if (exercise.restSeconds > 0) {
                    // What comes after this rest, for the shot clock and the buzzer.
                    val nextIdx = (i + 1 until sets.size).firstOrNull { !sets[it].completed }
                    val nextLabel = nextIdx?.let { j ->
                        val n = sets[j]
                        val kg = (n.weightKg ?: s.weightKg)?.let { Calc.fmt(it) }
                        val reps = n.reps ?: s.reps ?: targetPlaceholder(n.target).toIntOrNull()
                        "Set ${labels[j]}" + if (exercise.type == ExerciseType.WEIGHT_REPS && kg != null && reps != null) " · $kg kg × $reps" else ""
                    }
                    RestDivider(exercise, s, rest, finished, now, nextLabel, onActiveClick = onOpenTimer, onPlannedClick = onPickRest)
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
private fun SetRow(exercise: Exercise, set: WorkoutSet, label: String, prev: WorkoutSet?, above: WorkoutSet?, prBest: Double, isNext: Boolean = false) {
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
            thunk(context)
            RestTimer.start(context, exercise.restSeconds, set.id)
        }
    }

    var menu by remember { mutableStateOf(false) }
    var rpeMenu by remember { mutableStateOf(false) }
    if (rpeMenu) RpeDialog(set.rpe, onDismiss = { rpeMenu = false }) { v ->
        rpeMenu = false
        scope.launch { dao.updateSet(set.copy(rpe = v)) }
    }
    val done = set.completed
    val isPr = done && set.kind != "W" && prBest > 0 && setScore(type, set) > prBest
    // Ticking fills the row with bronze from left to right; a new best gets one soft shimmer.
    val fill = remember(set.id) { Animatable(if (done) 1f else 0f) }
    LaunchedEffect(done) { if (done) fill.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) else fill.snapTo(0f) }
    val wasPr = remember(set.id) { isPr }
    val shimmer = remember(set.id) { Animatable(0f) }
    LaunchedEffect(isPr) { if (isPr && !wasPr) { shimmer.snapTo(0f); shimmer.animateTo(1f, tween(1_100, easing = FastOutSlowInEasing)) } }
    val primary = MaterialTheme.colorScheme.primary
    Column {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .drawBehind {
                val r = CornerRadius(14.dp.toPx())
                if (fill.value > 0f) drawRoundRect(primary.copy(alpha = 0.18f), size = Size(size.width * fill.value, size.height), cornerRadius = r)
                val v = shimmer.value
                if (v > 0f && v < 1f) {
                    val band = size.width * 0.35f
                    val x = -band + (size.width + band) * v
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, primary.copy(alpha = 0.45f), Color.Transparent), startX = x, endX = x + band))
                }
            }
            .then(if (isNext) Modifier.border(1.5.dp, primary, RoundedCornerShape(14.dp)) else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(SetCol)) {
            Column(
                Modifier.fillMaxWidth().clickable { menu = true }.padding(vertical = if (set.rpe != null) 4.dp else 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = if (set.kind == "W") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                )
                set.rpe?.let {
                    Text("@${Calc.fmt(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                listOf("" to "Normal set", "W" to "Warm-up", "D" to "Drop set", "F" to "To failure").forEach { (k, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { menu = false; scope.launch { dao.updateSet(set.copy(kind = k)) } })
                }
                if (set.kind != "W") {
                    DropdownMenuItem(
                        text = { Text(if (set.rpe == null) "Set effort (RPE)…" else "Effort: RPE ${Calc.fmt(set.rpe)}…") },
                        onClick = { menu = false; rpeMenu = true },
                    )
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
            // Beat last time's set: a small bronze ▲.
            val beat = done && prev != null && set.kind != "W" && setScore(type, set) > setScore(type, prev) + 1e-9
            Row(Modifier.weight(1f).then(copyPrev).padding(horizontal = 4.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    prev?.let { describeSet(type, it) } ?: if (set.target.isNotEmpty()) "target ${set.target}" else "—",
                    Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (beat) Text(" ▲", style = MaterialTheme.typography.bodyMedium, color = primary, fontWeight = FontWeight.SemiBold)
            }
        }
        SetInput(weight, { weight = it; save(it, second) }, weightHint, KgCol, decimal = true, imeAction = ImeAction.Next, done = done)
        Spacer(Modifier.width(ColGap))
        SetInput(second, { second = it; save(weight, it) }, secondHint, SecondCol, decimal = type == ExerciseType.LOAD_DISTANCE, imeAction = ImeAction.Done, done = done)
        Spacer(Modifier.width(ColGap))
        Box(
            Modifier
                .size(CheckCol, 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
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
    // A new best on a lifted set: say what moved, in one line.
    if (isPr && type == ExerciseType.WEIGHT_REPS) {
        Text(
            "est. 1RM ${Calc.fmt(Calc.round2(prBest))} → ${Calc.fmt(Calc.round2(setScore(type, set)))} kg",
            Modifier.padding(start = SetCol + 4.dp, bottom = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = primary,
        )
    }
    }
}

/** One firm buzz when a set is ticked, like a hammer landing. */
private fun thunk(context: android.content.Context) {
    runCatching {
        val v: android.os.Vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(android.os.VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
        v.vibrate(
            if (android.os.Build.VERSION.SDK_INT >= 29) android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_HEAVY_CLICK)
            else android.os.VibrationEffect.createOneShot(35, 255),
        )
    }
}

/** A finished exercise, folded to one line: what you did and how it compares with last time. */
@Composable
private fun FoldedExercise(exercise: Exercise, sets: List<WorkoutSet>, previous: List<WorkoutSet>, onOpen: () -> Unit) {
    val type = exercise.type
    val working = sets.filter { it.kind != "W" }
    val top = working.maxByOrNull { setScore(type, it) }
    val prevTop = previous.filter { it.kind != "W" }.maxByOrNull { setScore(type, it) }
    val change = when {
        top == null || prevTop == null -> null
        type == ExerciseType.WEIGHT_REPS && (top.weightKg ?: 0.0) > (prevTop.weightKg ?: 0.0) -> "▲ ${Calc.fmt(Calc.round2((top.weightKg ?: 0.0) - (prevTop.weightKg ?: 0.0)))} kg on last time"
        setScore(type, top) > setScore(type, prevTop) + 1e-9 -> "▲ on last time"
        setScore(type, top) < setScore(type, prevTop) - 1e-9 -> "lighter than last time"
        else -> "same as last time"
    }
    val primary = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().bentoSurface(glowFor(exercise.name), onOpen, glowAlpha = 0.08f).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
            Row {
                Text(
                    "${working.size} sets" + (top?.let { " · best ${describeSet(type, it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                change?.let {
                    Text(" · $it", style = MaterialTheme.typography.bodySmall, color = if (it.startsWith("▲")) primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Canvas(Modifier.size(26.dp)) {
            drawCircle(Color(0xFF332B1F))
            val w = size.width
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.30f, w * 0.52f); lineTo(w * 0.45f, w * 0.66f); lineTo(w * 0.71f, w * 0.38f)
            }
            drawPath(path, primary, style = Stroke(w * 0.1f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}

/** The screen edge glows bronze for a moment when rest runs out. */
@Composable
private fun BuzzerGlow(finished: RestTimer.Finished?) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(finished?.at) {
        val f = finished ?: return@LaunchedEffect
        if (System.currentTimeMillis() - f.at > 2_000) return@LaunchedEffect
        a.snapTo(1f)
        a.animateTo(0f, tween(1_300, easing = FastOutSlowInEasing))
    }
    val primary = MaterialTheme.colorScheme.primary
    if (a.value > 0f) Canvas(Modifier.fillMaxSize()) {
        drawRect(primary.copy(alpha = 0.9f * a.value), style = Stroke(4.dp.toPx()))
        drawRect(primary.copy(alpha = 0.25f * a.value), style = Stroke(28.dp.toPx()))
    }
}

/**
 * Lets the UI test switch off list-item move animations: they start inside a layout pass, which
 * the Compose test clock can't handle (issuetracker.google.com/issues/325299275). Always on in the app.
 */
object Motion {
    /** Off in tests: animations keep Compose busy and make UI tests flaky. */
    @Volatile var animateLists = true
    /** When the current screen opened: cards only animate in just after that, never on scroll. */
    @Volatile var openedAt = 0L
    /** Cards animated since then, for a small stagger. */
    @Volatile var risen = 0

    fun screenOpened() { openedAt = android.os.SystemClock.uptimeMillis(); risen = 0 }
    fun justOpened(windowMs: Long = 900): Boolean = animateLists && android.os.SystemClock.uptimeMillis() - openedAt < windowMs
}

private val RpeValues = listOf(6.0, 7.0, 8.0, 9.0, 10.0)

/** Slim, optional effort row under a just-ticked set: one tap, or ignore it. */
@Composable
private fun RpePrompt(set: WorkoutSet) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, top = 2.dp, bottom = 2.dp).testTag("rpePrompt"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("How hard?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("10 = nothing left · 8 = 2 reps left", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
        RpeValues.forEach { v ->
            Box(
                Modifier.padding(start = 4.dp).size(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                    .clickable { Graph.scope.launch { Graph.dao.updateSet(set.copy(rpe = v)) } },
                contentAlignment = Alignment.Center,
            ) { Text(Calc.fmt(v), style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun RpeDialog(current: Double?, onDismiss: () -> Unit, onPick: (Double?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Effort (RPE)") },
        text = {
            Column {
                Muted("How many more reps could you have done? 10 = none, 9 = one, 8 = two, 7 = three, 6 = four or more.")
                Gap(8)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(6.0, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0).forEach { v ->
                        FilterChip(selected = v == current, onClick = { onPick(v) }, label = { Text(Calc.fmt(v)) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { if (current != null) TextButton(onClick = { onPick(null) }) { Text("Clear") } },
    )
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
    // Big and light, readable at arm's length.
    val textStyle = MaterialTheme.typography.headlineSmall.copy(
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Light,
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
        modifier = Modifier.width(width).height(48.dp),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize().background(boxColor, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, style = textStyle.copy(color = hintColor))
                inner()
            }
        },
    )
}

/**
 * Between sets. Planned: a slim "1:30" pill. After the set above is ticked: a shot clock, with big
 * numbers, a draining bar and what's next; the last five seconds count in bronze. When it runs out,
 * the buzzer: a bronze "TIME" flash (with the horn and a long buzz from the timer service).
 */
@Composable
private fun RestDivider(
    exercise: Exercise,
    set: WorkoutSet,
    rest: RestTimer.State?,
    finished: RestTimer.Finished?,
    now: Long,
    nextLabel: String?,
    onActiveClick: () -> Unit,
    onPlannedClick: () -> Unit,
) {
    val context = LocalContext.current
    val activeRest = rest?.takeIf { it.setId == set.id }
    val justBuzzed = finished?.takeIf { it.setId == set.id && now - it.at < 2_000 }
    when {
        activeRest != null -> ShotClock(activeRest, now, nextLabel, onActiveClick)
        justBuzzed != null -> BuzzerFlash(justBuzzed, nextLabel)
        else -> Box(
            Modifier.fillMaxWidth().height(30.dp).clickable { onPlannedClick() },
            contentAlignment = Alignment.Center,
        ) {
            val track = MaterialTheme.colorScheme.outlineVariant
            Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                drawLine(track, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2f, cap = StrokeCap.Round)
            }
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

@Composable
private fun ShotClock(r: RestTimer.State, now: Long, nextLabel: String?, onOpen: () -> Unit) {
    val context = LocalContext.current
    // Smooth ticking on the phone; in tests the screen's own 4-a-second clock is enough.
    var frameNow by remember { mutableLongStateOf(now) }
    if (Motion.animateLists) LaunchedEffect(r.endAt) { while (true) { withFrameMillis { }; frameNow = System.currentTimeMillis() } }
    val t = maxOf(now, frameNow)
    val remainingMs = (r.endAt - t).coerceAtLeast(0)
    val fraction = (remainingMs / (r.totalSeconds * 1000f)).coerceIn(0f, 1f)
    val secs = (remainingMs + 999) / 1000
    val hot = secs in 1..5
    // Each of the last five seconds lands with a small pulse, like a shot clock running out.
    val withinSecond = (remainingMs % 1000) / 1000f
    val pulse = if (hot) 1f + 0.12f * withinSecond * withinSecond else 1f
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .clip(shape).background(Color(0xFF141413)).border(1.dp, primary.copy(alpha = 0.25f), shape)
            .clickable { onOpen() }
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Rest", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf("−15 s" to -15, "+30 s" to 30).forEach { (label, d) ->
                TextButton(onClick = { RestTimer.adjust(context, d) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(label, style = MaterialTheme.typography.labelMedium) }
            }
            TextButton(onClick = { RestTimer.stop(context) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Skip rest", style = MaterialTheme.typography.labelMedium) }
        }
        Text(
            fmtClock(secs),
            Modifier.align(Alignment.CenterHorizontally).graphicsLayer { scaleX = pulse; scaleY = pulse }.testTag("shotClock"),
            style = MaterialTheme.typography.displayMedium,
            color = if (hot) primary else MaterialTheme.colorScheme.onSurface,
        )
        Box(Modifier.padding(end = 10.dp, top = 6.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(primary))
        }
        nextLabel?.let {
            Gap(8)
            Text("Next: $it", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The buzzer: a bronze card that says it's time, then fades. */
@Composable
private fun BuzzerFlash(f: RestTimer.Finished, nextLabel: String?) {
    val a = remember(f.at) { Animatable(1f) }
    LaunchedEffect(f.at) {
        delay(1_100)
        a.animateTo(0f, tween(400))
    }
    if (a.value <= 0f) return
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).graphicsLayer { alpha = a.value }
            .clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primary).padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("TIME", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = onPrimary, letterSpacing = 4.sp)
        nextLabel?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = onPrimary) }
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
                Gap(16)
                // The buzzer: arena horn (default), a beep, or just the buzz.
                var sound by remember { mutableStateOf(RestTimer.sound) }
                Muted("When rest ends")
                Gap(6)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RestTimer.Sound.entries.forEach { snd ->
                        FilterChip(
                            selected = sound == snd,
                            onClick = {
                                sound = snd
                                RestTimer.sound = snd
                                Graph.scope.launch { Graph.dao.putSetting(com.muir.bear.data.Setting(com.muir.bear.data.Settings.REST_SOUND, snd.id)) }
                                if (snd == RestTimer.Sound.HORN) com.muir.bear.timer.Buzzer.horn(context)
                            },
                            label = { Text(snd.label) },
                        )
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
