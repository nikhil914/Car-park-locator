package com.realworldmagic.parkpin.reminders

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.realworldmagic.parkpin.ParkPinApp
import com.realworldmagic.parkpin.testutil.TestSpots.spot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNotificationManager
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MeterReminderWorkerTest {
    private lateinit var app: ParkPinApp
    private lateinit var notifications: ShadowNotificationManager

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        shadowOf(app as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications = shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
    }

    private fun runWorker(spotId: Long, expiresAt: Long): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<MeterReminderWorker>(app)
            .setInputData(
                workDataOf(
                    MeterReminderWorker.KEY_SPOT_ID to spotId,
                    MeterReminderWorker.KEY_EXPIRES_AT_MS to expiresAt,
                ),
            )
            .build()
            .doWork()
    }

    /** The worker never reminds about spots that are not in Room, so tests insert a real one. */
    private fun activeSpot(expiresAt: Long): Long = runBlocking {
        app.container.parkingRepository.saveNewSpot(spot(savedAtMs = 1_000, meterExpiresAtMs = expiresAt))
    }

    @Test
    fun skipsDeletedSpot() {
        runWorker(spotId = 4242, expiresAt = System.currentTimeMillis() + 60_000)
        assertTrue(notifications.allNotifications.isEmpty())
    }

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()

    @Test
    fun postsNotificationOnMeterChannel() {
        val expires = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10) + 30_000
        val id = activeSpot(expires)

        assertEquals(ListenableWorker.Result.success(), runWorker(spotId = id, expiresAt = expires))

        val posted = notifications.allNotifications.single()
        assertEquals(NotificationChannels.METER_REMINDERS, posted.channelId)
        assertEquals("Parking meter expiring", posted.title())
        assertTrue(posted.text(), posted.text().startsWith("Your meter expires in 10 minutes"))
        assertTrue(posted.contentIntent != null)
    }

    @Test
    fun includesFloorAndNoteOfActiveSpot() = runBlocking {
        val expires = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)
        val id = app.container.parkingRepository.saveNewSpot(
            spot(savedAtMs = 1_000, note = "Row C", floorLabel = "P2", meterExpiresAtMs = expires),
        )

        runWorker(id, expires)

        val text = notifications.allNotifications.single().text()
        assertTrue(text, text.endsWith("P2 · Row C"))
    }

    @Test
    fun expiredMeterUsesExpiredText() {
        val expires = System.currentTimeMillis() - 1_000
        runWorker(spotId = activeSpot(expires), expiresAt = expires)
        assertEquals(
            "Your parking meter has expired. Tap to walk back.",
            notifications.allNotifications.single().text(),
        )
    }

    @Test
    fun skipsWhenSpotNoLongerActive() = runBlocking {
        val expires = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)
        val repo = app.container.parkingRepository
        val id = repo.saveNewSpot(spot(savedAtMs = 1_000, meterExpiresAtMs = expires))
        repo.clearActive()

        assertEquals(ListenableWorker.Result.success(), runWorker(id, expires))
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test
    fun skipsWhenMeterChangedSinceScheduling() = runBlocking {
        val expires = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)
        val id = app.container.parkingRepository.saveNewSpot(
            spot(savedAtMs = 1_000, meterExpiresAtMs = expires + 60_000),
        )

        runWorker(id, expires)
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test
    fun skipsWithoutNotificationPermission() {
        shadowOf(app as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runWorker(spotId = 3, expiresAt = System.currentTimeMillis() + 60_000)
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test
    fun invalidInputSucceedsWithoutPosting() {
        assertEquals(ListenableWorker.Result.success(), runWorker(spotId = -1, expiresAt = -1))
        assertTrue(notifications.allNotifications.isEmpty())
    }
}
