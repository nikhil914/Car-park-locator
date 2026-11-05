package com.realworldmagic.parkpin.reminders

import android.Manifest
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNotificationManager
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MeterReminderSchedulerTest {
    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: MeterReminderScheduler
    private lateinit var notifications: ShadowNotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        scheduler = MeterReminderScheduler(context)
        notifications = shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
    }

    private fun infos(spotId: Long): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(MeterReminderScheduler.uniqueName(spotId)).get()

    /** Drives the main looper until [predicate] holds; CoroutineWorker finishes off the test thread. */
    private fun awaitCondition(timeoutMs: Long = 5_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Condition not met in $timeoutMs ms")
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    private fun settleBriefly() {
        repeat(10) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    @Test
    fun futureReminder_waitsForDelayThenPostsNotification() {
        val now = System.currentTimeMillis()
        scheduler.schedule(spotId = 5, meterExpiresAtMs = now + TimeUnit.MINUTES.toMillis(60), leadMinutes = 10, nowMs = now)

        val info = infos(5).single()
        assertTrue(MeterReminderScheduler.TAG in info.tags)
        settleBriefly()
        assertEquals(WorkInfo.State.ENQUEUED, infos(5).single().state)
        assertTrue(notifications.allNotifications.isEmpty())

        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(info.id)

        awaitCondition { infos(5).single().state == WorkInfo.State.SUCCEEDED }
        val posted = notifications.allNotifications.single()
        assertEquals(NotificationChannels.METER_REMINDERS, posted.channelId)
        assertEquals("Parking meter expiring", posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }

    @Test
    fun leadTimeAlreadyPassed_firesWithoutDelay() {
        val now = System.currentTimeMillis()
        scheduler.schedule(spotId = 6, meterExpiresAtMs = now + TimeUnit.MINUTES.toMillis(3), leadMinutes = 10, nowMs = now)

        awaitCondition { infos(6).single().state == WorkInfo.State.SUCCEEDED }
        assertEquals(1, notifications.allNotifications.size)
    }

    @Test
    fun reschedule_replacesExistingWork() {
        val now = System.currentTimeMillis()
        scheduler.schedule(8, now + TimeUnit.MINUTES.toMillis(60), 10, now)
        val firstId = infos(8).single().id
        scheduler.schedule(8, now + TimeUnit.MINUTES.toMillis(90), 10, now)

        val pending = infos(8).filter { !it.state.isFinished }
        assertEquals(1, pending.size)
        assertTrue(pending.single().id != firstId)
        assertEquals(WorkInfo.State.CANCELLED, workManager.getWorkInfoById(firstId).get()!!.state)
    }

    @Test
    fun expiredMeter_schedulesNothingAndCancelsExisting() {
        val now = System.currentTimeMillis()
        scheduler.schedule(9, now + TimeUnit.MINUTES.toMillis(60), 10, now)
        scheduler.schedule(9, now - 1, 10, now)

        assertTrue(infos(9).all { it.state == WorkInfo.State.CANCELLED })
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test
    fun cancelAndCancelAll() {
        val now = System.currentTimeMillis()
        scheduler.schedule(10, now + TimeUnit.MINUTES.toMillis(60), 10, now)
        scheduler.schedule(11, now + TimeUnit.MINUTES.toMillis(60), 10, now)
        scheduler.schedule(12, now + TimeUnit.MINUTES.toMillis(60), 10, now)

        scheduler.cancel(10)
        assertEquals(WorkInfo.State.CANCELLED, infos(10).single().state)
        assertEquals(WorkInfo.State.ENQUEUED, infos(11).single().state)

        scheduler.cancelAll()
        assertEquals(WorkInfo.State.CANCELLED, infos(11).single().state)
        assertEquals(WorkInfo.State.CANCELLED, infos(12).single().state)
    }
}
