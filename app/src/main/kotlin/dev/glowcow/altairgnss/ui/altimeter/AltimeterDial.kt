package dev.glowcow.altairgnss.ui.altimeter

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.altimeter.Barometry
import dev.glowcow.altairgnss.ui.components.NO_VALUE
import dev.glowcow.altairgnss.ui.theme.AltairTheme
import dev.glowcow.altairgnss.ui.theme.AppFont
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * An aircraft-style altimeter: the hand goes round once per 100 m, so a figure is tens of metres
 * and a small mark is 2 m; the drum counts whole metres, the two windows show the sea-level
 * pressure the reading rests on. The face is dark in any theme.
 */
@Composable
fun AltimeterDial(altitude: Double?, referenceHpa: Double?, modifier: Modifier = Modifier) {
    val accent = AltairTheme.colors.accent
    val measurer = rememberTextMeasurer()
    // The hand glides between readings; after a jump, such as a calibration, it runs round to the new one.
    val shown by animateFloatAsState(altitude?.toFloat() ?: 0f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow), label = "altitude")
    val labels = listOf(R.string.dial_altitude, R.string.dial_metres, R.string.dial_mbar, R.string.dial_mmhg, R.string.dial_scale).map { stringResource(it) }
    val description = stringResource(R.string.altimeter_description, altitude?.let { stringResource(R.string.unit_metres, it.roundToInt().toString()) } ?: NO_VALUE)

    Canvas(modifier.fillMaxWidth().aspectRatio(1f).semantics { contentDescription = description }) {
        val r = size.minDimension / 2
        fun style(scale: Float, weight: FontWeight = FontWeight.Bold, family: FontFamily = AppFont, color: Color = MARK) =
            TextStyle(color = color, fontSize = (r * scale).toSp(), fontWeight = weight, fontFamily = family)

        drawCircle(BEZEL, r)
        drawCircle(BEZEL_EDGE, r * 0.965f, style = Stroke(1.5.dp.toPx()))
        drawCircle(FACE, r * 0.93f)

        // Fifty marks of 2 m; every fifth is ten metres and carries a figure.
        for (i in 0 until 50) {
            val ten = i % 5 == 0
            rotate(i * 7.2f) {
                drawLine(
                    MARK,
                    Offset(center.x, center.y - r * 0.89f),
                    Offset(center.x, center.y - r * (if (ten) 0.76f else 0.82f)),
                    (if (ten) 3.5f else 1.6f).dp.toPx(),
                    StrokeCap.Butt,
                )
            }
        }
        for (figure in 0..9) {
            val a = Math.toRadians(figure * 36.0)
            centred(measurer, figure.toString(), style(0.16f), Offset(center.x + r * 0.63f * sin(a).toFloat(), center.y - r * 0.63f * cos(a).toFloat()))
        }

        centred(measurer, labels[0], style(0.105f), Offset(center.x - r * 0.37f, center.y + r * 0.02f))
        centred(measurer, labels[1], style(0.072f), Offset(center.x + r * 0.37f, center.y + r * 0.02f))

        // The drum: five figures, a minus taking the first place below sea level.
        val drum = Size(r * 0.72f, r * 0.2f)
        val drumAt = Offset(center.x - drum.width / 2, center.y - r * 0.36f)
        drawRoundRect(WINDOW, drumAt, drum, CornerRadius(r * 0.03f))
        drawRoundRect(WINDOW_EDGE, drumAt, drum, CornerRadius(r * 0.03f), style = Stroke(1.dp.toPx()))
        val metres = shown.roundToInt()
        val figures = when {
            altitude == null -> "-----"
            metres < 0 -> "-" + String.format(Locale.ROOT, "%04d", abs(metres) % 10_000)
            else -> String.format(Locale.ROOT, "%05d", metres % 100_000)
        }
        val cell = drum.width / figures.length
        figures.forEachIndexed { i, figure ->
            if (i > 0) drawLine(WINDOW_EDGE, Offset(drumAt.x + cell * i, drumAt.y), Offset(drumAt.x + cell * i, drumAt.y + drum.height), 1.dp.toPx())
            centred(measurer, figure.toString(), style(0.16f, FontWeight.Medium, FontFamily.Monospace), Offset(drumAt.x + cell * (i + 0.5f), drumAt.y + drum.height / 2))
        }

        fun window(x: Float, label: String, value: String) {
            val box = Size(r * 0.42f, r * 0.17f)
            val at = Offset(center.x + x - box.width / 2, center.y + r * 0.2f)
            drawRoundRect(accent, at, box, CornerRadius(r * 0.035f), style = Stroke(1.5.dp.toPx()))
            // The label sits in a gap of the frame's top edge.
            val text = measurer.measure(label, style(0.07f, FontWeight.Medium, color = accent))
            drawRect(FACE, Offset(center.x + x - text.size.width / 2 - r * 0.02f, at.y - r * 0.02f), Size(text.size.width + r * 0.04f, r * 0.04f))
            drawText(text, topLeft = Offset(center.x + x - text.size.width / 2, at.y - text.size.height / 2))
            centred(measurer, value, style(0.085f, FontWeight.Medium, FontFamily.Monospace), Offset(center.x + x, at.y + box.height * 0.56f))
        }
        window(-r * 0.3f, labels[2], referenceHpa?.let { String.format(Locale.ROOT, "%.1f", it) } ?: NO_VALUE)
        window(r * 0.3f, labels[3], referenceHpa?.let { String.format(Locale.ROOT, "%.1f", it * Barometry.MMHG_PER_HPA) } ?: NO_VALUE)
        // What a figure of the scale is worth.
        centred(measurer, labels[4], style(0.068f, FontWeight.Medium), Offset(center.x, center.y + r * 0.455f))

        // The hand: a long tapered pointer with a short tail, over a hub.
        rotate(shown.mod(TURN_METRES) * 360f / TURN_METRES) {
            drawPath(
                Path().apply {
                    moveTo(center.x, center.y - r * 0.8f)
                    lineTo(center.x + r * 0.028f, center.y)
                    lineTo(center.x + r * 0.018f, center.y + r * 0.14f)
                    lineTo(center.x - r * 0.018f, center.y + r * 0.14f)
                    lineTo(center.x - r * 0.028f, center.y)
                    close()
                },
                MARK,
            )
        }
        drawCircle(HUB, r * 0.055f)
        drawCircle(BEZEL_EDGE, r * 0.055f, style = Stroke(1.dp.toPx()))
    }
}

private fun DrawScope.centred(measurer: TextMeasurer, text: String, style: TextStyle, at: Offset) {
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2, at.y - layout.size.height / 2))
}

/** Metres in one turn of the hand. */
private const val TURN_METRES = 100f

private val BEZEL = Color(0xFF1D1D20)
private val BEZEL_EDGE = Color(0xFF45454B)
private val FACE = Color(0xFF09090A)
private val MARK = Color(0xFFF4F4F4)
private val WINDOW = Color(0xFF1A1A1D)
private val WINDOW_EDGE = Color(0xFF4A4A50)
private val HUB = Color(0xFF26262A)
