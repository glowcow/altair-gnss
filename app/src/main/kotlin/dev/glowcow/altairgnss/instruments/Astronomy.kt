package dev.glowcow.altairgnss.instruments

import java.time.Instant
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin

/** The sun on one date at one place; [rise] and [set] are null when it does not cross the horizon. */
data class SunTimes(val rise: Instant?, val noon: Instant, val set: Instant?, val alwaysUp: Boolean = false)

/** The point on the Earth the sun stands over, degrees. */
data class SubsolarPoint(val latitude: Double, val longitude: Double)

object Sun {
    private const val J2000 = 2451545.0
    private const val UNIX_EPOCH_JD = 2440587.5
    private const val OBLIQUITY = 23.4397
    // The sun's upper edge on the horizon, with the usual allowance for refraction.
    private const val HORIZON = -0.833
    private const val YEAR_DAYS = 366

    private fun rad(degrees: Double) = degrees * PI / 180
    private fun instant(julianDate: Double) = Instant.ofEpochMilli(((julianDate - UNIX_EPOCH_JD) * 86_400_000).roundToLong())

    /** The sun's declination in radians and how many days true noon runs after mean noon, [days] after J2000. */
    private fun orbit(days: Double): Pair<Double, Double> {
        val anomaly = (357.5291 + 0.98560028 * days).mod(360.0)
        val centre = 1.9148 * sin(rad(anomaly)) + 0.02 * sin(rad(2 * anomaly)) + 0.0003 * sin(rad(3 * anomaly))
        val ecliptic = (anomaly + centre + 180 + 102.9372).mod(360.0)
        return asin(sin(rad(ecliptic)) * sin(rad(OBLIQUITY))) to 0.0053 * sin(rad(anomaly)) - 0.0069 * sin(rad(2 * ecliptic))
    }

    /** The sunrise equation; accurate to a couple of minutes away from the polar circles. */
    fun times(date: LocalDate, latitude: Double, longitude: Double): SunTimes {
        val days = date.toEpochDay() + 2440588 - J2000 + 0.0008 - longitude / 360
        val (declination, delay) = orbit(days)
        val transit = J2000 + days + delay
        val cosHour = (sin(rad(HORIZON)) - sin(rad(latitude)) * sin(declination)) / (cos(rad(latitude)) * cos(declination))
        if (cosHour > 1) return SunTimes(null, instant(transit), null)
        if (cosHour < -1) return SunTimes(null, instant(transit), null, alwaysUp = true)
        val hour = acos(cosHour) * 180 / PI / 360
        return SunTimes(instant(transit - hour), instant(transit), instant(transit + hour))
    }

    /**
     * Days from [date] to the first one on which the sun crosses the horizon again: the end of a
     * polar day or night. Null if it crosses it on [date] itself, or never within a year.
     */
    fun daysUntilCrossing(date: LocalDate, latitude: Double, longitude: Double): Int? {
        if (times(date, latitude, longitude).rise != null) return null
        return (1..YEAR_DAYS).firstOrNull { times(date.plusDays(it.toLong()), latitude, longitude).rise != null }
    }

    fun subsolar(timeMs: Long): SubsolarPoint {
        val days = timeMs / 86_400_000.0 + UNIX_EPOCH_JD - J2000
        val (declination, delay) = orbit(days)
        // Julian days turn over at noon in Greenwich, so the fraction is the time since true noon there.
        val sinceNoon = (days - delay).let { it - Math.rint(it) }
        return SubsolarPoint(declination * 180 / PI, -360 * sinceNoon)
    }

    /** Sine of the sun's height above the horizon at a place: positive by day, negative by night. */
    fun elevationSine(latitude: Double, longitude: Double, sun: SubsolarPoint): Double =
        sin(rad(latitude)) * sin(rad(sun.latitude)) + cos(rad(latitude)) * cos(rad(sun.latitude)) * cos(rad(longitude - sun.longitude))
}

/** Eight phases in the order the moon goes through them. */
enum class MoonPhase { NEW, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS, FULL, WANING_GIBBOUS, LAST_QUARTER, WANING_CRESCENT }

object Moon {
    const val SYNODIC_DAYS = 29.530588853
    // The new moon of 6 January 2000, 18:14 UTC.
    const val NEW_MOON_MS = 947_182_440_000L

    /** Days since the last new moon by the mean month; the true moon wanders within half a day of it. */
    fun age(timeMs: Long): Double = ((timeMs - NEW_MOON_MS) / 86_400_000.0).mod(SYNODIC_DAYS)

    fun phase(timeMs: Long): MoonPhase = MoonPhase.entries[floor(age(timeMs) / SYNODIC_DAYS * 8 + 0.5).toInt() % 8]

    /** Lit part of the disc, 0..1. */
    fun illumination(timeMs: Long): Double = (1 - cos(2 * PI * age(timeMs) / SYNODIC_DAYS)) / 2
}

object Heading {
    /** Brings an angle into 0..360. */
    fun normalize(degrees: Float): Float = degrees.mod(360f)

    /** Index of the nearest of the eight compass points, north first, clockwise. */
    fun point(degrees: Float): Int = floor(normalize(degrees) / 45f + 0.5f).toInt() % 8

    /** The shortest turn from [from] to [to], -180..180, so a dial never spins the long way round. */
    fun turn(from: Float, to: Float): Float = (to - from + 540f).mod(360f) - 180f
}
