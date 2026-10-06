package dev.glowcow.altairgnss.ui.status

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.container
import dev.glowcow.altairgnss.data.AppSettings
import dev.glowcow.altairgnss.gnss.Constellation
import dev.glowcow.altairgnss.gnss.CoordinateFormat
import dev.glowcow.altairgnss.gnss.Coordinates
import dev.glowcow.altairgnss.gnss.GnssState
import dev.glowcow.altairgnss.gnss.Signal
import dev.glowcow.altairgnss.ui.components.Chip
import dev.glowcow.altairgnss.ui.components.Group
import dev.glowcow.altairgnss.ui.components.GroupDivider
import dev.glowcow.altairgnss.ui.components.GroupRow
import dev.glowcow.altairgnss.ui.components.LocationGate
import dev.glowcow.altairgnss.ui.components.NO_VALUE
import dev.glowcow.altairgnss.ui.components.SignalLegend
import dev.glowcow.altairgnss.ui.components.StatGrid
import dev.glowcow.altairgnss.ui.components.TabScreen
import dev.glowcow.altairgnss.ui.components.TopTab
import dev.glowcow.altairgnss.ui.components.decimals
import dev.glowcow.altairgnss.ui.components.signalColor
import dev.glowcow.altairgnss.ui.components.tabular
import dev.glowcow.altairgnss.ui.theme.AltairTheme
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun StatusScreen(onTab: (TopTab) -> Unit) = TabScreen(TopTab.STATUS, onTab) { top, bottom ->
    LocationGate(top) { StatusContent(top, bottom) }
}

@Composable
private fun StatusContent(top: Dp, bottom: Dp) {
    val c = AltairTheme.colors
    val context = LocalContext.current
    val state by context.container.gnss.state.collectAsStateWithLifecycle()
    val settings by context.container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    var hidden by rememberSaveable { mutableStateOf(emptySet<Constellation>()) }
    var bySignal by rememberSaveable { mutableStateOf(true) }

    val present = state.signals.map { it.constellation }.distinct().sorted()
    val byId = compareBy<Signal> { it.constellation }.thenBy { it.svid }.thenByDescending { it.carrierHz ?: 0f }
    // Equal levels keep the ID order, so rows do not swap places between updates.
    val shown = state.signals.filter { it.constellation !in hidden }
        .sortedWith(if (bySignal) compareByDescending<Signal> { it.cn0DbHz }.then(byId) else byId)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = top, bottom = bottom + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(
                    when {
                        !state.enabled -> R.string.status_off
                        state.hasFix -> R.string.status_fix
                        else -> R.string.status_searching
                    },
                ),
                color = if (state.hasFix) c.accent else c.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            if (state.enabled) {
                Text(stringResource(R.string.status_signals, state.usedCount, state.signals.size), color = c.muted, fontSize = 14.sp)
            }
        }
        if (!state.enabled) {
            Group {
                GroupRow(stringResource(R.string.status_open_location), onClick = {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                })
            }
            return@Column
        }

        Group { StatGrid(fixStats(state, settings.coordinates)) }

        if (state.signals.isEmpty()) {
            Text(stringResource(R.string.status_no_signals), color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
            return@Column
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (constellation in present) {
                Chip(constellation.title, selected = constellation !in hidden) {
                    hidden = if (constellation in hidden) hidden - constellation else hidden + constellation
                }
            }
            Chip(stringResource(if (bySignal) R.string.sort_cn0 else R.string.sort_id), selected = false) { bySignal = !bySignal }
        }
        Group {
            SignalBars(shown)
            SignalLegend(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp))
        }
        Group { SignalTable(shown) }
        Text(stringResource(R.string.status_flags_hint), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun fixStats(state: GnssState, format: CoordinateFormat): List<Pair<String, String>> {
    // A fix that is no longer live would show a stale position as the current one.
    val fix = state.fix?.takeIf { state.hasFix }
    // Without a fix the receiver reports a placeholder DOP.
    val dop = state.dop?.takeIf { state.hasFix }
    val metres = @Composable { v: Double? -> v?.let { stringResource(R.string.unit_metres, it.decimals(1)) } ?: NO_VALUE }
    val accuracy = @Composable { v: Float? -> v?.let { stringResource(R.string.unit_accuracy_metres, it.decimals(1)) } ?: NO_VALUE }
    return listOf(
        stringResource(R.string.label_latitude) to (fix?.let { Coordinates.latitude(it.latitude, format) } ?: NO_VALUE),
        stringResource(R.string.label_longitude) to (fix?.let { Coordinates.longitude(it.longitude, format) } ?: NO_VALUE),
        stringResource(R.string.label_altitude_msl) to metres(fix?.mslAltitude),
        stringResource(R.string.label_altitude_ellipsoid) to metres(fix?.altitude),
        stringResource(R.string.label_accuracy_h) to accuracy(fix?.horizontalAccuracy),
        stringResource(R.string.label_accuracy_v) to accuracy(fix?.verticalAccuracy),
        stringResource(R.string.label_speed) to (fix?.speed?.let { stringResource(R.string.unit_kmh, (it * 3.6f).decimals(1)) } ?: NO_VALUE),
        stringResource(R.string.label_bearing) to (fix?.bearing?.let { stringResource(R.string.unit_degrees, it.decimals(0)) } ?: NO_VALUE),
        stringResource(R.string.label_pdop) to (dop?.pdop?.decimals(1) ?: NO_VALUE),
        stringResource(R.string.label_hvdop) to (dop?.let { "${it.hdop.decimals(1)} / ${it.vdop.decimals(1)}" } ?: NO_VALUE),
        stringResource(R.string.label_ttff) to (state.ttffMs?.let { stringResource(R.string.unit_seconds, (it / 1000f).decimals(1)) } ?: NO_VALUE),
        stringResource(R.string.label_fix_time) to (fix?.let { UTC_TIME.format(Instant.ofEpochMilli(it.timeMs)) } ?: NO_VALUE),
    )
}

private val UTC_TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC)

/** A bar per signal: height and colour are C/N0, none where nothing is heard; the labels of signals outside the fix are muted. */
@Composable
private fun SignalBars(signals: List<Signal>) {
    val c = AltairTheme.colors
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (signal in signals) {
            Column(Modifier.width(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (signal.isHeard) signal.cn0DbHz.decimals(0) else NO_VALUE, color = c.muted, fontSize = 10.sp, style = tabular(), maxLines = 1)
                Box(Modifier.height(96.dp), contentAlignment = Alignment.BottomCenter) {
                    if (signal.isHeard) {
                        Box(
                            Modifier
                                .width(14.dp)
                                .fillMaxHeight((signal.cn0DbHz / CN0_FULL).coerceIn(0.02f, 1f))
                                .clip(RoundedCornerShape(4.dp))
                                .background(signalColor(signal.cn0DbHz)),
                        )
                    }
                }
                Text(signal.label, color = if (signal.usedInFix) c.text else c.muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(signal.band.orEmpty(), color = c.muted, fontSize = 9.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SignalTable(signals: List<Signal>) {
    val c = AltairTheme.colors
    @Composable
    fun line(cells: List<String>, header: Boolean, used: Boolean = false) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = if (header) 12.dp else 9.dp)) {
            cells.forEachIndexed { i, cell ->
                Text(
                    cell,
                    color = when {
                        header -> c.muted
                        i == 0 && used -> c.accent
                        else -> c.text
                    },
                    fontSize = if (header) 12.sp else 14.sp,
                    fontWeight = if (i == 0 && !header) FontWeight.SemiBold else FontWeight.Normal,
                    style = tabular(),
                    textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.weight(COLUMN_WEIGHTS[i]),
                )
            }
        }
    }
    line(
        listOf(R.string.column_id, R.string.column_band, R.string.column_cn0, R.string.column_elevation, R.string.column_azimuth, R.string.column_flags)
            .map { stringResource(it) },
        header = true,
    )
    for (signal in signals) {
        GroupDivider()
        line(
            listOf(
                signal.label,
                signal.band ?: NO_VALUE,
                if (signal.isHeard) signal.cn0DbHz.decimals(1) else NO_VALUE,
                if (signal.hasPosition) stringResource(R.string.unit_degrees, signal.elevation.decimals(0)) else NO_VALUE,
                if (signal.hasPosition) stringResource(R.string.unit_degrees, signal.azimuth.decimals(0)) else NO_VALUE,
                buildString {
                    append(if (signal.hasAlmanac) 'A' else '·')
                    append(if (signal.hasEphemeris) 'E' else '·')
                    append(if (signal.usedInFix) 'U' else '·')
                },
            ),
            header = false,
            used = signal.usedInFix,
        )
    }
}

/** C/N0 that fills a bar; open-sky signals rarely exceed it. */
private const val CN0_FULL = 50f

private val COLUMN_WEIGHTS = listOf(1.1f, 1f, 1f, 1f, 1f, 1f)
