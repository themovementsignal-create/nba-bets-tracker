package com.muir.bear.timer

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
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
import com.muir.bear.ErrorLog
import com.muir.bear.Graph
import com.muir.bear.MainActivity
import com.muir.bear.R
import com.muir.bear.TrainingApp
import com.muir.bear.domain.Horn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Rest timer shared by the workout screen and a small foreground service. The service keeps the
 * countdown alive with the screen off and alerts when rest is over (vibration + beep in the app,
 * a notification when the app is in the background).
 */
object RestTimer {
    /**
     * [setId] is the set the countdown belongs to (null for a manually started timer).
     * [work] = the "on" part of a timed set (e.g. 15 s on the bike); when it runs out the set is
     * ticked and its rest starts by itself. [upNext] holds the timed sets still to do in that
     * exercise (id to seconds), so work → rest → work runs on without touching the phone.
     */
    data class State(
        val endAt: Long,
        val totalSeconds: Int,
        val setId: Long?,
        val work: Boolean = false,
        val restSeconds: Int = 0,
        val upNext: List<Pair<Long, Int>> = emptyList(),
    )

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state

    /** True while the app is on screen (set by MainActivity). */
    @Volatile var appVisible: Boolean = false

    /** What plays when rest ends. Saved in Settings (REST_SOUND), loaded at start. */
    enum class Sound(val id: String, val label: String) { HORN("horn", "Horn"), BEEP("beep", "Beep"), SILENT("silent", "Buzz only") }
    @Volatile var sound: Sound = Sound.HORN
    fun soundFrom(saved: String?): Sound = Sound.entries.firstOrNull { it.id == saved } ?: Sound.HORN

    /** The rest that just ran out (for the buzzer flash on the workout screen). */
    data class Finished(val setId: Long?, val at: Long)
    private val _lastFinished = MutableStateFlow<Finished?>(null)
    val lastFinished: StateFlow<Finished?> = _lastFinished

    fun start(context: Context, seconds: Int, setId: Long? = null) {
        if (seconds <= 0) return
        _state.value = State(System.currentTimeMillis() + seconds * 1000L, seconds, setId)
        send(context, RestTimerService.ACTION_START)
    }

    /** Starts the "on" countdown of a timed set; its rest, and the sets in [upNext], follow by themselves. */
    fun startWork(context: Context, seconds: Int, setId: Long, restSeconds: Int, upNext: List<Pair<Long, Int>>) {
        if (seconds <= 0) return
        _state.value = State(System.currentTimeMillis() + seconds * 1000L, seconds, setId, work = true, restSeconds = restSeconds, upNext = upNext)
        send(context, RestTimerService.ACTION_START)
    }

    /** What follows [s] when it runs out (or is skipped): work → its rest → the next set's work → … */
    internal fun after(s: State, now: Long): State? = when {
        s.work && s.restSeconds > 0 -> s.copy(endAt = now + s.restSeconds * 1000L, totalSeconds = s.restSeconds, work = false)
        s.upNext.isNotEmpty() -> {
            val (id, secs) = s.upNext.first()
            s.copy(endAt = now + secs * 1000L, totalSeconds = secs, setId = id, work = true, upNext = s.upNext.drop(1))
        }
        else -> null
    }

    /** "Done" during work (ticks the set, rest starts) or "Skip rest" (the next set starts, or the timer stops). */
    fun skip(context: Context) {
        val s = _state.value ?: return
        val next = advance(s, flash = false)
        send(context, if (next != null) RestTimerService.ACTION_START else RestTimerService.ACTION_STOP)
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

    /** The countdown ran out: returns what runs next (null = all done). */
    internal fun finished(): State? = _state.value?.let { advance(it, flash = true) }

    private fun advance(s: State, flash: Boolean): State? {
        val now = System.currentTimeMillis()
        if (s.work) s.setId?.let { tick(it, s.totalSeconds, now) }
        if (flash) _lastFinished.value = Finished(s.setId, now)
        val next = after(s, now)
        _state.value = next
        return next
    }

    /** Marks a timed set done once its work countdown is over (keeps any numbers already typed in). */
    private fun tick(setId: Long, seconds: Int, now: Long) {
        Graph.scope.launch {
            val set = Graph.dao.setById(setId) ?: return@launch
            if (!set.completed) Graph.dao.updateSet(set.copy(completed = true, completedAt = now, seconds = set.seconds ?: seconds))
        }
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
                    this, NOTIF_RUNNING, runningNotification(s),
                    if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
                )
                if (s == null) {
                    stopNow(); return START_NOT_STICKY
                }
                schedule(s)
            }
            else -> stopNow()
        }
        return START_NOT_STICKY
    }

    private fun schedule(s: RestTimer.State) {
        handler.removeCallbacks(finish)
        handler.postDelayed(finish, (s.endAt - System.currentTimeMillis()).coerceAtLeast(0))
    }

    private fun stopNow() {
        handler.removeCallbacks(finish)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun onFinished() {
        val next = RestTimer.finished()
        val sound = RestTimer.sound
        vibrate(sound)
        when (sound) {
            RestTimer.Sound.HORN -> Buzzer.horn(this)
            RestTimer.Sound.BEEP -> if (RestTimer.appVisible) beep()
            RestTimer.Sound.SILENT -> {}
        }
        if (next != null) {
            // Work → rest → next set: keep going, with the notification showing the new countdown.
            runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF_RUNNING, runningNotification(next)) }
            schedule(next)
            return
        }
        if (!RestTimer.appVisible) {
            val nm = getSystemService(NotificationManager::class.java)
            val n = NotificationCompat.Builder(this, TrainingApp.CHANNEL_TIMER_DONE)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setColor(BRONZE)
                .setContentTitle("Rest over")
                .setContentText("Time for your next set")
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .setTimeoutAfter(60_000)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                // The horn (or nothing) plays instead of the channel's own sound.
                .setSilent(sound != RestTimer.Sound.BEEP)
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

    private fun vibrate(sound: RestTimer.Sound) {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        runCatching {
            // With the horn: one long buzz alongside it, like the buzzer at the end of a quarter.
            val pattern = if (sound == RestTimer.Sound.HORN) longArrayOf(0, 1_000) else longArrayOf(0, 500, 200, 500, 200, 700)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    private fun runningNotification(s: RestTimer.State?): Notification =
        NotificationCompat.Builder(this, TrainingApp.CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setColor(BRONZE)
            .setContentTitle(if (s?.work == true) "Work" else "Resting")
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(s?.endAt ?: System.currentTimeMillis())
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
        private const val BRONZE = 0xFFA8875A.toInt()
    }
}

/** Plays the arena horn (see domain/Horn) on the media stream, ducking any music for a moment. */
object Buzzer {
    private val samples by lazy { Horn.samples() }

    fun horn(context: Context) {
        runCatching {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val am = context.getSystemService(AudioManager::class.java)
            val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build()
            am?.requestAudioFocus(focus)
            val data = samples
            val track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(
                    AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(Horn.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(data.size * 2)
                .build()
            track.write(data, 0, data.size)
            track.play()
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { track.stop() }
                track.release()
                am?.abandonAudioFocusRequest(focus)
            }, data.size * 1000L / Horn.SAMPLE_RATE + 300)
        }.onFailure { ErrorLog.log("TIMER", "Couldn't play the horn", it) }
    }
}
