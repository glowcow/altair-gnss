package dev.glowcow.altairgnss.ui.recording

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.ui.components.LocalUnits
import dev.glowcow.altairgnss.ui.components.clock
import dev.glowcow.altairgnss.ui.components.lengthFormat
import dev.glowcow.altairgnss.ui.components.signed
import dev.glowcow.altairgnss.ui.theme.AltairTheme
import dev.glowcow.altairgnss.ui.theme.AppFont
import kotlin.math.max
import kotlin.math.min

/**
 * Height over time. [heights] are metres from the zero of the recording, which is always on the
 * chart.
 */
@Composable
fun ProfileChart(times: List<Long>, heights: List<Double>, modifier: Modifier = Modifier) {
    val c = AltairTheme.colors
    val measurer = rememberTextMeasurer()
    val label = TextStyle(color = c.muted, fontSize = 11.sp, fontFamily = AppFont)
    val description = stringResource(R.string.chart_description)
    val unit = lengthFormat()
    val perMetre = LocalUnits.current.length.perMetre

    Canvas(modifier.fillMaxWidth().height(240.dp).semantics { contentDescription = description }) {
        if (times.size < 2) return@Canvas
        val start = times.first()
        val span = (times.last() - start).coerceAtLeast(1L).toDouble()
        // A flat recording still gets a scale to be drawn on.
        val low = min(heights.min(), 0.0).let { if (heights.max() - it < MIN_RANGE) it - MIN_RANGE / 2 else it }
        val high = max(heights.max(), 0.0).let { if (it - low < MIN_RANGE) low + MIN_RANGE else it }

        // The zero first: a line too close to one already drawn is left out, and the zero must stay.
        val scale = listOf(0.0, high, low).distinct().map { it to measurer.measure(String.format(unit, signed(it * perMetre, 0)), label) }
        val gutter = scale.maxOf { it.second.size.width } + 8.dp.toPx()
        val footer = measurer.measure("0", label).size.height + 6.dp.toPx()
        val left = 16.dp.toPx() + gutter
        val right = size.width - 16.dp.toPx()
        val top = 14.dp.toPx()
        val bottom = size.height - footer - 10.dp.toPx()
        fun x(time: Long) = left + ((time - start) / span * (right - left)).toFloat()
        fun y(height: Double) = top + ((high - height) / (high - low) * (bottom - top)).toFloat()

        val drawn = mutableListOf<Float>()
        for ((value, text) in scale) {
            if (drawn.any { kotlin.math.abs(it - y(value)) < text.size.height }) continue
            drawn += y(value)
            drawLine(c.line, Offset(left, y(value)), Offset(right, y(value)), 1.dp.toPx())
            drawText(text, topLeft = Offset(left - 8.dp.toPx() - text.size.width, y(value) - text.size.height / 2))
        }

        // More points than pixels: each step of the line keeps the lowest and the highest of its share.
        val step = max(1, times.size / ((right - left) / 2).toInt().coerceAtLeast(1))
        val line = Path()
        var started = false
        fun add(i: Int) {
            if (started) line.lineTo(x(times[i]), y(heights[i])) else line.moveTo(x(times[i]), y(heights[i]))
            started = true
        }
        for (from in times.indices step step) {
            val share = from until min(from + step, times.size)
            val lowest = share.minBy { heights[it] }
            val highest = share.maxBy { heights[it] }
            add(min(lowest, highest))
            if (lowest != highest) add(max(lowest, highest))
        }
        add(times.lastIndex)
        val fill = Path().apply {
            addPath(line)
            lineTo(x(times.last()), y(0.0))
            lineTo(x(start), y(0.0))
            close()
        }
        drawPath(fill, c.accent.copy(alpha = 0.14f))
        drawPath(line, c.accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        val end = measurer.measure(clock(times.last() - start), label)
        drawText(measurer.measure(clock(0), label), topLeft = Offset(left, bottom + 6.dp.toPx()))
        drawText(end, topLeft = Offset(right - end.size.width, bottom + 6.dp.toPx()))
    }
}

/** The least span of the scale, metres. */
private const val MIN_RANGE = 2.0
