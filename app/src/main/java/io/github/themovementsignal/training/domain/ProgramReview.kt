package io.github.themovementsignal.training.domain

/**
 * "Time to review your program" reminder. A block starts on the day you set (or your first
 * workout); once it's been running for the review period (default 6 weeks) the reminder shows,
 * unless you've asked to be reminded later.
 */
object ProgramReview {
    const val DEFAULT_WEEKS = 6

    data class Status(val startDay: Long, val weeksDone: Int, val reviewWeeks: Int, val due: Boolean)

    /** All days are epoch days. Returns null when there's no block yet (no start set, no workouts). */
    fun status(startDay: Long?, firstWorkoutDay: Long?, today: Long, reviewWeeks: Int?, snoozeUntil: Long?): Status? {
        val start = startDay ?: firstWorkoutDay ?: return null
        val weeks = (reviewWeeks ?: DEFAULT_WEEKS).coerceAtLeast(1)
        val days = (today - start).coerceAtLeast(0)
        val due = days >= weeks * 7L && (snoozeUntil == null || today >= snoozeUntil)
        return Status(start, (days / 7).toInt(), weeks, due)
    }
}
