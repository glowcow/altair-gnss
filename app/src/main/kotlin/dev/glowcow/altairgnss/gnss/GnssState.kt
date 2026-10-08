package dev.glowcow.altairgnss.gnss

import android.location.Location

/** A position report of the receiver; a field the receiver did not supply is null. */
data class Fix(
    val latitude: Double,
    val longitude: Double,
    /** Height above the WGS 84 ellipsoid, metres. */
    val altitude: Double? = null,
    /** Height above mean sea level, metres. */
    val mslAltitude: Double? = null,
    val horizontalAccuracy: Float? = null,
    val verticalAccuracy: Float? = null,
    /** Metres per second. */
    val speed: Float? = null,
    val speedAccuracy: Float? = null,
    val bearing: Float? = null,
    val bearingAccuracy: Float? = null,
    /** UTC time of the fix, milliseconds since the epoch. */
    val timeMs: Long = 0,
    /** When the fix was made on the clock that counts from boot, milliseconds. */
    val elapsedRealtimeMs: Long = 0,
)

data class GnssState(
    /** The system location switch and the GPS provider are on. */
    val enabled: Boolean = true,
    val signals: List<Signal> = emptyList(),
    val fix: Fix? = null,
    val dop: Dop? = null,
    val ttffMs: Int? = null,
    /** The receiver had signals, dropped them all and is searching again. */
    val restarted: Boolean = false,
    val disturbance: Disturbance = Disturbance.NONE,
) {
    val usedCount: Int get() = signals.count { it.usedInFix }

    /** The last fix is live only while the receiver still uses satellites for it. */
    val hasFix: Boolean get() = fix != null && usedCount > 0
}

internal fun Location.toFix() = Fix(
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
