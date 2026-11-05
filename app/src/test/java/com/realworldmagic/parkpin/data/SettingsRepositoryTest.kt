package com.realworldmagic.parkpin.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * SettingsRepository uses a process-wide `preferencesDataStore("settings")` singleton, so its
 * state can outlive a single Robolectric test. Everything runs in one ordered test so the result
 * does not depend on test ordering.
 */
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {
    private val originalLocale: Locale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun defaultsWritesAndReads() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Defaults (nothing written yet): metric depends on locale.
        Locale.setDefault(Locale.UK)
        val uk = SettingsRepository(context)
        assertEquals(
            AppSettings(useMetric = true, meterReminderLeadMinutes = 10, arEnabled = true, keepScreenOn = true),
            uk.settings.first(),
        )

        Locale.setDefault(Locale.US)
        assertFalse(SettingsRepository(context).settings.first().useMetric)

        // Writes and reads.
        uk.setUseMetric(false)
        uk.setMeterReminderLeadMinutes(25)
        uk.setArEnabled(false)
        uk.setKeepScreenOn(false)
        assertEquals(
            AppSettings(useMetric = false, meterReminderLeadMinutes = 25, arEnabled = false, keepScreenOn = false),
            uk.settings.first(),
        )

        // An explicit value overrides the locale default.
        uk.setUseMetric(true)
        Locale.setDefault(Locale.US)
        assertTrue(SettingsRepository(context).settings.first().useMetric)

        // Lead minutes are clamped to 0..120.
        uk.setMeterReminderLeadMinutes(500)
        assertEquals(120, uk.settings.first().meterReminderLeadMinutes)
        uk.setMeterReminderLeadMinutes(-5)
        assertEquals(0, uk.settings.first().meterReminderLeadMinutes)

        // Restore defaults for any later test in this JVM that reads settings.
        uk.setMeterReminderLeadMinutes(SettingsRepository.DEFAULT_LEAD_MINUTES)
        uk.setArEnabled(true)
        uk.setKeepScreenOn(true)
    }
}
