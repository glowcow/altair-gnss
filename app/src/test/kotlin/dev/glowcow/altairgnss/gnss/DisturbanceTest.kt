package dev.glowcow.altairgnss.gnss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisturbanceTest {
    private fun signal(svid: Int, cn0: Float, used: Boolean = false, system: Constellation = Constellation.GPS) =
        Signal(svid, system, cn0, 30f, 100f, hasAlmanac = true, hasEphemeris = false, usedInFix = used, carrierHz = 1575.42e6f)

    // Eleven GPS signals within three decibels, as a transmitter makes them.
    private val faked = (1..11).map { signal(it, 43f + it % 4) }

    // A real sky: strengths far apart.
    private val sky = listOf(48f, 45f, 41f, 38f, 36f, 31f, 27f, 22f).mapIndexed { i, cn0 -> signal(i + 1, cn0) }

    @Test
    fun signalsOfOneStrengthLookFaked() {
        assertEquals(Disturbance.SPOOFING, DisturbanceWatch.suspect(faked))
        assertEquals(Disturbance.INTERFERENCE, DisturbanceWatch.suspect(sky))
        assertEquals(Disturbance.NONE, DisturbanceWatch.suspect(sky.drop(3)))
        // Two systems at one strength are two groups of too few.
        assertEquals(Disturbance.NONE, DisturbanceWatch.suspect(faked.take(6).map { it.copy(cn0DbHz = 30f) } + faked.take(6).map { it.copy(cn0DbHz = 30f, constellation = Constellation.GALILEO) }))
    }

    @Test
    fun aSuspicionHasToLastAndSurvivesARestart() {
        val watch = DisturbanceWatch()
        assertEquals(Disturbance.NONE, watch.update(faked, 0))
        assertEquals(Disturbance.NONE, watch.update(faked, 20_000))
        // The receiver drops everything for a few seconds and hears the same again.
        assertEquals(Disturbance.NONE, watch.update(emptyList(), 25_000))
        assertEquals(Disturbance.SPOOFING, watch.update(faked, 31_000))
        // A fix clears it at once.
        assertEquals(Disturbance.NONE, watch.update(faked.map { it.copy(usedInFix = true) }, 32_000))
    }

    @Test
    fun strongSignalsWithoutAFixAreInterferenceAfterAMinute() {
        val watch = DisturbanceWatch()
        watch.update(sky, 0)
        assertEquals(Disturbance.NONE, watch.update(sky, 40_000))
        assertEquals(Disturbance.INTERFERENCE, watch.update(sky, 61_000))
        // Quiet for longer than a suspicion is held: it starts over.
        assertEquals(Disturbance.NONE, watch.update(emptyList(), 90_000))
        assertEquals(Disturbance.NONE, watch.update(sky, 91_000))
    }

    @Test
    fun aGainFarUnderTheQuietOneIsJamming() {
        val gains = GainWatch()
        assertFalse(gains.update(mapOf("1:1575" to 40.0), fixed = true))
        assertFalse(gains.update(mapOf("1:1575" to 38.5), fixed = false))
        assertTrue(gains.update(mapOf("1:1575" to 33.0), fixed = false))
        // A jammed gain does not become the quiet one, even with a fix.
        assertTrue(gains.update(mapOf("1:1575" to 33.0), fixed = true))
        // The gain says so whatever the signals are.
        assertEquals(Disturbance.INTERFERENCE, DisturbanceWatch().update(emptyList(), 0, jammed = true))
    }
}
