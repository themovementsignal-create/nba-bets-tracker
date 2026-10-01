package io.github.themovementsignal.training.io

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import io.github.themovementsignal.training.BuildConfig
import io.github.themovementsignal.training.ErrorLog
import io.github.themovementsignal.training.Graph
import io.github.themovementsignal.training.data.Exercise
import io.github.themovementsignal.training.data.ExerciseType
import io.github.themovementsignal.training.data.Workout
import io.github.themovementsignal.training.data.WorkoutSet
import io.github.themovementsignal.training.domain.Calc
import io.github.themovementsignal.training.domain.StrongCsv
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Backup, restore, export and import.
 *
 * The JSON backup is a generic dump of every table (column name → value), so it keeps working as the
 * schema grows: restore only copies columns that exist in the current database.
 */
object DataIO {

    private val skipTables = setOf("android_metadata", "room_master_table", "sqlite_sequence")

    private fun tables(): List<String> {
        val db = Graph.db.openHelper.readableDatabase
        val names = mutableListOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            while (c.moveToNext()) names += c.getString(0)
        }
        return names.filter { it !in skipTables && !it.startsWith("sqlite_") }.sorted()
    }

    /** Full backup of every table as JSON. Must be called off the main thread. */
    fun backupJson(): String {
        val root = JSONObject()
        root.put("app", "training")
        root.put("format", 1)
        root.put("schemaVersion", Graph.db.openHelper.readableDatabase.version)
        root.put("build", BuildConfig.VERSION_CODE)
        root.put("createdAt", System.currentTimeMillis())
        val data = JSONObject()
        val db = Graph.db.openHelper.readableDatabase
        for (table in tables()) {
            val rows = JSONArray()
            db.query("SELECT * FROM `$table`").use { c ->
                while (c.moveToNext()) {
                    val row = JSONObject()
                    for (i in 0 until c.columnCount) {
                        val v: Any? = when (c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                            Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                            Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                            Cursor.FIELD_TYPE_BLOB -> JSONObject.NULL
                            else -> c.getString(i)
                        }
                        row.put(c.getColumnName(i), v)
                    }
                    rows.put(row)
                }
            }
            data.put(table, rows)
        }
        root.put("tables", data)
        return root.toString()
    }

    data class RestoreResult(val tables: Int, val rows: Int)

    /** Replaces ALL data with the backup. Must be called off the main thread. */
    suspend fun restoreJson(text: String): RestoreResult {
        val root = JSONObject(text)
        require(root.optString("app") == "training") { "This doesn't look like a Training backup file." }
        val data = root.getJSONObject("tables")
        val existing = tables()
        var rowCount = 0
        var tableCount = 0
        Graph.db.withTransaction {
            val db = Graph.db.openHelper.writableDatabase
            for (table in existing) db.execSQL("DELETE FROM `$table`")
            for (table in existing) {
                val rows = data.optJSONArray(table) ?: continue
                val columns = mutableSetOf<String>()
                db.query("PRAGMA table_info(`$table`)").use { c ->
                    val nameIdx = c.getColumnIndex("name")
                    while (c.moveToNext()) columns += c.getString(nameIdx)
                }
                tableCount++
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    val cols = row.keys().asSequence().filter { it in columns }.toList()
                    if (cols.isEmpty()) continue
                    val args = cols.map { k -> if (row.isNull(k)) null else row.get(k) }.toTypedArray()
                    val sql = "INSERT OR REPLACE INTO `$table` (${cols.joinToString { "`$it`" }}) VALUES (${cols.joinToString { "?" }})"
                    db.execSQL(sql, args)
                    rowCount++
                }
            }
        }
        return RestoreResult(tableCount, rowCount)
    }

    private val csvTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    private fun csv(v: Any?): String {
        val s = v?.toString() ?: ""
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    /** Workouts in a Strong-compatible CSV (kg, metres). */
    suspend fun workoutsCsv(): String {
        val dao = Graph.dao
        val exercises = dao.allExercises().associateBy { it.id }
        val workouts = dao.allWorkouts().filter { it.endedAt != null }.associateBy { it.id }
        val sets = dao.allSets().filter { it.completed && it.workoutId in workouts }
            .sortedWith(compareBy({ workouts.getValue(it.workoutId).startedAt }, { it.exerciseOrder }, { it.setIndex }))
        val sb = StringBuilder()
        sb.append("Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Weight Unit,Reps,Distance,Distance Unit,Seconds,Notes,Workout Notes,RPE\n")
        val zone = ZoneId.systemDefault()
        for (s in sets) {
            val w = workouts.getValue(s.workoutId)
            val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(w.startedAt), zone).format(csvTime)
            val durMin = w.endedAt?.let { ((it - w.startedAt) / 60_000).toInt() } ?: 0
            val order = if (s.kind.isNotEmpty()) s.kind else (s.setIndex + 1).toString()
            sb.append(
                listOf(
                    date, w.name, "${durMin}m", exercises[s.exerciseId]?.name ?: "Unknown", order,
                    Calc.fmt(s.weightKg), "kg", s.reps ?: "", Calc.fmt(s.distanceM), "m", s.seconds ?: "",
                    "", w.notes, w.rpe ?: "",
                ).joinToString(",") { csv(it) }
            ).append('\n')
        }
        return sb.toString()
    }

    /** Writes JSON + CSV to the cache and opens the share sheet. */
    suspend fun shareExport(context: Context): Intent {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val stamp = LocalDate.now().toString()
        val json = File(dir, "training-backup-$stamp.json").apply { writeText(backupJson()) }
        val csvFile = File(dir, "training-workouts-$stamp.csv").apply { writeText(workoutsCsv()) }
        val authority = "${context.packageName}.files"
        val uris = arrayListOf(
            FileProvider.getUriForFile(context, authority, json),
            FileProvider.getUriForFile(context, authority, csvFile),
        )
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, "Training backup $stamp")
            clipData = ClipData.newRawUri("backup", uris[0]).apply { addItem(ClipData.Item(uris[1])) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Save or send backup")
    }

    fun writeToUri(context: Context, uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            ?: error("Could not open the chosen file")
    }

    fun readUri(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Could not open the chosen file")

    /** Daily automatic backup into app storage (which Android Auto Backup also copies). Keeps 10. */
    fun autoBackupIfDue(context: Context) {
        try {
            val dir = File(context.filesDir, "backups").apply { mkdirs() }
            val latest = dir.listFiles()?.maxByOrNull { it.lastModified() }
            if (latest != null && System.currentTimeMillis() - latest.lastModified() < 20 * 3600_000L) return
            File(dir, "auto-${LocalDate.now()}.json").writeText(backupJson())
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(10)?.forEach { it.delete() }
        } catch (e: Exception) {
            ErrorLog.log("BACKUP", "Automatic backup failed", e)
        }
    }

    fun autoBackups(context: Context): List<File> =
        File(context.filesDir, "backups").listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()

    // ---------------- Strong import ----------------

    data class ImportResult(
        val workouts: Int,
        val sets: Int,
        val duplicates: Int,
        val newExercises: List<String>,
        val skippedRows: Int,
        val problems: List<String>,
    )

    private fun key(name: String) = name.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    /** Candidate names for matching Strong's "Deadlift (Trap bar)" style to "Trap Bar Deadlift". */
    private fun candidates(name: String): List<String> {
        val m = Regex("^(.*)\\((.*)\\)\\s*$").find(name.trim())
        val list = mutableListOf(name)
        if (m != null) {
            val base = m.groupValues[1].trim()
            val qual = m.groupValues[2].trim()
            list += "$qual $base"
            list += base
        }
        return list
    }

    private fun inferType(name: String, rows: List<StrongCsv.Row>): String {
        val n = name.lowercase(Locale.ROOT)
        return when {
            rows.any { (it.distanceM ?: 0.0) > 0 } -> ExerciseType.LOAD_DISTANCE
            rows.any { (it.seconds ?: 0) > 0 } && rows.none { (it.reps ?: 0) > 0 } -> ExerciseType.TIMED
            listOf("assisted", "chin up", "chin-up", "pull up", "pull-up", "dip", "push up", "push-up", "muscle up")
                .any { n.contains(it) } -> ExerciseType.BODYWEIGHT
            else -> ExerciseType.WEIGHT_REPS
        }
    }

    suspend fun importStrong(text: String): ImportResult {
        val parsed = StrongCsv.parse(text)
        val dao = Graph.dao
        val zone = ZoneId.systemDefault()
        var workouts = 0
        var sets = 0
        var duplicates = 0
        val created = mutableListOf<String>()
        Graph.db.withTransaction {
            val byKey = dao.allExercises().associateBy { key(it.name) }.toMutableMap()
            val allRowsByExercise = parsed.workouts.flatMap { it.rows }.groupBy { it.exercise }

            suspend fun exerciseFor(name: String): Exercise {
                for (c in candidates(name)) byKey[key(c)]?.let { return it }
                val type = inferType(name, allRowsByExercise[name].orEmpty())
                val e = Exercise(name = name.trim(), type = type, isCustom = true)
                val id = dao.insertExercise(e)
                val saved = e.copy(id = id)
                byKey[key(name)] = saved
                created += name.trim()
                return saved
            }

            for (w in parsed.workouts) {
                val start = w.start.atZone(zone).toInstant().toEpochMilli()
                if (dao.countWorkoutsAt(start, w.name) > 0) {
                    duplicates++
                    continue
                }
                val end = start + (w.durationMin ?: 60) * 60_000L
                val wid = dao.insertWorkout(
                    Workout(name = w.name, startedAt = start, endedAt = end, notes = w.notes, source = "strong")
                )
                workouts++
                val order = w.rows.map { it.exercise }.distinct()
                val toInsert = mutableListOf<WorkoutSet>()
                for ((exIndex, exName) in order.withIndex()) {
                    val ex = exerciseFor(exName)
                    val assisted = exName.lowercase(Locale.ROOT).contains("assisted")
                    w.rows.filter { it.exercise == exName }.forEachIndexed { i, r ->
                        val weight = r.weightKg?.let { if (assisted && it > 0) -it else it }
                        toInsert += WorkoutSet(
                            workoutId = wid, exerciseId = ex.id, exerciseOrder = exIndex, setIndex = i,
                            weightKg = weight?.takeIf { it != 0.0 || ex.type == ExerciseType.WEIGHT_REPS },
                            reps = r.reps, seconds = r.seconds, distanceM = r.distanceM,
                            completed = true, completedAt = start, kind = r.kind,
                        )
                    }
                }
                dao.insertSets(toInsert)
                sets += toInsert.size
            }
        }
        return ImportResult(workouts, sets, duplicates, created, parsed.skippedRows, parsed.problems)
    }
}
