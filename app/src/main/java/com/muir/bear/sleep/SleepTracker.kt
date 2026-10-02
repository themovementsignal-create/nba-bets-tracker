package com.muir.bear.sleep

import android.Manifest
import android.annotation.SuppressLint
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
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions
import com.muir.bear.ErrorLog
import com.muir.bear.Graph
import com.muir.bear.MainActivity
import com.muir.bear.R
import com.muir.bear.TrainingApp
import com.muir.bear.data.Settings
import com.muir.bear.data.Sleep
import com.muir.bear.data.SleepSample
import com.muir.bear.domain.SleepAnalysis
import com.muir.bear.steps.Steps
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
        /** True after Bear restarted tracking by itself (e.g. the phone closed it overnight). */
        val resumed: Boolean = false,
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

    /** Changes (or turns off, with null) the alarm for a night that's already being tracked. */
    fun setAlarm(context: Context, alarmAt: Long?, windowMin: Int) {
        if (_state.value == null) return
        try {
            context.startService(
                Intent(context, SleepTrackerService::class.java).setAction(SleepTrackerService.ACTION_SET_ALARM)
                    .putExtra("alarmAt", alarmAt ?: -1L)
                    .putExtra("windowMin", windowMin)
            )
        } catch (e: Exception) {
            ErrorLog.log("SLEEP", "Could not reach the sleep tracker", e)
        }
    }

    /** The alarm sound the user picked, or the phone's default alarm sound. */
    fun alarmSound(context: Context, picked: String?): Uri? =
        picked?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
            ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    fun stop(context: Context) = send(context, SleepTrackerService.ACTION_STOP)

    /** Picks an interrupted night back up (from the Sleep screen). */
    fun resume(context: Context) {
        try {
            ContextCompat.startForegroundService(context, Intent(context, SleepTrackerService::class.java).setAction(SleepTrackerService.ACTION_RESUME))
        } catch (e: Exception) {
            ErrorLog.log("SLEEP", "Could not resume tracking", e)
        }
    }

    /** Ends a tracked night at [wakeAt]: stages, score and snoring from its samples. */
    suspend fun closeNight(sleepId: Long, wakeAt: Long) {
        val dao = Graph.dao
        val samples = dao.sleepSamples(sleepId)
        val summary = SleepAnalysis.summarise(samples.map { it.movement }, samples.map { it.snoreSec })
        dao.sleep(sleepId)?.let { s ->
            dao.updateSleep(
                s.copy(
                    wakeAt = wakeAt,
                    score = summary.score.takeIf { summary.minutes >= 60 },
                    deepMin = summary.deep, lightMin = summary.light, remMin = summary.rem,
                    awakeMin = summary.awake, snoreMin = summary.snoreMin,
                )
            )
        }
    }

    /**
     * On app start: a tracked night still open with no tracker running is either still going
     * (Android may restart the tracker) or clearly over, in which case close it at its last data.
     */
    suspend fun closeStaleNights(context: Context) {
        if (_state.value != null) return
        val dao = Graph.dao
        for (s in dao.allSleeps().filter { it.tracked && it.wakeAt == null }) {
            val last = dao.sleepSamples(s.id).maxOfOrNull { it.at }
            val action = com.muir.bear.domain.SleepRecovery.decide(s.bedAt, s.alarmAt, last, System.currentTimeMillis())
            if (action is com.muir.bear.domain.SleepRecovery.Close) {
                closeNight(s.id, action.wakeAt)
                ErrorLog.log("SLEEP", "Closed an interrupted night: tracking stopped at ${java.time.Instant.ofEpochMilli(action.wakeAt)}", null)
                if (savedSession(context)?.sleepId == s.id) saveSession(context, null)
            }
        }
    }

    // What the tracker needs to pick a night back up after Android restarts it.
    private const val PREFS = "bear_sleep"
    internal fun saveSession(context: Context, live: Live?) {
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (live == null) e.clear() else e.putLong("sleepId", live.sleepId).putLong("startedAt", live.startedAt)
            .putLong("alarmAt", live.alarmAt ?: -1L).putInt("windowMin", live.windowMin).putBoolean("listen", live.listening)
        e.apply()
    }
    internal fun savedSession(context: Context): Live? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = p.getLong("sleepId", -1L).takeIf { it > 0 } ?: return null
        return Live(id, p.getLong("startedAt", 0L), p.getLong("alarmAt", -1L).takeIf { it > 0 }, p.getInt("windowMin", 30), p.getBoolean("listen", false), modelReady = false)
    }

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
            ACTION_RESUME -> if (SleepTracker.state.value == null) resumeTracking(fromUser = true) else enterForeground(SleepTracker.state.value!!.listening)
            ACTION_SET_ALARM -> SleepTracker.state.value?.let { live ->
                val alarmAt = intent.getLongExtra("alarmAt", -1L).takeIf { it > 0 }
                val updated = live.copy(alarmAt = alarmAt, windowMin = intent.getIntExtra("windowMin", live.windowMin))
                SleepTracker._state.value = updated
                SleepTracker.saveSession(this, updated)
                if (alarmAt != null) WakeAlarm.schedule(this, alarmAt) else WakeAlarm.cancel(this)
                Graph.scope.launch { Graph.dao.sleep(live.sleepId)?.let { Graph.dao.updateSleep(it.copy(alarmAt = alarmAt)) } }
                updateNotification()
            }
            ACTION_STOP -> finishNight()
            // Android restarted us after closing the app (intent is null): carry on with the night.
            null -> if (SleepTracker.state.value == null) resumeTracking(fromUser = false)
            else -> if (SleepTracker.state.value == null) stopSelf()
        }
        // Ask Android to restart the tracker if it has to close it overnight.
        return START_STICKY
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
        val live = SleepTracker.Live(sleepId, now, alarmAt, windowMin, listen, modelReady = false)
        SleepTracker._state.value = live
        SleepTracker.saveSession(this, live)
        enterForeground(listen)
        // The alarm is held by Android itself, so it rings even if tracking is closed overnight.
        if (alarmAt != null) WakeAlarm.schedule(this, alarmAt) else WakeAlarm.cancel(this)
        beginSensing(listen)
        Steps.sampleOnce(applicationContext) {}
    }

    private fun beginSensing(listen: Boolean) {
        running = true
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bear:sleep")
            .apply { acquire(14 * 3600_000L) }
        sensorManager = getSystemService(SensorManager::class.java)
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager?.registerListener(motionListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (listen) startAudio()
        handler.postDelayed(minuteTick, 60_000)
    }

    /**
     * Picks the night back up after Android closed and restarted the tracker (or the user taps
     * Resume). Movement always resumes; the microphone only if Android allows it from here.
     */
    private fun resumeTracking(fromUser: Boolean) {
        val saved = SleepTracker.savedSession(this)
        val open = saved?.let { runBlocking { Graph.dao.sleep(it.sleepId) } }?.takeIf { it.wakeAt == null }
        if (saved == null || open == null) { SleepTracker.saveSession(this, null); stopSelf(); return }
        val samples = runBlocking { Graph.dao.sleepSamples(open.id) }
        nightMovement.clear()
        nightMovement += samples.map { it.movement }
        // From the background Android may refuse the microphone; fall back to movement only.
        var listen = saved.listening && hasMic() && fromUser
        try {
            SleepTracker._state.value = saved.copy(listening = listen, minutes = samples.size, snoreSec = samples.sumOf { it.snoreSec }, resumed = true)
            enterForeground(listen)
        } catch (e: Exception) {
            ErrorLog.log("SLEEP", "Resumed without the microphone", e)
            listen = false
            SleepTracker._state.value = SleepTracker._state.value?.copy(listening = false)
            try { enterForeground(false) } catch (e2: Exception) {
                ErrorLog.log("SLEEP", "Couldn't resume tracking", e2)
                SleepTracker._state.value = null
                stopSelf(); return
            }
        }
        ErrorLog.log("SLEEP", "Tracking resumed (${if (fromUser) "by you" else "restarted by Android"}); last data ${samples.lastOrNull()?.at?.let { clock(it) } ?: "none"}", null)
        WakeAlarm.restore(this)
        beginSensing(listen)
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

    /**
     * Smart wake: inside the wake window, ring early when you're in light sleep. The alarm time
     * itself is rung by Android ([WakeAlarm]), so it doesn't depend on this tick.
     */
    private fun checkAlarm() {
        val live = SleepTracker.state.value ?: return
        val alarmAt = live.alarmAt ?: return
        if (WakeAlarm.ringing.value) return
        if (WakeAlarm.nextAt(this) != alarmAt) return // snoozed, cancelled or changed
        val now = System.currentTimeMillis()
        if (now in (alarmAt - live.windowMin * 60_000L) until alarmAt && SleepAnalysis.isLightNow(nightMovement)) {
            WakeAlarm.ringNow(this)
        }
    }

    private fun finishNight() {
        val live = SleepTracker.state.value
        // Awake: silence a ringing alarm, or cancel tonight's if you're up before it.
        if (WakeAlarm.ringing.value) WakeAlarm.dismiss(this) else if (!WakeAlarm.isTest(this)) WakeAlarm.cancel(this)
        running = false
        handler.removeCallbacksAndMessages(null)
        sensorManager?.unregisterListener(motionListener)
        audioThread?.join(1500)
        classifier?.close()
        classifier = null
        SleepTracker.saveSession(this, null)
        if (live != null) {
            flushMinute()
            runBlocking { SleepTracker.closeNight(live.sleepId, System.currentTimeMillis()) }
            SleepTracker.justFinished.value = live.sleepId
        }
        Steps.sampleOnce(applicationContext) {}
        SleepTracker._state.value = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
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
            .setColor(BRONZE)
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

    private fun clock(millis: Long): String =
        java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        sensorManager?.unregisterListener(motionListener)
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "sleep_start"
        const val ACTION_STOP = "sleep_stop"
        const val ACTION_RESUME = "sleep_resume"
        const val ACTION_SET_ALARM = "sleep_set_alarm"
        private const val NOTIF_TRACKING = 11
        private const val BRONZE = 0xFFA8875A.toInt()
    }
}

