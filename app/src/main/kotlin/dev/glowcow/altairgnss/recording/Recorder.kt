package dev.glowcow.altairgnss.recording

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import dev.glowcow.altairgnss.altimeter.Altimeter
import dev.glowcow.altairgnss.data.SettingsStore
import dev.glowcow.altairgnss.gnss.Fix
import dev.glowcow.altairgnss.gnss.FusedPosition
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
    /** While it waits: metres the only position at hand may be off by, when that is too coarse to start on. */
    val coarse: Float? = null,
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
    private val fused: FusedPosition,
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

    /** Adds a checkpoint to the running recording at [timeMs], the moment the user asked for it. */
    suspend fun mark(timeMs: Long, label: String) {
        dao.active()?.let { dao.insert(Mark(trackId = it.id, timeMs = timeMs, label = label.trim().take(MAX_LABEL))) }
    }

    suspend fun labelMark(id: Long, label: String) = dao.setMarkLabel(id, label.trim().take(MAX_LABEL))

    suspend fun deleteMark(id: Long) = dao.deleteMark(id)

    /** Writes points until cancelled; the service runs it. */
    suspend fun record() {
        val track = dao.active() ?: return
        val climb = Climb()
        val steady = RunningMedian()
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
            // With network positioning asked for, the phone's own blend places the points where it is
            // sure enough; the receiver alone does otherwise, and wherever the blend has nothing.
            var blended: Fix? = null
            val network = launch {
                settings.settings.map { it.recordPosition && it.networkPosition }.distinctUntilChanged().collectLatest { wanted ->
                    blended = null
                    if (wanted) fused.fixes.collect { blended = it }
                }
            }
            // A place of the blend makes one point: it comes less often than a point is written, and
            // written again it would be a stop followed by a leap. [take] marks it used.
            var taken = 0L
            fun blend(fresh: Long = BLEND_FRESH_MS): Fix? = blended?.takeIf {
                SystemClock.elapsedRealtime() - it.elapsedRealtimeMs < fresh && (it.horizontalAccuracy ?: Float.MAX_VALUE) <= BLEND_ACCURACY
            }
            fun place(take: Boolean = false): Fix? {
                if (!placing) return null
                blend()?.takeIf { !take || it.elapsedRealtimeMs != taken }?.let {
                    if (take) taken = it.elapsedRealtimeMs
                    return it
                }
                return gnss.state.value.let { g -> g.fix?.takeIf { g.hasFix && SystemClock.elapsedRealtime() - it.elapsedRealtimeMs < FRESH_MS } }
            }
            var lastAltitude: Double? = null
            try {
                val chosen = settings.settings.first()
                val moving = chosen.movingSpeed
                val walked = WalkedSpeed(moving)
                var carried: Float? = null
                var startedAt = track.startedAt
                // With positions wanted, nothing is written until the receiver has held a fix for a while:
                // its first fixes can lie far from the place.
                val steadyMs = chosen.steadyFixSeconds * 1000L
                var heldSince: Long? = null
                while (placing && steadyMs > 0) {
                    val now = SystemClock.elapsedRealtime()
                    // The blend reports seldom indoors: a pause between two of its places is not a lost fix.
                    val fixed = place() != null || blend(BLEND_HELD_MS) != null
                    heldSince = if (fixed) heldSince ?: now else null
                    val held = heldSince?.let { now - it } ?: 0L
                    if (held >= steadyMs) break
                    val coarse = blended?.takeIf { !fixed && now - it.elapsedRealtimeMs < BLEND_HELD_MS }?.horizontalAccuracy
                    liveState.value = LiveProfile(0, 0.0, 0.0, null, null, 0.0, null, null, null, null, ((steadyMs - held + 999) / 1000).toInt(), coarse)
                    delay(INTERVAL_MS)
                }
                if (steadyMs > 0 && heldSince != null) {
                    startedAt = System.currentTimeMillis()
                    dao.setStartedAt(track.id, startedAt)
                }
                while (true) {
                    val state = altimeter.state.value
                    val now = System.currentTimeMillis()
                    val fix = place(take = true)
                    // A place without a speed gets one from the way walked; between two places of the
                    // blend the points carry that speed on, so the time moving adds up.
                    val speed = when {
                        fix?.speed != null -> fix.speed.also { walked.reset() }
                        fix != null -> walked.add(fix.latitude, fix.longitude, fix.horizontalAccuracy ?: BLEND_ACCURACY, now)
                        blend() != null -> carried
                        else -> null
                    }
                    carried = speed
                    // Without the altimeter's altitude the point takes the one that came with its place, else the last one written.
                    val altitude = state.altitude ?: fix?.mslAltitude ?: fix?.altitude ?: lastAltitude
                    if (altitude != null) {
                        lastAltitude = altitude
                        dao.insert(
                            Point(
                                trackId = track.id,
                                timeMs = now,
                                altitude = altitude,
                                hpa = state.pressureHpa,
                                latitude = fix?.latitude,
                                longitude = fix?.longitude,
                                speed = speed,
                                accuracy = fix?.horizontalAccuracy,
                            ),
                        )
                        if (fix != null) way.add(fix.latitude, fix.longitude, fix.horizontalAccuracy)
                        speed?.let { speed ->
                            if (speed > (fastest ?: 0f)) fastest = speed
                            if (movingMs == null) movingMs = 0L
                            if (speed >= moving) {
                                movingSum += speed
                                movingCount++
                                if (lastAt != 0L) movingMs = (movingMs ?: 0L) + (now - lastAt).coerceAtMost(MAX_STEP_MS)
                            }
                        }
                        lastAt = now
                        // The figures follow the altitude without its jumps; the points keep it as measured.
                        val even = steady.add(altitude)
                        if (first == null) first = even
                        climb.add(even)
                        if (even < min) min = even
                        if (even > max) max = even
                    }
                    val zero = active.value?.zeroAltitude ?: first
                    liveState.value = LiveProfile(
                        elapsedMs = now - startedAt,
                        gain = climb.gain,
                        loss = climb.loss,
                        lowest = zero?.takeIf { first != null }?.let { min - it },
                        highest = zero?.takeIf { first != null }?.let { max - it },
                        distance = way.metres,
                        speed = speed,
                        maxSpeed = fastest,
                        averageSpeed = if (movingCount > 0) (movingSum / movingCount).toFloat() else null,
                        movingMs = movingMs,
                    )
                    delay(INTERVAL_MS)
                }
            } finally {
                sensors.cancel()
                receiver.cancel()
                network.cancel()
            }
        }
    }

    companion object {
        const val INTERVAL_MS = 1_000L
        // A fix older than this describes where the phone was, not where it is.
        const val FRESH_MS = 3_000L
        const val MAX_STEP_MS = 10_000L
        const val MAX_LABEL = 80
        // The blend reports less often than the receiver, and counts only when it claims to be this close, metres.
        const val BLEND_FRESH_MS = 10_000L
        const val BLEND_ACCURACY = 30f
        // While waiting to start, a place of the blend counts as held this long.
        const val BLEND_HELD_MS = 30_000L
    }
}
