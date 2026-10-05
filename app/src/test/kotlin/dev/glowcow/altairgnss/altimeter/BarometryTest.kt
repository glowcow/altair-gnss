package dev.glowcow.altairgnss.altimeter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarometryTest {
    @Test
    fun `standard atmosphere`() {
        assertEquals(0.0, Barometry.altitude(1013.25, 1013.25), 1e-9)
        assertEquals(1000.0, Barometry.altitude(898.76, 1013.25), 2.0)
        assertEquals(3000.0, Barometry.altitude(701.12, 1013.25), 5.0)
    }

    @Test
    fun `a hectopascal near sea level is about eight metres`() {
        assertEquals(8.3, Barometry.altitude(1012.25, 1013.25), 0.2)
    }

    @Test
    fun `reference undoes altitude`() {
        val reference = Barometry.reference(955.3, 487.0)
        assertEquals(487.0, Barometry.altitude(955.3, reference), 1e-6)
    }

    @Test
    fun `depth below a calibrated entrance`() {
        val reference = Barometry.reference(980.0, 300.0)
        // 6 hPa more pressure underground: about 50 m down.
        assertEquals(-51.0, Barometry.altitude(986.0, reference) - 300.0, 2.0)
    }

    @Test
    fun `calibration needs enough fixes`() {
        val run = CalibrationRun()
        repeat(CalibrationRun.MIN_FIXES - 1) { run.add(100.0, 3f, 1000.0) }
        assertNull(run.result())
        run.add(100.0, 3f, 1000.0)
        val (reference, accuracy) = run.result()!!
        assertEquals(100.0, Barometry.altitude(1000.0, reference), 1e-6)
        assertEquals(3f, accuracy, 1e-4f)
    }

    @Test
    fun `accurate fixes outweigh poor ones`() {
        val run = CalibrationRun()
        repeat(5) { run.add(100.0, 2f, 1000.0) }
        repeat(5) { run.add(140.0, 12f, 1000.0) }
        val (reference, accuracy) = run.result()!!
        val altitude = Barometry.altitude(1000.0, reference)
        assertTrue("altitude $altitude", altitude in 100.0..102.0)
        assertTrue("accuracy $accuracy", accuracy in 2f..3f)
    }

    @Test
    fun `drift grows from the calibration error`() {
        assertEquals(3f, Drift.estimate(3f, 0), 1e-4f)
        assertEquals(1.5f, Drift.estimate(null, 3_600_000), 1e-4f)
        assertEquals(5f, Drift.estimate(4f, 2 * 3_600_000), 1e-3f)
    }

    @Test
    fun `blend starts at the target and then follows it slowly`() {
        assertEquals(1010.0, Blend.step(null, 1010.0, 1.0), 1e-9)
        assertEquals(1010.0 + 2.0 / Blend.TAU_SECONDS, Blend.step(1010.0, 1012.0, 1.0), 1e-9)
        assertEquals(1012.0, Blend.step(1010.0, 1012.0, 1000.0), 1e-9)
    }

    @Test
    fun `vertical speed settles on a steady climb and forgets it after a pause`() {
        val speed = VerticalSpeed(tauSeconds = 3.0)
        var last: Double? = null
        // Two metres a second, four samples a second, for twenty seconds.
        for (i in 0..80) last = speed.update(100.0 + i * 0.5, i * 250_000_000L)
        assertEquals(2.0, last!!, 1e-6)
        assertNull(speed.update(500.0, 60_000_000_000L))
        assertEquals(-1.0, speed.update(499.75, 60_250_000_000L)!!, 1e-6)
    }

    @Test
    fun `vertical speed needs two samples`() {
        val speed = VerticalSpeed()
        assertNull(speed.update(100.0, 1_000_000_000L))
        assertEquals(0.0, speed.update(100.0, 1_250_000_000L)!!, 1e-9)
        speed.reset()
        assertNull(speed.update(100.0, 1_500_000_000L))
    }

    @Test
    fun `smoother starts at the first sample and lags behind a step`() {
        val smoother = Smoother(1.0)
        assertEquals(1000.0, smoother.update(1000.0, 0), 1e-9)
        assertEquals(1000.632, smoother.update(1001.0, 1_000_000_000), 1e-3)
    }
}
