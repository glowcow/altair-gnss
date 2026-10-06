package dev.glowcow.altairgnss.altimeter

import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

enum class AltitudeSource { GNSS, BAROMETER, AUTO }

/** What a calibration rests on: averaged GNSS altitude, an altitude typed by the user, an airport's pressure report. */
enum class CalibrationKind { GNSS, ALTITUDE, AIRPORT }

/** What ties pressure to altitude: the pressure that would be measured at sea level. */
data class Calibration(
    val referenceHpa: Double,
    /** Wall-clock time it was made, milliseconds since the epoch. */
    val timeMs: Long,
    /** Error of the altitude it was made from, metres. */
    val accuracy: Float?,
    val kind: CalibrationKind,
    /** Air temperature, °C, taken at [baseAltitude] metres: an airport's report carries both. */
    val temperatureC: Double? = null,
    val baseAltitude: Double? = null,
)

/** The international barometric formula of the standard atmosphere. */
object Barometry {
    const val STANDARD_HPA = 1013.25
    const val MMHG_PER_HPA = 0.750062
    private const val SCALE = 44330.77
    private const val EXPONENT = 0.190263
    private const val KELVIN = 273.15
    // Sea-level temperature of the standard atmosphere and how fast it falls with height, K per metre.
    private const val STANDARD_KELVIN = 288.15
    private const val LAPSE = 0.0065

    /** Altitude in metres at [pressureHpa] when sea level reads [referenceHpa]. */
    fun altitude(pressureHpa: Double, referenceHpa: Double): Double =
        SCALE * (1 - (pressureHpa / referenceHpa).pow(EXPONENT))

    /**
     * Takes the standard atmosphere's temperature out of [altitude]. The formula assumes 15 °C at
     * sea level; in colder air a layer is thinner than it reckons, in warmer air thicker. So the
     * height above [base], where [celsius] was measured, is scaled by the real temperature over
     * the standard one for that level.
     */
    fun temperatureCorrected(altitude: Double, base: Double, celsius: Double): Double =
        base + (altitude - base) * (celsius + KELVIN) / (STANDARD_KELVIN - LAPSE * base)

    /** Altitude by [calibration], corrected for the air temperature where it carries one. */
    fun altitude(pressureHpa: Double, calibration: Calibration): Double {
        val standard = altitude(pressureHpa, calibration.referenceHpa)
        val celsius = calibration.temperatureC ?: return standard
        val base = calibration.baseAltitude ?: return standard
        return temperatureCorrected(standard, base, celsius)
    }

    /** Sea-level pressure that puts [pressureHpa] at [altitude] metres. */
    fun reference(pressureHpa: Double, altitude: Double): Double =
        pressureHpa / (1 - altitude / SCALE).pow(1 / EXPONENT)
}

/** Averages GNSS altitudes against the pressure read at the same moments. */
class CalibrationRun {
    private var weights = 0.0
    private var altitudes = 0.0
    private var accuracies = 0.0
    private var pressures = 0.0
    var count = 0
        private set

    fun add(altitude: Double, accuracy: Float, pressureHpa: Double) {
        // A fix that claims a smaller error counts for more.
        val weight = 1.0 / (accuracy * accuracy).coerceAtLeast(0.01f)
        weights += weight
        altitudes += weight * altitude
        accuracies += weight * accuracy
        pressures += pressureHpa
        count++
    }

    /**
     * Reference pressure and the error of the altitude behind it, or null with too few fixes.
     * Errors of consecutive fixes move together, so averaging is not credited with shrinking them.
     */
    fun result(): Pair<Double, Float>? {
        if (count < MIN_FIXES) return null
        return Barometry.reference(pressures / count, altitudes / weights) to (accuracies / weights).toFloat()
    }

    companion object {
        const val MIN_FIXES = 5
    }
}

object Drift {
    /** How fast weather usually moves a barometric altitude, metres per hour. */
    const val METRES_PER_HOUR = 1.5

    /** Expected error of a barometric altitude [elapsedMs] after a calibration of [accuracy] metres. */
    fun estimate(accuracy: Float?, elapsedMs: Long): Float {
        val drift = METRES_PER_HOUR * elapsedMs.coerceAtLeast(0) / 3_600_000.0
        val start = (accuracy ?: 0f).toDouble()
        return sqrt(start * start + drift * drift).toFloat()
    }
}

object Blend {
    /** Seconds over which the barometer's reference follows GNSS. */
    const val TAU_SECONDS = 120.0

    /** Moves [reference] towards the one GNSS implies now; short-term changes stay barometric. */
    fun step(reference: Double?, target: Double, seconds: Double): Double =
        if (reference == null) target else reference + (target - reference) * min(1.0, seconds / TAU_SECONDS)
}

/** Exponential smoothing of an unevenly sampled value. */
class Smoother(private val tauSeconds: Double) {
    private var value: Double? = null
    private var timeNanos = 0L

    fun update(sample: Double, sampleNanos: Long): Double {
        val previous = value
        val next = if (previous == null) sample else {
            val dt = (sampleNanos - timeNanos).coerceAtLeast(0) / 1e9
            previous + (sample - previous) * (1 - exp(-dt / tauSeconds))
        }
        value = next
        timeNanos = sampleNanos
        return next
    }
}

/** How fast the altitude changes, smoothed: metres per second, climbing is positive. */
class VerticalSpeed(private val tauSeconds: Double = 3.0) {
    private var altitude = 0.0
    private var timeNanos = 0L
    private var rate: Double? = null
    private var primed = false

    /** Takes one more altitude; null until two samples close enough in time have come. */
    fun update(sample: Double, sampleNanos: Long): Double? {
        val dt = (sampleNanos - timeNanos) / 1e9
        if (!primed || dt > MAX_GAP_SECONDS) {
            // After a pause the old altitude says nothing about the rate now.
            rate = null
        } else if (dt > 0) {
            val instant = (sample - altitude) / dt
            rate = rate?.let { it + (instant - it) * (1 - exp(-dt / tauSeconds)) } ?: instant
        } else {
            return rate
        }
        primed = true
        altitude = sample
        timeNanos = sampleNanos
        return rate
    }

    fun reset() {
        primed = false
        rate = null
    }

    private companion object {
        const val MAX_GAP_SECONDS = 5.0
    }
}
