package com.muir.bear.domain

/**
 * What appears on Today, in what order. Stored as one setting string, e.g.
 * "readiness,next,ticks,-protein-chart,…": a leading "-" means hidden. Items added in later
 * versions appear automatically (shown, at the end) so an old saved layout never hides new features.
 */
object Dashboard {
    enum class Item(val id: String, val label: String) {
        READINESS("readiness", "Readiness"),
        NEXT("next", "Next session"),
        TICKS("ticks", "Daily ticks"),
        SLEEP("sleep", "Sleep"),
        STEPS("steps", "Steps & NEAT"),
        CONDITIONING("conditioning", "Conditioning"),
        NIGGLES("niggles", "Niggles"),
        GEAR("gear", "Gear"),
        LIFTS("lifts", "Key lifts"),
        MONTH("month", "Sessions this month"),
        LOAD("load", "Weekly load"),
        WEEK("week", "This week"),
        BODYWEIGHT("bodyweight", "Bodyweight trend"),
        SLEEP_PERF("sleepperf", "Sleep vs performance"),
    }

    /** The small daily log chips inside "Daily ticks". */
    enum class Tick(val id: String, val label: String) {
        CHECKIN("checkin", "Check-in"),
        BODYWEIGHT("bodyweight", "Bodyweight"),
        SUPPLEMENTS("supplements", "Supplements"),
        SAUNA("sauna", "Sauna"),
        PROTEIN("protein", "Protein"),
    }

    data class Entry(val item: Item, val shown: Boolean)

    val default: List<Entry> = Item.entries.map { Entry(it, true) }
    /** Protein is off unless you turn it on. */
    val defaultTicks: Set<Tick> = Tick.entries.toSet() - Tick.PROTEIN

    fun parse(saved: String?): List<Entry> {
        if (saved.isNullOrBlank()) return default
        val byId = Item.entries.associateBy { it.id }
        val seen = mutableSetOf<Item>()
        val out = mutableListOf<Entry>()
        for (raw in saved.split(',')) {
            val token = raw.trim()
            val hidden = token.startsWith("-")
            val item = byId[token.removePrefix("-")] ?: continue
            if (seen.add(item)) out += Entry(item, !hidden)
        }
        Item.entries.filter { it !in seen }.forEach { out += Entry(it, true) }
        return out
    }

    fun serialize(entries: List<Entry>): String = entries.joinToString(",") { (if (it.shown) "" else "-") + it.item.id }

    /** Moves the entry at [index] up (delta −1) or down (+1); out-of-range moves do nothing. */
    fun move(entries: List<Entry>, index: Int, delta: Int): List<Entry> {
        val to = index + delta
        if (index !in entries.indices || to !in entries.indices) return entries
        return entries.toMutableList().apply { add(to, removeAt(index)) }
    }

    fun parseTicks(saved: String?): Set<Tick> {
        if (saved == null) return defaultTicks
        val byId = Tick.entries.associateBy { it.id }
        return saved.split(',').mapNotNull { byId[it.trim()] }.toSet()
    }

    fun serializeTicks(ticks: Set<Tick>): String = Tick.entries.filter { it in ticks }.joinToString(",") { it.id }

    /**
     * Which template is next in your rotation: the one after the most recently finished, in the
     * Train tab's order. [templates] in order; [finished] = (templateId, finishedAt). Null if none.
     */
    fun nextTemplate(templates: List<Long>, finished: List<Pair<Long, Long>>): Long? {
        if (templates.isEmpty()) return null
        val last = finished.filter { it.first in templates }.maxByOrNull { it.second }?.first ?: return templates.first()
        return templates[(templates.indexOf(last) + 1) % templates.size]
    }
}
