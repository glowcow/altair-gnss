package dev.glowcow.altairgnss.ui.altimeter

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.altimeter.AltimeterState
import dev.glowcow.altairgnss.altimeter.AltitudeSource
import dev.glowcow.altairgnss.altimeter.Barometry
import dev.glowcow.altairgnss.altimeter.Drift
import dev.glowcow.altairgnss.container
import dev.glowcow.altairgnss.ui.components.AltitudeSheet
import dev.glowcow.altairgnss.ui.components.ChoiceSheet
import dev.glowcow.altairgnss.ui.components.Group
import dev.glowcow.altairgnss.ui.components.GroupDivider
import dev.glowcow.altairgnss.ui.components.GroupRow
import dev.glowcow.altairgnss.ui.components.GroupSheet
import dev.glowcow.altairgnss.ui.components.LocationGate
import dev.glowcow.altairgnss.ui.components.NO_VALUE
import dev.glowcow.altairgnss.ui.components.StatGrid
import dev.glowcow.altairgnss.ui.components.TabScreen
import dev.glowcow.altairgnss.ui.components.TopTab
import dev.glowcow.altairgnss.ui.components.accuracyText
import dev.glowcow.altairgnss.ui.components.decimals
import dev.glowcow.altairgnss.ui.components.lengthText
import dev.glowcow.altairgnss.ui.components.signed
import dev.glowcow.altairgnss.ui.components.verticalSpeedText
import dev.glowcow.altairgnss.ui.theme.AltairTheme

@Composable
fun AltimeterScreen(onTab: (TopTab) -> Unit) = TabScreen(TopTab.ALTIMETER, onTab) { top, bottom ->
    LocationGate(top) { AltimeterContent(top, bottom) }
}

private enum class Sheet { SOURCE, ALTITUDE, AIRPORT }

@Composable
private fun AltimeterContent(top: Dp, bottom: Dp) {
    val c = AltairTheme.colors
    val altimeter = LocalContext.current.container.altimeter
    val state by altimeter.state.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<Sheet?>(null) }

    val plusMinus = @Composable { v: Float -> accuracyText(v) }
    val sourceName = stringResource(SOURCES.first { it.first == state.source }.second)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = top, bottom = bottom + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Group {
            AltimeterDial(state.altitude, state.pressureHpa, Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp))
            Text(
                when {
                    state.altitude == null && state.source == AltitudeSource.BAROMETER && state.calibration == null ->
                        stringResource(R.string.altitude_calibrate_first)
                    state.altitude == null -> stringResource(R.string.altitude_no_data)
                    else -> state.accuracy?.let { stringResource(R.string.altitude_above_sea_accuracy, sourceName, plusMinus(it)) }
                        ?: stringResource(R.string.altitude_above_sea, sourceName)
                },
                color = c.muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        Group { StatGrid(readings(state)) }

        if (!state.hasBarometer) {
            Group { GroupRow(stringResource(R.string.altitude_source), subtitle = stringResource(R.string.altitude_no_barometer), value = sourceName) }
            return@Column
        }

        Group {
            val progress = state.calibrating
            GroupRow(
                stringResource(R.string.calibrate_gnss),
                subtitle = when {
                    progress != null -> stringResource(R.string.calibrate_progress, (progress * 100).toInt())
                    state.calibrationFailed -> stringResource(R.string.calibrate_failed)
                    else -> stringResource(R.string.calibrate_gnss_hint)
                },
                onClick = if (progress == null) altimeter::calibrateFromGnss else null,
            )
            GroupDivider()
            GroupRow(stringResource(R.string.calibrate_altitude), subtitle = stringResource(R.string.calibrate_altitude_hint), onClick = { sheet = Sheet.ALTITUDE })
            GroupDivider()
            GroupRow(stringResource(R.string.calibrate_airport), subtitle = stringResource(R.string.calibrate_airport_hint), onClick = { sheet = Sheet.AIRPORT })
        }
        Group { GroupRow(stringResource(R.string.altitude_source), value = sourceName, onClick = { sheet = Sheet.SOURCE }) }
        Group { StatGrid(stats(state)) }
    }

    when (sheet) {
        Sheet.SOURCE -> ChoiceSheet(
            title = stringResource(R.string.altitude_source),
            options = SOURCES.map { (source, label) -> source to stringResource(label) },
            selected = state.source,
            onSelect = altimeter::setSource,
            onDismiss = { sheet = null },
        )
        Sheet.ALTITUDE -> AltitudeSheet(onSave = { altimeter.calibrateToAltitude(it) }, onDismiss = { sheet = null })
        Sheet.AIRPORT -> AirportSheet(onPick = altimeter::calibrateToAirport, onDismiss = { sheet = null })
        null -> Unit
    }
}

@Composable
private fun stats(state: AltimeterState): List<Pair<String, String>> {
    val metres = @Composable { v: Double? -> v?.let { lengthText(it) } ?: NO_VALUE }
    val calibration = state.calibration
    val now = System.currentTimeMillis()
    return listOf(
        stringResource(R.string.label_barometer_altitude) to metres(state.barometerAltitude),
        stringResource(R.string.label_gnss_altitude) to (
            state.gnssAccuracy?.let { stringResource(R.string.value_with_accuracy, metres(state.gnssAltitude), accuracyText(it)) }
                ?: metres(state.gnssAltitude)
            ),
        stringResource(R.string.label_calibrated) to (
            calibration?.let { DateUtils.getRelativeTimeSpanString(it.timeMs, now, DateUtils.MINUTE_IN_MILLIS).toString() } ?: NO_VALUE
            ),
        stringResource(R.string.label_drift) to (
            calibration?.let { accuracyText(Drift.estimate(it.accuracy, now - it.timeMs)) } ?: NO_VALUE
            ),
    ) + listOfNotNull(
        calibration?.temperatureC?.let { stringResource(R.string.label_air_temperature) to stringResource(R.string.unit_celsius, signed(it, 0)) },
    )
}

/** What the dial shows, in figures, and how fast the altitude changes. */
@Composable
private fun readings(state: AltimeterState): List<Pair<String, String>> {
    val hpa = @Composable { v: Double? -> v?.let { stringResource(R.string.unit_hpa, it.decimals(1)) } ?: NO_VALUE }
    val mmhg = @Composable { v: Double? -> v?.let { stringResource(R.string.unit_mmhg, (it * Barometry.MMHG_PER_HPA).decimals(1)) } ?: NO_VALUE }
    return listOf(
        stringResource(R.string.label_altitude) to (state.altitude?.let { lengthText(it) } ?: NO_VALUE),
        stringResource(R.string.label_vertical_speed) to (
            state.verticalSpeed?.let { verticalSpeedText(it) } ?: NO_VALUE
            ),
        stringResource(R.string.label_pressure) to hpa(state.pressureHpa),
        stringResource(R.string.label_pressure_mmhg) to mmhg(state.pressureHpa),
        stringResource(R.string.label_reference_pressure) to hpa(state.referenceHpa),
        stringResource(R.string.label_reference_mmhg) to mmhg(state.referenceHpa),
    )
}

private val SOURCES = listOf(
    AltitudeSource.AUTO to R.string.source_auto,
    AltitudeSource.BAROMETER to R.string.source_barometer,
    AltitudeSource.GNSS to R.string.source_gnss,
)
