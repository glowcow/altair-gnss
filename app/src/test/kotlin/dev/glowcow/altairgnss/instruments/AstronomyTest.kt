package dev.glowcow.altairgnss.instruments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs

class AstronomyTest {
    private fun assertNear(expected: String, actual: Instant?, minutes: Long = 6) {
        val want = Instant.parse(expected)
        assertTrue("expected $expected, got $actual", actual != null && abs(actual.epochSecond - want.epochSecond) <= minutes * 60)
    }

    @Test
    fun `greenwich at the march equinox`() {
        val sun = Sun.times(LocalDate.of(2026, 3, 20), 51.4779, 0.0)
        assertNear("2026-03-20T06:04:00Z", sun.rise)
        assertNear("2026-03-20T18:13:00Z", sun.set)
        assertNear("2026-03-20T12:07:00Z", sun.noon)
    }

    @Test
    fun `moscow at the june solstice`() {
        val sun = Sun.times(LocalDate.of(2026, 6, 21), 55.7558, 37.6173)
        assertNear("2026-06-21T00:44:00Z", sun.rise)
        assertNear("2026-06-21T18:18:00Z", sun.set)
    }

    @Test
    fun `the sun does not cross the horizon beyond the polar circle`() {
        val summer = Sun.times(LocalDate.of(2026, 6, 21), 69.65, 18.96)
        assertNull(summer.rise)
        assertTrue(summer.alwaysUp)
        val winter = Sun.times(LocalDate.of(2026, 12, 21), 69.65, 18.96)
        assertNull(winter.set)
        assertFalse(winter.alwaysUp)
    }

    @Test
    fun `days to the end of a polar day and night in tromso`() {
        assertNull(Sun.daysUntilCrossing(LocalDate.of(2026, 3, 20), 69.65, 18.96))
        val toSunset = Sun.daysUntilCrossing(LocalDate.of(2026, 6, 21), 69.65, 18.96)!!
        // The midnight sun lasts there until 26 July by the sun's upper edge.
        assertTrue("sunset in $toSunset days", toSunset in 33..37)
        val toSunrise = Sun.daysUntilCrossing(LocalDate.of(2026, 12, 21), 69.65, 18.96)!!
        assertTrue("sunrise in $toSunrise days", toSunrise in 22..28)
    }

    @Test
    fun `the sun stands over greenwich at its noon and over the equator at the equinox`() {
        val noon = Sun.subsolar(Instant.parse("2026-03-20T12:07:30Z").toEpochMilli())
        assertEquals(0.0, noon.longitude, 0.5)
        assertEquals(0.0, noon.latitude, 0.5)
        val morning = Sun.subsolar(Instant.parse("2026-03-20T09:00:00Z").toEpochMilli())
        assertEquals(47.0, morning.longitude, 1.0)
        val solstice = Sun.subsolar(Instant.parse("2026-06-21T12:00:00Z").toEpochMilli())
        assertEquals(23.44, solstice.latitude, 0.1)
    }

    @Test
    fun `day and night around the subsolar point`() {
        val sun = SubsolarPoint(0.0, 45.0)
        assertEquals(1.0, Sun.elevationSine(0.0, 45.0, sun), 1e-9)
        assertEquals(-1.0, Sun.elevationSine(0.0, -135.0, sun), 1e-9)
        assertEquals(0.0, Sun.elevationSine(0.0, 135.0, sun), 1e-9)
        assertTrue(Sun.elevationSine(60.0, 45.0, sun) > 0)
    }

    @Test
    fun `moon phases follow the mean month`() {
        assertEquals(MoonPhase.NEW, Moon.phase(Moon.NEW_MOON_MS))
        assertEquals(0.0, Moon.illumination(Moon.NEW_MOON_MS), 1e-9)
        val halfMonth = (Moon.SYNODIC_DAYS / 2 * 86_400_000).toLong()
        assertEquals(MoonPhase.FULL, Moon.phase(Moon.NEW_MOON_MS + halfMonth))
        assertEquals(MoonPhase.FIRST_QUARTER, Moon.phase(Moon.NEW_MOON_MS + halfMonth / 2))
        assertEquals(MoonPhase.LAST_QUARTER, Moon.phase(Moon.NEW_MOON_MS + halfMonth * 3 / 2))
    }

    @Test
    fun `a real full moon is found`() {
        val full = Instant.parse("2024-10-17T11:26:00Z").toEpochMilli()
        assertEquals(MoonPhase.FULL, Moon.phase(full))
        assertTrue(Moon.illumination(full) > 0.98)
    }

    @Test
    fun `compass points and turns`() {
        assertEquals(0, Heading.point(350f))
        assertEquals(0, Heading.point(22f))
        assertEquals(1, Heading.point(23f))
        assertEquals(4, Heading.point(180f))
        assertEquals(7, Heading.point(-45f))
        assertEquals(20f, Heading.turn(350f, 10f), 1e-4f)
        assertEquals(-20f, Heading.turn(10f, 350f), 1e-4f)
        assertEquals(5f, Heading.normalize(365f), 1e-4f)
        assertEquals(355f, Heading.normalize(-5f), 1e-4f)
    }
}
