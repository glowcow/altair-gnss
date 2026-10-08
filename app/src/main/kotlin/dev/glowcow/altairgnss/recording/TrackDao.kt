package dev.glowcow.altairgnss.recording

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY startedAt DESC")
    fun observeTracks(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    fun observeTrack(id: Long): Flow<Track?>

    @Query("SELECT * FROM tracks WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<Track?>

    @Query("SELECT * FROM tracks WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun active(): Track?

    @Query("SELECT * FROM tracks WHERE endedAt IS NOT NULL ORDER BY startedAt")
    suspend fun finished(): List<Track>

    @Query("SELECT * FROM tracks WHERE startedAt = :startedAt LIMIT 1")
    suspend fun startedAt(startedAt: Long): Track?

    @Insert
    suspend fun insert(track: Track): Long

    @Query("UPDATE tracks SET endedAt = :at WHERE id = :id")
    suspend fun close(id: Long, at: Long)

    @Query("UPDATE tracks SET startedAt = :at WHERE id = :id")
    suspend fun setStartedAt(id: Long, at: Long)

    @Query("UPDATE tracks SET zeroAltitude = :altitude WHERE id = :id")
    suspend fun setZero(id: Long, altitude: Double?)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insert(point: Point)

    @Insert
    suspend fun insertAll(points: List<Point>)

    @Query("SELECT * FROM points WHERE trackId = :trackId ORDER BY timeMs")
    fun observePoints(trackId: Long): Flow<List<Point>>

    @Query("SELECT * FROM points WHERE trackId = :trackId ORDER BY timeMs")
    suspend fun points(trackId: Long): List<Point>

    @Query("SELECT MAX(timeMs) FROM points WHERE trackId = :trackId")
    suspend fun lastPointTime(trackId: Long): Long?

    @Insert
    suspend fun insert(mark: Mark): Long

    @Insert
    suspend fun insertMarks(marks: List<Mark>)

    @Query("SELECT * FROM marks WHERE trackId = :trackId ORDER BY timeMs")
    fun observeMarks(trackId: Long): Flow<List<Mark>>

    @Query("SELECT * FROM marks WHERE trackId = :trackId ORDER BY timeMs")
    suspend fun marks(trackId: Long): List<Mark>

    @Query("UPDATE marks SET label = :label WHERE id = :id")
    suspend fun setMarkLabel(id: Long, label: String)

    @Query("DELETE FROM marks WHERE id = :id")
    suspend fun deleteMark(id: Long)
}
