package dev.glowcow.altairgnss.gnss

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import dev.glowcow.altairgnss.instruments.Trip
import dev.glowcow.altairgnss.instruments.TripStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.stateIn

/**
 * The receiver as one state. It runs only while [state] is collected, and collecting needs the
 * precise location permission.
 */
class GnssMonitor(private val context: Context, scope: CoroutineScope) {
    private val manager = context.getSystemService(LocationManager::class.java)
    /** The receiver's NMEA sentences, as they come while it runs. */
    val nmea = NmeaLog(java.io.File(context.cacheDir, "nmea"))
    private val tripCounter = Trip()
    private val tripState = MutableStateFlow(TripStats())

    /** Distance, time and speeds added up from every fix since [resetTrip]; it grows only while the receiver runs. */
    val trip: StateFlow<TripStats> = tripState

    fun resetTrip() {
        tripState.value = tripCounter.reset()
    }

    /**
     * Latitude and longitude of the best position at hand: the live fix, else the freshest one any
     * provider remembers. Enough to find what is nearby when the receiver has no fix.
     */
    @SuppressLint("MissingPermission")
    fun lastKnownPosition(): Pair<Double, Double>? {
        state.value.fix?.let { return it.latitude to it.longitude }
        return try {
            manager.allProviders.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }?.let { it.latitude to it.longitude }
        } catch (_: SecurityException) {
            null
        }
    }

    /**
     * Asks the receiver to refresh its assistance data (time and predicted orbits), or with [clear]
     * to forget all of it. False if the phone refuses any of it.
     */
    fun assist(clear: Boolean): Boolean = try {
        val commands = if (clear) listOf("delete_aiding_data") else listOf("force_time_injection", "force_psds_injection")
        commands.map { manager.sendExtraCommand(LocationManager.GPS_PROVIDER, it, null) }.all { it }
    } catch (_: SecurityException) {
        false
    }

    @SuppressLint("MissingPermission")
    val state: StateFlow<GnssState> = callbackFlow {
        var current = GnssState(enabled = manager.isProviderEnabled(LocationManager.GPS_PROVIDER))
        fun update(change: (GnssState) -> GnssState) {
            current = change(current)
            trySend(current)
        }

        val status = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) = update { it.copy(signals = status.toSignals()) }
            override fun onFirstFix(ttffMillis: Int) = update { it.copy(ttffMs = ttffMillis) }
            override fun onStopped() = update { it.copy(signals = emptyList()) }
        }
        val location = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                tripState.value = tripCounter.update(if (location.hasSpeed()) location.speed else null, location.elapsedRealtimeNanos / 1_000_000)
                update { it.copy(fix = location.toFix()) }
            }
            override fun onProviderEnabled(provider: String) = update { it.copy(enabled = true) }
            override fun onProviderDisabled(provider: String) = update { GnssState(enabled = false) }
        }
        val sentences = OnNmeaMessageListener { message, _ ->
            nmea.add(message)
            Nmea.dop(message)?.let { dop -> update { it.copy(dop = dop) } }
        }

        // Every callback arrives on the main thread, so the state needs no lock.
        val executor = context.mainExecutor
        try {
            manager.registerGnssStatusCallback(executor, status)
            manager.addNmeaListener(executor, sentences)
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL_MS, 0f, executor, location)
        } catch (_: SecurityException) {
            close()
        }
        trySend(current)
        awaitClose {
            manager.unregisterGnssStatusCallback(status)
            manager.removeNmeaListener(sentences)
            manager.removeUpdates(location)
        }
    }.conflate().stateIn(scope, SharingStarted.WhileSubscribed(STOP_DELAY_MS), GnssState())

    private fun GnssStatus.toSignals() = List(satelliteCount) { i ->
        Signal(
            svid = getSvid(i),
            constellation = when (getConstellationType(i)) {
                GnssStatus.CONSTELLATION_GPS -> Constellation.GPS
                GnssStatus.CONSTELLATION_GLONASS -> Constellation.GLONASS
                GnssStatus.CONSTELLATION_GALILEO -> Constellation.GALILEO
                GnssStatus.CONSTELLATION_BEIDOU -> Constellation.BEIDOU
                GnssStatus.CONSTELLATION_QZSS -> Constellation.QZSS
                GnssStatus.CONSTELLATION_IRNSS -> Constellation.IRNSS
                GnssStatus.CONSTELLATION_SBAS -> Constellation.SBAS
                else -> Constellation.UNKNOWN
            },
            cn0DbHz = getCn0DbHz(i),
            elevation = getElevationDegrees(i),
            azimuth = getAzimuthDegrees(i),
            hasAlmanac = hasAlmanacData(i),
            hasEphemeris = hasEphemerisData(i),
            usedInFix = usedInFix(i),
            carrierHz = if (hasCarrierFrequencyHz(i)) getCarrierFrequencyHz(i) else null,
        )
    }

    private fun Location.toFix() = Fix(
        latitude = latitude,
        longitude = longitude,
        altitude = if (hasAltitude()) altitude else null,
        mslAltitude = if (hasMslAltitude()) mslAltitudeMeters else null,
        horizontalAccuracy = if (hasAccuracy()) accuracy else null,
        verticalAccuracy = if (hasVerticalAccuracy()) verticalAccuracyMeters else null,
        speed = if (hasSpeed()) speed else null,
        speedAccuracy = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond else null,
        bearing = if (hasBearing()) bearing else null,
        bearingAccuracy = if (hasBearingAccuracy()) bearingAccuracyDegrees else null,
        timeMs = time,
        elapsedRealtimeMs = elapsedRealtimeNanos / 1_000_000,
    )

    private companion object {
        const val INTERVAL_MS = 1_000L
        // Survives a rotation or a switch between tabs without restarting the receiver.
        const val STOP_DELAY_MS = 2_000L
    }
}
