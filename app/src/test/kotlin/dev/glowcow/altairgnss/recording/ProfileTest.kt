package dev.glowcow.altairgnss.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileTest {
    private fun point(second: Int, altitude: Double, hpa: Double? = null) = Point(trackId = 1, timeMs = second * 1000L, altitude = altitude, hpa = hpa)

    @Test
    fun climbIgnoresWobble() {
        val climb = Climb()
        listOf(100.0, 100.4, 99.7, 100.3, 100.9).forEach(climb::add)
        assertEquals(0.0, climb.gain, 1e-9)
        assertEquals(0.0, climb.loss, 1e-9)
    }

    @Test
    fun climbAddsUpBothWays() {
        val climb = Climb()
        listOf(100.0, 105.0, 110.0, 90.0, 95.0).forEach(climb::add)
        assertEquals(15.0, climb.gain, 1e-9)
        assertEquals(20.0, climb.loss, 1e-9)
    }

    @Test
    fun statsOfAProfile() {
        val points = listOf(point(0, 200.0), point(60, 150.0), point(120, 210.0))
        val stats = Profile.stats(points, points.map { it.altitude })!!
        assertEquals(120_000L, stats.durationMs)
        assertEquals(150.0, stats.min, 1e-9)
        assertEquals(210.0, stats.max, 1e-9)
        assertEquals(60.0, stats.gain, 1e-9)
        assertEquals(50.0, stats.loss, 1e-9)
        assertNull(stats.distance)
        assertNull(Profile.stats(emptyList(), emptyList()))
    }

    @Test
    fun csvCarriesPlaceAndSpeedWhereKnown() {
        val points = listOf(
            point(0, 200.0, 990.0),
            point(1, 198.5).copy(latitude = 55.5, longitude = 37.25, speed = 1.5f),
        )
        val lines = Profile.csv(points, points.map { it.altitude }, 200.0).trim().lines()
        assertEquals("time,elapsed_s,altitude_m,from_zero_m,pressure_hpa,latitude,longitude,speed_mps", lines[0])
        assertEquals("1970-01-01T00:00:00Z,0,200.00,0.00,990.000,,,", lines[1])
        assertEquals("1970-01-01T00:00:01Z,1,198.50,-1.50,,55.500000,37.250000,1.50", lines[2])
    }

    @Test
    fun wayAlongTheGroundIgnoresWander() {
        val way = PathLength()
        // A metre is 9e-6 of a degree of latitude: two steps of a metre, then one of 100 m.
        way.add(55.0, 37.0)
        way.add(55.000009, 37.0)
        way.add(55.0, 37.0)
        assertEquals(0.0, way.metres, 1e-9)
        way.add(55.0009, 37.0)
        assertEquals(100.0, way.metres, 0.2)
    }

    @Test
    fun pathIsLaidOutRoundItsMiddle() {
        val points = listOf(
            point(0, 100.0).copy(latitude = 55.0, longitude = 37.0, speed = 1f),
            point(1, 105.0),
            point(2, 130.0).copy(latitude = 55.002, longitude = 37.0, speed = 3f),
        )
        val track = Profile.path(points, points.map { it.altitude })!!
        val path = track.points
        // The point without a place is left out; the other two lie 111 m either side of the middle.
        assertEquals(2, path.size)
        assertEquals(55.001, track.centre.latitude, 1e-5)
        assertEquals(37.0, track.centre.longitude, 1e-5)
        assertEquals(111.2, track.radius, 0.2)
        assertEquals(-111.2, path[0].north, 0.2)
        assertEquals(111.2, path[1].north, 0.2)
        assertEquals(0.0, path[0].up, 1e-9)
        assertEquals(30.0, path[1].up, 1e-9)
        // What a tap on the track shows: the altitude there, the time and the way since the start.
        assertEquals(130.0, path[1].altitude, 1e-9)
        assertEquals(2000L, path[1].elapsedMs)
        assertEquals(222.4, path[1].distance, 0.5)
        val stats = Profile.stats(points, points.map { it.altitude })!!
        assertEquals(222.4, stats.distance!!, 0.5)
        assertEquals(3f, stats.maxSpeed!!, 1e-6f)
        assertEquals(2f, stats.averageSpeed!!, 1e-6f)
    }

    @Test
    fun trackSitsInTheMiddleOfItsCircle() {
        // An L: the box round it is centred off the circle's centre, which lies on the long diagonal.
        val corner = listOf(0.0 to 0.0, 100.0 to 0.0, 100.0 to 30.0)
        val (x, y) = Profile.enclosingCentre(corner)
        assertEquals(50.0, x, 1.0)
        assertEquals(15.0, y, 1.0)
        // Three points of a circle of radius 10 round (5, 5), and one inside it.
        val (cx, cy) = Profile.enclosingCentre(listOf(15.0 to 5.0, 5.0 to 15.0, -5.0 to 5.0, 6.0 to 4.0))
        assertEquals(5.0, cx, 0.2)
        assertEquals(5.0, cy, 0.2)
    }

    @Test
    fun stopsDoNotPullTheAverageDown() {
        // Two minutes at 5 m/s, a minute standing with the receiver wandering at 0.3 m/s, a minute at 5 m/s.
        val points = (0..240).map { second ->
            val speed = if (second in 121..180) 0.3f else 5f
            point(second, 100.0).copy(latitude = 55.0 + second * 1e-5, longitude = 37.0, speed = speed)
        }
        val stats = Profile.stats(points, points.map { it.altitude })!!
        assertEquals(240_000L, stats.durationMs)
        assertEquals(180_000L, stats.movingMs)
        assertEquals(5f, stats.averageSpeed!!, 1e-4f)
        // A recording with no speeds at all has no moving time to speak of.
        assertNull(Profile.stats(listOf(point(0, 1.0), point(1, 2.0)), listOf(1.0, 2.0))!!.movingMs)
    }

    @Test
    fun aStopIsMarkedWhereItBegan() {
        fun at(second: Int, speed: Float?) = PathPoint(second.toDouble(), 0.0, 0.0, speed, 100.0, second * 1000L, second.toDouble())
        // Standing for 40 s from the 10th second, then a pause of 5 s that is too short to count.
        val path = (0..100).map { s -> at(s, if (s in 10..50 || s in 70..75) 0.2f else 4f) }
        val stops = Profile.stops(path)
        assertEquals(1, stops.size)
        assertEquals(10_000L, stops[0].at.elapsedMs)
        assertEquals(40_000L, stops[0].durationMs)
        // Points without a speed, under ground, are not stops.
        assertEquals(0, Profile.stops((0..100).map { at(it, null) }).size)
    }
}
