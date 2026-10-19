package com.muir.bear.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            item {
                SectionCard(if (p.templates.isEmpty()) "Nothing to import" else "This adds") {
                    p.templates.forEach { (name, count) -> StatLine(name, "$count exercises") }
                    if (p.newExercises.isNotEmpty()) {
                        Gap(8)
                        Muted("New to your library: ${p.newExercises.joinToString()}")
                    }
                    if (p.problems.isNotEmpty()) {
                        Gap(8)
                        p.problems.take(8).forEach { Text("· $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                    if (p.templates.isNotEmpty()) {
                        Gap(12)
                        BigButton("Import ${p.templates.size} template${if (p.templates.size == 1) "" else "s"}", enabled = !busy, onClick = {
                            val t = text ?: return@BigButton
                            work("Import") {
                                val r = DataIO.importTemplates(t)
                                preview = null
                                text = null
                                message = buildString {
                                    append("Added: ${r.templates.joinToString()}.")
                                    if (r.newExercises.isNotEmpty()) append("\nNew exercises: ${r.newExercises.joinToString()}")
                                    append("\nThey're on the Train tab. Tap Edit on one to fine-tune it.")
                                }
                            }
                        })
                    }
                }
            }
        }
        item {
            SectionCard("Format") {
                Muted("CSV: one row per exercise. Only template and exercise are needed; leave template blank to carry on the one above. Sets can be \"4\" or \"3x8-10\"; rest \"90\", \"90s\" or \"2:00\"; type weight, bodyweight, timed or distance.")
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
