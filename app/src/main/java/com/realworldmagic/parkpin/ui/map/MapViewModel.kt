package com.realworldmagic.parkpin.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.realworldmagic.parkpin.data.AppSettings
import com.realworldmagic.parkpin.data.ParkingRepository
import com.realworldmagic.parkpin.data.SettingsRepository
import com.realworldmagic.parkpin.domain.GeoMath
import com.realworldmagic.parkpin.domain.LocationFix
import com.realworldmagic.parkpin.domain.ParkingSpot
import com.realworldmagic.parkpin.location.LocationProvider
import com.realworldmagic.parkpin.ui.navigation.Routes
import com.realworldmagic.parkpin.ui.util.parkPinContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

data class MapUiState(
    val spot: ParkingSpot? = null,
    val fix: LocationFix? = null,
    val settings: AppSettings = AppSettings(),
    val loaded: Boolean = false,
) {
    val distanceM: Double? get() = if (spot != null && fix != null) GeoMath.distanceMeters(fix.point, spot.point) else null
}

class MapViewModel(
    repository: ParkingRepository,
    settingsRepository: SettingsRepository,
    locationProvider: LocationProvider,
    spotId: Long,
    locationAllowed: Boolean,
) : ViewModel() {
    private val spotFlow = if (spotId == Routes.ACTIVE_SPOT || spotId <= 0) repository.observeActiveSpot()
    else repository.observeSpot(spotId)

    private val fixFlow = (if (locationAllowed) locationProvider.locationUpdates(2000L) else kotlinx.coroutines.flow.emptyFlow<LocationFix>())
        .map<LocationFix, LocationFix?> { it }
        .onStart { emit(null) }
        .catch { emit(null) }

    val state: StateFlow<MapUiState> = combine(spotFlow, fixFlow, settingsRepository.settings) { spot, fix, settings ->
        MapUiState(spot, fix, settings, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), MapUiState())

    companion object {
        fun factory(spotId: Long, locationAllowed: Boolean): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = parkPinContainer()
                MapViewModel(c.parkingRepository, c.settingsRepository, c.locationProvider, spotId, locationAllowed)
            }
        }
    }
}
