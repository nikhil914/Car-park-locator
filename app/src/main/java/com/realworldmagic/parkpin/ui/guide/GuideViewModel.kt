package com.realworldmagic.parkpin.ui.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.realworldmagic.parkpin.data.AppSettings
import com.realworldmagic.parkpin.data.ParkingRepository
import com.realworldmagic.parkpin.data.SettingsRepository
import com.realworldmagic.parkpin.domain.BarometricFloorEstimator
import com.realworldmagic.parkpin.domain.FloorHint
import com.realworldmagic.parkpin.domain.GeoMath
import com.realworldmagic.parkpin.domain.GuidanceCalculator
import com.realworldmagic.parkpin.domain.GuidanceState
import com.realworldmagic.parkpin.domain.GuidanceTracker
import com.realworldmagic.parkpin.domain.Heading
import com.realworldmagic.parkpin.domain.LocationFix
import com.realworldmagic.parkpin.domain.ParkingSpot
import com.realworldmagic.parkpin.domain.StepEvent
import com.realworldmagic.parkpin.location.LocationProvider
import com.realworldmagic.parkpin.quicksave.WidgetUpdater
import com.realworldmagic.parkpin.reminders.MeterReminderScheduler
import com.realworldmagic.parkpin.sensors.BarometerSensor
import com.realworldmagic.parkpin.sensors.CompassSensor
import com.realworldmagic.parkpin.sensors.StepSensor
import com.realworldmagic.parkpin.ui.navigation.Routes
import com.realworldmagic.parkpin.ui.util.parkPinContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
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
    /** Barometric floor estimate; null when unavailable or the spot is too old (weather drift). */
    val floorHint: FloorHint? = null,
    /** The position is a dead-reckoning estimate (GPS weak or lost); its ± radius is guidance.fix.accuracyM. */
    val positionEstimated: Boolean = false,
    /** The device has a step detector, so "Enable step tracking" can be offered. */
    val stepTrackingAvailable: Boolean = false,
    /** Steps are being counted for dead reckoning. */
    val stepTrackingEnabled: Boolean = false,
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
    private val barometer: BarometerSensor? = null,
    /** Refreshes home-screen widgets and the Quick Settings tile after the active spot changes. */
    private val onSpotsChanged: () -> Unit = {},
    /** Step detector for pedestrian dead reckoning when GPS is weak; null disables PDR. */
    private val stepSensor: StepSensor? = null,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    /**
     * Step tracking is opt-in. It starts on by itself only when the runtime permission
     * (ACTIVITY_RECOGNITION, API 29+) was already granted; otherwise the user taps
     * "Enable step tracking" in the guide.
     */
    private val stepTrackingOn = MutableStateFlow(
        stepSensor != null && StepSensor.requiredPermission != null && stepSensor.canDetectSteps,
    )

    private val spotFlow: Flow<ParkingSpot?> =
        if (spotId == Routes.ACTIVE_SPOT || spotId <= 0) repository.observeActiveSpot()
        else repository.observeSpot(spotId)

    /** Raw fixes; they reach guidance only through the [GuidanceTracker] (AccuracyFilter + PDR). */
    private val rawFixFlow: Flow<LocationFix> = locationProvider.locationUpdates(1000L)
        .onEach { compass.setLastKnownLocation(it.point, it.altitudeM, it.timeMs) }
        .catch { }

    /** Shared so the UI and the dead-reckoning pipeline use one sensor registration. */
    private val headingFlow: Flow<Heading?> =
        (if (compass.isAvailable) compass.headingUpdates(cameraMode) else emptyFlow<Heading>())
            .map<Heading, Heading?> { it }
            .onStart { emit(null) }
            .catch { emit(null) }
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), replay = 1)

    /** Step events, collected only while tracking is on and the guide is visible (battery). */
    private val stepFlow: Flow<StepEvent> = stepTrackingOn.flatMapLatest { on ->
        val sensor = stepSensor
        if (on && sensor != null && sensor.canDetectSteps) sensor.stepEvents().catch { } else emptyFlow()
    }

    private val tickFlow: Flow<Unit> = flow {
        while (true) {
            delay(TICK_MS)
            emit(Unit)
        }
    }

    /**
     * Barometer runs only when the spot has a saved pressure and is still young enough for the
     * estimate to mean anything; [BarometricFloorEstimator.floorHint] re-checks the age per reading.
     */
    private val floorHintFlow: Flow<FloorHint?> = spotFlow.distinctUntilChanged().flatMapLatest { spot ->
        val baro = barometer
        val spotPressure = spot?.pressureHpa
        val young = spot != null &&
            System.currentTimeMillis() - spot.savedAtMs < BarometricFloorEstimator.MAX_RELIABLE_AGE_MS
        if (spot == null || spotPressure == null || !young || baro == null || !baro.isAvailable) {
            flowOf(null)
        } else {
            baro.pressureUpdates()
                .map<Float, FloorHint?> { p ->
                    BarometricFloorEstimator.floorHint(spotPressure, p, spot.savedAtMs, System.currentTimeMillis())
                }
                .distinctUntilChanged()
                .onStart { emit(null) }
                .catch { emit(null) }
        }
    }

    private sealed interface TrackEvent {
        data class SpotChanged(val spot: ParkingSpot?) : TrackEvent
        data class Fix(val fix: LocationFix) : TrackEvent
        data class HeadingChanged(val heading: Heading?) : TrackEvent
        data class Step(val event: StepEvent) : TrackEvent
        data class StepTracking(val on: Boolean) : TrackEvent
        data object Tick : TrackEvent
    }

    /**
     * One coroutine drives the (not thread-safe) tracker. A fresh tracker per collection means
     * filter, dead reckoning and arrival state reset whenever guidance stops. Nothing is emitted
     * until the spot has loaded, so the screen never flashes "No parking spot".
     */
    private val trackFlow: Flow<GuidanceTracker.Snapshot> = flow {
        val tracker = GuidanceTracker()
        var spotLoaded = false
        merge(
            spotFlow.distinctUntilChanged().map { TrackEvent.SpotChanged(it) },
            rawFixFlow.map { TrackEvent.Fix(it) },
            headingFlow.map { TrackEvent.HeadingChanged(it) },
            stepTrackingOn.map { TrackEvent.StepTracking(it && stepSensor?.canDetectSteps == true) },
            stepFlow.map { TrackEvent.Step(it) },
            tickFlow.map { TrackEvent.Tick },
        ).collect { ev ->
            val now = nowMs()
            val snap = when (ev) {
                is TrackEvent.SpotChanged -> { spotLoaded = true; tracker.onSpot(ev.spot, now) }
                is TrackEvent.Fix -> tracker.onFix(ev.fix, now)
                is TrackEvent.HeadingChanged -> { tracker.onHeading(ev.heading?.azimuthDeg?.toDouble()); null }
                is TrackEvent.Step -> tracker.onStep(ev.event, now)
                is TrackEvent.StepTracking -> { tracker.stepTracking = ev.on; tracker.onTick(now) }
                TrackEvent.Tick -> tracker.onTick(now)
            }
            if (snap != null && spotLoaded) emit(snap)
        }
    }.distinctUntilChanged()

    val state: StateFlow<GuideUiState> =
        combine(trackFlow, headingFlow, settingsRepository.settings, floorHintFlow, stepTrackingOn) { track, heading, settings, floorHint, stepsOn ->
            val g = GuidanceCalculator.calculate(track.spot, track.position, heading, track.arrived)
                .let { if (track.mockLocation) it.copy(mockLocation = true) else it }
            GuideUiState(
                guidance = g,
                settings = settings,
                loaded = true,
                etaMinutes = g.distanceM?.let { GeoMath.walkingEtaMinutes(it) },
                cardinal = g.bearingDeg?.let { GeoMath.cardinal(it) },
                clock = g.relativeDeg?.let { com.realworldmagic.parkpin.domain.ClockDirection.hour(it) },
                compassAvailable = compass.isAvailable,
                floorHint = floorHint,
                positionEstimated = track.estimated,
                stepTrackingAvailable = stepSensor?.isAvailable == true,
                stepTrackingEnabled = stepsOn && stepSensor?.canDetectSteps == true,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), GuideUiState())

    /**
     * Called after the user tapped "Enable step tracking" and the runtime permission (if any)
     * was answered. Turns step tracking on only when steps can actually be read.
     */
    fun setStepTracking(enabled: Boolean) {
        stepTrackingOn.value = enabled && stepSensor?.canDetectSteps == true
    }

    fun foundMyCar(onDone: () -> Unit) {
        val spot = state.value.guidance.spot
        viewModelScope.launch {
            if (spot?.meterExpiresAtMs != null) reminderScheduler.cancel(spot.id)
            if (spot?.isActive == true) {
                repository.clearActive()
                onSpotsChanged()
            }
            onDone()
        }
    }

    companion object {
        /** Re-evaluates the fused position so it can fall back to dead reckoning when GPS goes quiet. */
        private const val TICK_MS = 2_000L

        fun factory(spotId: Long, cameraMode: Boolean = false): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = parkPinContainer()
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                GuideViewModel(
                    c.parkingRepository, c.settingsRepository, c.locationProvider, c.compassSensor,
                    c.meterReminderScheduler, spotId, cameraMode, c.barometerSensor,
                    onSpotsChanged = { WidgetUpdater.refreshAll(app) },
                    stepSensor = c.stepSensor,
                )
            }
        }
    }
}
