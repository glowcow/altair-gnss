package dev.glowcow.altairgnss.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {
    @Test
    fun lengths() {
        assertEquals(3280.84, 1000 * LengthUnit.FEET.perMetre, 0.01)
        assertEquals(1000.0, 1000 * LengthUnit.METRES.perMetre, 0.0)
    }

    @Test
    fun speeds() {
        assertEquals(36.0, 10 * SpeedUnit.KMH.perMps, 1e-9)
        assertEquals(22.369, 10 * SpeedUnit.MPH.perMps, 0.001)
        assertEquals(19.438, 10 * SpeedUnit.KNOTS.perMps, 0.001)
    }
}
