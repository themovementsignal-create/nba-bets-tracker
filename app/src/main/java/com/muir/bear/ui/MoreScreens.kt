package com.muir.bear.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muir.bear.BuildConfig
import com.muir.bear.ErrorLog
import com.muir.bear.Graph
import com.muir.bear.data.Bar
import com.muir.bear.data.Equipment
import com.muir.bear.data.Setting
import com.muir.bear.data.Settings
import com.muir.bear.data.Venue
import com.muir.bear.domain.Calc
import com.muir.bear.domain.ProgramReview
import com.muir.bear.io.DataIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun MoreScreen(nav: Nav) {
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("More", style = MaterialTheme.typography.headlineMedium) }
        item { MenuGroup("Training", listOf("Exercise library" to Screen.Exercises, "Plate calculator & bars" to Screen.PlateCalc, "Gyms & travel mode" to Screen.Venues, "Jump height test" to Screen.JumpTest), nav) }
        item {
            MenuGroup(
                "Logs",
                listOf(
                    "Morning check-in" to Screen.CheckIn, "Sleep" to Screen.Sleep, "Bodyweight" to Screen.Bodyweight,
                    "Protein" to Screen.Protein, "Supplements" to Screen.Supplements, "Sauna" to Screen.Sauna,
                    "NEAT · treadmill" to Screen.Neat, "Conditioning" to Screen.Activity,
                    "Niggles" to Screen.Niggle, "Gear" to Screen.Gear,
                ),
                nav,
            )
        }
        item { MenuGroup("App", listOf("Targets & settings" to Screen.Settings, "Backup, export & import" to Screen.Data, "Error log" to Screen.Errors, "About" to Screen.About), nav) }
        item { Muted("Build ${BuildConfig.VERSION_CODE} · v${BuildConfig.VERSION_NAME}", Modifier.padding(8.dp)) }
    }
}

@Composable
private fun MenuGroup(title: String, items: List<Pair<String, Screen>>, nav: Nav) {
    SectionCard(title) {
        items.forEach { (label, screen) ->
            TextButton(onClick = { nav.go(screen) }, modifier = Modifier.fillMaxWidth()) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text("›")
            }
        }
    }
}

// ---------- Plate calculator ----------

@Composable
fun PlateCalcContent(initialWeight: Double?, initialBarId: Long?) {
    val dao = Graph.dao
    val bars by dao.bars().collectAsState(initial = emptyList())
    val platesSetting by dao.settingFlow(Settings.PLATES).collectAsState(initial = Settings.DEFAULT_PLATES)
    var target by remember { mutableStateOf(initialWeight?.let { Calc.fmt(it) } ?: "100") }
    var barId by remember { mutableStateOf(initialBarId) }
    val bar = bars.firstOrNull { it.id == barId } ?: bars.firstOrNull()
    val plates = Calc.parsePlates(platesSetting ?: Settings.DEFAULT_PLATES)

    Column {
        NumberField(target, { target = it }, "Target weight (kg)", Modifier.fillMaxWidth())
        Gap(8)
        ChipGroup(bars.map { it.id.toString() }, setOfNotNull(bar?.id?.toString()), { id ->
            bars.firstOrNull { it.id.toString() == id }?.let { "${it.name} ${Calc.fmt(it.weightKg)}" } ?: id
        }) { barId = it.toLongOrNull() }
        Gap(12)
        val t = Calc.parseNumber(target)
        if (t != null && bar != null) {
            val r = Calc.plates(t, bar.weightKg, plates)
            Text("Each side:", style = MaterialTheme.typography.titleMedium)
            Text(
                if (r.perSide.isEmpty()) "Just the bar" else r.perSide.joinToString("  ") { Calc.fmt(it) },
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            )
            Muted("Loaded: ${Calc.fmt(r.achieved)} kg" + if (r.remainder > 0.001) " (${Calc.fmt(r.remainder)} kg short of target)" else "")
        }
        Gap(4)
        Muted("Plates available: ${plates.joinToString(", ") { Calc.fmt(it) }}")
    }
}

@Composable
fun PlateCalcDialog(initialWeight: Double?, initialBarId: Long?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Plate calculator") },
        text = { PlateCalcContent(initialWeight, initialBarId) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun PlateCalcScreen(nav: Nav) {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val bars by dao.bars().collectAsState(initial = emptyList())
    val platesSetting by dao.settingFlow(Settings.PLATES).collectAsState(initial = null)
    var platesText by remember(platesSetting) { mutableStateOf(platesSetting ?: Settings.DEFAULT_PLATES) }
    var newBarName by remember { mutableStateOf("") }
    var newBarKg by remember { mutableStateOf("") }

    LogScaffold("Plate calculator", nav) {
        item { SectionCard { PlateCalcContent(null, null) } }
        item {
            SectionCard("Bars") {
                bars.forEach { b ->
                    var kg by remember(b.id) { mutableStateOf(Calc.fmt(b.weightKg)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(b.name, Modifier.weight(1f))
                        NumberField(kg, {
                            kg = it
                            Calc.parseNumber(it)?.let { w -> scope.launch { dao.updateBar(b.copy(weightKg = w)) } }
                        }, "kg", Modifier.width(96.dp))
                        TextButton(onClick = { scope.launch { dao.deleteBar(b.id) } }) { Text("✕", color = MaterialTheme.colorScheme.error) }
                    }
                }
                Gap(8)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.OutlinedTextField(newBarName, { newBarName = it }, label = { Text("New bar") }, singleLine = true, modifier = Modifier.weight(1f))
                    HGap(4)
                    NumberField(newBarKg, { newBarKg = it }, "kg", Modifier.width(80.dp))
                }
                TextButton(enabled = newBarName.isNotBlank() && Calc.parseNumber(newBarKg) != null, onClick = {
                    scope.launch { dao.insertBar(Bar(name = newBarName.trim(), weightKg = Calc.parseNumber(newBarKg)!!)); newBarName = ""; newBarKg = "" }
                }) { Text("+ Add bar") }
            }
        }
        item {
            SectionCard("Plates you have (kg, comma separated)") {
                TextInput(platesText, {
                    platesText = it
                    scope.launch { dao.putSetting(Setting(Settings.PLATES, it)) }
                }, "Plates")
            }
        }
    }
}

// ---------- About ----------

@Composable
fun AboutScreen(nav: Nav) {
    LogScaffold("About", nav) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BearWordmark(fontSize = 44.sp)
                Gap(4)
                BearMotto(fontSize = 15.sp)
            }
        }
        item { Muted("Build ${BuildConfig.VERSION_CODE} · v${BuildConfig.VERSION_NAME}") }
        item { Muted("Everything you log stays on this phone. Keep a backup from Backup, export & import.") }
        item { Muted("Snore detection uses Google's YAMNet sound model, Apache License 2.0.") }
        item { Muted("Typefaces: Archivo, Archivo Black and Cormorant Garamond, SIL Open Font License 1.1.") }
        item { Muted("Muscles-worked data from free-exercise-db (public domain).") }
    }
}

// ---------- Settings / targets ----------

@Composable
fun SettingsScreen(nav: Nav) {
    LogScaffold("Targets & settings", nav) {
        item { SettingField("Bodyweight target (kg)", Settings.BODYWEIGHT_TARGET, "98") }
        item { SettingField("Protein target minimum (g/day)", Settings.PROTEIN_MIN, "160") }
        item { SettingField("Protein target maximum (g/day)", Settings.PROTEIN_MAX, "175") }
        item { SettingField("Training sessions per month target", Settings.MONTHLY_SESSION_TARGET, "7") }
        item { SettingField("NEAT sessions per week (minimum)", Settings.NEAT_WEEKLY_SESSIONS, "3") }
        item { SettingField("NEAT session length (min)", Settings.NEAT_SESSION_MIN, "60") }
        item { SettingField("Review program every (weeks)", Settings.PROGRAM_REVIEW_WEEKS, ProgramReview.DEFAULT_WEEKS.toString()) }
        item { ProgramBlockRow() }
        item { Muted("Units are fixed: kg, km/m, °C.") }
    }
}

/** Shows how long the current training block has been running, and lets you restart the count. */
@Composable
private fun ProgramBlockRow() {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val start = dao.settingFlow(Settings.PROGRAM_START).collectAsState(initial = null).value?.toLongOrNull()
    val weeks = dao.settingFlow(Settings.PROGRAM_REVIEW_WEEKS).collectAsState(initial = null).value?.toIntOrNull()
    val workouts by dao.workoutSummaries().collectAsState(initial = emptyList())
    val status = ProgramReview.status(start, workouts.lastOrNull()?.workout?.startedAt?.toLocalDate()?.toEpochDay(), today(), weeks, null)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Current block")
            Muted(status?.let { "Since ${fmtDay(it.startDay)} · week ${it.weeksDone + 1} of ${it.reviewWeeks}" } ?: "Starts with your first workout")
        }
        TextButton(onClick = {
            scope.launch {
                dao.putSetting(Setting(Settings.PROGRAM_START, today().toString()))
                dao.putSetting(Setting(Settings.PROGRAM_REVIEW_SNOOZE, ""))
            }
        }) { Text("Start new block") }
    }
}

@Composable
private fun SettingField(label: String, key: String, default: String) {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val value by dao.settingFlow(key).collectAsState(initial = null)
    var text by remember(value) { mutableStateOf(value ?: default) }
    NumberField(text, {
        text = it
        if (Calc.parseNumber(it) != null) scope.launch { dao.putSetting(Setting(key, it)) }
    }, label, Modifier.fillMaxWidth())
}

// ---------- Venues ----------

@Composable
fun VenuesScreen(nav: Nav) {
    val dao = Graph.dao
    val scope = rememberCoroutineScope()
    val venues by dao.venues().collectAsState(initial = emptyList())
    val activeId by dao.settingFlow(Settings.ACTIVE_VENUE).collectAsState(initial = null)
    var newName by remember { mutableStateOf("") }

    LogScaffold("Gyms & travel mode", nav) {
        item { Muted("Pick where you're training on the Train tab. When a template exercise needs equipment the gym doesn't have, the first suitable alternative is used instead. Mark a gym as travel to label those workouts.") }
        venues.forEach { v ->
            item(key = v.id) {
                SectionCard(v.name + if (v.id.toString() == activeId) " (active)" else "") {
                    ChipGroup(Equipment.all, Calc.tags(v.equipment), Equipment::label) { tag ->
                        val tags = Calc.tags(v.equipment)
                        val updated = if (tag in tags) tags - tag else tags + tag
                        scope.launch { dao.updateVenue(v.copy(equipment = updated.joinToString(","))) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Travel / hotel gym", Modifier.weight(1f))
                        Switch(checked = v.isTravel, onCheckedChange = { scope.launch { dao.updateVenue(v.copy(isTravel = it)) } })
                    }
                    Row {
                        TextButton(onClick = { scope.launch { dao.putSetting(Setting(Settings.ACTIVE_VENUE, v.id.toString())) } }) { Text("Train here") }
                        if (venues.size > 1) DangerTextButton("Delete") { scope.launch { dao.deleteVenue(v.id) } }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.OutlinedTextField(newName, { newName = it }, label = { Text("New gym") }, singleLine = true, modifier = Modifier.weight(1f))
                HGap()
                Button(enabled = newName.isNotBlank(), onClick = {
                    scope.launch { dao.insertVenue(Venue(name = newName.trim(), equipment = "")); newName = "" }
                }) { Text("Add") }
            }
        }
    }
}

// ---------- Backup, export, import ----------

@Composable
fun DataScreen(nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var autoBackups by remember { mutableStateOf(DataIO.autoBackups(context)) }

    fun run(label: String, block: suspend () -> String) {
        busy = true
        scope.launch {
            message = try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: Exception) {
                ErrorLog.log("DATA", "$label failed", e)
                "$label failed: ${e.message ?: e.javaClass.simpleName}"
            }
            busy = false
            autoBackups = DataIO.autoBackups(context)
        }
    }

    val importStrong = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run("Strong import") {
            val r = DataIO.importStrong(DataIO.readUri(context, uri))
            buildString {
                append("Imported ${r.workouts} workouts (${r.sets} sets).")
                if (r.duplicates > 0) append("\nSkipped ${r.duplicates} already imported.")
                if (r.newExercises.isNotEmpty()) append("\nNew exercises: ${r.newExercises.joinToString()}")
                if (r.skippedRows > 0) append("\nSkipped ${r.skippedRows} unreadable/rest-timer rows.")
                if (r.problems.isNotEmpty()) append("\n" + r.problems.take(5).joinToString("\n"))
            }
        }
    }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) run("Backup") {
            DataIO.writeToUri(context, uri, DataIO.backupJson())
            "Backup saved."
        }
    }
    val pickRestore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            pendingRestore = try { withContext(Dispatchers.IO) { DataIO.readUri(context, uri) } } catch (e: Exception) { message = "Couldn't read file"; null }
        }
    }

    LogScaffold("Backup, export & import", nav) {
        item {
            SectionCard("Export / share") {
                Muted("Creates a full JSON backup plus a workouts CSV and opens the share sheet (Drive, email, Files…).")
                Gap(8)
                BigButton("Share backup + CSV", enabled = !busy, onClick = {
                    scope.launch {
                        try {
                            val intent = withContext(Dispatchers.IO) { DataIO.shareExport(context) }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            ErrorLog.log("DATA", "Share failed", e); message = "Share failed: ${e.message}"
                        }
                    }
                })
                Gap(8)
                BigButton("Save backup file…", secondary = true, enabled = !busy, onClick = { saveBackup.launch("training-backup-${LocalDate.now()}.json") })
            }
        }
        item {
            SectionCard("Import from Strong") {
                Muted("In Strong: Settings → Export data. Then pick the CSV here. Re-importing the same file skips workouts already imported.")
                Gap(8)
                BigButton("Choose Strong CSV…", enabled = !busy, onClick = { importStrong.launch(arrayOf("text/*", "application/*", "*/*")) })
            }
        }
        item {
            SectionCard("Restore") {
                Muted("Replaces ALL current data with a backup file. A safety backup of the current data is made first.")
                Gap(8)
                BigButton("Restore from backup file…", secondary = true, enabled = !busy, onClick = { pickRestore.launch(arrayOf("application/json", "text/*", "*/*")) })
                if (autoBackups.isNotEmpty()) {
                    Gap(8)
                    Muted("Automatic daily backups on this phone (also included in Android's own backup):")
                    autoBackups.take(5).forEach { f ->
                        TextButton(onClick = { pendingRestore = f.readText() }) { Text("Restore ${f.name}") }
                    }
                }
            }
        }
        if (busy) item { CircularProgressIndicator() }
        message?.let { item { SectionCard { SelectionContainer { Text(it) } } } }
    }

    pendingRestore?.let { text ->
        ConfirmDialog("Replace all data?", "Everything currently in the app will be replaced by this backup.", confirm = "Restore", onConfirm = {
            run("Restore") {
                DataIO.autoBackupIfDue(context)
                val dir = java.io.File(context.filesDir, "backups").apply { mkdirs() }
                java.io.File(dir, "before-restore-${System.currentTimeMillis()}.json").writeText(DataIO.backupJson())
                val r = DataIO.restoreJson(text)
                "Restored ${r.rows} rows across ${r.tables} tables."
            }
        }, onDismiss = { pendingRestore = null })
    }
}

// ---------- Error log ----------

@Composable
fun ErrorLogScreen(nav: Nav) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { text = withContext(Dispatchers.IO) { ErrorLog.read() } }
    Scaffold(topBar = {
        AppTopBar("Error log", onBack = { nav.back() }) {
            TextButton(onClick = { copy(context, text) }) { Text("Copy") }
            TextButton(onClick = { ErrorLog.clear(); text = "" }) { Text("Clear") }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp).verticalScroll(rememberScrollState())) {
            Muted("Build ${BuildConfig.VERSION_CODE}. Recent crashes and errors, newest at the bottom. Copy and paste this to Claude when something breaks.")
            Gap(8)
            if (text.isBlank()) Text("No errors logged. 🎉")
            else SelectionContainer {
                Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
            Gap(16)
            OutlinedButton(onClick = { copy(context, text) }, modifier = Modifier.fillMaxWidth()) { Text("Copy all") }
        }
    }
}

fun copy(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText("Bear error log", "Build ${BuildConfig.VERSION_CODE}\n$text"))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
