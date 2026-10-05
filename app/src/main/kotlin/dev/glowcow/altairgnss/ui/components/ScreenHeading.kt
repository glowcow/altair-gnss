package dev.glowcow.altairgnss.ui.components

import android.hardware.GeomagneticField
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glowcow.altairgnss.container
import dev.glowcow.altairgnss.data.AppSettings
import dev.glowcow.altairgnss.instruments.CompassReading
import dev.glowcow.altairgnss.instruments.Heading
import kotlin.math.roundToInt

/** Where the top of the screen points, and what the Earth's field should be like here. */
data class ScreenHeading(
    /** Degrees from north; null until the sensor reports, and always on a phone without one. */
    val degrees: Float?,
    /** From true north; magnetic if the user prefers it, or until a position is known for the correction. */
    val isTrue: Boolean,
    val reliable: Boolean,
    val available: Boolean,
    val reading: CompassReading?,
    /** Degrees true north lies east of magnetic north here. */
    val declination: Float?,
    /** Degrees the field dips below the horizon here. */
    val inclination: Float?,
    /** Strength of the Earth's field here by the world magnetic model, microtesla. */
    val expectedField: Float?,
)

@Composable
fun rememberScreenHeading(): ScreenHeading {
    val container = LocalContext.current.container
    val reading by container.compass.reading.collectAsStateWithLifecycle(null)
    val gnss by container.gnss.state.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    // The last known position is good enough: the field changes over hundreds of kilometres.
    val position = gnss.fix
    val model = remember(position?.latitude?.roundToInt(), position?.longitude?.roundToInt()) {
        position?.let { GeomagneticField(it.latitude.toFloat(), it.longitude.toFloat(), (it.altitude ?: 0.0).toFloat(), System.currentTimeMillis()) }
    }
    return ScreenHeading(
        // Magnetic north is the user's choice, or all there is before a position is known.
        degrees = reading?.let { Heading.normalize(it.magnetic + (model?.declination?.takeIf { settings.trueNorth } ?: 0f)) },
        isTrue = settings.trueNorth && model != null,
        reliable = reading?.reliable != false,
        available = container.compass.available,
        reading = reading,
        declination = model?.declination,
        inclination = model?.inclination,
        expectedField = model?.let { it.fieldStrength / 1000f },
    )
}

/** [heading] as an angle that is not wrapped at 360, so a dial follows it the short way through north. */
@Composable
fun animatedTurn(heading: Float?): Float {
    val turn = remember { Animatable(heading ?: 0f) }
    LaunchedEffect(heading) {
        if (heading != null) turn.animateTo(turn.value + Heading.turn(Heading.normalize(turn.value), heading), tween(TURN_MS))
    }
    return turn.value
}

private const val TURN_MS = 120
