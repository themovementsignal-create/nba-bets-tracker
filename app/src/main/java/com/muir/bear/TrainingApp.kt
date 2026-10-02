package com.muir.bear

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.muir.bear.data.AppDatabase
import com.muir.bear.data.TrainingDao
import com.muir.bear.data.MuscleData
import com.muir.bear.data.migrateDataV2
import com.muir.bear.data.seedIfNeeded
import com.muir.bear.steps.Steps
import com.muir.bear.io.DataIO
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** App-wide singletons. Kept deliberately simple: one database, one background scope. */
object Graph {
    lateinit var app: android.content.Context
    lateinit var db: AppDatabase
    val dao: TrainingDao get() = db.dao()
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> ErrorLog.log("BG", "Background task failed", e) }
    )
}

class TrainingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ErrorLog.init(this)
        Graph.app = applicationContext
        Graph.db = AppDatabase.build(this)
        Graph.scope.launch {
            seedIfNeeded(Graph.db)
            migrateDataV2(Graph.db)
            MuscleData.fillMissing()
            DataIO.autoBackupIfDue(this@TrainingApp)
        }
        createChannels()
        Steps.scheduleNightly(this)
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TIMER, "Rest timer (running)", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TIMER_DONE, "Rest timer finished", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SLEEP, "Sleep tracking (running)", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SLEEP_ALARM, "Wake-up alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                // The tracker plays the alarm itself; the channel stays silent to avoid a double sound.
                setSound(null, null)
                enableVibration(false)
            }
        )
        // Only used if sleep tracking died overnight: this one makes the alarm sound itself.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SLEEP_BACKUP, "Wake-up alarm (backup)", NotificationManager.IMPORTANCE_HIGH).apply {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                setSound(uri, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                enableVibration(true)
            }
        )
    }

    companion object {
        const val CHANNEL_TIMER = "rest_timer"
        const val CHANNEL_TIMER_DONE = "rest_timer_done"
        const val CHANNEL_SLEEP = "sleep_tracking"
        const val CHANNEL_SLEEP_ALARM = "sleep_alarm"
        const val CHANNEL_SLEEP_BACKUP = "sleep_alarm_backup"
    }
}
