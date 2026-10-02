package com.muir.bear.data

import com.muir.bear.Graph
import com.muir.bear.domain.Muscles
import org.json.JSONArray

/** Fills in muscles worked from Bear's own list and the bundled free-exercise-db lookup. */
object MuscleData {
    @Volatile private var rows: List<Triple<String, List<String>, List<String>>>? = null

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }

    private fun db(): List<Triple<String, List<String>, List<String>>> = rows ?: runCatching {
        val a = JSONArray(Graph.app.assets.open("exercise-muscles.json").bufferedReader().use { it.readText() })
        (0 until a.length()).map { i ->
            val r = a.getJSONArray(i)
            Triple(r.getString(0), r.getJSONArray(1).strings(), r.getJSONArray(2).strings())
        }
    }.getOrDefault(emptyList()).also { rows = it }

    fun lookup(name: String): Muscles.Worked? = Muscles.lookup(name, db())

    /** For every exercise without muscles, fill them in when the name is recognised. */
    suspend fun fillMissing() {
        val dao = Graph.dao
        for (e in dao.allExercises()) {
            if (e.muscles.isNotEmpty()) continue
            val w = lookup(e.name) ?: continue
            dao.updateExercise(e.copy(muscles = w.primary.joinToString(","), secondaryMuscles = w.secondary.joinToString(",")))
        }
    }
}
