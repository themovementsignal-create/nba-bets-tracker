package com.muir.bear.domain

import java.time.LocalDateTime

/**
 * Daily supplement reminders: each supplement can have one time of day. Saved as a setting like
 * "12@480,15@1260" (supplement id @ minutes after midnight), so no database change is needed.
 */
object SuppReminders {
    data class Daily(val supplementId: Long, val minuteOfDay: Int)

    fun parse(saved: String?): List<Daily> =
        saved.orEmpty().split(',').mapNotNull { part ->
            val (id, min) = part.trim().split('@').takeIf { it.size == 2 } ?: return@mapNotNull null
            val m = min.toIntOrNull()?.takeIf { it in 0 until 24 * 60 } ?: return@mapNotNull null
            id.toLongOrNull()?.let { Daily(it, m) }
        }.distinctBy { it.supplementId }

    fun serialize(list: List<Daily>): String = list.joinToString(",") { "${it.supplementId}@${it.minuteOfDay}" }

    /** Sets (or with null, removes) the reminder time for one supplement. */
    fun set(list: List<Daily>, supplementId: Long, minuteOfDay: Int?): List<Daily> =
        list.filter { it.supplementId != supplementId } + listOfNotNull(minuteOfDay?.let { Daily(supplementId, it) })

    fun timeOf(list: List<Daily>, supplementId: Long): Int? = list.firstOrNull { it.supplementId == supplementId }?.minuteOfDay

    fun fmt(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    /** The next reminder strictly after [now], or null if none are set. */
    fun next(list: List<Daily>, now: LocalDateTime): LocalDateTime? =
        list.map { it.minuteOfDay }.distinct().map { m ->
            val today = now.toLocalDate().atTime(m / 60, m % 60)
            if (today.isAfter(now)) today else today.plusDays(1)
        }.minOrNull()

    /** Supplements whose reminder is at [minuteOfDay]. */
    fun dueAt(list: List<Daily>, minuteOfDay: Int): List<Long> = list.filter { it.minuteOfDay == minuteOfDay }.map { it.supplementId }
}
