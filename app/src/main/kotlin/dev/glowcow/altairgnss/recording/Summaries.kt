package dev.glowcow.altairgnss.recording

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What one finished recording added up to, the way its own page counts it. */
data class Summary(
    val track: Track,
    val durationMs: Long,
    val movingMs: Long,
    val distance: Double,
    val gain: Double,
    val loss: Double,
)

/** Adds up recordings for the figures of a year; each is read once a process, until it or the moving speed changes. */
class Summaries(private val dao: TrackDao) {
    private val lock = Mutex()
    private val known = mutableMapOf<Long, Pair<Any, Summary>>()

    suspend fun of(tracks: List<Track>, moving: Float): List<Summary> = withContext(Dispatchers.IO) {
        lock.withLock {
            tracks.filter { it.endedAt != null }.map { track ->
                val key = Triple(track.startedAt, track.endedAt, moving) to track.activity
                known[track.id]?.takeIf { it.first == key }?.second?.copy(track = track) ?: summary(track, moving).also { known[track.id] = key to it }
            }
        }
    }

    private suspend fun summary(track: Track, moving: Float): Summary {
        val points = dao.points(track.id)
        val stats = Profile.stats(points, Profile.smooth(points), moving)
        return Summary(
            track,
            durationMs = track.endedAt!! - track.startedAt,
            movingMs = stats?.movingMs ?: 0L,
            distance = stats?.distance ?: 0.0,
            gain = stats?.gain ?: 0.0,
            loss = stats?.loss ?: 0.0,
        )
    }
}
