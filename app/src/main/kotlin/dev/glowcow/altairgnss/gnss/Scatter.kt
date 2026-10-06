package dev.glowcow.altairgnss.gnss

import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sqrt

/** A place on the Earth; [altitude] in metres where known. */
data class GeoPoint(val latitude: Double, val longitude: Double, val altitude: Double? = null)

/** Where a fix lies from a reference point, metres: to the east, to the north and up. */
data class Offset3(val east: Double, val north: Double, val up: Double?) {
    val distance: Double get() = hypot(east, north)

    /** Direction from the reference to the fix, degrees clockwise from north. */
    val bearing: Double get() = (Math.toDegrees(atan2(east, north)) + 360) % 360
}

/** How a set of fixes is spread round a reference point; distances are horizontal, metres. */
data class ScatterStats(
    val count: Int,
    val mean: Double,
    /** Half of the fixes lie within this distance, and 95 % of them within the next. */
    val cep50: Double,
    val cep95: Double,
    val max: Double,
    /** Standard deviation of the heights, where the fixes carry them. */
    val verticalSpread: Double?,
)

object Scatter {
    private const val EARTH_RADIUS = 6_371_008.8

    /** The mean position of [fixes]; null for none. */
    fun mean(fixes: List<GeoPoint>): GeoPoint? {
        if (fixes.isEmpty()) return null
        val heights = fixes.mapNotNull { it.altitude }
        return GeoPoint(fixes.sumOf { it.latitude } / fixes.size, fixes.sumOf { it.longitude } / fixes.size, heights.takeIf { it.isNotEmpty() }?.average())
    }

    /** Offsets on a plane touching the Earth at [reference]: exact enough for the metres a receiver wanders. */
    fun offset(fix: GeoPoint, reference: GeoPoint) = Offset3(
        east = Math.toRadians(fix.longitude - reference.longitude) * EARTH_RADIUS * cos(Math.toRadians(reference.latitude)),
        north = Math.toRadians(fix.latitude - reference.latitude) * EARTH_RADIUS,
        up = if (fix.altitude != null && reference.altitude != null) fix.altitude - reference.altitude else null,
    )

    /** The place [east] and [north] metres from [from], on the same plane as [offset]. */
    fun shift(from: GeoPoint, east: Double, north: Double) = GeoPoint(
        from.latitude + Math.toDegrees(north / EARTH_RADIUS),
        from.longitude + Math.toDegrees(east / (EARTH_RADIUS * cos(Math.toRadians(from.latitude)))),
        from.altitude,
    )

    fun stats(offsets: List<Offset3>): ScatterStats? {
        if (offsets.isEmpty()) return null
        val distances = offsets.map { it.distance }.sorted()
        fun within(part: Double) = distances[(ceil(part * distances.size).toInt() - 1).coerceIn(0, distances.lastIndex)]
        val heights = offsets.mapNotNull { it.up }
        return ScatterStats(
            count = offsets.size,
            mean = distances.average(),
            cep50 = within(0.5),
            cep95 = within(0.95),
            max = distances.last(),
            verticalSpread = heights.takeIf { it.size > 1 }?.let { h -> h.average().let { m -> sqrt(h.sumOf { (it - m) * (it - m) } / h.size) } },
        )
    }
}
