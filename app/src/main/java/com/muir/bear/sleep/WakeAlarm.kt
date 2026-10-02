package com.muir.bear.sleep

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.muir.bear.ErrorLog
import com.muir.bear.Graph
import com.muir.bear.MainActivity
import com.muir.bear.R
import com.muir.bear.TrainingApp
import com.muir.bear.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

/**
 * The wake-up alarm, built like a clock app's alarm so it doesn't depend on sleep tracking:
 *  - Scheduled with AlarmManager.setAlarmClock (the most reliable kind: exempt from Doze, shows the
 *    alarm icon, survives Bear being closed). Re-scheduled after a reboot or app update.
 *  - When it fires, [AlarmRingService] plays a looping alarm sound (with fallbacks), vibrates and
 *    shows the full-screen alarm until you snooze or dismiss.
 * The sleep tracker can ring it early (smart wake) through [ringNow].
 */
object WakeAlarm {
    private const val PREFS = "bear_alarm"
    private const val KEY_AT = "next_at"
    private const val KEY_TEST = "is_test"
    const val SNOOZE_MIN = 9

    private val _ringing = MutableStateFlow(false)
    val ringing: StateFlow<Boolean> = _ringing

    /** What is making the noise: "chosen", "default", "bundled", "ringtone", "beeps" or "none" (for checks and tests). */
    @Volatile var soundSource: String = "none"
        internal set

    private val _next = MutableStateFlow<Long?>(null)
    /** Next scheduled ring time (alarm or snooze), or null. */
    val next: StateFlow<Long?> = _next

    internal fun setRinging(v: Boolean) { _ringing.value = v }

    @Volatile private var migrated = false

    /**
     * Stored in device-protected storage, which Android lets us read after a reboot before you
     * unlock, so the alarm can be put back straight away. Only the alarm time lives there.
     */
    private fun prefs(context: Context): android.content.SharedPreferences {
        val app = context.applicationContext
        val dp = app.createDeviceProtectedStorageContext()
        if (!migrated && isUnlocked(app)) {
            // One-off move from the old (unlock-only) location.
            runCatching { dp.moveSharedPreferencesFrom(app, PREFS) }
            migrated = true
        }
        return dp.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun isUnlocked(context: Context): Boolean =
        context.getSystemService(android.os.UserManager::class.java)?.isUserUnlocked != false

    private fun ringIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 61, Intent(context, SleepAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun showIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 62,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_OPEN, "sleep"),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Schedules the alarm for [at]; replaces any earlier one. [test] alarms don't touch sleep tracking. */
    fun schedule(context: Context, at: Long, test: Boolean = false) {
        val app = context.applicationContext
        prefs(app).edit().putLong(KEY_AT, at).putBoolean(KEY_TEST, test).apply()
        _next.value = at
        arm(app, at)
    }

    private fun arm(context: Context, at: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, showIntent(context)), ringIntent(context))
        } catch (e: SecurityException) {
            // Shouldn't happen (USE_EXACT_ALARM), but never fail silently: fall back to the closest thing.
            ErrorLog.log("ALARM", "Exact alarm not allowed; using a while-idle alarm", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, ringIntent(context))
        }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        prefs(app).edit().remove(KEY_AT).remove(KEY_TEST).apply()
        _next.value = null
        app.getSystemService(AlarmManager::class.java)?.cancel(ringIntent(app))
    }

    fun nextAt(context: Context): Long? = prefs(context.applicationContext).getLong(KEY_AT, -1L).takeIf { it > 0 }
    fun isTest(context: Context): Boolean = prefs(context.applicationContext).getBoolean(KEY_TEST, false)

    /** After a reboot, update or clock change: put the pending alarm back (or ring now if it was missed by < 30 min). */
    fun restore(context: Context) {
        val at = nextAt(context) ?: return
        val now = System.currentTimeMillis()
        _next.value = at
        when {
            at > now -> arm(context.applicationContext, at)
            now - at < 30 * 60_000L -> ringNow(context)
            else -> cancel(context)
        }
    }

    /** Rings immediately (smart wake, or a missed alarm). */
    fun ringNow(context: Context) {
        if (_ringing.value) return
        val app = context.applicationContext
        app.getSystemService(AlarmManager::class.java)?.cancel(ringIntent(app))
        try {
            ContextCompat.startForegroundService(app, Intent(app, AlarmRingService::class.java).setAction(AlarmRingService.ACTION_RING))
        } catch (e: Exception) {
            ErrorLog.log("ALARM", "Couldn't start the alarm", e)
        }
    }

    fun snooze(context: Context) = send(context, AlarmRingService.ACTION_SNOOZE)
    fun dismiss(context: Context) = send(context, AlarmRingService.ACTION_DISMISS)

    private fun send(context: Context, action: String) {
        try {
            context.startService(Intent(context, AlarmRingService::class.java).setAction(action))
        } catch (e: Exception) {
            ErrorLog.log("ALARM", "Couldn't reach the alarm", e)
        }
    }
}

/** Fired by AlarmManager at the alarm time. */
class SleepAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            ContextCompat.startForegroundService(context, Intent(context, AlarmRingService::class.java).setAction(AlarmRingService.ACTION_RING))
        } catch (e: Exception) {
            ErrorLog.log("ALARM", "Couldn't start the alarm service; showing a plain alarm notification", e)
            AlarmRingService.fallbackNotification(context)
        }
    }
}

/** Puts the alarm back after a reboot, an app update, or a clock/time-zone change. */
class AlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WakeAlarm.restore(context)
    }
}

/** Plays the alarm until you snooze or dismiss. Runs on its own so it works with or without tracking. */
class AlarmRingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var savedVolume: Int? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> ring()
            ACTION_SNOOZE -> {
                stopSound()
                val at = System.currentTimeMillis() + WakeAlarm.SNOOZE_MIN * 60_000L
                WakeAlarm.schedule(this, at, test = WakeAlarm.isTest(this))
                finish()
            }
            ACTION_DISMISS -> {
                val test = WakeAlarm.isTest(this)
                stopSound()
                WakeAlarm.cancel(this)
                // Waking up ends the tracked night (a test alarm leaves tracking alone).
                if (!test && SleepTracker.state.value != null) SleepTracker.stop(this)
                finish()
            }
            else -> if (!WakeAlarm.ringing.value) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun ring() {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, NOTIF_RING, ringNotification(), type)
        } catch (e: Exception) {
            ErrorLog.log("ALARM", "Couldn't show the alarm screen", e)
            fallbackNotification(this)
        }
        if (WakeAlarm.ringing.value || player != null || tone != null) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bear:alarm")
            .apply { acquire(MAX_RING_MS + 60_000L) }
        ensureAudible()
        startSound()
        vibrate()
        // "Ringing" means the sound and vibration have actually started.
        WakeAlarm.setRinging(true)
        // Never ring forever (e.g. phone left at home): stop after 30 minutes.
        handler.postDelayed({ stopSound(); WakeAlarm.cancel(this); finish() }, MAX_RING_MS)
    }

    /** If the alarm volume is off or very low, raise it while ringing (restored afterwards). */
    private fun ensureAudible() {
        val am = getSystemService(AudioManager::class.java) ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val cur = am.getStreamVolume(AudioManager.STREAM_ALARM)
        val floor = (max * 0.6).toInt().coerceAtLeast(1)
        if (cur < floor) {
            try {
                am.setStreamVolume(AudioManager.STREAM_ALARM, floor, 0)
                savedVolume = cur
            } catch (e: Exception) {
                ErrorLog.log("ALARM", "Couldn't raise the alarm volume", e)
            }
        }
    }

    private fun startSound() {
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        fun play(uri: android.net.Uri?): MediaPlayer? = uri?.let {
            runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setDataSource(this@AlarmRingService, it)
                    isLooping = true
                    setVolume(0.3f, 0.3f)
                    prepare()
                    start()
                }
            }.onFailure { e -> ErrorLog.log("ALARM", "Alarm sound failed: $uri", e) }.getOrNull()
        }
        // Before the first unlock after a reboot your settings are locked away: use the default sound.
        val picked = if (WakeAlarm.isUnlocked(this)) runCatching { runBlocking { Graph.dao.setting(Settings.ALARM_SOUND) } }.getOrNull() else null
        // Your sound -> the phone's alarm sound -> Bear's own chime (bundled, can't go missing)
        // -> the ringtone -> plain beeps.
        val bundled = android.net.Uri.parse("android.resource://$packageName/${R.raw.bear_alarm}")
        val tries = listOf(
            "chosen" to picked?.takeIf { it.isNotBlank() }?.let { android.net.Uri.parse(it) },
            "default" to android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM),
            "bundled" to bundled,
            "ringtone" to android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE),
        )
        WakeAlarm.soundSource = "none"
        for ((name, uri) in tries) {
            player = play(uri)
            if (player != null) { WakeAlarm.soundSource = name; break }
        }
        if (player != null) {
            // Gentle start, full volume within about 30 seconds.
            var step = 0
            handler.post(object : Runnable {
                override fun run() {
                    val p = player ?: return
                    val v = (0.3f + step * 0.07f).coerceAtMost(1f)
                    runCatching { p.setVolume(v, v) }
                    step++
                    if (v < 1f) handler.postDelayed(this, 3_000)
                }
            })
        } else {
            // Last resort: plain beeps on the alarm stream, which almost nothing can block.
            tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }.getOrNull()
            if (tone != null) {
                WakeAlarm.soundSource = "beeps"
                handler.post(object : Runnable {
                    override fun run() {
                        val t = tone ?: return
                        runCatching { t.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 900) }
                        handler.postDelayed(this, 1_400)
                    }
                })
            } else {
                ErrorLog.log("ALARM", "No alarm sound could be played at all; vibrating only", null)
            }
        }
    }

    private fun stopSound() {
        handler.removeCallbacksAndMessages(null)
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        tone?.let { runCatching { it.stopTone() }; it.release() }
        tone = null
        runCatching { vibrator().cancel() }
        savedVolume?.let { v -> runCatching { getSystemService(AudioManager::class.java)?.setStreamVolume(AudioManager.STREAM_ALARM, v, 0) } }
        savedVolume = null
        WakeAlarm.setRinging(false)
    }

    private fun finish() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        getSystemService(NotificationManager::class.java)?.cancel(NOTIF_RING)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun vibrator(): Vibrator = if (Build.VERSION.SDK_INT >= 31) {
        getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(VIBRATOR_SERVICE) as Vibrator
    }

    private fun vibrate() {
        runCatching {
            val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            @Suppress("DEPRECATION")
            vibrator().vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 700), 0), attrs)
        }
    }

    private fun actionIntent(action: String, code: Int): PendingIntent = PendingIntent.getService(
        this, code, Intent(this, AlarmRingService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ringNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 63,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_OPEN, "sleep"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, TrainingApp.CHANNEL_SLEEP_ALARM)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setColor(BRONZE)
            .setContentTitle("Good morning")
            .setContentText("Time to get up")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(open, true)
            .setContentIntent(open)
            .addAction(0, "Snooze ${WakeAlarm.SNOOZE_MIN} min", actionIntent(ACTION_SNOOZE, 64))
            .addAction(0, "I'm up", actionIntent(ACTION_DISMISS, 65))
            .build()
    }

    override fun onDestroy() {
        stopSound()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_RING = "alarm_ring"
        const val ACTION_SNOOZE = "alarm_snooze"
        const val ACTION_DISMISS = "alarm_dismiss"
        private const val NOTIF_RING = 14
        private const val BRONZE = 0xFFA8875A.toInt()
        private const val MAX_RING_MS = 30 * 60_000L

        /** If even the alarm service can't start, a loud alarm-category notification is the last line. */
        fun fallbackNotification(context: Context) {
            val open = PendingIntent.getActivity(context, 66, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(context, TrainingApp.CHANNEL_SLEEP_BACKUP)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle("Good morning")
                .setContentText("Your alarm")
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setFullScreenIntent(open, true)
                .setAutoCancel(true)
                .build()
            runCatching { context.getSystemService(NotificationManager::class.java).notify(13, n) }
        }
    }
}
