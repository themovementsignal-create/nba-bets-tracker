package com.muir.bear

import android.Manifest
import android.app.AlarmManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.muir.bear.sleep.WakeAlarm
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The wake-up alarm end to end on a real Android system: scheduled with AlarmManager, fired by
 * Android (not by Bear's tracking), rings, snoozes and dismisses.
 */
@RunWith(AndroidJUnit4::class)
class WakeAlarmTest {
    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun waitUntil(timeoutMs: Long, what: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > end) throw AssertionError("Timed out waiting for: $what")
            Thread.sleep(200)
        }
    }

    @After
    fun tidy() {
        WakeAlarm.dismiss(context)
        WakeAlarm.cancel(context)
    }

    @Test
    fun androidRingsTheAlarmThenSnoozeAndDismissWork() {
        val at = System.currentTimeMillis() + 3_000
        WakeAlarm.schedule(context, at, test = true)
        // Held by Android's alarm clock, not by Bear.
        val am = context.getSystemService(AlarmManager::class.java)
        assertEquals(at, am.nextAlarmClock?.triggerTime)

        waitUntil(30_000, "alarm to ring") { WakeAlarm.ringing.value }
        Log.i("WakeAlarmTest", "ringing; sound source = ${WakeAlarm.soundSource}")
        // Something must actually be playing (the bundled chime at the very least).
        assertTrue("no alarm sound played", WakeAlarm.soundSource != "none")

        WakeAlarm.snooze(context)
        waitUntil(10_000, "snooze to silence it") { !WakeAlarm.ringing.value }
        val snoozedTo = WakeAlarm.nextAt(context)!!
        assertTrue(snoozedTo - System.currentTimeMillis() in (WakeAlarm.SNOOZE_MIN - 1) * 60_000L..WakeAlarm.SNOOZE_MIN * 60_000L)

        WakeAlarm.dismiss(context)
        waitUntil(10_000, "dismiss to clear it") { WakeAlarm.nextAt(context) == null }
        assertNull(WakeAlarm.next.value)
    }

    @Test
    fun alarmSurvivesARebootBeforeUnlock() {
        // The alarm time is kept where Android can read it before you unlock after a reboot...
        val at = System.currentTimeMillis() + 2 * 60 * 60_000L
        WakeAlarm.schedule(context, at)
        val dp = context.createDeviceProtectedStorageContext().getSharedPreferences("bear_alarm", android.content.Context.MODE_PRIVATE)
        assertEquals(at, dp.getLong("next_at", -1L))
        // ...and the pieces that put it back and ring it are allowed to run then (direct boot).
        val pm = context.packageManager
        val flags = android.content.pm.PackageManager.MATCH_DIRECT_BOOT_AWARE or android.content.pm.PackageManager.MATCH_DIRECT_BOOT_UNAWARE
        assertTrue(pm.getReceiverInfo(android.content.ComponentName(context, com.muir.bear.sleep.AlarmBootReceiver::class.java), flags).directBootAware)
        assertTrue(pm.getReceiverInfo(android.content.ComponentName(context, com.muir.bear.sleep.SleepAlarmReceiver::class.java), flags).directBootAware)
        assertTrue(pm.getServiceInfo(android.content.ComponentName(context, com.muir.bear.sleep.AlarmRingService::class.java), flags).directBootAware)
    }

    @Test
    fun restoreReArmsAPendingAlarm() {
        val at = System.currentTimeMillis() + 60 * 60_000L
        WakeAlarm.schedule(context, at)
        // Simulate Android having dropped it (reboot / force stop), then the boot receiver running.
        context.getSystemService(AlarmManager::class.java).cancel(
            android.app.PendingIntent.getBroadcast(
                context, 61, android.content.Intent(context, com.muir.bear.sleep.SleepAlarmReceiver::class.java),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        WakeAlarm.restore(context)
        assertEquals(at, context.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime)
    }
}
