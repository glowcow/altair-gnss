package dev.glowcow.altairgnss.airports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetarTest {
    private val json = """
        [
          {"icaoId":"UUDD","obsTime":1791212400,"altim":1013,"temp":-7,"lat":55.409,"lon":37.906,"elev":165,"name":"Moscow/Domodedovo Arpt, MO, RU","clouds":[{"cover":"BKN","base":3500}]},
          {"icaoId":"UUEE","obsTime":1791212400,"altim":1012.4,"lat":55.973,"lon":37.415,"elev":186,"name":"Moscow/Sheremetyevo Intl, MO, RU"},
          {"icaoId":"XXXX","obsTime":1791212400,"lat":55.0,"lon":37.0,"name":"No pressure"},
          {"icaoId":"YYYY","obsTime":1791212400,"altim":29.92,"lat":55.0,"lon":37.0,"name":"Inches by mistake"},
          {"icaoId":"UUBC","obsTime":1791100000,"altim":1014,"lat":54.552,"lon":36.37,"name":"Kaluga Intl, KL, RU"}
        ]
    """.trimIndent()

    @Test
    fun `reads the reports and skips the unusable ones`() {
        val reports = Metar.parse(json)
        assertEquals(listOf("UUDD", "UUEE", "UUBC"), reports.map { it.icao })
        assertEquals(1013.0, reports[0].qnhHpa, 1e-9)
        assertEquals(1012.4, reports[1].qnhHpa, 1e-9)
        assertEquals(1_791_212_400_000L, reports[0].timeMs)
        assertEquals("Moscow/Domodedovo Arpt, MO, RU", reports[0].name)
        assertEquals(-7.0, reports[0].temperatureC!!, 1e-9)
        assertEquals(165.0, reports[0].elevation!!, 1e-9)
        assertEquals(null, reports[1].temperatureC)
        assertEquals(null, reports[2].elevation)
    }

    @Test
    fun `offers fresh reports, the nearest first`() {
        val now = 1_791_212_400_000L + 20 * 60_000
        val nearest = Metar.nearest(Metar.parse(json), 55.75, 37.62, 200.0, now)
        // Kaluga's report is more than a day old.
        assertEquals(listOf("UUEE", "UUDD"), nearest.map { it.report.icao })
        assertEquals(28.0, nearest[0].distanceKm, 2.0)
        assertEquals(42.0, nearest[1].distanceKm, 2.0)
        assertTrue(Metar.nearest(Metar.parse(json), 55.75, 37.62, 20.0, now).isEmpty())
    }

    @Test
    fun `a farther airport is trusted less`() {
        val report = Metar.parse(json).first()
        assertEquals(5f, NearbyAirport(report, 0.0).accuracy, 1e-4f)
        assertEquals(15f, NearbyAirport(report, 100.0).accuracy, 1e-4f)
    }

    @Test
    fun `the box is built around a rounded centre`() {
        assertEquals("54.50,34.82,57.50,40.18", Metar.box(55.7558, 37.6173, 166.8))
        assertEquals(Metar.box(55.80, 37.40, 100.0), Metar.box(55.99, 37.70, 100.0))
    }

    @Test
    fun `distance between two cities`() {
        assertEquals(634.0, Metar.distanceKm(55.7558, 37.6173, 59.9343, 30.3351), 3.0)
    }
}
