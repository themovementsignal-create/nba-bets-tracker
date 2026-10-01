package io.github.themovementsignal.training.timer

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.themovementsignal.training.ErrorLog
import io.github.themovementsignal.training.MainActivity
import io.github.themovementsignal.training.R
import io.github.themovementsignal.training.TrainingApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Rest timer shared by the workout screen and a small foreground service. The service keeps the
 * countdown alive with the screen off and alerts when rest is over (vibration + beep in the app,
 * a notification when the app is in the background).
 */
object RestTimer {
    /** [setId] is the set that started the timer (null for a manually started timer). */
    data class State(val endAt: Long, val totalSeconds: Int, val setId: Long?)

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state

    /** True while the app is on screen (set by MainActivity). */
    @Volatile var appVisible: Boolean = false

    fun start(context: Context, seconds: Int, setId: Long? = null) {
        if (seconds <= 0) return
        _state.value = State(System.currentTimeMillis() + seconds * 1000L, seconds, setId)
        send(context, RestTimerService.ACTION_START)
    }

    fun adjust(context: Context, deltaSeconds: Int) {
        val s = _state.value ?: return
        val newEnd = s.endAt + deltaSeconds * 1000L
        if (newEnd <= System.currentTimeMillis()) {
            stop(context)
            return
        }
        _state.value = s.copy(endAt = newEnd, totalSeconds = (s.totalSeconds + deltaSeconds).coerceAtLeast(1))
        send(context, RestTimerService.ACTION_START)
    }

    fun stop(context: Context) {
        if (_state.value == null) return
        _state.value = null
        send(context, RestTimerService.ACTION_STOP)
    }

    internal fun finished() {
        _state.value = null
    }

    private fun send(context: Context, action: String) {
        val intent = Intent(context, RestTimerService::class.java).setAction(action)
        try {
            if (action == RestTimerService.ACTION_START) context.startForegroundService(intent) else context.startService(intent)
        } catch (e: Exception) {
            // e.g. not allowed to start from the background; the in-app countdown still works.
            ErrorLog.log("TIMER", "Could not start rest timer service", e)
        }
    }
}

class RestTimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val finish = Runnable { onFinished() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val s = RestTimer.state.value
                // Always enter the foreground first: Android requires it after startForegroundService.
                ServiceCompat.startForeground(
                    this, NOTIF_RUNNING, runningNotification(s?.endAt ?: System.currentTimeMillis()),
                    if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
                )
                if (s == null) {
                    stopNow(); return START_NOT_STICKY
                }
                handler.removeCallbacks(finish)
                handler.postDelayed(finish, (s.endAt - System.currentTimeMillis()).coerceAtLeast(0))
            }
            else -> stopNow()
        }
        return START_NOT_STICKY
    }

    private fun stopNow() {
        handler.removeCallbacks(finish)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun onFinished() {
        RestTimer.finished()
        vibrate()
        if (RestTimer.appVisible) {
            beep()
        } else {
            val nm = getSystemService(NotificationManager::class.java)
            val n = NotificationCompat.Builder(this, TrainingApp.CHANNEL_TIMER_DONE)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setColor(GOLD)
                .setContentTitle("Rest over")
                .setContentText("Time for your next set")
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .setTimeoutAfter(60_000)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .build()
            runCatching { nm.notify(NOTIF_DONE, n) }
        }
        stopNow()
    }

    private fun beep() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
            handler.postDelayed({ tone.release() }, 800)
        }
    }

    private fun vibrate() {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        runCatching {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 700), -1))
        }
    }

    private fun runningNotification(endAt: Long): Notification =
        NotificationCompat.Builder(this, TrainingApp.CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setColor(GOLD)
            .setContentTitle("Resting")
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endAt)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .build()

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun onDestroy() {
        handler.removeCallbacks(finish)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        private const val NOTIF_RUNNING = 1
        private const val NOTIF_DONE = 2
        private const val GOLD = 0xFFF5C542.toInt()
    }
}
