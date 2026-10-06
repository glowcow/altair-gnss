package dev.glowcow.altairgnss.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.container
import dev.glowcow.altairgnss.gnss.GeoPoint
import dev.glowcow.altairgnss.gnss.Offset3
import dev.glowcow.altairgnss.gnss.Scatter
import dev.glowcow.altairgnss.instruments.Heading
import dev.glowcow.altairgnss.ui.components.CoordinatesSheet
import dev.glowcow.altairgnss.ui.components.DetailScreen
import dev.glowcow.altairgnss.ui.components.Group
import dev.glowcow.altairgnss.ui.components.GroupDivider
import dev.glowcow.altairgnss.ui.components.GroupRow
import dev.glowcow.altairgnss.ui.components.GroupSheet
import dev.glowcow.altairgnss.ui.components.NO_VALUE
import dev.glowcow.altairgnss.ui.components.StatGrid
import dev.glowcow.altairgnss.ui.components.compassPoints
import dev.glowcow.altairgnss.ui.components.lengthText
import dev.glowcow.altairgnss.ui.theme.AltairTheme

private enum class ScatterSheet { REFERENCE, COORDINATES }

/**
 * Fixes collected while the page is open, plotted round a reference point: their mean, or a point
 * the user fixes. Says how far the receiver wanders.
 */
@Composable
fun ScatterScreen(onBack: () -> Unit) {
    val c = AltairTheme.colors
    val gnss by LocalContext.current.container.gnss.state.collectAsStateWithLifecycle()
    var fixes by remember { mutableStateOf(emptyList<GeoPoint>()) }
    // Null counts from the mean of the fixes.
    var fixed by remember { mutableStateOf<GeoPoint?>(null) }
    var sheet by rememberSaveable { mutableStateOf<ScatterSheet?>(null) }

    val fix = gnss.fix?.takeIf { gnss.hasFix }
    LaunchedEffect(fix?.timeMs) {
        if (fix != null) fixes = (fixes + GeoPoint(fix.latitude, fix.longitude, fix.mslAltitude ?: fix.altitude)).takeLast(MAX_FIXES)
    }
    val reference = fixed ?: remember(fixes) { Scatter.mean(fixes) }
    val offsets = remember(fixes, reference) { reference?.let { r -> fixes.map { Scatter.offset(it, r) } }.orEmpty() }
    val stats = remember(offsets) { Scatter.stats(offsets) }
    // The plot grows by steps, so it does not rescale with every fix.
    val ring = RINGS.firstOrNull { it >= (stats?.max ?: 0.0) } ?: RINGS.last()
    val points = compassPoints()

    DetailScreen(stringResource(R.string.tools_scatter), onBack) {
        val metres = @Composable { v: Double? -> v?.let { lengthText(it) } ?: NO_VALUE }
        Group {
            ScatterPlot(offsets, ring, Modifier.padding(14.dp))
            Text(
                if (stats == null) stringResource(R.string.scatter_no_fix) else stringResource(R.string.scatter_ring, lengthText(ring, 0)),
                color = c.muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }
        Group {
            StatGrid(
                listOf(
                    stringResource(R.string.label_fixes) to (stats?.count?.toString() ?: NO_VALUE),
                    stringResource(R.string.label_now_from_point) to (
                        offsets.lastOrNull()?.let { stringResource(R.string.scatter_offset, lengthText(it.distance), points[Heading.point(it.bearing.toFloat())]) } ?: NO_VALUE
                        ),
                    stringResource(R.string.label_mean_error) to metres(stats?.mean),
                    stringResource(R.string.label_farthest) to metres(stats?.max),
                    stringResource(R.string.label_cep50) to metres(stats?.cep50),
                    stringResource(R.string.label_cep95) to metres(stats?.cep95),
                    stringResource(R.string.label_vertical_spread) to (stats?.verticalSpread?.let { "±" + lengthText(it) } ?: NO_VALUE),
                ),
            )
        }
        Group {
            GroupRow(
                stringResource(R.string.scatter_reference),
                value = stringResource(if (fixed == null) R.string.scatter_reference_mean else R.string.scatter_reference_fixed),
                onClick = { sheet = ScatterSheet.REFERENCE },
            )
            GroupDivider()
            GroupRow(stringResource(R.string.scatter_reset), subtitle = stringResource(R.string.scatter_reset_hint), onClick = { fixes = emptyList() })
        }
        Text(stringResource(R.string.scatter_hint), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
    }

    when (sheet) {
        ScatterSheet.REFERENCE -> GroupSheet(stringResource(R.string.scatter_reference), onDismiss = { sheet = null }) { pick ->
            Group {
                GroupRow(stringResource(R.string.scatter_reference_mean), onClick = { pick { fixed = null } })
                GroupDivider()
                GroupRow(stringResource(R.string.scatter_reference_now), onClick = fixes.lastOrNull()?.let { now -> { pick { fixed = now } } })
                GroupDivider()
                GroupRow(stringResource(R.string.scatter_reference_enter), onClick = { pick { sheet = ScatterSheet.COORDINATES } })
            }
        }
        ScatterSheet.COORDINATES -> CoordinatesSheet(onSave = { latitude, longitude -> fixed = GeoPoint(latitude, longitude) }, onDismiss = { sheet = null })
        null -> Unit
    }
}

/** Dots on a target with north up; [ring] is the distance of its rim, metres. The latest fix is the solid dot. */
@Composable
private fun ScatterPlot(offsets: List<Offset3>, ring: Double, modifier: Modifier = Modifier) {
    val c = AltairTheme.colors
    val description = stringResource(R.string.scatter_description)
    Canvas(modifier.fillMaxWidth().aspectRatio(1f).semantics { contentDescription = description }) {
        val rim = size.minDimension / 2 - 6.dp.toPx()
        for (part in 1..4) drawCircle(c.line, rim * part / 4, style = Stroke(1.dp.toPx()))
        drawLine(c.line, Offset(center.x - rim, center.y), Offset(center.x + rim, center.y), 1.dp.toPx())
        drawLine(c.line, Offset(center.x, center.y - rim), Offset(center.x, center.y + rim), 1.dp.toPx())
        fun at(offset: Offset3): Offset {
            // A fix beyond the rim is drawn on it.
            val scale = (rim / ring) * (ring / offset.distance).coerceAtMost(1.0)
            return Offset(center.x + (offset.east * scale).toFloat(), center.y - (offset.north * scale).toFloat())
        }
        for (offset in offsets) drawCircle(c.accent.copy(alpha = 0.35f), 3.dp.toPx(), at(offset))
        offsets.lastOrNull()?.let {
            drawCircle(c.group, 7.dp.toPx(), at(it))
            drawCircle(c.accent, 5.dp.toPx(), at(it))
        }
    }
}

/** Distances the rim of the plot can stand for, metres. */
private val RINGS = listOf(2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0, 1000.0)

// An hour of fixes at one a second.
private const val MAX_FIXES = 3600
