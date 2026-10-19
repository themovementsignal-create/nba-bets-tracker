package com.muir.bear.domain

/**
 * Reads training templates from a JSON or CSV file (written by hand, a spreadsheet or an AI chat).
 *
 * JSON: `{"templates": [{"name": "Session 3 · Full Body", "notes": "…", "exercises": [
 *   {"name": "Back Squat", "sets": 4, "reps": "5", "rest": "3:00"}, "Plank"]}]}`
 * (a bare list of templates, or a single template object, also works; "target" = "reps").
 * Also the richer "bear-program/1" shape: {"program": {"name": …}, "routines": [{"name", "day",
 * "duration_min", "exercises": [{"order", "name", "type": reps|time|distance, "sets", "reps_min",
 * "reps_max", "seconds", "metres", "per_side", "load_kg", "rest_s", "warmup", "notes",
 * "progression"}]}]}. Load and "per side" go into the target text ("8-10/side · 16 kg").
 *
 * CSV, one row per exercise, with a header: `template,exercise,sets,reps,rest,type,superset,warmup,notes`.
 * Only template and exercise are needed; a blank template carries on the one above; "3x8-10" in
 * the sets column means 3 sets of 8–10.
 *
 * Pure parsing only: matching names to the exercise library happens when importing.
 */
object TemplateImport {
    /** Exercise types, same ids as the app's ExerciseType. */
    const val WEIGHT_REPS = "WEIGHT_REPS"
    const val BODYWEIGHT = "BODYWEIGHT"
    const val TIMED = "TIMED"
    const val LOAD_DISTANCE = "LOAD_DISTANCE"

    /**
     * [notes]: coaching notes from the file, kept on a newly added exercise. [superset]: a letter
     * shared by exercises done back to back ("" = none). [warmup]: prep work, not tracked for progress.
     */
    data class Item(
        val exercise: String,
        val sets: Int,
        val target: String,
        val restSeconds: Int?,
        val type: String?,
        val notes: String = "",
        val superset: String = "",
        val warmup: Boolean = false,
    )
    data class Plan(val name: String, val notes: String, val items: List<Item>)
    data class Parsed(val templates: List<Plan>, val problems: List<String>)

    fun parse(text: String): Parsed {
        val t = text.trimStart('﻿', ' ', '\n', '\r', '\t')
        return if (t.startsWith("{") || t.startsWith("[")) parseJson(t) else parseCsv(t)
    }

    // ---------- JSON ----------

    private fun parseJson(text: String): Parsed {
        val root = try { MiniJson.parse(text) } catch (e: IllegalArgumentException) {
            return Parsed(emptyList(), listOf("Not valid JSON: ${e.message}"))
        }
        val problems = mutableListOf<String>()
        val list: List<Any?> = when (root) {
            is List<*> -> root
            is Map<*, *> -> (root["templates"] ?: root["sessions"] ?: root["workouts"] ?: root["routines"] ?: root["days"]) as? List<*> ?: listOf(root)
            else -> emptyList()
        }
        // A program wrapper ("program": {"name": "Summer Block", …}) names the block in each template's notes.
        val programName = ((root as? Map<*, *>)?.get("program") as? Map<*, *>)?.let { str(it["name"])?.trim() }
        val plans = list.mapIndexedNotNull { i, raw ->
            val m = raw as? Map<*, *> ?: run { problems += "Template ${i + 1}: not an object"; return@mapIndexedNotNull null }
            val name = str(m["name"] ?: m["title"] ?: m["template"])?.trim().orEmpty()
            if (name.isEmpty()) { problems += "Template ${i + 1}: no name"; return@mapIndexedNotNull null }
            val exercises = ((m["exercises"] ?: m["items"]) as? List<*> ?: emptyList<Any?>())
                .let { xs -> if (xs.all { (it as? Map<*, *>)?.get("order") is Double }) xs.sortedBy { ((it as Map<*, *>)["order"] as Double) } else xs }
            val items = exercises.mapIndexedNotNull { j, e ->
                when (e) {
                    is String -> e.trim().takeIf { it.isNotEmpty() }?.let { Item(it, 3, "", null, null) }
                    is Map<*, *> -> {
                        val ex = str(e["name"] ?: e["exercise"])?.trim().orEmpty()
                        if (ex.isEmpty()) { problems += "$name, exercise ${j + 1}: no name"; null }
                        else {
                            val (sets, fromSets) = setsAndTarget(str(e["sets"]))
                            Item(
                                exercise = ex,
                                sets = sets,
                                target = target(e) ?: fromSets,
                                restSeconds = restSeconds(str(e["rest"] ?: e["restSeconds"] ?: e["rest_seconds"] ?: e["rest_s"])),
                                type = type(str(e["type"])),
                                superset = superset(str(e["superset"] ?: e["group"])),
                                warmup = yes(e["warmup"] ?: e["warm_up"]),
                                notes = listOfNotNull(
                                    str(e["notes"])?.trim()?.takeIf { it.isNotEmpty() },
                                    str(e["progression"])?.trim()?.takeIf { it.isNotEmpty() }?.let { "Progression: $it" },
                                ).joinToString(" "),
                            )
                        }
                    }
                    else -> { problems += "$name, exercise ${j + 1}: not understood"; null }
                }
            }
            if (items.isEmpty()) problems += "$name: no exercises"
            val duration = str(m["duration_min"] ?: m["duration"])?.trim()?.takeIf { it.isNotEmpty() }?.let { "~$it min" }
            val notes = listOfNotNull(programName, str(m["day"])?.trim()?.takeIf { it.isNotEmpty() }, duration, str(m["notes"])?.trim()?.takeIf { it.isNotEmpty() })
            Plan(name, notes.joinToString(" · "), items)
        }.filter { it.items.isNotEmpty() }
        return Parsed(plans, problems)
    }

    /**
     * What to aim for in a set: "reps"/"target" as written, else built from reps_min/reps_max,
     * seconds or metres, with "/side" and the starting load added ("8-10/side · 16 kg").
     */
    private fun target(e: Map<*, *>): String? {
        val written = str(e["reps"] ?: e["target"])?.trim()?.takeIf { it.isNotEmpty() }
        val lo = str(e["reps_min"]); val hi = str(e["reps_max"])
        val base = written ?: when {
            lo != null && hi != null && lo != hi -> "$lo-$hi"
            lo != null || hi != null -> lo ?: hi
            e["seconds"] != null -> "${str(e["seconds"])}s"
            (e["metres"] ?: e["meters"]) != null -> "${str(e["metres"] ?: e["meters"])}m"
            else -> null
        } ?: return null
        val side = if (e["per_side"] == true) "/side" else ""
        val load = (e["load_kg"] as? Double)?.takeIf { it > 0 }?.let { " · ${str(it)} kg" } ?: ""
        return base + side + load
    }

    private fun str(v: Any?): String? = when (v) {
        null -> null
        is Double -> if (v == Math.floor(v)) v.toLong().toString() else v.toString()
        else -> v.toString()
    }

    // ---------- CSV ----------

    private fun parseCsv(text: String): Parsed {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return Parsed(emptyList(), listOf("The file is empty"))
        val sep = if (lines.first().count { it == '\t' } > lines.first().count { it == ',' }) '\t'
        else if (lines.first().count { it == ';' } > lines.first().count { it == ',' }) ';' else ','
        val header = splitCsv(lines.first(), sep).map { it.trim().lowercase() }
        fun col(vararg names: String) = header.indexOfFirst { it in names }
        val cTemplate = col("template", "session", "day", "workout", "template name")
        val cExercise = col("exercise", "exercise name", "name", "movement")
        val cSets = col("sets", "set")
        val cReps = col("reps", "target", "rep range")
        val cRest = col("rest", "rest seconds", "rest (s)", "rest_seconds")
        val cType = col("type")
        val cSuperset = col("superset", "group", "superset group")
        val cWarmup = col("warmup", "warm-up", "warm up")
        val cNotes = col("notes", "note")
        if (cTemplate < 0 || cExercise < 0) {
            return Parsed(emptyList(), listOf("The first row needs at least the columns \"template\" and \"exercise\""))
        }
        val problems = mutableListOf<String>()
        val order = mutableListOf<String>()
        val items = mutableMapOf<String, MutableList<Item>>()
        val notes = mutableMapOf<String, String>()
        var current = ""
        lines.drop(1).forEachIndexed { i, line ->
            val f = splitCsv(line, sep)
            fun at(c: Int) = if (c >= 0) f.getOrNull(c)?.trim().orEmpty() else ""
            at(cTemplate).takeIf { it.isNotEmpty() }?.let { current = it }
            val ex = at(cExercise)
            if (current.isEmpty()) { problems += "Row ${i + 2}: no template name"; return@forEachIndexed }
            if (ex.isEmpty()) return@forEachIndexed
            if (current !in items) { order += current; items[current] = mutableListOf() }
            val (sets, fromSets) = setsAndTarget(at(cSets))
            items.getValue(current) += Item(
                ex, sets, at(cReps).ifEmpty { fromSets }, restSeconds(at(cRest)), type(at(cType)),
                superset = superset(at(cSuperset)), warmup = yes(at(cWarmup).ifEmpty { null }),
            )
            at(cNotes).takeIf { it.isNotEmpty() && notes[current] == null }?.let { notes[current] = it }
        }
        return Parsed(order.map { Plan(it, notes[it].orEmpty(), items.getValue(it)) }, problems)
    }

    /** Splits one CSV line, honouring "quoted, fields" and "" escapes. */
    fun splitCsv(line: String, sep: Char = ','): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && line.getOrNull(i + 1) == '"' -> { sb.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == sep && !quoted -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    // ---------- Field helpers ----------

    /** "a", "A", "Superset B" → "A", "B"; blank, "none" or null → "". */
    fun superset(raw: String?): String {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty() || s.equals("none", true) || s.equals("null", true) || s == "-") return ""
        return s.removePrefix("superset").removePrefix("Superset").trim().take(3).uppercase()
    }

    /** true / "yes" / "y" / "x" / "1" / "warm-up" → true. */
    fun yes(v: Any?): Boolean = when (v) {
        is Boolean -> v
        is Double -> v != 0.0
        is String -> v.trim().lowercase() in setOf("true", "yes", "y", "x", "1", "warmup", "warm-up", "w")
        else -> false
    }

    /** "4" → 4 sets; "3x8-10" or "3 × 8" → 3 sets with that target; blank → 3. */
    fun setsAndTarget(raw: String?): Pair<Int, String> {
        val s = raw?.trim().orEmpty()
        val m = Regex("^(\\d+)\\s*[x×X*]\\s*(.+)$").find(s)
        if (m != null) return (m.groupValues[1].toIntOrNull() ?: 3).coerceIn(1, 20) to m.groupValues[2].trim()
        return (s.toDoubleOrNull()?.toInt() ?: 3).coerceIn(1, 20) to ""
    }

    /** "90", "90s", "2:00", "2m", "2 min" → seconds; blank or nonsense → null. */
    fun restSeconds(raw: String?): Int? {
        val s = raw?.trim()?.lowercase().orEmpty()
        if (s.isEmpty()) return null
        Regex("^(\\d+):(\\d{1,2})$").find(s)?.let { return it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
        Regex("^(\\d+(?:\\.\\d+)?)\\s*(m|min|mins|minutes)$").find(s)?.let { return (it.groupValues[1].toDouble() * 60).toInt() }
        Regex("^(\\d+)\\s*(s|sec|secs|seconds)?$").find(s)?.let { return it.groupValues[1].toInt() }
        return null
    }

    fun type(raw: String?): String? = when (raw?.trim()?.lowercase()?.replace('-', '_')?.replace(' ', '_')) {
        null, "" -> null
        "weight", "weights", "weight_reps", "strength" -> WEIGHT_REPS
        // "reps" alone could be weighted or bodyweight: guessed from the name when added.
        "reps" -> null
        "bodyweight", "body_weight", "bw" -> BODYWEIGHT
        "timed", "time", "hold", "isometric", "seconds" -> TIMED
        "distance", "load_distance", "carry", "sled" -> LOAD_DISTANCE
        else -> null
    }
}

/** A tiny JSON reader (objects, arrays, strings, numbers, booleans, null) so this stays pure Kotlin. */
object MiniJson {
    fun parse(text: String): Any? {
        val p = P(text)
        val v = p.value()
        p.ws()
        if (p.i != text.length) throw IllegalArgumentException("unexpected text at position ${p.i}")
        return v
    }

    private class P(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun fail(what: String): Nothing = throw IllegalArgumentException("$what at position $i")

        fun value(): Any? {
            ws()
            if (i >= s.length) fail("unexpected end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> string()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> if (c == '-' || c.isDigit()) number() else fail("unexpected '$c'")
            }
        }

        fun word(w: String, v: Any?): Any? {
            if (!s.startsWith(w, i)) fail("expected $w")
            i += w.length
            return v
        }

        fun number(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDoubleOrNull() ?: fail("bad number")
        }

        fun string(): String {
            i++ // opening quote
            val sb = StringBuilder()
            while (i < s.length) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) fail("bad escape")
                        when (val e = s[i++]) {
                            'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                            'u' -> {
                                if (i + 4 > s.length) fail("bad unicode escape")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4
                            }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
            fail("unterminated string")
        }

        fun arr(): List<Any?> {
            i++
            val out = mutableListOf<Any?>()
            ws()
            if (i < s.length && s[i] == ']') { i++; return out }
            while (true) {
                out += value()
                ws()
                when (s.getOrNull(i)) {
                    ',' -> i++
                    ']' -> { i++; return out }
                    else -> fail("expected , or ]")
                }
            }
        }

        fun obj(): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            ws()
            if (i < s.length && s[i] == '}') { i++; return out }
            while (true) {
                ws()
                if (s.getOrNull(i) != '"') fail("expected a quoted key")
                val k = string()
                ws()
                if (s.getOrNull(i) != ':') fail("expected :")
                i++
                out[k] = value()
                ws()
                when (s.getOrNull(i)) {
                    ',' -> i++
                    '}' -> { i++; return out }
                    else -> fail("expected , or }")
                }
            }
        }
    }
}
