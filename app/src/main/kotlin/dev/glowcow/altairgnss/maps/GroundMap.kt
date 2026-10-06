package dev.glowcow.altairgnss.maps

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import dev.glowcow.altairgnss.gnss.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.floor

/** A piece of map as one picture: [centreX], [centreY] is where the centre it was made for falls in it, pixels. */
class MapLayer(val image: ImageBitmap, val centreX: Float, val centreY: Float, val metresPerPixel: Double) {
    /** How far from the centre the picture reaches on its nearest side, metres. */
    val reach: Double get() = minOf(centreX, centreY, image.width - centreX, image.height - centreY) * metresPerPixel
}

/**
 * The ground under a track: a sharp map for the disc the track stands on, and a blurred one of far
 * more country round it, which the sharp one fades into.
 */
class GroundMap(val sharp: MapLayer, val far: MapLayer)

class MapLoader(private val store: TileStore) {
    // The tile server allows two connections at a time.
    private val connections = Semaphore(2)

    /** The map round [centre] for a disc of [radius] metres; null when no tile of it could be had. */
    suspend fun load(centre: GeoPoint, radius: Double): GroundMap? {
        // Three tiles across the disc: about a pixel of map to a pixel of screen.
        val zoom = Tiles.zoomFor(2 * radius, centre.latitude, 3 * Tiles.SIZE)
        val sharp = mosaic(centre, zoom, radius * SHARP_REACH) ?: return null
        val wide = mosaic(centre, (zoom - FAR_ZOOM_OUT).coerceAtLeast(0), radius * FAR_REACH) ?: return null
        return GroundMap(sharp.layer(), withContext(Dispatchers.Default) { blurred(wide) })
    }

    private class Mosaic(val picture: Bitmap, val centreX: Float, val centreY: Float, val metresPerPixel: Double) {
        fun layer() = MapLayer(picture.asImageBitmap(), centreX, centreY, metresPerPixel)
    }

    /** The tiles within [reach] metres of [centre] at [zoom], put together. */
    private suspend fun mosaic(centre: GeoPoint, zoom: Int, reach: Double): Mosaic? = coroutineScope {
        val metresPerPixel = Tiles.metresPerPixel(centre.latitude, zoom)
        val cx = Tiles.x(centre.longitude, zoom) * Tiles.SIZE
        val cy = Tiles.y(centre.latitude, zoom) * Tiles.SIZE
        // No more than MAX_SIDE tiles a side, however long the track.
        val pixels = minOf(reach / metresPerPixel, MAX_SIDE * Tiles.SIZE / 2.0 - Tiles.SIZE / 2.0)
        val x0 = floor((cx - pixels) / Tiles.SIZE).toInt()
        val x1 = floor((cx + pixels) / Tiles.SIZE).toInt()
        val y0 = floor((cy - pixels) / Tiles.SIZE).toInt()
        val y1 = floor((cy + pixels) / Tiles.SIZE).toInt()
        val places = (x0..x1).flatMap { x -> (y0..y1).map { y -> x to y } }
        val tiles = places.map { (x, y) -> async { connections.withPermit { store.tile(zoom, x, y) } } }.awaitAll()
        if (tiles.all { it == null }) return@coroutineScope null
        val picture = createBitmap((x1 - x0 + 1) * Tiles.SIZE, (y1 - y0 + 1) * Tiles.SIZE)
        val canvas = Canvas(picture)
        places.forEachIndexed { i, (x, y) ->
            tiles[i]?.let { canvas.drawBitmap(it, ((x - x0) * Tiles.SIZE).toFloat(), ((y - y0) * Tiles.SIZE).toFloat(), null) }
        }
        Mosaic(picture, (cx - x0 * Tiles.SIZE).toFloat(), (cy - y0 * Tiles.SIZE).toFloat(), metresPerPixel)
    }

    /** The same country with nothing to read on it: shrunk, smeared, and drawn large again by the view. */
    private fun blurred(layer: Mosaic): MapLayer {
        val small = layer.picture.scale(layer.picture.width / SHRINK, layer.picture.height / SHRINK)
        val w = small.width
        val h = small.height
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        repeat(BLUR_PASSES) { boxBlur(pixels, w, h, BLUR_RADIUS) }
        small.setPixels(pixels, 0, w, 0, 0, w, h)
        return MapLayer(small.asImageBitmap(), layer.centreX / SHRINK, layer.centreY / SHRINK, layer.metresPerPixel * SHRINK)
    }

    private companion object {
        // How far each layer reaches from the centre, in radii of the disc, and how many zooms out the far one is.
        const val SHARP_REACH = 1.7
        const val FAR_REACH = 5.0
        const val FAR_ZOOM_OUT = 3
        const val MAX_SIDE = 6
        const val SHRINK = 4
        const val BLUR_RADIUS = 4
        const val BLUR_PASSES = 3
    }
}

/** One pass of a box blur over opaque pixels, across and then down. */
internal fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int) {
    val line = IntArray(maxOf(width, height))
    fun pass(count: Int, length: Int, index: (Int, Int) -> Int) {
        for (row in 0 until count) {
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val p = pixels[index(row, i.coerceIn(0, length - 1))]
                r += p shr 16 and 0xFF
                g += p shr 8 and 0xFF
                b += p and 0xFF
            }
            val window = 2 * radius + 1
            for (i in 0 until length) {
                line[i] = (0xFF shl 24) or (r / window shl 16) or (g / window shl 8) or (b / window)
                val leaving = pixels[index(row, (i - radius).coerceIn(0, length - 1))]
                val entering = pixels[index(row, (i + radius + 1).coerceIn(0, length - 1))]
                r += (entering shr 16 and 0xFF) - (leaving shr 16 and 0xFF)
                g += (entering shr 8 and 0xFF) - (leaving shr 8 and 0xFF)
                b += (entering and 0xFF) - (leaving and 0xFF)
            }
            for (i in 0 until length) pixels[index(row, i)] = line[i]
        }
    }
    pass(height, width) { row, i -> row * width + i }
    pass(width, height) { column, i -> i * width + column }
}
