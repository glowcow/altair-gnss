package dev.glowcow.altairgnss.recording

import android.content.Context
import android.location.Address
import android.location.Geocoder
import dev.glowcow.altairgnss.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/**
 * Names where each finished recording began, once, through the platform's geocoder — only while
 * network positioning is on: the geocoder sends the place to the phone's location service.
 */
class Places(private val context: Context, private val scope: CoroutineScope, private val dao: TrackDao, private val settings: SettingsStore) {
    private val lock = Mutex()

    // A recording the geocoder could not answer for is tried again only by the next process.
    private val tried = mutableSetOf<Long>()

    fun fill() {
        if (!Geocoder.isPresent()) return
        scope.launch {
            if (!settings.settings.first().networkPosition) return@launch
            lock.withLock {
                for (track in dao.unplaced()) {
                    if (!tried.add(track.id)) continue
                    val start = dao.firstPlaced(track.id)
                    if (start == null) {
                        dao.setPlace(track.id, "")
                        continue
                    }
                    name(start.latitude!!, start.longitude!!)?.let { dao.setPlace(track.id, it) }
                }
            }
        }
    }

    /** The district and the town at a place, or the region, empty where there is none; null when the geocoder failed. */
    private suspend fun name(latitude: Double, longitude: Double): String? = suspendCancellableCoroutine { done ->
        Geocoder(context, context.resources.configuration.locales[0]).getFromLocation(
            latitude,
            longitude,
            1,
            object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    done.resume(addresses.firstOrNull()?.let(::title).orEmpty())
                }

                override fun onError(message: String?) = done.resume(null)
            },
        )
    }

    // A city's own district first ("Severnyy administrativnyy okrug, Moskva"); a village is its name alone.
    private fun title(address: Address): String = address.locality
        ?.let { town -> listOfNotNull(address.subLocality?.takeIf { it != town }, town).joinToString(", ") }
        ?: address.subAdminArea ?: address.adminArea ?: address.countryName.orEmpty()
}
