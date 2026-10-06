package dev.glowcow.altairgnss.recording

import dev.glowcow.altairgnss.gnss.GeoPoint
import dev.glowcow.altairgnss.gnss.Scatter
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

/** Figures of a profile; altitudes are metres above sea level. */
data class ProfileStats(
    val durationMs: Long,
    val start: Double,
    val end: Double,
    val min: Double,
    val max: Double,
    val gain: Double,
    val loss: Double,
    /** Metres along the ground and speeds in m/s, from the points the receiver placed; null without any. */
    val distance: Double? = null,
    val maxSpeed: Float? = null,
    /** The average over the time spent moving, so stops do not pull it down. */
    val averageSpeed: Float? = null,
    /** Time spent moving, out of [durationMs]; null where no speed was known. */
    val movingMs: Long? = null,
)

/** Adds up climbs and descents; a move smaller than [STEP] is the sensor's wobble and does not count. */
class Climb {
    var gain = 0.0
        private set
    var loss = 0.0
        private set
    private var anchor: Double? = null

    fun add(altitude: Double) {
        val from = anchor
        if (from == null) {
            anchor = altitude
            return
        }
        val moved = altitude - from
        if (abs(moved) < STEP) return
        if (moved > 0) gain += moved else loss -= moved
        anchor = altitude
    }

    private companion object {
        const val STEP = 1.0
    }
}

/** Adds up the way along the ground; a step shorter than [STEP] is the receiver's wander and does not count. */
class PathLength {
    var metres = 0.0
        private set
    private var anchor: GeoPoint? = null

    fun add(latitude: Double, longitude: Double) {
        val here = GeoPoint(latitude, longitude)
        val from = anchor
        if (from == null) {
            anchor = here
            return
        }
        val step = Scatter.offset(here, from).distance
        if (step < STEP) return
        metres += step
        anchor = here
    }

    private companion object {
        const val STEP = 3.0
    }
}

/** A stop on the way: where the track stood still, and for how long. */
class PathStop(val at: PathPoint, val durationMs: Long)

/** A track laid out on a plane round [centre], the middle of the smallest circle that holds it. */
class TrackPath(val points: List<PathPoint>, val centre: GeoPoint, val stops: List<PathStop> = emptyList()) {
    /** Metres from the centre to the farthest point. */
    val radius: Double get() = points.maxOf { hypot(it.east, it.north) }
}

/**
 * One point of a track on a plane round its middle: metres east and north, metres above its lowest
 * point; with what it was like there — the altitude, the speed, the time and the way since the start.
 */
data class PathPoint(
    val east: Double,
    val north: Double,
    val up: Double,
    val speed: Float?,
    val altitude: Double,
    val elapsedMs: Long,
    val distance: Double,
)

object Profile {
    /**
     * The points the receiver placed, laid out round the centre of the smallest circle that holds
     * the track, so the track sits evenly on a round ground; [altitudes] go with [points].
     */
    fun path(points: List<Point>, altitudes: List<Double>, moving: Float = MOVING): TrackPath? {
        val placed = points.indices.filter { points[it].latitude != null && points[it].longitude != null }
        if (placed.size < 2) return null
        val origin = GeoPoint(points[placed.first()].latitude!!, points[placed.first()].longitude!!)
        val offsets = placed.map { Scatter.offset(GeoPoint(points[it].latitude!!, points[it].longitude!!), origin) }
        val (centreEast, centreNorth) = enclosingCentre(offsets.map { it.east to it.north })
        val lowest = placed.minOf { altitudes[it] }
        val way = PathLength()
        val laid = placed.mapIndexed { n, i ->
            val point = points[i]
            way.add(point.latitude!!, point.longitude!!)
            PathPoint(
                offsets[n].east - centreEast,
                offsets[n].north - centreNorth,
                altitudes[i] - lowest,
                point.speed,
                altitudes[i],
                point.timeMs - points.first().timeMs,
                way.metres,
            )
        }
        return TrackPath(laid, Scatter.shift(origin, centreEast, centreNorth), stops(laid, moving))
    }

    /** The stretches of [path] slower than [moving] that lasted at least [STOP_MS], each marked at its first point. */
    internal fun stops(path: List<PathPoint>, moving: Float = MOVING): List<PathStop> {
        val found = mutableListOf<PathStop>()
        var from: PathPoint? = null
        var last: PathPoint? = null
        fun close() {
            val began = from ?: return
            val ended = last ?: return
            if (ended.elapsedMs - began.elapsedMs >= STOP_MS) found += PathStop(began, ended.elapsedMs - began.elapsedMs)
            from = null
        }
        for (point in path) {
            val still = point.speed != null && point.speed < moving
            if (still) {
                if (from == null) from = point
                last = point
            } else {
                close()
            }
        }
        close()
        return found
    }

    /**
     * Centre of a circle close to the smallest one round [points]: Ritter's circle through the two
     * farthest-apart points found in two sweeps, grown to hold the rest, then nudged towards its
     * farthest point for as long as that makes it smaller.
     */
    internal fun enclosingCentre(points: List<Pair<Double, Double>>): Pair<Double, Double> {
        fun far(x: Double, y: Double) = points.maxBy { hypot(it.first - x, it.second - y) }
        fun reach(x: Double, y: Double) = points.maxOf { hypot(it.first - x, it.second - y) }
        val a = far(points.first().first, points.first().second)
        val b = far(a.first, a.second)
        var x = (a.first + b.first) / 2
        var y = (a.second + b.second) / 2
        var radius = hypot(a.first - b.first, a.second - b.second) / 2
        for ((px, py) in points) {
            val distance = hypot(px - x, py - y)
            if (distance > radius) {
                radius = (radius + distance) / 2
                x += (px - x) * (distance - radius) / distance
                y += (py - y) * (distance - radius) / distance
            }
        }
        var step = 0.1
        repeat(SHRINK_STEPS) {
            val (fx, fy) = far(x, y)
            val tx = x + (fx - x) * step
            val ty = y + (fy - y) * step
            if (reach(tx, ty) < reach(x, y)) {
                x = tx
                y = ty
            } else {
                step /= 2
            }
        }
        return x to y
    }

    /** Figures of a recording; a point slower than [moving] m/s is standing. */
    fun stats(points: List<Point>, altitudes: List<Double>, moving: Float = MOVING): ProfileStats? {
        if (points.isEmpty()) return null
        val climb = Climb().also { altitudes.forEach(it::add) }
        val way = PathLength()
        for (point in points) if (point.latitude != null && point.longitude != null) way.add(point.latitude, point.longitude)
        val speeds = points.mapNotNull { it.speed }
        val inMotion = speeds.filter { it >= moving }
        // The time of every step that ended at a moving point; a gap in the recording is not a step.
        var movingMs = 0L
        for (i in 1 until points.size) {
            val step = points[i].timeMs - points[i - 1].timeMs
            if ((points[i].speed ?: 0f) >= moving && step in 1..MAX_STEP_MS) movingMs += step
        }
        return ProfileStats(
            durationMs = points.last().timeMs - points.first().timeMs,
            start = altitudes.first(),
            end = altitudes.last(),
            min = altitudes.min(),
            max = altitudes.max(),
            gain = climb.gain,
            loss = climb.loss,
            distance = way.metres.takeIf { points.any { it.latitude != null } },
            maxSpeed = speeds.maxOrNull(),
            averageSpeed = inMotion.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            movingMs = movingMs.takeIf { speeds.isNotEmpty() },
        )
    }

    /** The recording as a table, a row a point. */
    fun csv(points: List<Point>, altitudes: List<Double>, zero: Double): String = buildString {
        append("time,elapsed_s,altitude_m,from_zero_m,pressure_hpa,latitude,longitude,speed_mps\n")
        val start = points.firstOrNull()?.timeMs ?: return@buildString
        points.forEachIndexed { i, point ->
            append(Instant.ofEpochMilli(point.timeMs)).append(',')
            append((point.timeMs - start) / 1000).append(',')
            append(String.format(Locale.ROOT, "%.2f,%.2f,", altitudes[i], altitudes[i] - zero))
            append(point.hpa?.let { String.format(Locale.ROOT, "%.3f", it) }.orEmpty()).append(',')
            append(point.latitude?.let { String.format(Locale.ROOT, "%.6f", it) }.orEmpty()).append(',')
            append(point.longitude?.let { String.format(Locale.ROOT, "%.6f", it) }.orEmpty()).append(',')
            append(point.speed?.let { String.format(Locale.ROOT, "%.2f", it) }.orEmpty())
            append('\n')
        }
    }

    // Slower than this, 3 km/h, the phone is standing unless the settings say otherwise: the receiver wanders that much on a table.
    const val MOVING = 3f / 3.6f

    // Standing this long is a stop worth marking on the track.
    private const val STOP_MS = 30_000L
    private const val MAX_STEP_MS = 10_000L

    private const val SHRINK_STEPS = 60
}
