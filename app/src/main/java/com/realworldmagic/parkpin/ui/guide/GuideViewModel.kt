package com.realworldmagic.parkpin.ui.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.realworldmagic.parkpin.data.AppSettings
import com.realworldmagic.parkpin.data.ParkingRepository
import com.realworldmagic.parkpin.data.SettingsRepository
import com.realworldmagic.parkpin.domain.ArrivalDetector
import com.realworldmagic.parkpin.domain.GeoMath
import com.realworldmagic.parkpin.domain.GuidanceCalculator
import com.realworldmagic.parkpin.domain.GuidanceState
import com.realworldmagic.parkpin.domain.Heading
import com.realworldmagic.parkpin.domain.LocationFix
import com.realworldmagic.parkpin.domain.ParkingSpot
import com.realworldmagic.parkpin.location.LocationProvider
import com.realworldmagic.parkpin.reminders.MeterReminderScheduler
import com.realworldmagic.parkpin.sensors.CompassSensor
import com.realworldmagic.parkpin.ui.navigation.Routes
import com.realworldmagic.parkpin.ui.util.parkPinContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Guidance + derived presentation values for the guide and AR screens. */
data class GuideUiState(
    val guidance: GuidanceState = GuidanceState(),
    val settings: AppSettings = AppSettings(),
    val loaded: Boolean = false,
    val etaMinutes: Int? = null,
    val cardinal: String? = null,
    val clock: Int? = null,
    val compassAvailable: Boolean = true,
)

/**
 * Shared guidance pipeline: spot + live fixes + heading -> [GuidanceState].
 * Sensor/location flows are only collected while the UI subscribes (WhileSubscribed), so
 * collecting with collectAsStateWithLifecycle stops GPS + compass when the screen is not visible.
 */
@OptIn(ExperimentalCoroutinesApi::class)
open class GuideViewModel(
    private val repository: ParkingRepository,
    settingsRepository: SettingsRepository,
    private val locationProvider: LocationProvider,
    private val compass: CompassSensor,
    private val reminderScheduler: MeterReminderScheduler,
    spotId: Long,
    cameraMode: Boolean,
) : ViewModel() {

    private val arrivalDetector = ArrivalDetector()

    private val spotFlow: Flow<ParkingSpot?> =
        if (spotId == Routes.ACTIVE_SPOT || spotId <= 0) repository.observeActiveSpot()
        else repository.observeSpot(spotId)

    private val fixFlow: Flow<LocationFix?> = locationProvider.locationUpdates(1000L)
        .onEach { compass.setLastKnownLocation(it.point, it.altitudeM, it.timeMs) }
        .map<LocationFix, LocationFix?> { it }
        .onStart { emit(null) }
        .catch { emit(null) }

    private val headingFlow: Flow<Heading?> =
        (if (compass.isAvailable) compass.headingUpdates(cameraMode) else emptyFlow<Heading>())
            .map<Heading, Heading?> { it }
            .onStart { emit(null) }
            .catch { emit(null) }

    private data class SpotFix(val spot: ParkingSpot?, val fix: LocationFix?, val arrived: Boolean)

    private val spotFixFlow: Flow<SpotFix> = combine(spotFlow.distinctUntilChanged(), fixFlow) { spot, fix ->
        val arrived = if (spot != null && fix != null) {
            arrivalDetector.update(GeoMath.distanceMeters(fix.point, spot.point), fix.accuracyM)
        } else false
        SpotFix(spot, fix, arrived)
    }

    val state: StateFlow<GuideUiState> =
        combine(spotFixFlow, headingFlow, settingsRepository.settings) { sf, heading, settings ->
            val g = GuidanceCalculator.calculate(sf.spot, sf.fix, heading, sf.arrived)
            GuideUiState(
                guidance = g,
                settings = settings,
                loaded = true,
                etaMinutes = g.distanceM?.let { GeoMath.walkingEtaMinutes(it) },
                cardinal = g.bearingDeg?.let { GeoMath.cardinal(it) },
                clock = g.relativeDeg?.let { com.realworldmagic.parkpin.domain.ClockDirection.hour(it) },
                compassAvailable = compass.isAvailable,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), GuideUiState())

    fun foundMyCar(onDone: () -> Unit) {
        val spot = state.value.guidance.spot
        viewModelScope.launch {
            if (spot?.meterExpiresAtMs != null) reminderScheduler.cancel(spot.id)
            if (spot?.isActive == true) repository.clearActive()
            onDone()
        }
    }

    companion object {
        fun factory(spotId: Long, cameraMode: Boolean = false): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = parkPinContainer()
                GuideViewModel(
                    c.parkingRepository, c.settingsRepository, c.locationProvider, c.compassSensor,
                    c.meterReminderScheduler, spotId, cameraMode,
                )
            }
        }
    }
}
