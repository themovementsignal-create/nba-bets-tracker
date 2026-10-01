package io.github.themovementsignal.training

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import io.github.themovementsignal.training.data.AppDatabase
import io.github.themovementsignal.training.data.TrainingDao
import io.github.themovementsignal.training.data.seedIfNeeded
import io.github.themovementsignal.training.io.DataIO
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** App-wide singletons. Kept deliberately simple: one database, one background scope. */
object Graph {
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
        Graph.db = AppDatabase.build(this)
        Graph.scope.launch {
            seedIfNeeded(Graph.db)
            DataIO.autoBackupIfDue(this@TrainingApp)
        }
        createChannels()
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
    }

    companion object {
        const val CHANNEL_TIMER = "rest_timer"
        const val CHANNEL_TIMER_DONE = "rest_timer_done"
    }
}
