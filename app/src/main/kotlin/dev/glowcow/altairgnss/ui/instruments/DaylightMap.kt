package dev.glowcow.altairgnss.ui.instruments

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.gnss.Fix
import dev.glowcow.altairgnss.instruments.SubsolarPoint
import dev.glowcow.altairgnss.instruments.Sun
import dev.glowcow.altairgnss.ui.theme.AltairTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * The world in the equirectangular projection: bright where the sun is up, shaded with city lights
 * where it is down, with the equator and the polar circles drawn across. A sun mark stands where
 * the sun is overhead, a dot at [position]; the legend under the map says which is which.
 */
@Composable
fun DaylightMap(position: Fix?, now: Long, modifier: Modifier = Modifier) {
    val c = AltairTheme.colors
    val day = ImageBitmap.imageResource(R.drawable.earth_day)
    val night = ImageBitmap.imageResource(R.drawable.earth_night)
    val gold = colorResource(R.color.altair_gold)
    // The terminator moves a quarter of a degree a minute, less than a pixel of the mask.
    val sun = remember(now / MASK_REFRESH_MS) { Sun.subsolar(now) }
    val mask = remember(sun) { nightMask(sun) }
    val description = stringResource(R.string.map_description)

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(2f).semantics { contentDescription = description }) {
            val whole = IntSize(size.width.toInt(), size.height.toInt())
            val bounds = Rect(Offset.Zero, size)
            fun at(latitude: Double, longitude: Double) =
                Offset(((longitude + 180) / 360 * size.width).toFloat(), ((90 - latitude) / 180 * size.height).toFloat())

            drawImage(day, dstSize = whole, colorFilter = ColorFilter.colorMatrix(DAY_BOOST))
            drawIntoCanvas { canvas ->
                // The night side: a shade through which the land still shows, cut out by the mask.
                canvas.saveLayer(bounds, Paint())
                drawRect(NIGHT_SHADE)
                drawImage(mask, dstSize = whole, blendMode = BlendMode.DstIn, filterQuality = FilterQuality.Medium)
                canvas.restore()
                // City lights are added on top of it, in the same cut-out.
                canvas.saveLayer(bounds, Paint().apply { blendMode = BlendMode.Screen })
                drawImage(night, dstSize = whole)
                drawImage(mask, dstSize = whole, blendMode = BlendMode.DstIn, filterQuality = FilterQuality.Medium)
                canvas.restore()
            }

            // The equator, and dashed, the polar circles: beyond them the sun can stay up or down for days.
            val stroke = 1.dp.toPx()
            val dashes = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
            drawLine(PARALLEL, at(0.0, -180.0), at(0.0, 180.0), stroke)
            for (latitude in listOf(POLAR_CIRCLE, -POLAR_CIRCLE)) {
                drawLine(PARALLEL, at(latitude, -180.0), at(latitude, 180.0), stroke, pathEffect = dashes)
            }

            sunMark(at(sun.latitude, sun.longitude), gold)
            if (position != null) {
                at(position.latitude, position.longitude).let {
                    drawCircle(Color.White, 7.dp.toPx(), it)
                    drawCircle(c.accent, 5.dp.toPx(), it)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Legend(gold, stringResource(R.string.map_sun))
            Legend(c.accent, stringResource(if (position != null) R.string.map_you else R.string.map_no_position))
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(text, color = AltairTheme.colors.muted, fontSize = 13.sp)
    }
}

/** A disc with rays on a dark halo, readable over land and sea alike. */
private fun DrawScope.sunMark(at: Offset, gold: Color) {
    drawCircle(Color.Black.copy(alpha = 0.35f), 13.dp.toPx(), at)
    for (ray in 0 until 8) {
        rotate(ray * 45f, at) {
            drawLine(gold, Offset(at.x, at.y - 7.5.dp.toPx()), Offset(at.x, at.y - 11.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
        }
    }
    drawCircle(gold, 5.dp.toPx(), at)
}

/** One pixel per degree: opaque where it is night, clear where it is day. */
private fun nightMask(sun: SubsolarPoint): ImageBitmap {
    val pixels = IntArray(MASK_WIDTH * MASK_HEIGHT)
    val sinSun = sin(Math.toRadians(sun.latitude))
    val cosSun = cos(Math.toRadians(sun.latitude))
    val cosHour = DoubleArray(MASK_WIDTH) { x -> cos(Math.toRadians(x + 0.5 - 180 - sun.longitude)) }
    for (y in 0 until MASK_HEIGHT) {
        val latitude = Math.toRadians(90 - (y + 0.5))
        val a = sin(latitude) * sinSun
        val b = cos(latitude) * cosSun
        for (x in 0 until MASK_WIDTH) {
            // Same as Sun.elevationSine, with the trigonometry of rows and columns taken out of the loop.
            val elevation = a + b * cosHour[x]
            val night = ((TWILIGHT_START - elevation) / (TWILIGHT_START - TWILIGHT_END)).coerceIn(0.0, 1.0)
            pixels[y * MASK_WIDTH + x] = (night * 255).toInt() shl 24
        }
    }
    return Bitmap.createBitmap(pixels, MASK_WIDTH, MASK_HEIGHT, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private const val MASK_WIDTH = 360
private const val MASK_HEIGHT = 180
private const val MASK_REFRESH_MS = 60_000L

// Sines of the sun's height where dusk begins and where the night is full: the horizon and -6°.
private const val TWILIGHT_START = 0.0
private const val TWILIGHT_END = -0.105

/** The day picture is dark for a small map; this lifts it. */
private val DAY_BOOST = ColorMatrix(
    floatArrayOf(
        1.3f, 0f, 0f, 0f, 14f,
        0f, 1.3f, 0f, 0f, 14f,
        0f, 0f, 1.3f, 0f, 14f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

/** Latitude of the polar circles: 90° less the tilt of the Earth's axis. */
private const val POLAR_CIRCLE = 66.56

private val PARALLEL = Color.White.copy(alpha = 0.6f)

private val NIGHT_SHADE = Color(0xFF050A1C).copy(alpha = 0.78f)
