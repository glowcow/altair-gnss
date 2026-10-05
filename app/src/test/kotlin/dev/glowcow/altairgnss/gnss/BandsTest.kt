package dev.glowcow.altairgnss.gnss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BandsTest {
    @Test
    fun `the same carrier is named per constellation`() {
        assertEquals("L1", Bands.name(Constellation.GPS, 1575.42e6f))
        assertEquals("E1", Bands.name(Constellation.GALILEO, 1575.42e6f))
        assertEquals("B1C", Bands.name(Constellation.BEIDOU, 1575.42e6f))
        assertEquals("L5", Bands.name(Constellation.GPS, 1176.45e6f))
        assertEquals("E5a", Bands.name(Constellation.GALILEO, 1176.45e6f))
        assertEquals("B2a", Bands.name(Constellation.BEIDOU, 1176.45e6f))
    }

    @Test
    fun `glonass channels fall into their band`() {
        assertEquals("L1", Bands.name(Constellation.GLONASS, 1598.0625e6f))
        assertEquals("L1", Bands.name(Constellation.GLONASS, 1605.375e6f))
        assertEquals("L2", Bands.name(Constellation.GLONASS, 1246.0e6f))
    }

    @Test
    fun `beidou B1I is not B1C`() {
        assertEquals("B1I", Bands.name(Constellation.BEIDOU, 1561.098e6f))
    }

    @Test
    fun `L6 exists only for qzss`() {
        assertEquals("L6", Bands.name(Constellation.QZSS, 1278.75e6f))
        assertNull(Bands.name(Constellation.GPS, 1278.75e6f))
    }

    @Test
    fun `an unknown carrier has no name`() {
        assertNull(Bands.name(Constellation.GPS, 1000e6f))
        assertNull(Bands.name(Constellation.UNKNOWN, 1575.42e6f))
    }

    @Test
    fun `a signal is labelled by system letter and number`() {
        val signal = Signal(12, Constellation.GALILEO, 30f, 45f, 90f, hasAlmanac = true, hasEphemeris = true, usedInFix = true, carrierHz = 1176.45e6f)
        assertEquals("E12", signal.label)
        assertEquals("E5a", signal.band)
        assertNull(signal.copy(carrierHz = null).band)
    }
}
