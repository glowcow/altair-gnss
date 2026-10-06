package dev.glowcow.altairgnss.recording

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import dev.glowcow.altairgnss.altimeter.Altimeter
import dev.glowcow.altairgnss.data.SettingsStore
import dev.glowcow.altairgnss.gnss.GnssMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The running recording as it stands; heights are metres from its zero. */
data class LiveProfile(
    val elapsedMs: Long,
    val fromZero: Double?,
    val gain: Double,
    val loss: Double,
    val lowest: Double?,
    val highest: Double?,
    /** Metres along the ground so far, and the speed now, m/s; null without a fix. */
    val distance: Double,
    val speed: Float?,
    /** The fastest so far and the average while moving, m/s. */
    val maxSpeed: Float?,
    val averageSpeed: Float?,
    /** Time spent moving so far; null until the receiver has given a speed. */
    val movingMs: Long?,
    /** Seconds the fix still has to hold before the recording begins; null once it runs. */
    val waitingSeconds: Int? = null,
)

/**
 * Records the altimeter's altitude once a second into a [Track]. The points are written by
 * [RecordingService], which keeps the phone awake for it; this class starts and stops it.
 */
class Recorder(
    private val context: Context,
    private val scope: CoroutineScope,
    private val dao: TrackDao,
    private val altimeter: Altimeter,
    private val gnss: GnssMonitor,
    private val settings: SettingsStore,
) {
    /** The recording that runs now. */
    val active: StateFlow<Track?> = dao.observeActive().stateIn(scope, SharingStarted.Eagerly, null)

    private val liveState = MutableStateFlow<LiveProfile?>(null)
    val live: StateFlow<LiveProfile?> = liveState

    init {
        // A recording left open by a process that was killed ends at its last point.
        scope.launch { dao.active()?.let { end(it, null) } }
    }

    // A recording that never got an altitude holds nothing worth keeping.
    private suspend fun end(track: Track, at: Long?) {
        val last = dao.lastPointTime(track.id)
        if (last == null) dao.delete(track.id) else dao.close(track.id, at ?: last)
    }

    fun start() {
        scope.launch {
            if (dao.active() != null) return@launch
            dao.insert(Track(startedAt = System.currentTimeMillis()))
            context.startForegroundService(Intent(context, RecordingService::class.java))
        }
    }

    fun stop() {
        scope.launch {
            dao.active()?.let { end(it, System.currentTimeMillis()) }
            context.stopService(Intent(context, RecordingService::class.java))
            liveState.value = null
        }
    }

    /** Counts heights of the running recording from where the phone is now. */
    fun zeroHere() {
        scope.launch {
            val track = dao.active() ?: return@launch
            altimeter.state.value.altitude?.let { dao.setZero(track.id, it) }
        }
    }

    suspend fun delete(id: Long) = dao.delete(id)

    /** Writes points until cancelled; the service runs it. */
    suspend fun record() {
        val track = dao.active() ?: return
        val climb = Climb()
        val way = PathLength()
        var first: Double? = null
        var min = Double.MAX_VALUE
        var max = -Double.MAX_VALUE
        var fastest: Float? = null
        var movingSum = 0.0
        var movingCount = 0
        var movingMs: Long? = null
        var lastAt = 0L
        coroutineScope {
            // The altimeter and the receiver run only while their states are collected. With positions
            // wanted the receiver is kept on whatever the altitude comes from, to place the points
            // wherever it has a fix; with them off it is left to the altimeter, which underground
            // works by the barometer alone.
            val sensors = launch { altimeter.state.collect {} }
            var placing = true
            val receiver = launch {
                settings.settings.map { it.recordPosition }.distinctUntilChanged().collectLatest { wanted ->
                    placing = wanted
                    if (wanted) gnss.state.collect {}
                }
            }
            try {
                val chosen = settings.settings.first()
                val moving = chosen.movingSpeed
                var startedAt = track.startedAt
                // With positions wanted, nothing is written until the receiver has held a fix for a while:
                // its first fixes can lie far from the place.
                val steadyMs = chosen.steadyFixSeconds * 1000L
                var heldSince: Long? = null
                while (placing && steadyMs > 0) {
                    val now = SystemClock.elapsedRealtime()
                    val fixed = gnss.state.value.let { g -> g.hasFix && g.fix != null && now - g.fix.elapsedRealtimeMs < FRESH_MS }
                    heldSince = if (fixed) heldSince ?: now else null
                    val held = heldSince?.let { now - it } ?: 0L
                    if (held >= steadyMs) break
                    liveState.value = LiveProfile(0, null, 0.0, 0.0, null, null, 0.0, null, null, null, null, ((steadyMs - held + 999) / 1000).toInt())
                    delay(INTERVAL_MS)
                }
                if (steadyMs > 0 && heldSince != null) {
                    startedAt = System.currentTimeMillis()
                    dao.setStartedAt(track.id, startedAt)
                }
                while (true) {
                    val state = altimeter.state.value
                    val now = System.currentTimeMillis()
                    val altitude = state.altitude
                    val fix = if (!placing) {
                        null
                    } else {
                        gnss.state.value.let { g -> g.fix?.takeIf { g.hasFix && SystemClock.elapsedRealtime() - it.elapsedRealtimeMs < FRESH_MS } }
                    }
                    if (altitude != null) {
                        dao.insert(
                            Point(
                                trackId = track.id,
                                timeMs = now,
                                altitude = altitude,
                                hpa = state.pressureHpa,
                                latitude = fix?.latitude,
                                longitude = fix?.longitude,
                                speed = fix?.speed,
                            ),
                        )
                        if (fix != null) way.add(fix.latitude, fix.longitude)
                        fix?.speed?.let { speed ->
                            if (speed > (fastest ?: 0f)) fastest = speed
                            if (movingMs == null) movingMs = 0L
                            if (speed >= moving) {
                                movingSum += speed
                                movingCount++
                                if (lastAt != 0L) movingMs = (movingMs ?: 0L) + (now - lastAt).coerceAtMost(MAX_STEP_MS)
                            }
                        }
                        lastAt = now
                        if (first == null) first = altitude
                        climb.add(altitude)
                        if (altitude < min) min = altitude
                        if (altitude > max) max = altitude
                    }
                    val zero = active.value?.zeroAltitude ?: first
                    liveState.value = LiveProfile(
                        elapsedMs = now - startedAt,
                        fromZero = if (altitude != null && zero != null) altitude - zero else null,
                        gain = climb.gain,
                        loss = climb.loss,
                        lowest = zero?.let { min - it },
                        highest = zero?.let { max - it },
                        distance = way.metres,
                        speed = fix?.speed,
                        maxSpeed = fastest,
                        averageSpeed = if (movingCount > 0) (movingSum / movingCount).toFloat() else null,
                        movingMs = movingMs,
                    )
                    delay(INTERVAL_MS)
                }
            } finally {
                sensors.cancel()
                receiver.cancel()
            }
        }
    }

    private companion object {
        const val INTERVAL_MS = 1_000L
        // A fix older than this describes where the phone was, not where it is.
        const val FRESH_MS = 3_000L
        const val MAX_STEP_MS = 10_000L
    }
}
