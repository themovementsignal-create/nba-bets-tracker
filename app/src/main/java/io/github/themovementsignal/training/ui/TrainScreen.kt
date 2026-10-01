package io.github.themovementsignal.training.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.themovementsignal.training.Graph
import io.github.themovementsignal.training.data.Exercise
import io.github.themovementsignal.training.data.Setting
import io.github.themovementsignal.training.data.Settings
import io.github.themovementsignal.training.data.Template
import io.github.themovementsignal.training.data.TemplateExercise
import io.github.themovementsignal.training.data.TemplateKind
import io.github.themovementsignal.training.data.Venue
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TrainScreen(nav: Nav) {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val templates by dao.templates().collectAsState(initial = emptyList())
    val active by dao.activeWorkout().collectAsState(initial = null)
    val venues by dao.venues().collectAsState(initial = emptyList())
    val activeVenueId by dao.settingFlow(Settings.ACTIVE_VENUE).collectAsState(initial = null)
    var newTemplate by remember { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf<Template?>(null) }

    fun start(t: Template?) {
        if (t?.kind == TemplateKind.ACTIVITY) {
            nav.go(Screen.Activity); return
        }
        scope.launch {
            val id = Actions.startWorkout(t?.id)
            nav.go(Screen.Workout(id))
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Train", style = MaterialTheme.typography.headlineMedium) }
        active?.let { w ->
            item {
                SectionCard("Workout in progress", onClick = { nav.go(Screen.Workout(w.id)) }) {
                    Text(w.name, style = MaterialTheme.typography.titleLarge)
                    Muted("Started ${fmtTime(w.startedAt)} · tap to resume")
                }
            }
        }
        item {
            VenuePicker(venues, activeVenueId?.toLongOrNull()) { v ->
                scope.launch { dao.putSetting(Setting(Settings.ACTIVE_VENUE, v.id.toString())) }
            }
        }
        items(templates, key = { it.id }) { t ->
            TemplateCard(
                t,
                onStart = { if (active != null && t.kind != TemplateKind.ACTIVITY) confirmReplace = t else start(t) },
                onEdit = { nav.go(Screen.TemplateEdit(t.id)) },
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton("Start empty workout", onClick = { if (active != null) confirmReplace = Template(name = "") else start(null) }, secondary = true)
                BigButton("+ New template", onClick = { newTemplate = true }, secondary = true)
                BigButton("Exercise library", onClick = { nav.go(Screen.Exercises) }, secondary = true)
                BigButton("Plate calculator", onClick = { nav.go(Screen.PlateCalc) }, secondary = true)
            }
        }
    }

    confirmReplace?.let { t ->
        AlertDialog(
            onDismissRequest = { confirmReplace = null },
            title = { Text("Workout already in progress") },
            text = { Text("Resume it, or start a new one alongside? (The current one stays unfinished until you finish or discard it.)") },
            confirmButton = {
                TextButton(onClick = { confirmReplace = null; active?.let { nav.go(Screen.Workout(it.id)) } }) { Text("Resume") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReplace = null; start(if (t.name.isEmpty()) null else t) }) { Text("Start new") }
            },
        )
    }

    if (newTemplate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newTemplate = false },
            title = { Text("New template") },
            text = { TextInput(name, { name = it }, "Name") },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    newTemplate = false
                    scope.launch {
                        val id = dao.insertTemplate(Template(name = name.trim(), sortOrder = templates.size))
                        nav.go(Screen.TemplateEdit(id))
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { newTemplate = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun VenuePicker(venues: List<Venue>, activeId: Long?, onPick: (Venue) -> Unit) {
    if (venues.isEmpty()) return
    SectionCard("Training at") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            venues.forEach { v ->
                val label = v.name + if (v.isTravel) " ✈" else ""
                if (v.id == activeId) {
                    Button(onClick = { onPick(v) }, modifier = Modifier.weight(1f)) { Text(label, maxLines = 1) }
                } else {
                    OutlinedButton(onClick = { onPick(v) }, modifier = Modifier.weight(1f)) { Text(label, maxLines = 1) }
                }
            }
        }
        Gap(4)
        Muted("Exercises you can't do here are swapped for an alternative automatically.")
    }
}

@Composable
private fun TemplateCard(t: Template, onStart: () -> Unit, onEdit: () -> Unit) {
    val dao = Graph.dao
    val items by dao.templateExercises(t.id).collectAsState(initial = emptyList())
    var names by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    LaunchedEffect(items) { names = dao.allExercises().associate { it.id to it.name } }
    SectionCard(t.name) {
        if (t.kind == TemplateKind.ACTIVITY) {
            Muted("Log basketball or a conditioning session (duration + RPE).")
        } else if (items.isEmpty()) {
            Muted("No exercises yet. Tap Edit to add some.")
        } else {
            items.forEach { te -> Muted("${te.sets} × ${names[te.exerciseId] ?: "…"}" + if (te.target.isNotEmpty()) " (${te.target})" else "") }
        }
        Gap(8)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                Text(if (t.kind == TemplateKind.ACTIVITY) "Log session" else "Start")
            }
            if (t.kind != TemplateKind.ACTIVITY) OutlinedButton(onClick = onEdit) { Text("Edit") }
        }
    }
}

@Composable
fun TemplateEditScreen(templateId: Long, nav: Nav) {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    var template by remember { mutableStateOf<Template?>(null) }
    LaunchedEffect(templateId) { template = dao.template(templateId) }
    val items by dao.templateExercises(templateId).collectAsState(initial = emptyList())
    val exercises by dao.exercises().collectAsState(initial = emptyList())
    val names = exercises.associate { it.id to it.name }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var name by remember(template) { mutableStateOf(template?.name.orEmpty()) }

    Scaffold(topBar = {
        AppTopBar("Edit template", onBack = { nav.back() }) {
            DangerTextButton("Delete") { confirmDelete = true }
        }
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                TextInput(name, {
                    name = it
                    template?.let { t -> scope.launch { dao.updateTemplate(t.copy(name = it)) } }
                }, "Template name")
            }
            items(items, key = { it.id }) { te ->
                TemplateItemRow(
                    te, names[te.exerciseId] ?: "…",
                    onChange = { scope.launch { dao.updateTemplateExercise(it) } },
                    onDelete = { scope.launch { dao.deleteTemplateExercise(te.id) } },
                    onMove = { delta ->
                        val idx = items.indexOf(te)
                        val other = items.getOrNull(idx + delta) ?: return@TemplateItemRow
                        scope.launch {
                            dao.updateTemplateExercise(te.copy(position = other.position))
                            dao.updateTemplateExercise(other.copy(position = te.position))
                        }
                    },
                )
            }
            item { BigButton("+ Add exercise", onClick = { picking = true }, secondary = true) }
        }
    }
    if (picking) {
        ExercisePickerDialog("Add to template", onDismiss = { picking = false }, onPick = { e: Exercise ->
            picking = false
            scope.launch {
                dao.insertTemplateExercise(
                    TemplateExercise(templateId = templateId, exerciseId = e.id, position = (items.maxOfOrNull { it.position } ?: -1) + 1)
                )
            }
        })
    }
    if (confirmDelete) {
        ConfirmDialog("Delete template?", "Past workouts are kept.", onConfirm = {
            scope.launch {
                withContext(NonCancellable) { dao.deleteTemplateExercises(templateId); dao.deleteTemplate(templateId) }
                nav.back()
            }
        }, onDismiss = { confirmDelete = false })
    }
}

@Composable
private fun TemplateItemRow(te: TemplateExercise, name: String, onChange: (TemplateExercise) -> Unit, onDelete: () -> Unit, onMove: (Int) -> Unit) {
    var sets by remember(te.id) { mutableStateOf(te.sets.toString()) }
    var target by remember(te.id) { mutableStateOf(te.target) }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onMove(-1) }) { Text("▲") }
            TextButton(onClick = { onMove(1) }) { Text("▼") }
            DangerTextButton("✕", onDelete)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(sets, {
                sets = it
                it.toIntOrNull()?.takeIf { n -> n in 1..20 }?.let { n -> onChange(te.copy(sets = n, target = target)) }
            }, "Sets", Modifier.width(100.dp), decimal = false)
            androidx.compose.material3.OutlinedTextField(
                value = target,
                onValueChange = { target = it; onChange(te.copy(target = it, sets = sets.toIntOrNull() ?: te.sets)) },
                label = { Text("Target (e.g. 5, 8-10, 30s, 20m)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
