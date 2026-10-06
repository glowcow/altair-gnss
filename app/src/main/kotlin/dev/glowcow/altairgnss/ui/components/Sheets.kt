package dev.glowcow.altairgnss.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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

/** A sheet that takes one number within [range]; a comma works as the decimal point. */
@Composable
fun NumberSheet(title: String, range: ClosedFloatingPointRange<Double>, onSave: (Double) -> Unit, onDismiss: () -> Unit) =
    GroupSheet(title, onDismiss) { pick ->
        var text by rememberSaveable { mutableStateOf("") }
        val value = text.replace(',', '.').toDoubleOrNull()?.takeIf { it in range }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )
        Group { GroupRow(stringResource(R.string.save), onClick = value?.let { v -> { pick { onSave(v) } } }) }
    }

/** A sheet that takes an altitude above sea level in the chosen unit and hands it over in metres. */
@Composable
fun AltitudeSheet(onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    val unit = LocalUnits.current.length
    NumberSheet(
        title = stringResource(if (unit == LengthUnit.METRES) R.string.input_altitude else R.string.input_altitude_feet),
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
        val signed = KeyboardOptions(keyboardType = KeyboardType.Text)
        OutlinedTextField(
            value = latitude,
            onValueChange = { latitude = it },
            label = { Text(stringResource(R.string.input_latitude)) },
            singleLine = true,
            keyboardOptions = signed,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        OutlinedTextField(
            value = longitude,
            onValueChange = { longitude = it },
            label = { Text(stringResource(R.string.input_longitude)) },
            singleLine = true,
            keyboardOptions = signed,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )
        Group { GroupRow(stringResource(R.string.save), onClick = if (lat != null && lon != null) ({ pick { onSave(lat, lon) } }) else null) }
    }

// Altitudes a calibration accepts, metres.
private const val MIN_ALTITUDE = -500.0
private const val MAX_ALTITUDE = 9000.0
