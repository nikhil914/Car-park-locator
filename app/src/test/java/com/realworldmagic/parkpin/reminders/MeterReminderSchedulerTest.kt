package com.realworldmagic.parkpin.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.realworldmagic.parkpin.ParkPinApp
import com.realworldmagic.parkpin.testutil.TestSpots.spot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowNotificationManager
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MeterReminderSchedulerTest {
    private lateinit var app: ParkPinApp
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: MeterReminderScheduler
    private lateinit var notifications: ShadowNotificationManager
    private lateinit var alarms: ShadowAlarmManager

    private val minute = TimeUnit.MINUTES.toMillis(1)

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        shadowOf(app as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(app, config)
        workManager = WorkManager.getInstance(app)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        scheduler = MeterReminderScheduler(app)
        notifications = shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        alarms = shadowOf(app.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
    }

    private fun infos(spotId: Long): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(MeterReminderScheduler.uniqueName(spotId)).get()

    /** Active spot in Room with a meter, so the notifier does not treat it as deleted. */
    private fun activeSpotWithMeter(expiresAt: Long): Long = runBlocking {
        app.container.parkingRepository.saveNewSpot(spot(savedAtMs = 1_000, meterExpiresAtMs = expiresAt))
    }

    /** Drives the main looper until [predicate] holds; work and receivers finish off the test thread. */
    private fun awaitCondition(timeoutMs: Long = 5_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Condition not met in $timeoutMs ms")
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    @Test
    fun exactAllowed_setsAlarmClockAtLeadTime() {
        val now = System.currentTimeMillis()
        val mode = scheduler.schedule(spotId = 5, meterExpiresAtMs = now + 60 * minute, leadMinutes = 10, nowMs = now)

        assertEquals(MeterReminderScheduler.Mode.EXACT_ALARM, mode)
        val alarm = alarms.scheduledAlarms.single()
        assertEquals(now + 50 * minute, alarm.triggerAtMs)
        assertNotNull(alarm.alarmClockInfo)
        assertTrue(infos(5).isEmpty())
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test
    fun exactDenied_fallsBackToWindowedAlarm() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val now = System.currentTimeMillis()
        val mode = scheduler.schedule(spotId = 5, meterExpiresAtMs = now + 60 * minute, leadMinutes = 10, nowMs = now)

        assertEquals(MeterReminderScheduler.Mode.WINDOWED_ALARM, mode)
        val alarm = alarms.scheduledAlarms.single()
        assertNull(alarm.alarmClockInfo)
        assertEquals(now + 40 * minute, alarm.triggerAtMs)
        assertEquals(10 * minute, alarm.windowLengthMs)
    }

    @Test
    fun firedAlarm_postsNotification() {
        val now = System.currentTimeMillis()
        val expires = now + 60 * minute
        val id = activeSpotWithMeter(expires)
        scheduler.schedule(id, expires, 10, now)

        alarms.fireAlarm(alarms.scheduledAlarms.single())

        awaitCondition { notifications.allNotifications.isNotEmpty() }
        val posted = notifications.allNotifications.single()
        assertEquals(NotificationChannels.METER_REMINDERS, posted.channelId)
        assertEquals("Parking meter expiring", posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }

    @Test
    fun leadTimeAlreadyPassed_firesWithoutDelay() {
        val now = System.currentTimeMillis()
        val expires = now + 3 * minute
        val id = activeSpotWithMeter(expires)
        val mode = scheduler.schedule(spotId = id, meterExpiresAtMs = expires, leadMinutes = 10, nowMs = now)

        assertEquals(MeterReminderScheduler.Mode.WORK_MANAGER, mode)
        assertTrue(alarms.scheduledAlarms.isEmpty())
        awaitCondition { infos(id).single().state == WorkInfo.State.SUCCEEDED }
        awaitCondition { notifications.allNotifications.size == 1 }
    }

    @Test
    fun reschedule_replacesExistingAlarm() {
        val now = System.currentTimeMillis()
        scheduler.schedule(8, now + 60 * minute, 10, now)
        scheduler.schedule(8, now + 90 * minute, 10, now)

        val alarm = alarms.scheduledAlarms.single()
        assertEquals(now + 80 * minute, alarm.triggerAtMs)
    }

    @Test
    fun leadTimeChange_movesAlarm() {
        val now = System.currentTimeMillis()
        scheduler.schedule(8, now + 60 * minute, 10, now)
        scheduler.schedule(8, now + 60 * minute, 30, now)

        assertEquals(now + 30 * minute, alarms.scheduledAlarms.single().triggerAtMs)
    }

    @Test
    fun expiredMeter_schedulesNothingAndCancelsExisting() {
        val now = System.currentTimeMillis()
        scheduler.schedule(9, now + 60 * minute, 10, now)
        val mode = scheduler.schedule(9, now - 1, 10, now)

        assertEquals(MeterReminderScheduler.Mode.NONE, mode)
        assertTrue(alarms.scheduledAlarms.isEmpty())
        assertTrue(infos(9).all { it.state == WorkInfo.State.CANCELLED })
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test
    fun cancelAndCancelAll() {
        val now = System.currentTimeMillis()
        scheduler.schedule(10, now + 60 * minute, 10, now)
        scheduler.schedule(11, now + 70 * minute, 10, now)
        scheduler.schedule(12, now + 80 * minute, 10, now)
        assertEquals(3, alarms.scheduledAlarms.size)

        scheduler.cancel(10)
        assertEquals(
            listOf(now + 60 * minute, now + 70 * minute),
            alarms.scheduledAlarms.map { it.triggerAtMs }.sorted(),
        )

        // A fresh instance (e.g. after process death) still knows which alarms to cancel.
        MeterReminderScheduler(app).cancelAll()
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test
    fun rescheduleReceiver_restoresActiveSpotAlarm() = runBlocking {
        val now = System.currentTimeMillis()
        val expires = now + 60 * minute
        activeSpotWithMeter(expires)
        assertTrue(alarms.scheduledAlarms.isEmpty())

        MeterReminderRescheduleReceiver.rescheduleActive(app, now)

        val lead = app.container.settingsRepository.settings.first()
        assertEquals(expires - lead.meterReminderLeadMinutes * minute, alarms.scheduledAlarms.single().triggerAtMs)
    }
}
