package dev.glowcow.altairgnss.recording

import org.junit.Assert.assertEquals
import org.junit.Test

class ScaleTest {
    @Test
    fun timeStepLeavesRoomForItsLabels() {
        // Seventeen and a half minutes across 900 pixels, a label 120 wide: every five minutes is 257 apart, every two only 103.
        assertEquals(5 * 60_000L, Scale.timeStep(1_050_000, 900f, 120f))
        // Three hours on the same width: half an hour is 150 apart.
        assertEquals(30 * 60_000L, Scale.timeStep(3 * 3_600_000L, 900f, 120f))
        // A minute: ten seconds is 150 apart.
        assertEquals(10_000L, Scale.timeStep(60_000, 900f, 120f))
        // Longer than any round step: whole days.
        assertEquals(2 * 24 * 3_600_000L, Scale.timeStep(10 * 24 * 3_600_000L, 900f, 120f))
    }

    @Test
    fun timeMarksAndTheirLabels() {
        assertEquals(listOf(300_000L, 600_000L, 900_000L), Scale.timeMarks(1_050_000, 300_000))
        assertEquals("0:05", Scale.timeLabel(300_000, 300_000))
        assertEquals("0:00:30", Scale.timeLabel(30_000, 10_000))
        assertEquals("2:30", Scale.timeLabel(9_000_000, 1_800_000))
    }

    @Test
    fun levelsAreRound() {
        assertEquals(listOf(0.0, 5.0, 10.0, 15.0), Scale.levels(-2.0, 18.0, 5))
        assertEquals(listOf(900.0, 1000.0, 1100.0, 1200.0, 1300.0), Scale.levels(801.0, 1300.0, 6))
        assertEquals(emptyList<Double>(), Scale.levels(5.0, 5.0, 4))
    }
}
