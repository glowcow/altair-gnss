package dev.glowcow.altairgnss.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.data.LengthUnit
import dev.glowcow.altairgnss.data.SpeedUnit

/** The units chosen in the settings. Values travel in metres and metres per second and are converted where shown. */
data class Units(val length: LengthUnit = LengthUnit.METRES, val speed: SpeedUnit = SpeedUnit.KMH)

val LocalUnits = staticCompositionLocalOf { Units() }

/** The format of a length with its unit: "%s m" or "%s ft". */
@Composable
fun lengthFormat(): String = stringResource(if (LocalUnits.current.length == LengthUnit.METRES) R.string.unit_metres else R.string.unit_feet)

@Composable
fun lengthText(metres: Double, digits: Int = 1): String = String.format(lengthFormat(), (metres * LocalUnits.current.length.perMetre).decimals(digits))

/** A height from a zero, with its sign. */
@Composable
fun signedLengthText(metres: Double, digits: Int = 1): String = String.format(lengthFormat(), signed(metres * LocalUnits.current.length.perMetre, digits))

@Composable
fun accuracyText(metres: Float): String = "±" + lengthText(metres.toDouble())

/** Metres up to a kilometre and kilometres beyond; feet up to a thousand and miles beyond. */
@Composable
fun distanceText(metres: Double, digits: Int = 2): String = when (LocalUnits.current.length) {
    LengthUnit.METRES -> if (metres < 1000) lengthText(metres, 0) else stringResource(R.string.unit_km, (metres / 1000).decimals(digits))
    LengthUnit.FEET -> if (metres < FEET_SHOWN) lengthText(metres, 0) else stringResource(R.string.unit_miles, (metres / METRES_PER_MILE).decimals(digits))
}

@Composable
fun speedText(mps: Float, digits: Int = 1): String = LocalUnits.current.speed.let { unit ->
    stringResource(
        when (unit) {
            SpeedUnit.KMH -> R.string.unit_kmh
            SpeedUnit.MPH -> R.string.unit_mph
            SpeedUnit.KNOTS -> R.string.unit_knots
        },
        (mps * unit.perMps).toFloat().decimals(digits),
    )
}

/** The speed unit alone, for the face of a dial. */
@Composable
fun speedLabel(): String = stringResource(
    when (LocalUnits.current.speed) {
        SpeedUnit.KMH -> R.string.unit_kmh_label
        SpeedUnit.MPH -> R.string.unit_mph_label
        SpeedUnit.KNOTS -> R.string.unit_knots_label
    },
)

/** Metres per second, or feet per minute as altimeters in feet have it; climbing is positive. */
@Composable
fun verticalSpeedText(mps: Double): String = when (LocalUnits.current.length) {
    LengthUnit.METRES -> stringResource(R.string.unit_mps, signed(mps, 1))
    LengthUnit.FEET -> stringResource(R.string.unit_fpm, signed(mps * LengthUnit.FEET.perMetre * 60, 0))
}

private const val METRES_PER_MILE = 1609.344

// A thousand feet, in metres.
private const val FEET_SHOWN = 304.8
