package dev.glowcow.altairgnss.data

/** What altitudes and distances are shown in; everything is kept in metres. */
enum class LengthUnit(val perMetre: Double) {
    METRES(1.0),
    FEET(3.280839895),
}

/** What speeds are shown in; everything is kept in metres per second. */
enum class SpeedUnit(val perMps: Double) {
    KMH(3.6),
    MPH(2.2369362921),
    KNOTS(1.9438444924),
}
