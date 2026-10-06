package dev.glowcow.altairgnss.ui.instruments

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.container
import dev.glowcow.altairgnss.ui.components.DetailScreen
import dev.glowcow.altairgnss.ui.components.Group
import dev.glowcow.altairgnss.ui.components.GroupRow
import dev.glowcow.altairgnss.ui.components.NO_VALUE
import dev.glowcow.altairgnss.ui.components.StatGrid
import dev.glowcow.altairgnss.ui.components.clock
import dev.glowcow.altairgnss.ui.components.decimals
import dev.glowcow.altairgnss.ui.components.distanceText
import dev.glowcow.altairgnss.ui.components.speedText
import dev.glowcow.altairgnss.ui.components.rememberScreenHeading
import dev.glowcow.altairgnss.ui.components.signed
import dev.glowcow.altairgnss.ui.theme.AltairTheme

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
    val speed = @Composable { v: Float? -> v?.let { speedText(it) } ?: NO_VALUE }

    Group { Speedometer(fix?.speed, Modifier.padding(bottom = 12.dp), large = true) }
    Group {
        StatGrid(
            listOf(
                stringResource(R.string.label_speed_mps) to (fix?.speed?.let { stringResource(R.string.unit_mps, it.decimals(1)) } ?: NO_VALUE),
                stringResource(R.string.label_acceleration) to (trip.acceleration?.let { stringResource(R.string.unit_mps2, signed(it, 2)) } ?: NO_VALUE),
                stringResource(R.string.label_speed_max) to speed(trip.maxSpeed.takeIf { trip.movingMs > 0 }),
                stringResource(R.string.label_speed_average) to speed(trip.averageSpeed),
                stringResource(R.string.label_distance) to distanceText(trip.distance),
                stringResource(R.string.label_moving_time) to clock(trip.movingMs),
                stringResource(R.string.label_course) to (fix?.bearing?.let { stringResource(R.string.unit_degrees, it.decimals(0)) } ?: NO_VALUE),
                stringResource(R.string.label_speed_accuracy) to (fix?.speedAccuracy?.let { "±" + speedText(it) } ?: NO_VALUE),
            ),
        )
    }
    Group { GroupRow(stringResource(R.string.trip_reset), subtitle = stringResource(R.string.trip_hint), onClick = monitor::resetTrip) }
    Text(stringResource(R.string.speed_scale_hint), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
}
