package dev.glowcow.altairgnss.ui.sky

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.container
import dev.glowcow.altairgnss.gnss.Signal
import dev.glowcow.altairgnss.ui.components.Group
import dev.glowcow.altairgnss.ui.components.LocationGate
import dev.glowcow.altairgnss.ui.components.NO_VALUE
import dev.glowcow.altairgnss.ui.components.SignalLegend
import dev.glowcow.altairgnss.ui.components.Stat
import dev.glowcow.altairgnss.ui.components.TabScreen
import dev.glowcow.altairgnss.ui.components.TopTab
import dev.glowcow.altairgnss.ui.components.animatedTurn
import dev.glowcow.altairgnss.ui.components.decimals
import dev.glowcow.altairgnss.ui.components.rememberScreenHeading
import dev.glowcow.altairgnss.ui.components.signalColor
import dev.glowcow.altairgnss.ui.theme.AltairTheme
import dev.glowcow.altairgnss.ui.theme.AppFont
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SkyScreen(onTab: (TopTab) -> Unit) = TabScreen(TopTab.SKY, onTab) { top, bottom ->
    LocationGate(top) { SkyContent(top, bottom) }
}

@Composable
private fun SkyContent(top: Dp, bottom: Dp) {
    val state by LocalContext.current.container.gnss.state.collectAsStateWithLifecycle()
    // A satellite heard on two carriers is one dot; it is in the fix if any of its signals is.
    val satellites = state.signals
        .groupBy { it.constellation to it.svid }
        .map { (_, signals) -> signals.maxBy { it.cn0DbHz }.copy(usedInFix = signals.any { it.usedInFix }) }
    val used = satellites.filter { it.usedInFix }
    // The plot turns with the phone, so its north stays over the real one.
    val turn = animatedTurn(rememberScreenHeading().degrees)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = top, bottom = bottom + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Group {
            SkyPlot(satellites, turn, Modifier.fillMaxWidth().aspectRatio(1f).padding(14.dp))
            SignalLegend(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp))
        }
        Group {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(stringResource(R.string.sky_in_view), satellites.size.toString(), Modifier.weight(1f))
                Stat(stringResource(R.string.sky_in_fix), used.size.toString(), Modifier.weight(1f))
                Stat(
                    stringResource(R.string.sky_average),
                    if (used.isEmpty()) NO_VALUE else stringResource(R.string.unit_dbhz, used.map { it.cn0DbHz }.average().decimals(1)),
                    Modifier.weight(1.4f),
                )
            }
        }
    }
}

/**
 * The sky from above: the zenith in the centre, the horizon on the rim. [turn] is where the top of
 * the screen points; labels stay upright. A dot is filled when in the fix.
 */
@Composable
private fun SkyPlot(satellites: List<Signal>, turn: Float, modifier: Modifier = Modifier) {
    val c = AltairTheme.colors
    val measurer = rememberTextMeasurer()
    val cardinals = listOf(R.string.sky_north, R.string.sky_east, R.string.sky_south, R.string.sky_west).map { stringResource(it) }
    val cardinalStyle = TextStyle(color = c.muted, fontSize = 12.sp, fontFamily = AppFont, fontWeight = FontWeight.SemiBold)
    val labelStyle = TextStyle(color = c.text, fontSize = 10.sp, fontFamily = AppFont)

    Canvas(modifier) {
        val centre = Offset(size.width / 2, size.height / 2)
        val rim = size.minDimension / 2 - 14.dp.toPx()
        fun at(elevation: Float, azimuth: Float): Offset {
            val r = rim * (90f - elevation.coerceIn(0f, 90f)) / 90f
            val a = Math.toRadians((azimuth - turn).toDouble())
            return Offset(centre.x + r * sin(a).toFloat(), centre.y - r * cos(a).toFloat())
        }

        for (elevation in listOf(0f, 30f, 60f)) drawCircle(c.line, rim * (90f - elevation) / 90f, centre, style = Stroke(1.dp.toPx()))
        drawLine(c.line, at(0f, 0f), at(0f, 180f), 1.dp.toPx())
        drawLine(c.line, at(0f, 90f), at(0f, 270f), 1.dp.toPx())
        cardinals.forEachIndexed { i, text ->
            val layout = measurer.measure(text, cardinalStyle)
            val a = Math.toRadians(i * 90.0 - turn)
            val p = Offset(centre.x + (rim + 9.dp.toPx()) * sin(a).toFloat(), centre.y - (rim + 9.dp.toPx()) * cos(a).toFloat())
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2, p.y - layout.size.height / 2))
        }

        val dot = 5.dp.toPx()
        for (satellite in satellites) {
            val p = at(satellite.elevation, satellite.azimuth)
            val color = signalColor(satellite.cn0DbHz)
            if (satellite.usedInFix) drawCircle(color, dot, p) else drawCircle(color, dot - 0.75.dp.toPx(), p, style = Stroke(1.5.dp.toPx()))
            val layout = measurer.measure(satellite.label, labelStyle)
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2, p.y + dot + 1.dp.toPx()))
        }
    }
}
