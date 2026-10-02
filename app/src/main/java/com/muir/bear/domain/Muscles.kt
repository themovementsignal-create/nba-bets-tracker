package com.muir.bear.domain

/**
 * Which muscles an exercise works, using free-exercise-db's 17 muscle names (public domain,
 * github.com/yuhonas/free-exercise-db). Main muscles count as one set, "also works" as half a set,
 * which is the usual convention when counting weekly sets per muscle.
 */
object Muscles {
    val all = listOf(
        "chest", "shoulders", "triceps", "biceps", "forearms", "lats", "middle back", "traps",
        "lower back", "abdominals", "glutes", "quadriceps", "hamstrings", "adductors", "abductors",
        "calves", "neck",
    )

    fun label(m: String): String = when (m) {
        "quadriceps" -> "Quads"
        "abdominals" -> "Abs"
        "middle back" -> "Upper back"
        else -> m.replaceFirstChar { it.uppercase() }
    }

    data class Worked(val primary: List<String>, val secondary: List<String> = emptyList())

    // Declared before [seeded], which uses normalize() while initialising.
    private val synonyms = mapOf(
        "db" to "dumbbell", "dumbbells" to "dumbbell", "bb" to "barbell", "kb" to "kettlebell",
        "pullup" to "pull up", "pullups" to "pull up", "chinup" to "chin up", "chinups" to "chin up",
        "pushup" to "push up", "pushups" to "push up", "rdl" to "romanian deadlift", "ohp" to "overhead press",
    )
    private val ignored = setOf("the", "with", "on", "a", "of", "and", "to", "medium", "grip")

    /** Hand-checked muscles for the exercises Bear ships with. */
    private val seeded: Map<String, Worked> = mapOf(
        "Trap Bar Deadlift" to Worked(listOf("quadriceps", "glutes", "hamstrings"), listOf("lower back", "traps", "forearms")),
        "Bulgarian Split Squat" to Worked(listOf("quadriceps", "glutes"), listOf("hamstrings", "adductors")),
        "Goblet Squat" to Worked(listOf("quadriceps", "glutes"), listOf("adductors", "abdominals")),
        "Sled Push" to Worked(listOf("quadriceps", "glutes"), listOf("calves", "hamstrings")),
        "Step-Down Isometric" to Worked(listOf("quadriceps"), listOf("glutes")),
        "Seated Calf Raise" to Worked(listOf("calves")),
        "Dead Hang" to Worked(listOf("forearms"), listOf("lats")),
        "Chin-Up" to Worked(listOf("lats", "biceps"), listOf("middle back", "forearms")),
        "Horizontal Press" to Worked(listOf("chest", "triceps"), listOf("shoulders")),
        "Row" to Worked(listOf("middle back", "lats"), listOf("biceps", "shoulders")),
        "Overhead Press" to Worked(listOf("shoulders", "triceps"), listOf("traps")),
        "Face Pull" to Worked(listOf("shoulders"), listOf("traps", "middle back")),
        "Cable Crossover" to Worked(listOf("chest"), listOf("shoulders")),
        "Romanian Deadlift" to Worked(listOf("hamstrings", "glutes"), listOf("lower back", "forearms")),
        "DB Romanian Deadlift" to Worked(listOf("hamstrings", "glutes"), listOf("lower back", "forearms")),
        "Reverse Lunge" to Worked(listOf("quadriceps", "glutes"), listOf("hamstrings")),
        "DB Walking Lunge" to Worked(listOf("quadriceps", "glutes"), listOf("hamstrings", "calves")),
        "Farmer Carry" to Worked(listOf("forearms", "traps"), listOf("abdominals", "glutes")),
        "Farmer Hold" to Worked(listOf("forearms"), listOf("traps")),
        "Single-Leg Calf Raise" to Worked(listOf("calves")),
        "Lat Pulldown" to Worked(listOf("lats"), listOf("biceps", "middle back")),
        "DB Row" to Worked(listOf("middle back", "lats"), listOf("biceps")),
        "Bench Press" to Worked(listOf("chest", "triceps"), listOf("shoulders")),
        "Push-Up" to Worked(listOf("chest", "triceps"), listOf("shoulders", "abdominals")),
        "Pike Push-Up" to Worked(listOf("shoulders", "triceps"), listOf("chest")),
        "Band Face Pull" to Worked(listOf("shoulders"), listOf("traps", "middle back")),
        "Reverse Fly" to Worked(listOf("shoulders"), listOf("middle back", "traps")),
        "DB Fly" to Worked(listOf("chest"), listOf("shoulders")),
        "Back Squat" to Worked(listOf("quadriceps", "glutes"), listOf("hamstrings", "lower back", "adductors")),
        "Front Squat" to Worked(listOf("quadriceps"), listOf("glutes", "abdominals")),
        "Deadlift" to Worked(listOf("hamstrings", "glutes", "lower back"), listOf("quadriceps", "traps", "forearms")),
        "Hip Thrust" to Worked(listOf("glutes"), listOf("hamstrings")),
        "Pull-Up" to Worked(listOf("lats"), listOf("biceps", "middle back")),
        "Dip" to Worked(listOf("triceps", "chest"), listOf("shoulders")),
        "Nordic Curl" to Worked(listOf("hamstrings")),
        "Plank" to Worked(listOf("abdominals")),
        "Copenhagen Plank" to Worked(listOf("adductors"), listOf("abdominals")),
        "Sled Pull" to Worked(listOf("hamstrings", "glutes"), listOf("quadriceps", "calves", "forearms")),
        "Box Jump" to Worked(listOf("quadriceps", "glutes"), listOf("calves", "hamstrings")),
        "Kettlebell Swing" to Worked(listOf("glutes", "hamstrings"), listOf("lower back", "shoulders")),
    ).mapKeys { normalize(it.key) }

    fun normalize(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim().split(" ")
            .joinToString(" ") { synonyms[it] ?: it }
            .replace(Regex("s\\b"), "") // crude plural folding: "curls" -> "curl"
            .trim()

    private fun tokens(name: String): Set<String> = normalize(name).split(" ").filter { it.isNotEmpty() && it !in ignored }.toSet()

    /**
     * Finds the muscles for an exercise name: Bear's own list first, then free-exercise-db
     * ([db] rows of name, main muscles, also-works muscles) by exact or close name match.
     * Returns null when nothing matches well enough; better no data than wrong data.
     */
    fun lookup(name: String, db: List<Triple<String, List<String>, List<String>>>): Worked? {
        val n = normalize(name)
        seeded[n]?.let { return it }
        db.firstOrNull { normalize(it.first) == n }?.let { return Worked(it.second, it.third) }
        val mine = tokens(name)
        if (mine.isEmpty()) return null
        var best: Triple<String, List<String>, List<String>>? = null
        var bestScore = 0.0
        for (row in db) {
            val theirs = tokens(row.first)
            if (theirs.isEmpty()) continue
            val score = (mine intersect theirs).size.toDouble() / (mine union theirs).size
            if (score > bestScore) { bestScore = score; best = row }
        }
        return if (best != null && bestScore >= 0.6) Worked(best.second, best.third) else null
    }
}
