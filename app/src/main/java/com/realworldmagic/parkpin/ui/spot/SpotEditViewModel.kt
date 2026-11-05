package com.realworldmagic.parkpin.ui.spot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.realworldmagic.parkpin.data.ParkingRepository
import com.realworldmagic.parkpin.data.SettingsRepository
import com.realworldmagic.parkpin.domain.ParkingSpot
import com.realworldmagic.parkpin.quicksave.WidgetUpdater
import com.realworldmagic.parkpin.reminders.MeterReminderScheduler
import com.realworldmagic.parkpin.ui.navigation.Routes
import com.realworldmagic.parkpin.ui.util.parkPinContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpotEditUiState(
    val loaded: Boolean = false,
    val original: ParkingSpot? = null,
    val note: String = "",
    val floorLabel: String = "",
    val photoUri: String? = null,
    val meterExpiresAtMs: Long? = null,
    val leadMinutes: Int = 10,
    val saved: Boolean = false,
) {
    /** Current edits applied to the loaded spot. */
    fun toSpot(): ParkingSpot? = original?.copy(
        note = note.trim(),
        floorLabel = floorLabel.trim().ifBlank { null },
        photoUri = photoUri,
        meterExpiresAtMs = meterExpiresAtMs,
    )
}

class SpotEditViewModel(
    private val repository: ParkingRepository,
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: MeterReminderScheduler,
    private val spotId: Long,
    /** Called after a successful save so the widget / QS tile reflect the edit. */
    private val onSpotsChanged: () -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow(SpotEditUiState())
    val state: StateFlow<SpotEditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val spot = if (spotId == Routes.ACTIVE_SPOT || spotId <= 0) repository.observeActiveSpot().first()
            else repository.observeSpot(spotId).first()
            val lead = settingsRepository.settings.first().meterReminderLeadMinutes
            _state.value = SpotEditUiState(
                loaded = true,
                original = spot,
                note = spot?.note.orEmpty(),
                floorLabel = spot?.floorLabel.orEmpty(),
                photoUri = spot?.photoUri,
                meterExpiresAtMs = spot?.meterExpiresAtMs,
                leadMinutes = lead,
            )
        }
    }

    fun setNote(v: String) = _state.update { it.copy(note = v) }
    fun setFloor(v: String) = _state.update { it.copy(floorLabel = v) }
    fun setPhoto(uri: String?) = _state.update { it.copy(photoUri = uri) }

    fun setMeterMinutes(minutes: Int) =
        _state.update { it.copy(meterExpiresAtMs = System.currentTimeMillis() + minutes * 60_000L) }

    fun clearMeter() = _state.update { it.copy(meterExpiresAtMs = null) }

    fun save() {
        val s = _state.value
        val spot = s.toSpot() ?: return
        viewModelScope.launch {
            repository.update(spot)
            val old = s.original?.meterExpiresAtMs
            val new = spot.meterExpiresAtMs
            // Always (re)schedule while a meter is set: schedule() replaces the previous reminder,
            // so a changed lead time takes effect even when the expiry itself did not change.
            if (new != null && new > System.currentTimeMillis()) {
                reminderScheduler.schedule(spot.id, new, s.leadMinutes)
            } else if (old != null || new != null) {
                reminderScheduler.cancel(spot.id)
            }
            onSpotsChanged()
            _state.update { it.copy(original = spot, saved = true) }
        }
    }

    companion object {
        fun factory(spotId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = parkPinContainer()
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                SpotEditViewModel(c.parkingRepository, c.settingsRepository, c.meterReminderScheduler, spotId) {
                    WidgetUpdater.refreshAll(app)
                }
            }
        }
    }
}
