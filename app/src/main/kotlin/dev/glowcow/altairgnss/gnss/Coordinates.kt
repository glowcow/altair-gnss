package dev.glowcow.altairgnss.gnss

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

enum class CoordinateFormat { DD, DDM, DMS }

object Coordinates {
    fun latitude(degrees: Double, format: CoordinateFormat) = format(degrees, format, if (degrees >= 0) 'N' else 'S')

    fun longitude(degrees: Double, format: CoordinateFormat) = format(degrees, format, if (degrees >= 0) 'E' else 'W')

    // Rounding happens on the smallest shown unit first, so 59.99996′ becomes the next degree, not 60.0000′.
    private fun format(degrees: Double, format: CoordinateFormat, hemisphere: Char): String {
        val a = abs(degrees)
        return when (format) {
            CoordinateFormat.DD -> String.format(Locale.ROOT, "%.6f° %c", a, hemisphere)
            CoordinateFormat.DDM -> {
                val total = (a * 60 * 10_000).roundToLong()
                String.format(Locale.ROOT, "%d° %07.4f′ %c", total / 600_000, (total % 600_000) / 10_000.0, hemisphere)
            }
            CoordinateFormat.DMS -> {
                val total = (a * 3600 * 100).roundToLong()
                val rest = total % 360_000
                String.format(Locale.ROOT, "%d° %02d′ %05.2f″ %c", total / 360_000, rest / 6_000, (rest % 6_000) / 100.0, hemisphere)
            }
        }
    }
}
