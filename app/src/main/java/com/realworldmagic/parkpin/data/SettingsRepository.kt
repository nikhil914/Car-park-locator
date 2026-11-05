package com.realworldmagic.parkpin.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.Locale

data class AppSettings(
    val useMetric: Boolean = true,
    val meterReminderLeadMinutes: Int = 10,
    val arEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore
    private val defaultMetric = defaultUseMetric()

    val settings: Flow<AppSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            AppSettings(
                useMetric = p[KEY_USE_METRIC] ?: defaultMetric,
                meterReminderLeadMinutes = p[KEY_LEAD_MINUTES] ?: DEFAULT_LEAD_MINUTES,
                arEnabled = p[KEY_AR_ENABLED] ?: true,
                keepScreenOn = p[KEY_KEEP_SCREEN_ON] ?: true,
            )
        }
        .distinctUntilChanged()

    suspend fun setUseMetric(value: Boolean) {
        dataStore.edit { it[KEY_USE_METRIC] = value }
    }

    suspend fun setMeterReminderLeadMinutes(value: Int) {
        dataStore.edit { it[KEY_LEAD_MINUTES] = value.coerceIn(0, 120) }
    }

    suspend fun setArEnabled(value: Boolean) {
        dataStore.edit { it[KEY_AR_ENABLED] = value }
    }

    suspend fun setKeepScreenOn(value: Boolean) {
        dataStore.edit { it[KEY_KEEP_SCREEN_ON] = value }
    }

    companion object {
        const val DEFAULT_LEAD_MINUTES = 10
        private val KEY_USE_METRIC = booleanPreferencesKey("use_metric")
        private val KEY_LEAD_MINUTES = intPreferencesKey("meter_reminder_lead_minutes")
        private val KEY_AR_ENABLED = booleanPreferencesKey("ar_enabled")
        private val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")

        /** Imperial default only for the US, Liberia and Myanmar. */
        private fun defaultUseMetric(): Boolean =
            Locale.getDefault().country.uppercase(Locale.ROOT) !in setOf("US", "LR", "MM")
    }
}
