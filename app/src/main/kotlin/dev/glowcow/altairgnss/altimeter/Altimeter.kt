package dev.glowcow.altairgnss.altimeter

import android.os.SystemClock
import dev.glowcow.altairgnss.airports.NearbyAirport
import dev.glowcow.altairgnss.data.AltimeterPrefs
import dev.glowcow.altairgnss.data.SettingsStore
import dev.glowcow.altairgnss.gnss.GnssMonitor
import dev.glowcow.altairgnss.gnss.GnssState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AltimeterState(
    val hasBarometer: Boolean = false,
    /** The source in effect: GNSS on a phone without a barometer, whatever is chosen. */
    val source: AltitudeSource = AltitudeSource.GNSS,
    val pressureHpa: Double? = null,
    val gnssAltitude: Double? = null,
    val gnssAccuracy: Float? = null,
    /** Altitude by the stored calibration alone. */
    val barometerAltitude: Double? = null,
    /** Altitude by [source], metres above sea level. */
    val altitude: Double? = null,
    val accuracy: Float? = null,
    /** Sea-level pressure behind [altitude]; with GNSS as the source, the one GNSS implies. */
    val referenceHpa: Double? = null,
    /** Metres per second, climbing is positive. */
    val verticalSpeed: Double? = null,
    val calibration: Calibration? = null,
    /** Progress of a running GNSS calibration, 0..1. */
    val calibrating: Float? = null,
    val calibrationFailed: Boolean = false,
)

/**
 * Altitude from the chosen source. In the barometer mode GNSS is not even started, except for the
 * seconds of a calibration.
 */
class Altimeter(
    private val scope: CoroutineScope,
    gnss: GnssMonitor,
    pressure: PressureMonitor,
    private val store: SettingsStore,
) {
    private class Run(val startedAt: Long, val samples: CalibrationRun = CalibrationRun())

    private val hasBarometer = pressure.available
    private val run = MutableStateFlow<Run?>(null)
    private val failed = MutableStateFlow(false)

    // Touched only from the main thread, where every flow below is collected.
    private var lastFixTime = 0L
    private var blended: Double? = null
    private val vertical = VerticalSpeed()
    private var verticalSpeed: Double? = null
    private var lastSampleNanos = 0L
    private var verticalReference = 0.0

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<AltimeterState> = combine(store.altimeter, run) { prefs, run -> prefs to run }
        .flatMapLatest { (prefs, run) ->
            val needsGnss = !hasBarometer || prefs.source != AltitudeSource.BAROMETER || run != null
            val fixes: Flow<GnssState?> = if (needsGnss) gnss.state else flowOf(null)
            combine(pressure.samples, fixes, failed) { sample, state, failed -> compute(prefs, run, sample, state, failed) }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_DELAY_MS), AltimeterState(hasBarometer = hasBarometer))

    private fun compute(prefs: AltimeterPrefs, run: Run?, sample: PressureSample?, gnss: GnssState?, failed: Boolean): AltimeterState {
        val hpa = sample?.hpa
        val fix = gnss?.fix?.takeIf { gnss.hasFix }
        val gnssAltitude = fix?.mslAltitude ?: fix?.altitude
        val gnssAccuracy = fix?.verticalAccuracy
        val newFix = fix != null && fix.timeMs != lastFixTime
        val usable = gnssAltitude != null && gnssAccuracy != null && gnssAccuracy <= MAX_ACCURACY
        val now = System.currentTimeMillis()

        var progress: Float? = null
        if (run != null) {
            if (newFix && usable && hpa != null) run.samples.add(gnssAltitude, gnssAccuracy, hpa)
            val elapsed = SystemClock.elapsedRealtime() - run.startedAt
            if (elapsed >= CALIBRATION_MS) finish(run, now) else progress = elapsed.toFloat() / CALIBRATION_MS
        }

        val source = if (hasBarometer) prefs.source else AltitudeSource.GNSS
        if (source == AltitudeSource.AUTO && newFix && usable && hpa != null) {
            val seconds = if (lastFixTime == 0L) 1.0 else ((fix.timeMs - lastFixTime) / 1000.0).coerceIn(0.0, MAX_STEP_SECONDS)
            blended = Blend.step(blended ?: prefs.calibration?.referenceHpa, Barometry.reference(hpa, gnssAltitude), seconds)
        }
        // The barometer follows height far better than GNSS does, so it gives the rate whenever it exists.
        if (sample != null) {
            val reference = prefs.calibration?.referenceHpa ?: Barometry.STANDARD_HPA
            if (reference != verticalReference) {
                // A new calibration moves the altitude at once; that jump is not a climb.
                vertical.reset()
                verticalReference = reference
            }
            if (sample.timeNanos != lastSampleNanos) {
                lastSampleNanos = sample.timeNanos
                verticalSpeed = vertical.update(Barometry.altitude(sample.hpa, reference), sample.timeNanos)
            }
        } else if (newFix && gnssAltitude != null) {
            verticalSpeed = vertical.update(gnssAltitude, fix.elapsedRealtimeMs * 1_000_000)
        }
        if (newFix) lastFixTime = fix.timeMs

        val barometerAltitude = prefs.calibration?.let { c -> hpa?.let { Barometry.altitude(it, c) } }
        val blendedAltitude = blended?.let { r -> hpa?.let { Barometry.altitude(it, r) } }
        return AltimeterState(
            hasBarometer = hasBarometer,
            source = source,
            pressureHpa = hpa,
            gnssAltitude = gnssAltitude,
            gnssAccuracy = gnssAccuracy,
            barometerAltitude = barometerAltitude,
            altitude = when (source) {
                AltitudeSource.GNSS -> gnssAltitude
                AltitudeSource.BAROMETER -> barometerAltitude
                AltitudeSource.AUTO -> blendedAltitude ?: gnssAltitude
            },
            accuracy = when (source) {
                AltitudeSource.BAROMETER -> prefs.calibration?.let { Drift.estimate(it.accuracy, now - it.timeMs) }
                else -> gnssAccuracy
            },
            referenceHpa = when (source) {
                AltitudeSource.GNSS -> if (hpa != null && gnssAltitude != null) Barometry.reference(hpa, gnssAltitude) else null
                AltitudeSource.BAROMETER -> prefs.calibration?.referenceHpa
                AltitudeSource.AUTO -> blended ?: prefs.calibration?.referenceHpa
            },
            verticalSpeed = verticalSpeed,
            calibration = prefs.calibration,
            calibrating = progress,
            calibrationFailed = failed,
        )
    }

    private fun finish(run: Run, now: Long) {
        val result = run.samples.result()
        if (result != null) save(Calibration(result.first, now, result.second, CalibrationKind.GNSS))
        failed.value = result == null
        this.run.value = null
    }

    // Calibrating is asking for the barometer: the source switches to it, away from GNSS.
    private fun save(calibration: Calibration) {
        scope.launch {
            store.setCalibration(calibration)
            store.setAltitudeSource(AltitudeSource.BAROMETER)
        }
    }

    fun setSource(source: AltitudeSource) {
        scope.launch { store.setAltitudeSource(source) }
    }

    /** Averages GNSS altitude against pressure for [CALIBRATION_MS]; the outcome arrives in [state]. */
    fun calibrateFromGnss() {
        failed.value = false
        run.value = Run(SystemClock.elapsedRealtime())
    }

    /** Calibrates to an altitude the user knows; false without a pressure reading. */
    fun calibrateToAltitude(altitude: Double): Boolean {
        val hpa = state.value.pressureHpa ?: return false
        failed.value = false
        save(Calibration(Barometry.reference(hpa, altitude), System.currentTimeMillis(), 0f, CalibrationKind.ALTITUDE))
        return true
    }

    /** Takes the sea-level pressure an airport reports as the reference, and its air temperature with it. */
    fun calibrateToAirport(airport: NearbyAirport) {
        failed.value = false
        val report = airport.report
        save(Calibration(report.qnhHpa, System.currentTimeMillis(), airport.accuracy, CalibrationKind.AIRPORT, report.temperatureC, report.elevation))
    }

    companion object {
        const val CALIBRATION_MS = 20_000L
        // A vertical error above this says more about the sky view than about the altitude.
        const val MAX_ACCURACY = 15f
        private const val MAX_STEP_SECONDS = 5.0
        private const val STOP_DELAY_MS = 2_000L
    }
}
