package dev.glowcow.altairgnss.instruments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripTest {
    @Test
    fun `a steady run adds distance, time and an average`() {
        val trip = Trip()
        var stats = TripStats()
        for (second in 0..10) stats = trip.update(5f, second * 1000L)
        assertEquals(50.0, stats.distance, 1e-6)
        assertEquals(10_000L, stats.movingMs)
        assertEquals(5f, stats.averageSpeed!!, 1e-4f)
        assertEquals(5f, stats.maxSpeed, 1e-4f)
        assertEquals(0f, stats.acceleration!!, 1e-4f)
    }

    @Test
    fun `standing still is not travel`() {
        val trip = Trip()
        var stats = TripStats()
        for (second in 0..10) stats = trip.update(0.2f, second * 1000L)
        assertEquals(0.0, stats.distance, 1e-9)
        assertNull(stats.averageSpeed)
    }

    @Test
    fun `acceleration follows a speeding up and the maximum is kept`() {
        val trip = Trip()
        trip.update(0f, 0)
        var stats = TripStats()
        for (second in 1..20) stats = trip.update(second * 2f, second * 1000L)
        assertEquals(2f, stats.acceleration!!, 1e-3f)
        stats = trip.update(10f, 21_000L)
        assertEquals(40f, stats.maxSpeed, 1e-4f)
    }

    @Test
    fun `a gap or a fix without speed breaks the chain`() {
        val trip = Trip()
        trip.update(5f, 0)
        trip.update(5f, 1000)
        var stats = trip.update(5f, 60_000)
        assertEquals(5.0, stats.distance, 1e-6)
        assertNull(stats.acceleration)
        trip.update(null, 61_000)
        stats = trip.update(5f, 62_000)
        assertEquals(5.0, stats.distance, 1e-6)
    }

    @Test
    fun `reset starts over`() {
        val trip = Trip()
        trip.update(5f, 0)
        trip.update(5f, 1000)
        assertEquals(TripStats(), trip.reset())
        assertEquals(0.0, trip.update(5f, 2000).distance, 1e-9)
    }
}
