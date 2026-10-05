package dev.glowcow.altairgnss.gnss

import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinatesTest {
    @Test
    fun `decimal degrees carry the hemisphere instead of a sign`() {
        assertEquals("55.751244° N", Coordinates.latitude(55.751244, CoordinateFormat.DD))
        assertEquals("33.868800° S", Coordinates.latitude(-33.8688, CoordinateFormat.DD))
        assertEquals("82.413900° W", Coordinates.longitude(-82.4139, CoordinateFormat.DD))
    }

    @Test
    fun `degrees and decimal minutes`() {
        assertEquals("55° 45.0746′ N", Coordinates.latitude(55.751244, CoordinateFormat.DDM))
        assertEquals("37° 37.1040′ E", Coordinates.longitude(37.6184, CoordinateFormat.DDM))
    }

    @Test
    fun `degrees, minutes and seconds`() {
        assertEquals("55° 45′ 04.48″ N", Coordinates.latitude(55.751244, CoordinateFormat.DMS))
    }

    @Test
    fun `rounding rolls over to the next unit`() {
        assertEquals("11° 00.0000′ E", Coordinates.longitude(10.9999999, CoordinateFormat.DDM))
        assertEquals("11° 00′ 00.00″ E", Coordinates.longitude(10.9999999, CoordinateFormat.DMS))
    }
}
