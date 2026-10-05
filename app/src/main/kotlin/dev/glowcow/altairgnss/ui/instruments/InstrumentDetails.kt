package dev.glowcow.altairgnss.ui.instruments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.container
import dev.glowcow.altairgnss.ui.components.Group
import dev.glowcow.altairgnss.ui.components.GroupRow
import dev.glowcow.altairgnss.ui.components.IconButton48
import dev.glowcow.altairgnss.ui.components.NO_VALUE
import dev.glowcow.altairgnss.ui.components.StatGrid
import dev.glowcow.altairgnss.ui.components.decimals
import dev.glowcow.altairgnss.ui.components.rememberScreenHeading
import dev.glowcow.altairgnss.ui.theme.AltairIcons
import dev.glowcow.altairgnss.ui.theme.AltairTheme
import java.util.Locale

/** A dial full screen with what goes with it underneath. */
@Composable
private fun DetailScreen(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = AltairTheme.colors
    Column(Modifier.fillMaxSize().background(c.groupBg).statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton48(AltairIcons.Back, stringResource(R.string.back), onClick = onBack)
            Text(title, color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** A number with its sign, except for what rounds to zero: "-0" reads as a fault. */
private fun signed(value: Float, digits: Int): String {
    val text = String.format(Locale.getDefault(), "%+.${digits}f", value)
    return if (text.none { it in '1'..'9' }) text.drop(1) else text
}

@Composable
fun CompassScreen(onBack: () -> Unit) = DetailScreen(stringResource(R.string.title_compass), onBack) {
    val c = AltairTheme.colors
    val gnss by LocalContext.current.container.gnss.state.collectAsStateWithLifecycle()
    val heading = rememberScreenHeading()
    val reading = heading.reading
    val fix = gnss.fix?.takeIf { gnss.hasFix }
    val degrees = @Composable { v: Float? -> v?.let { stringResource(R.string.unit_degrees, it.decimals(0)) } ?: NO_VALUE }
    val tilt = @Composable { v: Float? -> v?.let { stringResource(R.string.unit_degrees, signed(it, 0)) } ?: NO_VALUE }
    val field = @Composable { v: Float? -> v?.let { stringResource(R.string.unit_microtesla, it.decimals(1)) } ?: NO_VALUE }

    Group { Compass(heading.degrees, Modifier.padding(bottom = 12.dp), large = true) }
    Group {
        StatGrid(
            listOf(
                stringResource(R.string.label_heading_true) to degrees(heading.degrees?.takeIf { heading.isTrue }),
                stringResource(R.string.label_heading_magnetic) to degrees(reading?.magnetic),
                stringResource(R.string.label_declination) to (heading.declination?.let { stringResource(R.string.unit_degrees, signed(it, 1)) } ?: NO_VALUE),
                stringResource(R.string.label_course) to degrees(fix?.bearing),
                stringResource(R.string.label_pitch) to tilt(reading?.pitch),
                stringResource(R.string.label_roll) to tilt(reading?.roll),
            ),
        )
    }
    Group {
        StatGrid(
            listOf(
                stringResource(R.string.label_field) to field(reading?.fieldMicroTesla),
                stringResource(R.string.label_field_expected) to field(heading.expectedField),
                stringResource(R.string.label_inclination) to (heading.inclination?.let { stringResource(R.string.unit_degrees, signed(it, 1)) } ?: NO_VALUE),
            ),
        )
    }
    Text(stringResource(R.string.compass_field_hint), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
fun SpeedScreen(onBack: () -> Unit) = DetailScreen(stringResource(R.string.title_speed), onBack) {
    val c = AltairTheme.colors
    val monitor = LocalContext.current.container.gnss
    val gnss by monitor.state.collectAsStateWithLifecycle()
    val trip by monitor.trip.collectAsStateWithLifecycle()
    val fix = gnss.fix?.takeIf { gnss.hasFix }
    val kmh = @Composable { v: Float? -> v?.let { stringResource(R.string.unit_kmh, (it * 3.6f).decimals(1)) } ?: NO_VALUE }
    val seconds = trip.movingMs / 1000

    Group { Speedometer(fix?.speed?.let { it * 3.6f }, Modifier.padding(bottom = 12.dp), large = true) }
    Group {
        StatGrid(
            listOf(
                stringResource(R.string.label_speed_mps) to (fix?.speed?.let { stringResource(R.string.unit_mps, it.decimals(1)) } ?: NO_VALUE),
                stringResource(R.string.label_acceleration) to (trip.acceleration?.let { stringResource(R.string.unit_mps2, signed(it, 2)) } ?: NO_VALUE),
                stringResource(R.string.label_speed_max) to kmh(trip.maxSpeed.takeIf { trip.movingMs > 0 }),
                stringResource(R.string.label_speed_average) to kmh(trip.averageSpeed),
                stringResource(R.string.label_distance) to (
                    if (trip.distance < 1000) stringResource(R.string.unit_metres, trip.distance.decimals(0))
                    else stringResource(R.string.unit_km, (trip.distance / 1000).decimals(2))
                    ),
                stringResource(R.string.label_moving_time) to String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60),
                stringResource(R.string.label_course) to (fix?.bearing?.let { stringResource(R.string.unit_degrees, it.decimals(0)) } ?: NO_VALUE),
                stringResource(R.string.label_speed_accuracy) to (fix?.speedAccuracy?.let { stringResource(R.string.unit_kmh, "±" + (it * 3.6f).decimals(1)) } ?: NO_VALUE),
            ),
        )
    }
    Group { GroupRow(stringResource(R.string.trip_reset), subtitle = stringResource(R.string.trip_hint), onClick = monitor::resetTrip) }
    Text(stringResource(R.string.speed_scale_hint), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
}
