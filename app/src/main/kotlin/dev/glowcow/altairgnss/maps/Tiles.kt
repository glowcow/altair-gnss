package dev.glowcow.altairgnss.maps

import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.tan

/** The arithmetic of map tiles in the Web Mercator projection, as every tile server lays them out. */
object Tiles {
    /** Pixels along the side of a tile. */
    const val SIZE = 256
    const val MAX_ZOOM = 17
    private const val EQUATOR = 40_075_016.686

    /** Where a longitude falls across the world at [zoom], in tiles from its western edge. */
    fun x(longitude: Double, zoom: Int): Double = (longitude + 180) / 360 * (1 shl zoom)

    /** Where a latitude falls down the world at [zoom], in tiles from its northern edge. */
    fun y(latitude: Double, zoom: Int): Double = (1 - asinh(tan(Math.toRadians(latitude))) / PI) / 2 * (1 shl zoom)

    /** Metres on the ground a pixel of a tile covers at [latitude]. */
    fun metresPerPixel(latitude: Double, zoom: Int): Double = EQUATOR * cos(Math.toRadians(latitude)) / (SIZE.toDouble() * (1 shl zoom))

    /** The deepest zoom at which [metres] at [latitude] still fit into [pixels]. */
    fun zoomFor(metres: Double, latitude: Double, pixels: Int): Int =
        floor(log2(EQUATOR * cos(Math.toRadians(latitude)) * pixels / (SIZE * metres))).toInt().coerceIn(0, MAX_ZOOM)
}
