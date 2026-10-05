package dev.glowcow.altairgnss.airports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import javax.net.ssl.HttpsURLConnection

/** Asks the Aviation Weather Center for the latest airport reports around a place. */
class AirportService(private val userAgent: String) {
    /** Fresh reports within [SEARCH_KM], the nearest first; throws when the service cannot be reached. */
    suspend fun nearby(latitude: Double, longitude: Double): List<NearbyAirport> = withContext(Dispatchers.IO) {
        // The box is wider than the search, since its centre is only roughly ours.
        val url = "$ENDPOINT?format=json&bbox=${Metar.box(latitude, longitude, SEARCH_KM + BOX_MARGIN_KM)}"
        val c = URI(url).toURL().openConnection() as HttpsURLConnection
        try {
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", userAgent)
            check(c.responseCode == HttpsURLConnection.HTTP_OK) { "The service answered ${c.responseCode}" }
            val body = c.inputStream.use { it.readNBytes(MAX_BYTES).decodeToString() }
            Metar.nearest(Metar.parse(body), latitude, longitude, SEARCH_KM, System.currentTimeMillis())
        } finally {
            c.disconnect()
        }
    }

    private companion object {
        const val ENDPOINT = "https://aviationweather.gov/api/data/metar"
        const val SEARCH_KM = 200.0
        const val BOX_MARGIN_KM = 40.0
        const val TIMEOUT_MS = 15_000
        const val MAX_BYTES = 2 * 1024 * 1024
    }
}
