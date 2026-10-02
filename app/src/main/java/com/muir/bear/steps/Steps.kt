package com.muir.bear.steps

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import com.muir.bear.ErrorLog
import com.muir.bear.Graph
import com.muir.bear.data.DailySteps
import com.muir.bear.data.Setting
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pedometer using the phone's built-in step counter (counts while the phone is on you, even with
 * the app closed). The sensor gives a running total since the phone booted; we store each reading
 * and add the difference to today's total.
 */
object Steps {
    private const val LAST_COUNTER = "steps_last_counter"
    private val mutex = Mutex()
    private var listener: SensorEventListener? = null

    fun available(context: Context): Boolean =
        context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    /** Records a counter reading: the difference since the last reading is added to today. */
    suspend fun record(counter: Long) = mutex.withLock {
        val dao = Graph.dao
        val last = dao.setting(LAST_COUNTER)?.toLongOrNull()
        dao.putSetting(Setting(LAST_COUNTER, counter.toString()))
        if (last == null) return@withLock
        // A lower reading means the phone restarted and the counter began again from zero.
        val delta = if (counter >= last) counter - last else counter
        if (delta <= 0 || delta > 100_000) return@withLock
        val today = LocalDate.now().toEpochDay()
        val current = dao.stepsOn(today)?.steps ?: 0
        dao.upsertSteps(DailySteps(today, current + delta.toInt()))
    }

    /** Listen while the app is open, so the count on screen is live. */
    fun startListening(context: Context) {
        if (listener != null) return
        val sm = context.getSystemService(SensorManager::class.java) ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        val l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val value = event.values.firstOrNull()?.toLong() ?: return
                Graph.scope.launch { record(value) }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        try {
            sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            listener = l
        } catch (e: SecurityException) {
            // Physical activity permission not granted yet.
        } catch (e: Exception) {
            ErrorLog.log("STEPS", "Could not start the step counter", e)
        }
    }

    fun stopListening(context: Context) {
        val l = listener ?: return
        context.getSystemService(SensorManager::class.java)?.unregisterListener(l)
        listener = null
    }

    /** Takes one reading in the background (used just before midnight so steps land on the right day). */
    fun sampleOnce(context: Context, done: () -> Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null) { done(); return }
        val finished = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        lateinit var l: SensorEventListener
        fun finish(value: Long?) {
            if (!finished.compareAndSet(false, true)) return
            sm.unregisterListener(l)
            Graph.scope.launch {
                if (value != null) record(value)
                done()
            }
        }
        l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = finish(event.values.firstOrNull()?.toLong())
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        try {
            sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            handler.postDelayed({ finish(null) }, 5_000)
        } catch (e: Exception) {
            finish(null)
        }
    }

    /** Schedules a reading at 23:58 each night (inexact, battery friendly). */
    fun scheduleNightly(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val at = LocalDate.now().atTime(23, 58).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            .let { if (it < System.currentTimeMillis()) it + 86_400_000L else it }
        val pi = PendingIntent.getBroadcast(
            context, 41, Intent(context, StepsAlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
    }
}

class StepsAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Steps.sampleOnce(context.applicationContext) {
            Steps.scheduleNightly(context.applicationContext)
            pending.finish()
        }
    }
}
