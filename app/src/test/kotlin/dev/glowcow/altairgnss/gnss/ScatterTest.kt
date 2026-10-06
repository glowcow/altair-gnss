package dev.glowcow.altairgnss.gnss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScatterTest {
    private val here = GeoPoint(55.0, 37.0, 150.0)

    @Test
    fun offsetToTheNorthAndEast() {
        // A degree of latitude is 111.2 km; a degree of longitude shrinks with the cosine of the latitude.
        val north = Scatter.offset(GeoPoint(55.001, 37.0, 152.0), here)
        assertEquals(111.19, north.north, 0.05)
        assertEquals(0.0, north.east, 1e-6)
        assertEquals(2.0, north.up!!, 1e-9)
        assertEquals(0.0, north.bearing, 1e-6)
        val east = Scatter.offset(GeoPoint(55.0, 37.001), here)
        assertEquals(63.78, east.east, 0.05)
        assertEquals(90.0, east.bearing, 1e-6)
        assertNull(east.up)
    }

    @Test
    fun meanOfFixes() {
        val mean = Scatter.mean(listOf(GeoPoint(55.0, 37.0, 100.0), GeoPoint(55.002, 37.004), GeoPoint(55.001, 37.002, 200.0)))!!
        assertEquals(55.001, mean.latitude, 1e-9)
        assertEquals(37.002, mean.longitude, 1e-9)
        assertEquals(150.0, mean.altitude!!, 1e-9)
        assertNull(Scatter.mean(emptyList()))
    }

    @Test
    fun statsOfDistances() {
        // Twenty fixes 1..20 m north of the point.
        val offsets = (1..20).map { Offset3(0.0, it.toDouble(), if (it % 2 == 0) 1.0 else -1.0) }
        val stats = Scatter.stats(offsets)!!
        assertEquals(20, stats.count)
        assertEquals(10.5, stats.mean, 1e-9)
        assertEquals(10.0, stats.cep50, 1e-9)
        assertEquals(19.0, stats.cep95, 1e-9)
        assertEquals(20.0, stats.max, 1e-9)
        assertEquals(1.0, stats.verticalSpread!!, 1e-9)
        assertNull(Scatter.stats(emptyList()))
    }
}
