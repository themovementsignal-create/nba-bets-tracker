package io.github.themovementsignal.training.sleep

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.RingtoneManager
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
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions
import io.github.themovementsignal.training.ErrorLog
import io.github.themovementsignal.training.Graph
import io.github.themovementsignal.training.MainActivity
import io.github.themovementsignal.training.R
import io.github.themovementsignal.training.TrainingApp
import io.github.themovementsignal.training.data.Sleep
import io.github.themovementsignal.training.data.SleepSample
import io.github.themovementsignal.training.domain.SleepAnalysis
import io.github.themovementsignal.training.steps.Steps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Overnight sleep tracking, Sleep Cycle style: the phone lies on the mattress, the accelerometer
 * measures movement and the microphone listens for snoring (classified on the phone by Google's
 * YAMNet model; no audio is stored or sent anywhere). A smart alarm wakes you during light sleep
 * inside your wake window.
 */
object SleepTracker {
    data class Live(
        val sleepId: Long,
        val startedAt: Long,
        val alarmAt: Long?,
        val windowMin: Int,
        val listening: Boolean,
        val modelReady: Boolean,
        val minutes: Int = 0,
        val snoreSec: Int = 0,
        val ringing: Boolean = false,
        val snoozedUntil: Long? = null,
    )

    internal val _state = MutableStateFlow<Live?>(null)
    val state: StateFlow<Live?> = _state

    /** Set when a tracked night has just ended, so the UI can ask "how did you sleep?". */
    val justFinished = MutableStateFlow<Long?>(null)

    fun start(context: Context, alarmAt: Long?, windowMin: Int, listen: Boolean) {
        val i = Intent(context, SleepTrackerService::class.java).setAction(SleepTrackerService.ACTION_START)
            .putExtra("alarmAt", alarmAt ?: -1L)
            .putExtra("windowMin", windowMin)
            .putExtra("listen", listen)
        ContextCompat.startForegroundService(context, i)
    }

    fun stop(context: Context) = send(context, SleepTrackerService.ACTION_STOP)
    fun snooze(context: Context) = send(context, SleepTrackerService.ACTION_SNOOZE)

    private fun send(context: Context, action: String) {
        try {
            context.startService(Intent(context, SleepTrackerService::class.java).setAction(action))
        } catch (e: Exception) {
            ErrorLog.log("SLEEP", "Could not reach the sleep tracker", e)
        }
    }
}

class SleepTrackerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var sensorManager: SensorManager? = null
    private var audioThread: Thread? = null
    @Volatile private var running = false
    private var classifier: AudioClassifier? = null
    private var player: MediaPlayer? = null

    // Per-minute accumulators (written from sensor/audio threads).
    private val lock = Any()
    private var movementAcc = 0f
    private var noiseMaxDb = 0f
    private var snoreSecAcc = 0
    private val gravity = FloatArray(3)
    private var gravityReady = false
    private val nightMovement = mutableListOf<Float>()

    private val minuteTick = object : Runnable {
        override fun run() {
            flushMinute()
            checkAlarm()
            handler.postDelayed(this, 60_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (SleepTracker.state.value == null) {
                val alarmAt = intent.getLongExtra("alarmAt", -1L).takeIf { it > 0 }
                startTracking(alarmAt, intent.getIntExtra("windowMin", 30), intent.getBooleanExtra("listen", true))
            } else {
                enterForeground(SleepTracker.state.value!!.listening)
            }
            ACTION_SNOOZE -> snooze()
            ACTION_STOP -> finishNight()
            else -> if (SleepTracker.state.value == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun hasMic() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun enterForeground(listening: Boolean) {
        val type = if (Build.VERSION.SDK_INT >= 30 && listening) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(this, NOTIF_TRACKING, trackingNotification(), type)
    }

    private fun startTracking(alarmAt: Long?, windowMin: Int, listenWanted: Boolean) {
        val listen = listenWanted && hasMic()
        val now = System.currentTimeMillis()
        val sleepId = runBlocking { Graph.dao.insertSleep(Sleep(bedAt = now, tracked = true, alarmAt = alarmAt)) }
        SleepTracker._state.value = SleepTracker.Live(sleepId, now, alarmAt, windowMin, listen, modelReady = false)
        enterForeground(listen)
        running = true

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sig:sleep")
            .apply { acquire(14 * 3600_000L) }

        sensorManager = getSystemService(SensorManager::class.java)
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager?.registerListener(motionListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (listen) startAudio()
        handler.postDelayed(minuteTick, 60_000)
        alarmAt?.let { scheduleBackupAlarm(it) }
        Steps.sampleOnce(applicationContext) {}
    }

    // ---------- Movement ----------

    private val motionListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val alpha = 0.9f
            if (!gravityReady) {
                e.values.copyInto(gravity, 0, 0, 3); gravityReady = true; return
            }
            for (i in 0..2) gravity[i] = alpha * gravity[i] + (1 - alpha) * e.values[i]
            val x = e.values[0] - gravity[0]
            val y = e.values[1] - gravity[1]
            val z = e.values[2] - gravity[2]
            val mag = sqrt(x * x + y * y + z * z)
            // Ignore sensor noise; count only real movement.
            if (mag > 0.03f) synchronized(lock) { movementAcc += mag }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // ---------- Sound + snoring ----------

    @SuppressLint("MissingPermission")
    private fun startAudio() {
        try {
            classifier = AudioClassifier.createFromOptions(
                this,
                AudioClassifier.AudioClassifierOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("yamnet.tflite").build())
                    .setRunningMode(RunningMode.AUDIO_CLIPS)
                    .setMaxResults(5)
                    .build(),
            )
            SleepTracker._state.value = SleepTracker._state.value?.copy(modelReady = true)
        } catch (e: Throwable) {
            ErrorLog.log("SLEEP", "Snore model failed to load; recording loudness only", e)
        }
        audioThread = Thread({ audioLoop() }, "sleep-audio").apply { start() }
    }

    @SuppressLint("MissingPermission")
    private fun audioLoop() {
        val rate = 16_000
        val frame = 15_600 // 0.975 s, YAMNet's input size
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, frame * 2))
        } catch (e: Exception) {
            ErrorLog.log("SLEEP", "Microphone unavailable", e); return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) { record.release(); return }
        val pcm = ShortArray(frame)
        val floats = FloatArray(frame)
        try {
            record.startRecording()
            while (running) {
                var read = 0
                while (read < frame && running) {
                    val n = record.read(pcm, read, frame - read)
                    if (n <= 0) break
                    read += n
                }
                if (read < frame) continue
                var sum = 0.0
                for (i in 0 until frame) {
                    val f = pcm[i] / 32768f
                    floats[i] = f
                    sum += f * f
                }
                val rms = sqrt(sum / frame)
                val db = (20 * log10(rms.coerceAtLeast(1e-6)) + 90).toFloat() // rough dB scale
                var snoring = false
                // Only run the model when there is some sound, to save battery.
                if (db > 35f) classifier?.let { c ->
                    try {
                        val data = AudioData.create(
                            AudioData.AudioDataFormat.builder().setNumOfChannels(1).setSampleRate(rate.toFloat()).build(),
                            frame,
                        )
                        data.load(floats)
                        val result = c.classify(data)
                        snoring = result.classificationResults().any { r ->
                            r.classifications().any { cl ->
                                cl.categories().any { it.categoryName().equals("Snoring", true) && it.score() >= 0.3f }
                            }
                        }
                    } catch (e: Throwable) {
                        ErrorLog.log("SLEEP", "Snore classification failed", e)
                        classifier = null
                    }
                }
                synchronized(lock) {
                    if (db > noiseMaxDb) noiseMaxDb = db
                    if (snoring) snoreSecAcc += 1
                }
            }
        } catch (e: Exception) {
            ErrorLog.log("SLEEP", "Audio loop stopped", e)
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }

    // ---------- Per-minute bookkeeping and smart alarm ----------

    private fun flushMinute() {
        val live = SleepTracker.state.value ?: return
        val (movement, noise, snore) = synchronized(lock) {
            val r = Triple(movementAcc, noiseMaxDb, snoreSecAcc)
            movementAcc = 0f; noiseMaxDb = 0f; snoreSecAcc = 0
            r
        }
        nightMovement += movement
        val sample = SleepSample(sleepId = live.sleepId, at = System.currentTimeMillis() - 60_000, movement = movement, noiseDb = noise, snoreSec = snore)
        Graph.scope.launch { Graph.dao.insertSleepSample(sample) }
        SleepTracker._state.value = live.copy(minutes = live.minutes + 1, snoreSec = live.snoreSec + snore)
        updateNotification()
    }

    private fun checkAlarm() {
        val live = SleepTracker.state.value ?: return
        val alarmAt = live.alarmAt ?: return
        if (live.ringing) return
        val now = System.currentTimeMillis()
        val snoozed = live.snoozedUntil
        val due = when {
            snoozed != null -> now >= snoozed
            now >= alarmAt -> true
            now >= alarmAt - live.windowMin * 60_000L -> SleepAnalysis.isLightNow(nightMovement)
            else -> false
        }
        if (due) ring()
    }

    private fun ring() {
        val live = SleepTracker.state.value ?: return
        SleepTracker._state.value = live.copy(ringing = true, snoozedUntil = null)
        try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@SleepTrackerService, uri)
                isLooping = true
                setVolume(0.05f, 0.05f)
                prepare()
                start()
            }
            // Gentle wake: volume rises over about a minute.
            var step = 1
            handler.post(object : Runnable {
                override fun run() {
                    val p = player ?: return
                    val v = (0.05f + step * 0.05f).coerceAtMost(1f)
                    runCatching { p.setVolume(v, v) }
                    step++
                    if (v < 1f) handler.postDelayed(this, 3_000)
                }
            })
        } catch (e: Exception) {
            ErrorLog.log("SLEEP", "Could not play the alarm sound", e)
        }
        vibrateRepeating()
        val nm = getSystemService(NotificationManager::class.java)
        runCatching { nm.notify(NOTIF_ALARM, alarmNotification()) }
    }

    private fun stopRinging() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        vibrator().cancel()
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ALARM)
    }

    private fun snooze() {
        val live = SleepTracker.state.value ?: return
        stopRinging()
        SleepTracker._state.value = live.copy(ringing = false, snoozedUntil = System.currentTimeMillis() + 9 * 60_000L)
        handler.removeCallbacks(minuteTick)
        handler.postDelayed(minuteTick, 30_000)
        updateNotification()
    }

    private fun finishNight() {
        val live = SleepTracker.state.value
        stopRinging()
        running = false
        handler.removeCallbacksAndMessages(null)
        sensorManager?.unregisterListener(motionListener)
        audioThread?.join(1500)
        classifier?.close()
        classifier = null
        cancelBackupAlarm()
        if (live != null) {
            flushMinute()
            val sleepId = live.sleepId
            runBlocking {
                val dao = Graph.dao
                val samples = dao.sleepSamples(sleepId)
                val summary = SleepAnalysis.summarise(samples.map { it.movement }, samples.map { it.snoreSec })
                dao.sleep(sleepId)?.let { s ->
                    dao.updateSleep(
                        s.copy(
                            wakeAt = System.currentTimeMillis(),
                            score = summary.score.takeIf { summary.minutes >= 60 },
                            deepMin = summary.deep, lightMin = summary.light, remMin = summary.rem,
                            awakeMin = summary.awake, snoreMin = summary.snoreMin,
                        )
                    )
                }
            }
            SleepTracker.justFinished.value = sleepId
        }
        Steps.sampleOnce(applicationContext) {}
        SleepTracker._state.value = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---------- Backup alarm (if the tracker were killed overnight) ----------

    private fun backupIntent(): PendingIntent = PendingIntent.getBroadcast(
        this, 51, Intent(this, SleepAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun scheduleBackupAlarm(at: Long) {
        val am = getSystemService(AlarmManager::class.java) ?: return
        try {
            // One minute after the latest wake time, only fires if tracking stopped unexpectedly.
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at + 60_000, openAppIntent()), backupIntent())
        } catch (e: SecurityException) {
            ErrorLog.log("SLEEP", "Exact backup alarm not allowed", e)
        }
    }

    private fun cancelBackupAlarm() {
        getSystemService(AlarmManager::class.java)?.cancel(backupIntent())
    }

    // ---------- Notifications ----------

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 52,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_OPEN, "sleep"),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun actionIntent(action: String, code: Int): PendingIntent = PendingIntent.getService(
        this, code, Intent(this, SleepTrackerService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun trackingNotification(): Notification {
        val live = SleepTracker.state.value
        val alarm = live?.alarmAt?.let { "Alarm ${clock(it)}" + if ((live.windowMin) > 0) " (wake window ${live.windowMin} min)" else "" } ?: "No alarm"
        val snore = live?.takeIf { it.listening }?.let { " · snoring ${it.snoreSec / 60} min" } ?: ""
        return NotificationCompat.Builder(this, TrainingApp.CHANNEL_SLEEP)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setColor(GOLD)
            .setContentTitle("Tracking your sleep")
            .setContentText(alarm + snore)
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .addAction(0, "Stop · I'm awake", actionIntent(ACTION_STOP, 53))
            .build()
    }

    private fun updateNotification() {
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF_TRACKING, trackingNotification()) }
    }

    private fun alarmNotification(): Notification =
        NotificationCompat.Builder(this, TrainingApp.CHANNEL_SLEEP_ALARM)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setColor(GOLD)
            .setContentTitle("Good morning")
            .setContentText("Time to get up")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(openAppIntent(), true)
            .setContentIntent(openAppIntent())
            .addAction(0, "Snooze 9 min", actionIntent(ACTION_SNOOZE, 54))
            .addAction(0, "I'm up", actionIntent(ACTION_STOP, 55))
            .build()

    private fun vibrator(): Vibrator = if (Build.VERSION.SDK_INT >= 31) {
        getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(VIBRATOR_SERVICE) as Vibrator
    }

    private fun vibrateRepeating() {
        runCatching { vibrator().vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 800), 0)) }
    }

    private fun clock(millis: Long): String =
        java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        sensorManager?.unregisterListener(motionListener)
        stopRinging()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "sleep_start"
        const val ACTION_STOP = "sleep_stop"
        const val ACTION_SNOOZE = "sleep_snooze"
        private const val NOTIF_TRACKING = 11
        private const val NOTIF_ALARM = 12
        private const val GOLD = 0xFFF5C542.toInt()
    }
}

/** Fires only if the tracker was killed overnight: rings a plain alarm notification so you still wake up. */
class SleepAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (SleepTracker.state.value != null) return
        val open = PendingIntent.getActivity(
            context, 56, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, TrainingApp.CHANNEL_SLEEP_BACKUP)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Good morning")
            .setContentText("Your alarm (sleep tracking stopped overnight)")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(open, true)
            .setAutoCancel(true)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(13, n) }
    }
}
