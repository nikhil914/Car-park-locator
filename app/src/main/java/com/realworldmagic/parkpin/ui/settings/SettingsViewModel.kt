package com.realworldmagic.parkpin.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.realworldmagic.parkpin.data.AppSettings
import com.realworldmagic.parkpin.data.ParkingRepository
import com.realworldmagic.parkpin.data.SettingsRepository
import com.realworldmagic.parkpin.reminders.MeterReminderScheduler
import com.realworldmagic.parkpin.ui.util.parkPinContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val repository: ParkingRepository,
    private val reminderScheduler: MeterReminderScheduler,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setUseMetric(v: Boolean) {
        viewModelScope.launch { settingsRepository.setUseMetric(v) }
    }
    fun setLeadMinutes(v: Int) {
        viewModelScope.launch { settingsRepository.setMeterReminderLeadMinutes(v) }
    }
    fun setArEnabled(v: Boolean) {
        viewModelScope.launch { settingsRepository.setArEnabled(v) }
    }
    fun setKeepScreenOn(v: Boolean) {
        viewModelScope.launch { settingsRepository.setKeepScreenOn(v) }
    }

    /** Deletes every saved spot (active included) and cancels their reminders. */
    fun deleteAllHistory(onDone: (Int) -> Unit) {
        viewModelScope.launch {
        val count = repository.observeHistory().first().size
        reminderScheduler.cancelAll()
        repository.deleteAll()
        onDone(count)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = parkPinContainer()
                SettingsViewModel(c.settingsRepository, c.parkingRepository, c.meterReminderScheduler)
            }
        }
    }
}
