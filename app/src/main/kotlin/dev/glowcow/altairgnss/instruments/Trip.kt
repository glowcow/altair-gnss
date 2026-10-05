package dev.glowcow.altairgnss.instruments

import kotlin.math.max

/** What has been travelled since the last reset. */
data class TripStats(
    /** Metres covered while moving. */
    val distance: Double = 0.0,
    val movingMs: Long = 0,
    /** Metres per second. */
    val maxSpeed: Float = 0f,
    /** Metres per second squared, lightly smoothed; null until two fixes in a row carry a speed. */
    val acceleration: Float? = null,
) {
    /** Average speed over the time spent moving, metres per second. */
    val averageSpeed: Float? get() = if (movingMs > 0) (distance / (movingMs / 1000.0)).toFloat() else null
}

/** Adds up a trip from the speed of consecutive fixes. */
class Trip {
    private var lastSpeed: Float? = null
    private var lastMs = 0L
    private var stats = TripStats()

    fun update(speed: Float?, elapsedMs: Long): TripStats {
        val previous = lastSpeed
        val seconds = (elapsedMs - lastMs) / 1000.0
        var acceleration: Float? = null
        // Fixes too far apart say nothing about what happened between them.
        if (speed != null && previous != null && seconds > 0 && seconds <= MAX_GAP_SECONDS) {
            val raw = ((speed - previous) / seconds).toFloat()
            acceleration = stats.acceleration?.let { it + (raw - it) * ACCELERATION_GAIN } ?: raw
            // A standing receiver still reports a little speed; that is not travel.
            if (speed >= MOVING_SPEED) {
                stats = stats.copy(
                    distance = stats.distance + (speed + previous) / 2 * seconds,
                    movingMs = stats.movingMs + (seconds * 1000).toLong(),
                )
            }
        }
        stats = stats.copy(maxSpeed = max(stats.maxSpeed, speed ?: 0f), acceleration = acceleration)
        lastSpeed = speed
        lastMs = elapsedMs
        return stats
    }

    fun reset(): TripStats {
        lastSpeed = null
        stats = TripStats()
        return stats
    }

    private companion object {
        const val MAX_GAP_SECONDS = 5.0
        const val MOVING_SPEED = 0.5f
        const val ACCELERATION_GAIN = 0.5f
    }
}
