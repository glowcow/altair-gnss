package dev.glowcow.altairgnss.gnss

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The phone's own blend of satellites, Wi-Fi and cell towers — what a map app goes by. Only a
 * recording that asks for it listens, and only while [fixes] is collected.
 */
class FusedPosition(private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val provider: String?
        get() = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull { manager.hasProvider(it) }

    @SuppressLint("MissingPermission")
    val fixes: Flow<Fix> = callbackFlow {
        val listener = LocationListener { trySend(it.toFix()) }
        val request = LocationRequest.Builder(INTERVAL_MS).setQuality(LocationRequest.QUALITY_HIGH_ACCURACY).build()
        try {
            provider?.let { manager.requestLocationUpdates(it, request, context.mainExecutor, listener) }
        } catch (_: SecurityException) {
            close()
        }
        awaitClose { manager.removeUpdates(listener) }
    }

    private companion object {
        const val INTERVAL_MS = 1_000L
    }
}
