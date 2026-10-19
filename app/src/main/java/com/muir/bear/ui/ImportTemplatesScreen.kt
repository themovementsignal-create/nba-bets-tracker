package com.muir.bear.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Checkbox
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import com.muir.bear.data.ExerciseType
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.muir.bear.ErrorLog
import com.muir.bear.io.DataIO
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A file's text handed to the import screen from elsewhere (the UI tour uses it; later, "Open with"). */
object TemplateFile {
    val pending = MutableStateFlow<String?>(null)
}

/**
 * Add training templates from a JSON or CSV file: pick it, see what it would add, then import.
 * Nothing already in the app is changed.
 */
@Composable
fun ImportTemplatesScreen(nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<DataIO.TemplatePreview?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    // Templates kept ticked in the review (all, to start with).
    var keep by remember(preview) { mutableStateOf(preview?.templates?.indices?.toSet().orEmpty()) }

    fun work(label: String, block: suspend () -> Unit) {
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: Exception) {
                ErrorLog.log("DATA", "$label failed", e)
                message = "$label failed: ${e.message ?: e.javaClass.simpleName}"
            }
            busy = false
        }
    }

    val handed by TemplateFile.pending.collectAsState()
    LaunchedEffect(handed) {
        val t = handed ?: return@LaunchedEffect
        TemplateFile.pending.value = null
        work("Reading the file") { text = t; preview = DataIO.previewTemplates(t); message = null }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) work("Reading the file") {
            val t = DataIO.readUri(context, uri)
            text = t
            preview = DataIO.previewTemplates(t)
            message = null
        }
    }
    val saveExample = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) work("Saving the example") {
            DataIO.writeToUri(context, uri, DataIO.TEMPLATE_EXAMPLE_CSV)
            message = "Example saved. Open it in a spreadsheet, change it, then choose it here."
        }
    }

    LogScaffold("Import templates", nav) {
        item {
            SectionCard("From a file") {
                Muted("A CSV (from any spreadsheet) or a JSON file. You'll see what it adds before anything is saved. Exercises are matched to your library by name; new ones are added for you.")
                Gap(12)
                BigButton("Choose file…", enabled = !busy, onClick = { pick.launch(arrayOf("text/*", "application/json", "application/*", "*/*")) })
                Gap(8)
                BigButton("Save an example CSV…", secondary = true, enabled = !busy, onClick = { saveExample.launch("bear-templates-example.csv") })
            }
        }
        preview?.let { p ->
            if (p.templates.isEmpty()) item {
                SectionCard("Nothing to import") {
                    if (p.problems.isEmpty()) Muted("No templates found in that file.")
                    p.problems.take(8).forEach { Text("· $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            } else {
                item {
                    SectionCard("Review before importing") {
                        Muted("Check each session reads right. Untick any you don't want. Nothing is saved until you tap Import.")
                        if (p.newExercises.isNotEmpty()) {
                            Gap(8)
                            Text("New to your library: ${p.newExercises.joinToString()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        if (p.problems.isNotEmpty()) {
                            Gap(8)
                            Muted("Skipped:")
                            p.problems.take(8).forEach { Text("· $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                p.templates.forEachIndexed { ti, t ->
                    item(key = "review-$ti") {
                        ReviewTemplate(t, included = ti in keep) { on -> keep = if (on) keep + ti else keep - ti }
                    }
                }
                item {
                    BigButton(
                        if (keep.isEmpty()) "Nothing ticked" else "Import ${keep.size} template${if (keep.size == 1) "" else "s"}",
                        enabled = !busy && keep.isNotEmpty(),
                        onClick = {
                            val t = text ?: return@BigButton
                            val chosen = keep
                            work("Import") {
                                val r = DataIO.importTemplates(t, chosen)
                                preview = null
                                text = null
                                message = buildString {
                                    append("Added: ${r.templates.joinToString()}.")
                                    if (r.newExercises.isNotEmpty()) append("\nNew exercises: ${r.newExercises.joinToString()}")
                                    append("\nThey're on the Train tab. Tap Edit on one to fine-tune it.")
                                }
                            }
                        },
                    )
                    Gap(4)
                    BigButton("Cancel", secondary = true, enabled = !busy, onClick = { preview = null; text = null })
                }
            }
        }
        item {
            SectionCard("Format") {
                Muted("CSV: one row per exercise. Only template and exercise are needed; leave template blank to carry on the one above. Sets can be \"4\" or \"3x8-10\"; rest \"90\", \"90s\" or \"2:00\"; type weight, bodyweight, timed or distance; superset a letter (exercises sharing one are done back to back); warmup yes for prep work.")
                Gap(6)
                SelectionContainer {
                    Text(
                        "template,exercise,sets,reps,rest\nSession 3,Back Squat,4,5,3:00\n,Chin-up,3,6-8,2:00",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
                Gap(6)
                Muted("JSON: {\"templates\": [{\"name\": \"Session 3\", \"exercises\": [{\"name\": \"Back Squat\", \"sets\": 4, \"reps\": \"5\", \"rest\": \"3:00\"}]}]}")
            }
        }
        if (busy) item { Row { CircularProgressIndicator() } }
        message?.let { item { SectionCard { SelectionContainer { Text(it) } } } }
    }
}

/** One template from the file, exercise by exercise, as it will be saved. */
@Composable
private fun ReviewTemplate(t: DataIO.PreviewTemplate, included: Boolean, onToggle: (Boolean) -> Unit) {
    val dim = if (included) 1f else 0.45f
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = included, onCheckedChange = onToggle)
            HGap(4)
            Column(Modifier.weight(1f)) {
                Text(t.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim))
                Muted("${t.items.size} exercises" + if (t.name != t.fileName.trim()) " · renamed: \"${t.fileName.trim()}\" already exists" else "")
            }
        }
        if (t.notes.isNotBlank()) { Gap(4); Muted(t.notes) }
        Gap(8)
        t.items.forEachIndexed { i, it ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                Text("${i + 1}", Modifier.width(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim))
                Column(Modifier.weight(1f)) {
                    Text(it.name, color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim))
                    Text(
                        "${it.sets} × ${it.target.ifBlank { "—" }} · rest ${fmtClock(it.restSeconds.toLong())} · ${typeLabel(it.type)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim),
                    )
                    val tags = listOfNotNull(
                        "Warm-up".takeIf { _ -> it.warmup },
                        it.superset.takeIf { s -> s.isNotEmpty() }?.let { s -> "Superset $s" },
                    )
                    if (tags.isNotEmpty()) Text(
                        tags.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = dim),
                    )
                    if (it.fileRestIgnored) Text(
                        "Keeps your usual rest for this exercise (the file asked for a different one).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f * dim),
                    )
                }
                HGap(8)
                val (tag, color) = when {
                    it.matched == null -> "New" to MaterialTheme.colorScheme.primary
                    it.matched.equals(it.name, ignoreCase = true) -> "In library" to MaterialTheme.colorScheme.onSurfaceVariant
                    else -> "= ${it.matched}" to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(tag, style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = dim), textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 140.dp))
            }
        }
    }
}

private fun typeLabel(type: String) = when (type) {
    ExerciseType.BODYWEIGHT -> "bodyweight"
    ExerciseType.TIMED -> "timed hold"
    ExerciseType.LOAD_DISTANCE -> "load × distance"
    else -> "weight × reps"
}
