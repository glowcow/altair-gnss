package dev.glowcow.altairgnss.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.data.LengthUnit

/** A sheet that takes one number within [range] into a field named [label]; a comma works as the decimal point. */
@Composable
fun NumberSheet(title: String, label: String, range: ClosedFloatingPointRange<Double>, onSave: (Double) -> Unit, onDismiss: () -> Unit) =
    GroupSheet(title, onDismiss) { pick ->
        var text by rememberSaveable { mutableStateOf("") }
        val value = text.replace(',', '.').toDoubleOrNull()?.takeIf { it in range }
        Group { GroupField(label, text, { text = it }, keyboard = KeyboardType.Decimal, focused = true) }
        Spacer(Modifier.height(12.dp))
        Group { GroupRow(stringResource(R.string.save), onClick = value?.let { v -> { pick { onSave(v) } } }) }
    }

/** A sheet that takes an altitude above sea level in the chosen unit and hands it over in metres. */
@Composable
fun AltitudeSheet(onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    val unit = LocalUnits.current.length
    NumberSheet(
        title = stringResource(if (unit == LengthUnit.METRES) R.string.input_altitude else R.string.input_altitude_feet),
        label = stringResource(R.string.label_altitude),
        range = (MIN_ALTITUDE * unit.perMetre)..(MAX_ALTITUDE * unit.perMetre),
        onSave = { onSave(it / unit.perMetre) },
        onDismiss = onDismiss,
    )
}

/** A sheet that takes a latitude and a longitude in decimal degrees. */
@Composable
fun CoordinatesSheet(onSave: (latitude: Double, longitude: Double) -> Unit, onDismiss: () -> Unit) =
    GroupSheet(stringResource(R.string.input_coordinates), onDismiss) { pick ->
        var latitude by rememberSaveable { mutableStateOf("") }
        var longitude by rememberSaveable { mutableStateOf("") }
        fun parse(text: String, limit: Double) = text.replace(',', '.').toDoubleOrNull()?.takeIf { it in -limit..limit }
        val lat = parse(latitude, 90.0)
        val lon = parse(longitude, 180.0)
        // A minus sign is not on every number pad, so these take the full keyboard.
        Group {
            GroupField(stringResource(R.string.input_latitude), latitude, { latitude = it }, focused = true)
            GroupDivider()
            GroupField(stringResource(R.string.input_longitude), longitude, { longitude = it })
        }
        Spacer(Modifier.height(12.dp))
        Group { GroupRow(stringResource(R.string.save), onClick = if (lat != null && lon != null) ({ pick { onSave(lat, lon) } }) else null) }
    }

// Altitudes a calibration accepts, metres.
private const val MIN_ALTITUDE = -500.0
private const val MAX_ALTITUDE = 9000.0
