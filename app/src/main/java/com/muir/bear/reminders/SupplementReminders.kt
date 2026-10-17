package com.muir.bear.reminders

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.muir.bear.ErrorLog
import com.muir.bear.Graph
import com.muir.bear.MainActivity
import com.muir.bear.R
import com.muir.bear.TrainingApp
import com.muir.bear.data.Setting
import com.muir.bear.data.Settings
import com.muir.bear.data.SupplementLog
import com.muir.bear.domain.SuppReminders
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Supplement reminders: a daily time per supplement, plus a one-off "remind me in…" timer. Only the
 * next reminder is ever scheduled (one exact alarm); each time it fires, the one after is set.
 * The notification lists what's still to take, with "Taken" and "In 30 min" buttons.
 */
object SupplementReminders {
    const val LATER_MIN = 30
    private const val NOTIF = 21
    private const val EXTRA_AT = "at"
    private const val EXTRA_IDS = "ids"
    private const val ACTION_FIRE = "supp_fire"
    private const val ACTION_TAKEN = "supp_taken"
    private const val ACTION_LATER = "supp_later"

    private fun intent(context: Context, action: String, code: Int, extras: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getBroadcast(
            context, code, Intent(context, SupplementReminderReceiver::class.java).setAction(action).apply(extras),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun millis(t: LocalDateTime): Long = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Sets the alarm for the next reminder (daily or one-off), or clears it if there's none. */
    suspend fun reschedule(context: Context) {
        val app = context.applicationContext
        val dao = Graph.dao
        val now = System.currentTimeMillis()
        val daily = SuppReminders.parse(dao.setting(Settings.SUPP_REMINDERS))
            .let { list -> dao.allSupplements().filter { it.active }.map { it.id }.toSet().let { active -> list.filter { it.supplementId in active } } }
        val once = dao.setting(Settings.SUPP_REMIND_ONCE)?.toLongOrNull()?.takeIf { it > now }
        val nextDaily = SuppReminders.next(daily, LocalDateTime.now())?.let(::millis)
        val at = listOfNotNull(nextDaily, once).minOrNull()
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        if (at == null) {
            am.cancel(intent(app, ACTION_FIRE, 71))
            return
        }
        val pi = intent(app, ACTION_FIRE, 71) { putExtra(EXTRA_AT, at) }
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            ErrorLog.log("SUPPS", "Exact reminder not allowed; using a while-idle one", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** One-off reminder [minutes] from now (null cancels it). */
    suspend fun remindIn(context: Context, minutes: Int?) {
        val at = minutes?.let { System.currentTimeMillis() + it * 60_000L }
        Graph.dao.putSetting(Setting(Settings.SUPP_REMIND_ONCE, at?.toString() ?: ""))
        reschedule(context)
    }

    internal suspend fun handle(context: Context, intent: Intent) {
        val dao = Graph.dao
        val nm = context.getSystemService(NotificationManager::class.java)
        val today = LocalDate.now().toEpochDay()
        when (intent.action) {
            ACTION_FIRE -> {
                val at = intent.getLongExtra(EXTRA_AT, System.currentTimeMillis())
                val minute = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalTime().let { it.hour * 60 + it.minute }
                val active = dao.allSupplements().filter { it.active }
                val taken = dao.allSupplementLogs().filter { it.day == today }.map { it.supplementId }.toSet()
                val once = dao.setting(Settings.SUPP_REMIND_ONCE)?.toLongOrNull()
                val onceDue = once != null && once <= at + 60_000L
                if (onceDue) dao.putSetting(Setting(Settings.SUPP_REMIND_ONCE, ""))
                // A one-off reminder covers everything not yet taken; a daily one, what's due now.
                val dueIds = if (onceDue) active.map { it.id }.toSet()
                else SuppReminders.dueAt(SuppReminders.parse(dao.setting(Settings.SUPP_REMINDERS)), minute).toSet()
                val due = active.filter { it.id in dueIds && it.id !in taken }
                if (due.isNotEmpty()) notify(context, due.map { it.id }.toLongArray(), due.joinToString(", ") { it.name })
            }
            ACTION_TAKEN -> {
                intent.getLongArrayExtra(EXTRA_IDS)?.forEach { dao.insertSupplementLog(SupplementLog(it, today)) }
                nm?.cancel(NOTIF)
            }
            ACTION_LATER -> {
                nm?.cancel(NOTIF)
                remindIn(context, LATER_MIN)
                return
            }
        }
        reschedule(context)
    }

    private fun notify(context: Context, ids: LongArray, names: String) {
        val open = PendingIntent.getActivity(
            context, 72,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_OPEN, "supplements"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, TrainingApp.CHANNEL_SUPPS)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setColor(0xFFA8875A.toInt())
            .setContentTitle("Supplements")
            .setContentText(names)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Taken", intent(context, ACTION_TAKEN, 73) { putExtra(EXTRA_IDS, ids) })
            .addAction(0, "In $LATER_MIN min", intent(context, ACTION_LATER, 74))
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java)?.notify(NOTIF, n) }
            .onFailure { ErrorLog.log("SUPPS", "Couldn't show the reminder", it) }
    }
}

class SupplementReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Graph.scope.launch {
            try {
                SupplementReminders.handle(context.applicationContext, intent)
            } finally {
                pending.finish()
            }
        }
    }
}
