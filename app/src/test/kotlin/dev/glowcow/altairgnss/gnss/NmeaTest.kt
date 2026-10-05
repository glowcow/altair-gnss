package dev.glowcow.altairgnss.gnss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NmeaTest {
    private fun sentence(body: String) = "$" + body + "*" + "%02X".format(Nmea.checksum(body))

    @Test
    fun `reads dop from a gsa sentence`() {
        assertEquals(Dop(2.5f, 1.3f, 2.1f), Nmea.dop("\$GPGSA,A,3,04,05,,09,12,,,24,,,,,2.5,1.3,2.1*39"))
    }

    @Test
    fun `accepts any talker, a trailing system id and line ends`() {
        assertEquals(Dop(1.2f, 0.7f, 1.0f), Nmea.dop(sentence("GNGSA,A,3,01,02,03,04,05,06,07,08,09,10,11,12,1.2,0.7,1.0,1") + "\r\n"))
    }

    @Test
    fun `rejects a bad checksum`() {
        assertNull(Nmea.dop("\$GPGSA,A,3,04,05,,09,12,,,24,,,,,2.5,1.3,2.1*38"))
    }

    @Test
    fun `ignores other sentences and empty dop fields`() {
        assertNull(Nmea.dop(sentence("GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,")))
        assertNull(Nmea.dop(sentence("GNGSA,A,1,,,,,,,,,,,,,,,")))
        assertNull(Nmea.dop(""))
    }
}
