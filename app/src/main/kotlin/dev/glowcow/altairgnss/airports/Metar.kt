package dev.glowcow.altairgnss.airports

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** The latest weather report of an airport, as far as an altimeter cares. */
data class AirportReport(
    val icao: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** Sea-level pressure the airport gives to aircraft (QNH), hPa. */
    val qnhHpa: Double,
    /** When it was observed, milliseconds since the epoch. */
    val timeMs: Long,
)

data class NearbyAirport(val report: AirportReport, val distanceKm: Double) {
    /**
     * Error to expect from calibrating to this report, metres: the pressure is given to a whole
     * hectopascal, and it changes with the distance from the airport.
     */
    val accuracy: Float get() = (REPORT_ERROR + distanceKm * ERROR_PER_KM).toFloat()

    private companion object {
        const val REPORT_ERROR = 5.0
        const val ERROR_PER_KM = 0.1
    }
}

object Metar {
    /** Reads the JSON list of reports; entries without a place or a pressure are skipped. */
    fun parse(json: String): List<AirportReport> =
        Json.parseToJsonElement(json).jsonArray.mapNotNull { runCatching { report(it.jsonObject) }.getOrNull() }

    private fun report(o: JsonObject): AirportReport? {
        val qnh = o["altim"]?.jsonPrimitive?.doubleOrNull ?: return null
        // A setting far from any weather is a broken report.
        if (qnh !in 850.0..1090.0) return null
        return AirportReport(
            icao = o["icaoId"]?.jsonPrimitive?.content ?: return null,
            name = o["name"]?.jsonPrimitive?.content.orEmpty(),
            latitude = o["lat"]?.jsonPrimitive?.doubleOrNull ?: return null,
            longitude = o["lon"]?.jsonPrimitive?.doubleOrNull ?: return null,
            qnhHpa = qnh,
            timeMs = (o["obsTime"]?.jsonPrimitive?.longOrNull ?: return null) * 1000,
        )
    }

    /**
     * A box of about [km] around a place as `south,west,north,east`. Its centre is rounded to half
     * a degree first, so the request does not carry the exact position.
     */
    fun box(latitude: Double, longitude: Double, km: Double): String {
        val lat = (latitude * 2).roundToInt() / 2.0
        val lon = (longitude * 2).roundToInt() / 2.0
        val dLat = km / KM_PER_DEGREE
        val dLon = dLat / cos(Math.toRadians(lat.coerceIn(-80.0, 80.0)))
        return listOf(lat - dLat, lon - dLon, lat + dLat, lon + dLon).joinToString(",") { "%.2f".format(java.util.Locale.ROOT, it) }
    }

    /** Great-circle distance, kilometres. */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val a = sin(Math.toRadians(lat2 - lat1) / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(Math.toRadians(lon2 - lon1) / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
    }

    /** The reports worth offering: fresh ones within [km], the nearest first. */
    fun nearest(reports: List<AirportReport>, latitude: Double, longitude: Double, km: Double, nowMs: Long): List<NearbyAirport> =
        reports
            .filter { nowMs - it.timeMs <= MAX_AGE_MS }
            .map { NearbyAirport(it, distanceKm(latitude, longitude, it.latitude, it.longitude)) }
            .filter { it.distanceKm <= km }
            .sortedBy { it.distanceKm }

    private const val KM_PER_DEGREE = 111.2
    private const val EARTH_RADIUS_KM = 6371.0
    // Reports come out every half hour or hour; an older one describes other weather.
    private const val MAX_AGE_MS = 3 * 3_600_000L
}
