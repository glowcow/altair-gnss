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

/** The altitude as it comes in, with the sensor's jumps taken out: the median of the last [size] values. */
class RunningMedian(private val size: Int = 15) {
    private val last = ArrayDeque<Double>()

    fun add(value: Double): Double {
        last += value
        if (last.size > size) last.removeFirst()
        return last.sorted()[last.size / 2]
    }
}

/**
 * Adds up the way along the ground. A step shorter than [STEP], or than what either of its ends
 * may be off by, is as likely the wander of the position as a move, and does not count.
 */
class PathLength {
    var metres = 0.0
        private set
    private var anchor: GeoPoint? = null
    private var doubt = 0.0

    /** [accuracy] is metres the place may be off by, where its source said. */
    fun add(latitude: Double, longitude: Double, accuracy: Float? = null) {
        val here = GeoPoint(latitude, longitude)
        val unsure = (accuracy?.toDouble() ?: 0.0).coerceAtMost(MAX_DOUBT)
        val from = anchor
        if (from == null) {
            anchor = here
            doubt = unsure
            return
        }
        val step = Scatter.offset(here, from).distance
        if (step < maxOf(STEP, doubt, unsure)) return
        metres += step
        anchor = here
        doubt = unsure
    }

    private companion object {
        const val STEP = 3.0
        // A place that claims to be farther off than this still counts once it has moved this far.
        const val MAX_DOUBT = 30.0
    }
}

/**
 * A speed for places that came without one, as Wi-Fi and cell towers give them: the way from the
 * last place the phone has clearly left, over the time it took. Standing inside what the place may
 * be off by for as long as a move at [moving] m/s would have left it is a speed of zero.
 */
class WalkedSpeed(private val moving: Float) {
    private var anchor: GeoPoint? = null
    private var anchorAt = 0L
    private var last: Float? = null

    /** The speed at [timeMs] at a place that may be [accuracy] metres off; null until there is something to go by. */
    fun add(latitude: Double, longitude: Double, accuracy: Float, timeMs: Long): Float? {
        val here = GeoPoint(latitude, longitude)
        val from = anchor
        if (from == null) {
            anchor = here
            anchorAt = timeMs
            return last
        }
        val window = (accuracy / moving * 1000).toLong()
        val took = timeMs - anchorAt
        if (took <= 0) return last
        val moved = Scatter.offset(here, from).distance
        if (moved >= accuracy) {
            // A leap no one travels is the position jumping to another guess.
            (moved / took * 1000).toFloat().takeIf { it <= MAX_SPEED }?.let { last = it }
            anchor = here
            anchorAt = timeMs
        } else if (took >= window) {
            last = 0f
            // The wait behind a standing phone must not thin out the speed of its next move.
            anchorAt = timeMs - window
        }
        return last
    }

    fun reset() {
        anchor = null
        last = null
    }

    private companion object {
        const val MAX_SPEED = 70f
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
 * One point of a track on a plane round its middle: metres east and north, metres above the level
 * heights are counted from; with what it was like there — the altitude, the speed, the time and the way since the start.
 * [afterGap] says the recording had no place for a while before this point, so the way to it from
 * the point before is a straight guess.
 */
data class PathPoint(
    val east: Double,
    val north: Double,
    val up: Double,
    val speed: Float?,
    val altitude: Double,
    val elapsedMs: Long,
    val distance: Double,
    val afterGap: Boolean = false,
)

object Profile {
    /**
     * The points the receiver placed, laid out round the centre of the smallest circle that holds
     * the track, so the track sits evenly on a round ground; [altitudes] go with [points]. The
     * ground is at [base] metres above sea level, or at the lowest point of the track without one.
     */
    fun path(points: List<Point>, altitudes: List<Double>, moving: Float = MOVING, base: Double? = null): TrackPath? {
        val placed = points.indices.filter { points[it].latitude != null && points[it].longitude != null }
        if (placed.size < 2) return null
        val origin = GeoPoint(points[placed.first()].latitude!!, points[placed.first()].longitude!!)
        val offsets = placed.map { Scatter.offset(GeoPoint(points[it].latitude!!, points[it].longitude!!), origin) }
        val (centreEast, centreNorth) = enclosingCentre(offsets.map { it.east to it.north })
        val lowest = base ?: placed.minOf { altitudes[it] }
        val way = PathLength()
        val laid = placed.mapIndexed { n, i ->
            val point = points[i]
            way.add(point.latitude!!, point.longitude!!, point.accuracy)
            PathPoint(
                offsets[n].east - centreEast,
                offsets[n].north - centreNorth,
                altitudes[i] - lowest,
                point.speed,
                altitudes[i],
                point.timeMs - points.first().timeMs,
                way.metres,
                afterGap = n > 0 && point.timeMs - points[placed[n - 1]].timeMs > GAP_MS,
            )
        }
        return TrackPath(laid, Scatter.shift(origin, centreEast, centreNorth), stops(laid, moving))
    }

    /**
     * The altitudes of [points] with the sensor's jumps taken out: the median of the seconds round
     * each point drops a lone spike, and the mean of the same stretch evens out what is left.
     */
    fun smooth(points: List<Point>): List<Double> {
        val steady = around(points, points.map { it.altitude }) { it.sorted()[it.size / 2] }
        return around(points, steady) { it.average() }
    }

    /** [fold] of the [values] within [SMOOTH_MS] of each point. */
    private fun around(points: List<Point>, values: List<Double>, fold: (List<Double>) -> Double): List<Double> {
        var from = 0
        var to = 0
        return points.indices.map { i ->
            while (points[i].timeMs - points[from].timeMs > SMOOTH_MS) from++
            while (to < points.lastIndex && points[to + 1].timeMs - points[i].timeMs <= SMOOTH_MS) to++
            fold(values.subList(from, to + 1))
        }
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
        for (point in points) if (point.latitude != null && point.longitude != null) way.add(point.latitude, point.longitude, point.accuracy)
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
        append("time,elapsed_s,altitude_m,from_zero_m,pressure_hpa,latitude,longitude,speed_mps,accuracy_m\n")
        val start = points.firstOrNull()?.timeMs ?: return@buildString
        points.forEachIndexed { i, point ->
            append(Instant.ofEpochMilli(point.timeMs)).append(',')
            append((point.timeMs - start) / 1000).append(',')
            append(String.format(Locale.ROOT, "%.2f,%.2f,", altitudes[i], altitudes[i] - zero))
            append(point.hpa?.let { String.format(Locale.ROOT, "%.3f", it) }.orEmpty()).append(',')
            append(point.latitude?.let { String.format(Locale.ROOT, "%.6f", it) }.orEmpty()).append(',')
            append(point.longitude?.let { String.format(Locale.ROOT, "%.6f", it) }.orEmpty()).append(',')
            append(point.speed?.let { String.format(Locale.ROOT, "%.2f", it) }.orEmpty()).append(',')
            append(point.accuracy?.let { String.format(Locale.ROOT, "%.1f", it) }.orEmpty())
            append('\n')
        }
    }

    // Slower than this, 3 km/h, the phone is standing unless the settings say otherwise: the receiver wanders that much on a table.
    const val MOVING = 3f / 3.6f

    // Standing this long is a stop worth marking on the track.
    private const val STOP_MS = 30_000L
    private const val MAX_STEP_MS = 10_000L

    // Longer than this without a place is a gap in the track, not a late second.
    private const val GAP_MS = 3_000L

    // How far to either side of a point its altitude is evened out.
    private const val SMOOTH_MS = 7_000L

    private const val SHRINK_STEPS = 60
}
