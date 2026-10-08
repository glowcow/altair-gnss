package dev.glowcow.altairgnss.ui.recording

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.recording.Scale
import dev.glowcow.altairgnss.ui.components.LocalUnits
import dev.glowcow.altairgnss.ui.components.clock
import dev.glowcow.altairgnss.ui.components.lengthFormat
import dev.glowcow.altairgnss.ui.components.signed
import dev.glowcow.altairgnss.ui.theme.AltairTheme
import dev.glowcow.altairgnss.ui.theme.AppFont
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A marked moment on a chart: when, and what stands on its flag. */
class ChartMark(val timeMs: Long, val text: String)

/**
 * Height over time. [heights] are metres from the level the recording is counted from, which is
 * always on the chart; when [absolute] they are altitudes, and the chart spans only what they do.
 * Time is marked along the bottom at round steps as far apart as their labels need. [marks] stand
 * as flags over their moments. [full] fills the space given and draws round levels of height too;
 * [topInset] is kept clear at the top for what lies over the chart.
 */
@Composable
fun ProfileChart(
    times: List<Long>,
    heights: List<Double>,
    modifier: Modifier = Modifier,
    absolute: Boolean = false,
    marks: List<ChartMark> = emptyList(),
    full: Boolean = false,
    topInset: Dp = 0.dp,
) {
    val c = AltairTheme.colors
    val measurer = rememberTextMeasurer()
    val label = TextStyle(color = c.muted, fontSize = 11.sp, fontFamily = AppFont)
    val flag = TextStyle(color = c.bg, fontSize = 10.sp, fontFamily = AppFont)
    val description = stringResource(R.string.chart_description)
    val unit = lengthFormat()
    val perMetre = LocalUnits.current.length.perMetre

    Canvas((if (full) modifier.fillMaxSize() else modifier.fillMaxWidth().height(260.dp)).semantics { contentDescription = description }) {
        if (times.size < 2) return@Canvas
        val start = times.first()
        val span = (times.last() - start).coerceAtLeast(1L)
        // A flat recording still gets a scale to be drawn on.
        val floor = if (absolute) heights.min() else 0.0
        val low = min(heights.min(), floor).let { if (heights.max() - it < MIN_RANGE) it - MIN_RANGE / 2 else it }
        val high = max(heights.max(), floor).let { if (it - low < MIN_RANGE) low + MIN_RANGE else it }

        // The level heights are counted from comes first: a line too close to one already drawn is left out, and that one must stay.
        val levels = (listOf(floor) + if (full) Scale.levels(low * perMetre, high * perMetre, FULL_LEVELS).map { it / perMetre } else listOf(high, low)).distinct()
        // Levels closer than a whole unit are told apart by a tenth.
        val digits = if (levels.any { abs(it * perMetre - Math.rint(it * perMetre)) > 0.01 } && full) 1 else 0
        val scale = levels.map {
            val figure = if (absolute) String.format(Locale.getDefault(), "%.${digits}f", it * perMetre) else signed(it * perMetre, digits)
            it to measurer.measure(String.format(unit, figure), label)
        }
        val gutter = scale.maxOf { it.second.size.width } + 8.dp.toPx()
        val line = measurer.measure("0", label).size.height
        val flagHeight = measurer.measure("0", flag).size.height + 4.dp.toPx()
        val left = 16.dp.toPx() + gutter
        val right = size.width - 16.dp.toPx()
        val top = 14.dp.toPx() + topInset.toPx() + if (marks.isEmpty()) 0f else flagHeight + 4.dp.toPx()
        val bottom = size.height - line - 16.dp.toPx()
        fun x(time: Long) = left + ((time - start).toDouble() / span * (right - left)).toFloat()
        fun y(height: Double) = top + ((high - height) / (high - low) * (bottom - top)).toFloat()

        val drawn = mutableListOf<Float>()
        for ((value, text) in scale) {
            if (drawn.any { abs(it - y(value)) < text.size.height }) continue
            drawn += y(value)
            drawLine(c.line, Offset(left, y(value)), Offset(right, y(value)), 1.dp.toPx())
            drawText(text, topLeft = Offset(left - 8.dp.toPx() - text.size.width, y(value) - text.size.height / 2))
        }

        // Time along the bottom: the start, the end, and round steps between them that do not touch either.
        val end = measurer.measure(clock(span), label)
        val widest = measurer.measure("0:00:00", label).size.width
        val step = Scale.timeStep(span, right - left, widest + 14.dp.toPx())
        val first = measurer.measure(Scale.timeLabel(0, step), label)
        drawText(first, topLeft = Offset(left, bottom + 6.dp.toPx()))
        drawText(end, topLeft = Offset(right - end.size.width, bottom + 6.dp.toPx()))
        for (at in Scale.timeMarks(span, step)) {
            val text = measurer.measure(Scale.timeLabel(at, step), label)
            val from = x(start + at) - text.size.width / 2
            if (from < left + first.size.width + 8.dp.toPx() || from + text.size.width > right - end.size.width - 8.dp.toPx()) continue
            drawLine(c.line, Offset(x(start + at), top), Offset(x(start + at), bottom), 1.dp.toPx())
            drawText(text, topLeft = Offset(from, bottom + 6.dp.toPx()))
        }

        // More points than pixels: each step of the line keeps the lowest and the highest of its share.
        val share = max(1, times.size / ((right - left) / 2).toInt().coerceAtLeast(1))
        val curve = Path()
        var started = false
        fun add(i: Int) {
            if (started) curve.lineTo(x(times[i]), y(heights[i])) else curve.moveTo(x(times[i]), y(heights[i]))
            started = true
        }
        for (from in times.indices step share) {
            val part = from until min(from + share, times.size)
            val lowest = part.minBy { heights[it] }
            val highest = part.maxBy { heights[it] }
            add(min(lowest, highest))
            if (lowest != highest) add(max(lowest, highest))
        }
        add(times.lastIndex)
        val fill = Path().apply {
            addPath(curve)
            lineTo(x(times.last()), y(max(floor, low)))
            lineTo(x(start), y(max(floor, low)))
            close()
        }
        drawPath(fill, c.accent.copy(alpha = 0.14f))
        drawPath(curve, c.accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // The marked moments: a line down the chart and a flag over it; a flag that would cover one already there is left out.
        var taken = left - 4.dp.toPx()
        for (mark in marks) {
            val at = x(mark.timeMs.coerceIn(start, times.last()))
            drawLine(c.text, Offset(at, top - 4.dp.toPx()), Offset(at, bottom), 1.dp.toPx())
            val text = measurer.measure(mark.text, flag, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = FLAG_WIDTH.roundToPx()))
            val width = text.size.width + 10.dp.toPx()
            val from = (at - width / 2).coerceIn(left, max(left, right - width))
            if (from < taken + 4.dp.toPx()) continue
            taken = from + width
            drawRoundRect(c.text, Offset(from, top - 4.dp.toPx() - flagHeight), Size(width, flagHeight), CornerRadius(flagHeight / 2))
            drawText(text, topLeft = Offset(from + 5.dp.toPx(), top - 4.dp.toPx() - flagHeight + 2.dp.toPx()))
        }
    }
}

/** The least span of the scale, metres. */
private const val MIN_RANGE = 2.0

// How many round levels of height a full-screen chart draws at most, and how wide a flag may be.
private const val FULL_LEVELS = 8
private val FLAG_WIDTH = 96.dp
