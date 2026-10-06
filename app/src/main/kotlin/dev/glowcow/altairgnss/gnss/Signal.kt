package dev.glowcow.altairgnss.gnss

import kotlin.math.abs

/** Satellite systems in the order they are listed; [letter] is the RINEX system code. */
enum class Constellation(val letter: Char, val title: String) {
    GPS('G', "GPS"),
    GLONASS('R', "GLONASS"),
    GALILEO('E', "Galileo"),
    BEIDOU('C', "BeiDou"),
    QZSS('J', "QZSS"),
    IRNSS('I', "NavIC"),
    SBAS('S', "SBAS"),
    UNKNOWN('?', "?"),
}

/** One tracked signal: a satellite on one carrier. */
data class Signal(
    val svid: Int,
    val constellation: Constellation,
    val cn0DbHz: Float,
    val elevation: Float,
    val azimuth: Float,
    val hasAlmanac: Boolean,
    val hasEphemeris: Boolean,
    val usedInFix: Boolean,
    val carrierHz: Float? = null,
) {
    val label: String get() = "${constellation.letter}$svid"

    /** False while the receiver tracks the signal without knowing where its satellite is: both angles are zero then. */
    val hasPosition: Boolean get() = elevation != 0f || azimuth != 0f

    /** False for a satellite the receiver only expects, with no signal measured. */
    val isHeard: Boolean get() = cn0DbHz > 0f
    val band: String? get() = carrierHz?.let { Bands.name(constellation, it) }
}

object Bands {
    /** Name of the band a carrier frequency belongs to, e.g. L1, E5a, B2a. */
    fun name(constellation: Constellation, carrierHz: Float): String? {
        val mhz = carrierHz / 1e6f
        fun near(f: Float) = abs(mhz - f) < 1.5f
        return when (constellation) {
            Constellation.GPS, Constellation.QZSS -> when {
                near(1575.42f) -> "L1"
                near(1227.6f) -> "L2"
                near(1176.45f) -> "L5"
                constellation == Constellation.QZSS && near(1278.75f) -> "L6"
                else -> null
            }
            // FDMA: every satellite has its own channel around the band centre.
            Constellation.GLONASS -> when {
                mhz in 1597f..1610f -> "L1"
                mhz in 1241f..1253f -> "L2"
                near(1202.025f) -> "L3"
                else -> null
            }
            Constellation.GALILEO -> when {
                near(1575.42f) -> "E1"
                near(1176.45f) -> "E5a"
                near(1207.14f) -> "E5b"
                near(1191.795f) -> "E5"
                near(1278.75f) -> "E6"
                else -> null
            }
            Constellation.BEIDOU -> when {
                near(1561.098f) -> "B1I"
                near(1575.42f) -> "B1C"
                near(1176.45f) -> "B2a"
                near(1207.14f) -> "B2b"
                near(1268.52f) -> "B3I"
                else -> null
            }
            Constellation.IRNSS -> when {
                near(1575.42f) -> "L1"
                near(1176.45f) -> "L5"
                near(2492.028f) -> "S"
                else -> null
            }
            Constellation.SBAS -> when {
                near(1575.42f) -> "L1"
                near(1176.45f) -> "L5"
                else -> null
            }
            Constellation.UNKNOWN -> null
        }
    }
}
