package dev.glowcow.altairgnss.ui.instruments

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.instruments.Heading
import dev.glowcow.altairgnss.ui.components.LocalUnits
import dev.glowcow.altairgnss.ui.components.NO_VALUE
import dev.glowcow.altairgnss.ui.components.animatedTurn
import dev.glowcow.altairgnss.ui.components.compassPoints
import dev.glowcow.altairgnss.ui.components.decimals
import dev.glowcow.altairgnss.ui.components.speedLabel
import dev.glowcow.altairgnss.ui.components.tabular
import dev.glowcow.altairgnss.ui.theme.AltairTheme
import dev.glowcow.altairgnss.ui.theme.AppFont
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** The reading in the middle of a dial; [drop] sets it lower, [captionDrop] the caption lower under the value. */
@Composable
private fun DialReading(value: String, caption: String, large: Boolean, drop: Dp = 0.dp, captionDrop: Dp = 0.dp) {
    val c = AltairTheme.colors
    Column(Modifier.offset(y = drop), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = c.text, fontSize = if (large) 64.sp else 34.sp, fontWeight = FontWeight.Bold, style = tabular(), maxLines = 1)
        Text(
            caption,
            color = c.muted,
            fontSize = if (large) 22.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.offset(y = captionDrop),
        )
    }
}

/**
 * A card that turns under a fixed mark at the top; the heading sits in the middle, set apart by a
 * ring. [large] is the full-screen dial, with finer marks and degrees on the card.
 */
@Composable
fun Compass(heading: Float?, modifier: Modifier = Modifier, large: Boolean = false) {
    val c = AltairTheme.colors
    val measurer = rememberTextMeasurer()
    val cardinals = listOf(R.string.sky_north, R.string.sky_east, R.string.sky_south, R.string.sky_west).map { stringResource(it) }
    val points = compassPoints()
    val card = animatedTurn(heading)
    val letter = TextStyle(color = c.text, fontSize = if (large) 20.sp else 13.sp, fontFamily = AppFont, fontWeight = FontWeight.Bold)
    val degree = TextStyle(color = c.muted, fontSize = 12.sp, fontFamily = AppFont)

    Box(modifier.fillMaxWidth().aspectRatio(1f).padding(start = 12.dp, end = 12.dp, top = 12.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2 - 8.dp.toPx()
            val grow = if (large) 1.7f else 1f
            val ring = radius * if (large) RING_LARGE else RING_SMALL
            // Letters and degrees share one circle: on the large card a fixed way in from the marks,
            // on the small one halfway between the marks and the ring.
            val labels = if (large) radius - 30.dp.toPx() else (radius - 9.dp.toPx() + ring) / 2
            // Top of a line whose capitals are centred on that circle.
            fun top(layout: TextLayoutResult, style: TextStyle) =
                center.y - labels - (layout.firstBaseline - style.fontSize.toPx() * CAP_HEIGHT / 2)
            rotate(-card) {
                for (degrees in 0 until 360 step if (large) 5 else 10) {
                    val major = degrees % 30 == 0
                    rotate(degrees.toFloat()) {
                        drawLine(
                            if (major) c.muted else c.line,
                            Offset(center.x, center.y - radius),
                            Offset(center.x, center.y - radius + (if (major) 9 else 5).dp.toPx() * grow),
                            (if (major) 2 else 1).dp.toPx(),
                            StrokeCap.Round,
                        )
                    }
                    // Between the cardinal letters the large card carries its degrees.
                    if (large && major && degrees % 90 != 0) {
                        val layout = measurer.measure(degrees.toString(), degree)
                        rotate(degrees.toFloat()) {
                            drawText(layout, topLeft = Offset(center.x - layout.size.width / 2, top(layout, degree)))
                        }
                    }
                }
                cardinals.forEachIndexed { i, name ->
                    val layout = measurer.measure(name, if (i == 0) letter.copy(color = c.accent) else letter)
                    rotate(i * 90f) {
                        drawText(layout, topLeft = Offset(center.x - layout.size.width / 2, top(layout, letter)))
                    }
                }
            }
            drawLine(c.accent, Offset(center.x, center.y - radius - 8.dp.toPx()), Offset(center.x, center.y - radius + 10.dp.toPx() * grow), 3.dp.toPx(), StrokeCap.Round)
            // The ring keeps the reading apart from the turning card.
            drawCircle(c.groupBg, ring)
            drawCircle(c.line, ring, style = Stroke(1.dp.toPx()))
        }
        DialReading(
            heading?.let { stringResource(R.string.unit_degrees, (it.roundToInt() % 360).toString()) } ?: NO_VALUE,
            heading?.let { points[Heading.point(it)] }.orEmpty(),
            large,
            drop = if (large) 8.dp else 4.dp,
            captionDrop = if (large) 6.dp else 3.dp,
        )
    }
}

/**
 * An arc that fills with speed, shown in the chosen unit. Its full scale is the smallest of
 * [SPEED_SCALES] the speed fits in, so walking and flying both use most of the arc.
 */
@Composable
fun Speedometer(mps: Float?, modifier: Modifier = Modifier, large: Boolean = false) {
    val speed = mps?.let { (it * LocalUnits.current.speed.perMps).toFloat() }
    val c = AltairTheme.colors
    val measurer = rememberTextMeasurer()
    val full = SPEED_SCALES.firstOrNull { (speed ?: 0f) <= it } ?: SPEED_SCALES.last()
    val filled by animateFloatAsState(((speed ?: 0f) / full).coerceIn(0f, 1f), label = "speed")
    val scale = TextStyle(color = c.muted, fontSize = if (large) 14.sp else 11.sp, fontFamily = AppFont)

    Box(modifier.fillMaxWidth().aspectRatio(1f).padding(start = 12.dp, end = 12.dp, top = 12.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = (if (large) 14 else 8).dp.toPx()
            val radius = size.minDimension / 2 - 8.dp.toPx() - width / 2
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arc = Size(radius * 2, radius * 2)
            drawArc(c.line, ARC_START, ARC_SWEEP, false, topLeft, arc, style = Stroke(width, cap = StrokeCap.Round))
            if (filled > 0f) drawArc(c.accent, ARC_START, ARC_SWEEP * filled, false, topLeft, arc, style = Stroke(width, cap = StrokeCap.Round))
            // Figures of the scale, inside the arc: its ends on the small dial, every quarter on the large one.
            val steps = if (large) 4 else 1
            for (step in 0..steps) {
                val a = Math.toRadians((ARC_START + ARC_SWEEP * step / steps).toDouble())
                val at = radius - width / 2 - (if (large) 20 else 13).dp.toPx()
                // A quarter of the scale of 10 is 2.5, not 2.
                val figure = full.toFloat() * step / steps
                val layout = measurer.measure(if (figure % 1f == 0f) figure.toInt().toString() else figure.decimals(1), scale)
                drawText(layout, topLeft = Offset(center.x + at * cos(a).toFloat() - layout.size.width / 2, center.y + at * sin(a).toFloat() - layout.size.height / 2))
            }
        }
        DialReading(speed?.decimals(if (speed < 100f) 1 else 0) ?: NO_VALUE, speedLabel(), large)
    }
}

// Radius of the ring round the compass reading, as a part of the dial's.
private const val RING_SMALL = 0.6f
private const val RING_LARGE = 0.56f

// Height of a capital letter of the font, as a part of its size.
private const val CAP_HEIGHT = 0.716f

/** Full scales of the speedometer, in the unit shown. */
private val SPEED_SCALES = listOf(10, 20, 40, 80, 160, 320, 1000)

// The arc opens downwards: from the lower left, clockwise over the top, to the lower right.
private const val ARC_START = 135f
private const val ARC_SWEEP = 270f
