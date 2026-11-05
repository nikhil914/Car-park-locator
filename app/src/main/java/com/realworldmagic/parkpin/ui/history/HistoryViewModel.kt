package com.realworldmagic.parkpin.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.realworldmagic.parkpin.data.AppSettings
import com.realworldmagic.parkpin.data.ParkingRepository
import com.realworldmagic.parkpin.data.SettingsRepository
import com.realworldmagic.parkpin.domain.LocationFix
import com.realworldmagic.parkpin.domain.ParkingSpot
import com.realworldmagic.parkpin.location.LocationProvider
import com.realworldmagic.parkpin.reminders.MeterReminderScheduler
import com.realworldmagic.parkpin.ui.util.parkPinContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val spots: List<ParkingSpot> = emptyList(),
    val fix: LocationFix? = null,
    val settings: AppSettings = AppSettings(),
    val loaded: Boolean = false,
)

class HistoryViewModel(
    private val repository: ParkingRepository,
    settingsRepository: SettingsRepository,
    locationProvider: LocationProvider,
    private val reminderScheduler: MeterReminderScheduler,
    locationAllowed: Boolean,
) : ViewModel() {

    private val fixFlow = (if (locationAllowed) locationProvider.locationUpdates(5000L) else emptyFlow<LocationFix>())
        .map<LocationFix, LocationFix?> { it }
        .onStart { emit(null) }
        .catch { emit(null) }

    val state: StateFlow<HistoryUiState> =
        combine(repository.observeHistory(), fixFlow, settingsRepository.settings) { spots, fix, settings ->
            HistoryUiState(spots.sortedByDescending { it.savedAtMs }, fix, settings, loaded = true)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), HistoryUiState())

    fun delete(spot: ParkingSpot) {
        viewModelScope.launch {
            if (spot.meterExpiresAtMs != null) reminderScheduler.cancel(spot.id)
            repository.delete(spot.id)
        }
    }

    companion object {
        fun factory(locationAllowed: Boolean): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = parkPinContainer()
                HistoryViewModel(c.parkingRepository, c.settingsRepository, c.locationProvider, c.meterReminderScheduler, locationAllowed)
            }
        }
    }
}
