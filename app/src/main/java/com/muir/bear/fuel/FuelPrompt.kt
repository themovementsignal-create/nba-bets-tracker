package com.muir.bear.fuel

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
import com.muir.bear.data.FoodCategory
import com.muir.bear.data.LogSource
import com.muir.bear.data.MealSlot
import com.muir.bear.data.Setting
import com.muir.bear.data.Settings
import com.muir.bear.domain.SnackPicker
import com.muir.bear.domain.SnackPrompt
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The afternoon snack prompt: one notification a day at your time (weekdays and weekends set
 * separately), with one suggestion from your snack list. Only the next prompt is scheduled (one
 * exact alarm); it's set again after it fires, on boot, on a clock change and when settings change.
 * No prompt if a snack is already logged today, "Skip today" was pressed, or prompts are off.
 */
object FuelPrompt {
    private const val NOTIF = 31
    private const val EXTRA_ITEM = "item"
    private const val ACTION_FIRE = "fuel_fire"
    private const val ACTION_LOG = "fuel_log"
    private const val ACTION_SKIP = "fuel_skip"

    private fun intent(context: Context, action: String, code: Int, extras: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getBroadcast(
            context, code, Intent(context, FuelPromptReceiver::class.java).setAction(action).apply(extras),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private suspend fun doneToday(): Boolean {
        val dao = Graph.dao
        val today = FuelStore.todayIso()
        return dao.setting(Settings.FUEL_SKIP_DAY) == today || dao.foodLogsOnce(today).any { it.slot == MealSlot.SNACK }
    }

    suspend fun reschedule(context: Context) {
        val app = context.applicationContext
        val dao = Graph.dao
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        if (dao.setting(Settings.FUEL_PROMPT_ON) == "0") {
            am.cancel(intent(app, ACTION_FIRE, 81))
            return
        }
        val next = SnackPrompt.next(
            LocalDateTime.now(),
            SnackPrompt.parse(dao.setting(Settings.FUEL_PROMPT_WEEKDAY)),
            SnackPrompt.parse(dao.setting(Settings.FUEL_PROMPT_WEEKEND)),
            doneToday(),
        )
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = intent(app, ACTION_FIRE, 81)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            ErrorLog.log("FUEL", "Exact snack prompt not allowed; using a while-idle one", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Today's suggestion: the same all day, never archived, not yesterday's snack, favourites ×2. */
    suspend fun suggestion(): com.muir.bear.data.FoodItem? {
        val dao = Graph.dao
        val items = dao.allFoodItems().filter { it.category in FoodCategory.snacks }
        val yesterday = FuelStore.iso(LocalDate.now().minusDays(1))
        val ate = dao.foodLogsOnce(yesterday).mapNotNull { it.foodItemId }.toSet()
        val pick = SnackPicker.next(items.map { SnackPicker.Item(it.id, it.favourite, it.archived) }, ate, LocalDate.now().toEpochDay())
        return items.firstOrNull { it.id == pick?.id }
    }

    /** Clears today's prompt, e.g. once a snack is logged from the list. */
    fun dismiss(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIF)
    }

    internal suspend fun handle(context: Context, intent: Intent) {
        val dao = Graph.dao
        val nm = context.getSystemService(NotificationManager::class.java)
        when (intent.action) {
            ACTION_FIRE -> {
                if (dao.setting(Settings.FUEL_PROMPT_ON) != "0" && !doneToday()) {
                    suggestion()?.let { notify(context, it) }
                }
            }
            ACTION_LOG -> {
                nm?.cancel(NOTIF)
                val item = dao.foodItem(intent.getLongExtra(EXTRA_ITEM, -1))
                if (item != null) FuelStore.logSnack(item, LogSource.PROMPT)
            }
            ACTION_SKIP -> {
                nm?.cancel(NOTIF)
                dao.putSetting(Setting(Settings.FUEL_SKIP_DAY, FuelStore.todayIso()))
            }
        }
        reschedule(context)
    }

    private fun notify(context: Context, item: com.muir.bear.data.FoodItem) {
        // "Something else" opens the snack list.
        val open = PendingIntent.getActivity(
            context, 82,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_OPEN, "snacks"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, TrainingApp.CHANNEL_FUEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setColor(0xFFA8875A.toInt())
            .setContentTitle("Fuel")
            .setContentText(SnackPrompt.text(item.name, item.portion))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Log it", intent(context, ACTION_LOG, 83) { putExtra(EXTRA_ITEM, item.id) })
            .addAction(0, "Something else", open)
            .addAction(0, "Skip today", intent(context, ACTION_SKIP, 84))
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java)?.notify(NOTIF, n) }
            .onFailure { ErrorLog.log("FUEL", "Couldn't show the snack prompt", it) }
    }
}

class FuelPromptReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Graph.scope.launch {
            try {
                FuelPrompt.handle(context.applicationContext, intent)
            } finally {
                pending.finish()
            }
        }
    }
}
