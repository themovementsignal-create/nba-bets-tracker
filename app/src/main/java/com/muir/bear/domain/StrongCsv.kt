package com.muir.bear.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Tolerant parser for Strong app CSV exports.
 *
 * Handles: comma / semicolon / tab delimiters, quoted fields with commas and newlines, a UTF-8 BOM,
 * header names in any case or order (and with units like "Weight (kg)"), lbs/miles conversion,
 * decimal commas, several date formats and durations like "1h 5m".
 */
object StrongCsv {

    data class Row(
        val start: LocalDateTime,
        val workoutName: String,
        val durationMin: Int?,
        val exercise: String,
        /** "" normal, "W", "D", "F". */
        val kind: String,
        val setOrder: Int,
        val weightKg: Double?,
        val reps: Int?,
        val distanceM: Double?,
        val seconds: Int?,
        val notes: String,
        val workoutNotes: String,
        val rpe: Double?,
    )

    data class Workout(
        val start: LocalDateTime,
        val name: String,
        val durationMin: Int?,
        val notes: String,
        val rows: List<Row>,
    )

    data class Result(val workouts: List<Workout>, val skippedRows: Int, val problems: List<String>)

    private const val LB_TO_KG = 0.45359237
    private const val MILE_TO_M = 1609.344

    fun parse(text: String): Result {
        val clean = text.removePrefix("﻿")
        val firstLine = clean.lineSequence().firstOrNull { it.isNotBlank() } ?: return Result(emptyList(), 0, listOf("File is empty"))
        val delimiter = detectDelimiter(firstLine)
        val records = readRecords(clean, delimiter)
        if (records.isEmpty()) return Result(emptyList(), 0, listOf("No rows found"))

        val header = records.first().map { normalise(it) }
        // For each candidate name in order: exact header match first, then a header that starts with it
        // (so "Weight (kg)" matches "weight").
        fun col(vararg names: String): Int {
            for (n in names) {
                header.indexOf(n).takeIf { it >= 0 }?.let { return it }
                header.indexOfFirst { it.startsWith(n) }.takeIf { it >= 0 }?.let { return it }
            }
            return -1
        }

        val cDate = col("date")
        val cWorkout = col("workoutname", "routinename", "title")
        val cDuration = col("duration")
        val cExercise = col("exercisename", "exercise")
        val cSetOrder = col("setorder", "set")
        val cWeight = col("weight")
        val cWeightUnit = col("weightunit")
        val cReps = col("reps")
        val cDistance = col("distance")
        val cDistanceUnit = col("distanceunit")
        val cSeconds = col("seconds", "time")
        val cNotes = col("notes")
        val cWorkoutNotes = col("workoutnotes")
        val cRpe = col("rpe")

        val problems = mutableListOf<String>()
        if (cDate < 0) problems += "No Date column found"
        if (cExercise < 0) problems += "No Exercise Name column found"
        if (cDate < 0 || cExercise < 0) return Result(emptyList(), records.size - 1, problems)

        val weightHeader = header.getOrNull(cWeight).orEmpty()
        // Newer exports use "Duration (sec)"; older ones "1h 5m".
        val durationInSeconds = header.getOrNull(cDuration).orEmpty().contains("sec")
        val distanceHeader = header.getOrNull(cDistance).orEmpty()

        val rows = mutableListOf<Row>()
        var skipped = 0
        for ((lineIndex, rec) in records.drop(1).withIndex()) {
            fun get(i: Int) = if (i >= 0) rec.getOrNull(i)?.trim().orEmpty() else ""
            if (rec.all { it.isBlank() }) continue
            val start = parseDate(get(cDate))
            val exercise = get(cExercise)
            val setOrderRaw = get(cSetOrder)
            if (start == null || exercise.isEmpty() || setOrderRaw.equals("Rest Timer", ignoreCase = true)) {
                skipped++
                if (problems.size < 20 && start == null) problems += "Row ${lineIndex + 2}: unreadable date '${get(cDate)}'"
                continue
            }
            val weightUnit = get(cWeightUnit).lowercase(Locale.ROOT)
            val isLbs = weightUnit.startsWith("lb") || weightHeader.contains("lb")
            val weight = Calc.parseNumber(get(cWeight))?.let { if (isLbs) it * LB_TO_KG else it }
            val distUnit = get(cDistanceUnit).lowercase(Locale.ROOT).ifEmpty { distanceHeader.removePrefix("distance") }
            val distance = Calc.parseNumber(get(cDistance))?.let {
                when {
                    distUnit.startsWith("mi") -> it * MILE_TO_M
                    distUnit == "m" || distUnit.startsWith("met") -> it
                    distUnit.startsWith("ft") -> it * 0.3048
                    distUnit.startsWith("yd") -> it * 0.9144
                    else -> it * 1000.0 // Strong exports distance in km by default
                }
            }
            val kind = when (setOrderRaw.uppercase(Locale.ROOT).firstOrNull()) {
                'W' -> "W"
                'D' -> "D"
                'F' -> "F"
                else -> ""
            }
            rows += Row(
                start = start,
                workoutName = get(cWorkout).ifEmpty { "Imported workout" },
                durationMin = if (durationInSeconds) {
                    Calc.parseNumber(get(cDuration))?.let { (it / 60.0).roundToInt() } ?: parseDurationMinutes(get(cDuration))
                } else {
                    parseDurationMinutes(get(cDuration))
                },
                exercise = exercise,
                kind = kind,
                setOrder = setOrderRaw.filter { it.isDigit() }.toIntOrNull() ?: 0,
                weightKg = weight?.takeIf { it != 0.0 || get(cReps).isNotEmpty() },
                reps = Calc.parseNumber(get(cReps))?.toInt()?.takeIf { it > 0 },
                distanceM = distance?.takeIf { it > 0 },
                seconds = Calc.parseNumber(get(cSeconds))?.toInt()?.takeIf { it > 0 },
                notes = get(cNotes),
                workoutNotes = get(cWorkoutNotes),
                rpe = Calc.parseNumber(get(cRpe)),
            )
        }

        val workouts = rows
            .groupBy { it.start to it.workoutName }
            .map { (key, list) ->
                Workout(
                    start = key.first,
                    name = key.second,
                    durationMin = list.firstNotNullOfOrNull { it.durationMin },
                    notes = list.firstOrNull { it.workoutNotes.isNotEmpty() }?.workoutNotes.orEmpty(),
                    rows = list,
                )
            }
            .sortedBy { it.start }
        return Result(workouts, skipped, problems)
    }

    private fun normalise(h: String): String =
        h.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    fun detectDelimiter(line: String): Char {
        val candidates = listOf(',', ';', '\t')
        return candidates.maxBy { c -> countOutsideQuotes(line, c) }
    }

    private fun countOutsideQuotes(line: String, c: Char): Int {
        var inQuotes = false
        var n = 0
        for (ch in line) {
            if (ch == '"') inQuotes = !inQuotes else if (ch == c && !inQuotes) n++
        }
        return n
    }

    /** RFC-4180-ish reader that tolerates stray quotes and CRLF/LF line endings. */
    fun readRecords(text: String, delimiter: Char): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var field = StringBuilder()
        var record = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"'); i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> if (field.isEmpty()) inQuotes = true else field.append(ch)
                    delimiter -> { record.add(field.toString()); field = StringBuilder() }
                    '\r' -> {}
                    '\n' -> {
                        record.add(field.toString()); field = StringBuilder()
                        records.add(record); record = mutableListOf()
                    }
                    else -> field.append(ch)
                }
            }
            i++
        }
        if (field.isNotEmpty() || record.isNotEmpty()) {
            record.add(field.toString())
            records.add(record)
        }
        return records.filter { r -> r.any { it.isNotBlank() } }
    }

    private val dateFormats = listOf(
        "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm",
        "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm",
        "dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy HH:mm", "d/M/yyyy H:mm", "dd.MM.yyyy HH:mm:ss", "dd.MM.yyyy HH:mm",
        "M/d/yyyy h:mm a", "M/d/yyyy h:mm:ss a", "MMM d, yyyy h:mm a", "MMM d, yyyy, h:mm a",
    ).map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

    private val dateOnlyFormats = listOf("yyyy-MM-dd", "dd/MM/yyyy", "M/d/yyyy", "dd.MM.yyyy")
        .map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

    fun parseDate(raw: String): LocalDateTime? {
        val s = raw.trim().replace(Regex("\\s+"), " ").removeSuffix("Z")
        if (s.isEmpty()) return null
        for (f in dateFormats) {
            runCatching { return LocalDateTime.parse(s, f) }
        }
        // Drop fractional seconds / offsets like "2023-01-02 10:00:00.000+01:00".
        val trimmed = s.replace(Regex("(\\d{2}:\\d{2}:\\d{2})[.,]\\d+"), "$1").replace(Regex("[+-]\\d{2}:?\\d{2}$"), "").trim()
        if (trimmed != s) parseDate(trimmed)?.let { return it }
        for (f in dateOnlyFormats) {
            runCatching { return LocalDate.parse(s, f).atTime(12, 0) }
        }
        return null
    }

    /** "1h 5m", "45m", "1h", "01:05:00", "3900s", "65" (minutes) → minutes. */
    fun parseDurationMinutes(raw: String): Int? {
        val s = raw.trim().lowercase(Locale.ROOT)
        if (s.isEmpty()) return null
        if (s.contains(':')) {
            val parts = s.split(':').mapNotNull { it.trim().toIntOrNull() }
            return when (parts.size) {
                3 -> parts[0] * 60 + parts[1] + if (parts[2] >= 30) 1 else 0
                2 -> parts[0] * 60 + parts[1]
                else -> null
            }
        }
        val h = Regex("(\\d+)\\s*h").find(s)?.groupValues?.get(1)?.toInt() ?: 0
        val m = Regex("(\\d+)\\s*m(?!s)").find(s)?.groupValues?.get(1)?.toInt() ?: 0
        val sec = Regex("(\\d+)\\s*s").find(s)?.groupValues?.get(1)?.toInt() ?: 0
        if (h > 0 || m > 0 || sec > 0) return h * 60 + m + if (sec >= 30) 1 else 0
        return s.toDoubleOrNull()?.toInt()
    }
}
