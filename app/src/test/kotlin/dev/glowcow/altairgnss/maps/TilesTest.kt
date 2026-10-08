package dev.glowcow.altairgnss.maps

import org.junit.Assert.assertEquals
import org.junit.Test

class TilesTest {
    @Test
    fun theMiddleOfTheWorldIsTheMiddleOfTheTiles() {
        assertEquals(1.0, Tiles.x(0.0, 1), 1e-9)
        assertEquals(1.0, Tiles.y(0.0, 1), 1e-9)
        assertEquals(0.0, Tiles.x(-180.0, 5), 1e-9)
    }

    @Test
    fun aKnownTile() {
        // Zoom 12 over the Bernese Alps.
        assertEquals(2139, Tiles.x(8.0, 12).toInt())
        assertEquals(1448, Tiles.y(46.5, 12).toInt())
    }

    @Test
    fun aPixelShrinksWithZoomAndLatitude() {
        assertEquals(156_543.03, Tiles.metresPerPixel(0.0, 0), 0.01)
        assertEquals(Tiles.metresPerPixel(0.0, 10) / 2, Tiles.metresPerPixel(60.0, 10), 1e-6)
    }

    @Test
    fun zoomThatFitsADistance() {
        // 24 km into 768 pixels at 46.5 N: 31 m to a pixel, between zoom 11 (52.6) and 12 (26.3).
        assertEquals(11, Tiles.zoomFor(24_000.0, 46.5, 768))
        assertEquals(Tiles.MAX_ZOOM, Tiles.zoomFor(10.0, 46.5, 768))
        assertEquals(0, Tiles.zoomFor(1e9, 0.0, 768))
    }

    @Test
    fun zoomNearestToAPixel() {
        // At the equator zoom 17 has 1.19 m to a pixel and zoom 18 has 0.60.
        assertEquals(17, Tiles.zoomAt(1.2, 0.0))
        assertEquals(18, Tiles.zoomAt(0.7, 0.0))
        assertEquals(Tiles.MAX_ZOOM, Tiles.zoomAt(0.01, 0.0))
    }

    @Test
    fun boxBlurKeepsAFlatPictureAndSpreadsADot() {
        val flat = IntArray(25) { 0xFF336699.toInt() }
        boxBlur(flat, 5, 5, 1)
        assertEquals(0xFF336699.toInt(), flat[12])
        val dot = IntArray(25) { 0xFF000000.toInt() }.also { it[12] = 0xFFFFFFFF.toInt() }
        boxBlur(dot, 5, 5, 1)
        // A ninth of white in the middle and in its eight neighbours, none in the corners.
        assertEquals(0xFF1C1C1C.toInt(), dot[12])
        assertEquals(0xFF1C1C1C.toInt(), dot[6])
        assertEquals(0xFF000000.toInt(), dot[0])
    }
}
