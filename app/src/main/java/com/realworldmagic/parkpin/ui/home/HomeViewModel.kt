package com.realworldmagic.parkpin.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.realworldmagic.parkpin.data.AppSettings
import com.realworldmagic.parkpin.data.ParkingRepository
import com.realworldmagic.parkpin.data.SettingsRepository
import com.realworldmagic.parkpin.domain.ParkingSpot
import com.realworldmagic.parkpin.location.LocationProvider
import com.realworldmagic.parkpin.location.captureBestFix
import com.realworldmagic.parkpin.reminders.MeterReminderScheduler
import com.realworldmagic.parkpin.ui.util.parkPinContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CaptureUiState(
    val capturing: Boolean = false,
    val liveAccuracyM: Float? = null,
    val lastSaveApproximate: Boolean = false,
    val message: String? = null,
    /** Incremented on each successful save so the UI can fire a one-shot haptic. */
    val savedEvent: Int = 0,
)

class HomeViewModel(
    private val repository: ParkingRepository,
    settingsRepository: SettingsRepository,
    private val locationProvider: LocationProvider,
    private val reminderScheduler: MeterReminderScheduler,
) : ViewModel() {

    val activeSpot: StateFlow<ParkingSpot?> = repository.observeActiveSpot()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _capture = MutableStateFlow(CaptureUiState())
    val capture: StateFlow<CaptureUiState> = _capture.asStateFlow()

    private var captureJob: Job? = null

    fun saveSpotHere() {
        if (_capture.value.capturing) return
        captureJob = viewModelScope.launch {
            _capture.update { it.copy(capturing = true, liveAccuracyM = null, message = null) }
            // Live accuracy readout while the best-fix capture runs.
            val liveJob = launch {
                locationProvider.locationUpdates(1000L)
                    .catch { }
                    .collect { fix -> _capture.update { s -> s.copy(liveAccuracyM = fix.accuracyM) } }
            }
            try {
                val result = captureBestFix(locationProvider)
                val fix = result.fix
                if (fix == null) {
                    _capture.update { it.copy(message = "Couldn't get a location fix. Try again outdoors.") }
                } else {
                    repository.saveNewSpot(
                        ParkingSpot(
                            point = fix.point,
                            accuracyM = fix.accuracyM,
                            altitudeM = fix.altitudeM,
                            savedAtMs = System.currentTimeMillis(),
                        )
                    )
                    _capture.update {
                        it.copy(
                            lastSaveApproximate = result.approximate,
                            liveAccuracyM = fix.accuracyM,
                            savedEvent = it.savedEvent + 1,
                            message = if (result.approximate) "Saved approximate location (weak GPS)." else "Parking spot saved!",
                        )
                    }
                }
            } catch (e: SecurityException) {
                _capture.update { it.copy(message = "Location permission is required.") }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _capture.update { it.copy(message = "Saving failed: ${e.message ?: "unknown error"}") }
            } finally {
                liveJob.cancel()
                _capture.update { it.copy(capturing = false) }
            }
        }
    }

    fun cancelCapture() {
        captureJob?.cancel()
    }

    fun foundMyCar() {
        val spot = activeSpot.value
        viewModelScope.launch {
            spot?.let { if (it.meterExpiresAtMs != null) reminderScheduler.cancel(it.id) }
            repository.clearActive()
            _capture.update { it.copy(message = "Glad you found it!") }
        }
    }

    fun messageShown() = _capture.update { it.copy(message = null) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = parkPinContainer()
                HomeViewModel(c.parkingRepository, c.settingsRepository, c.locationProvider, c.meterReminderScheduler)
            }
        }
    }
}
