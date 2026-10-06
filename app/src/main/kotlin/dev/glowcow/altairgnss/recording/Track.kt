package dev.glowcow.altairgnss.recording

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** One recording of altitude over time. */
@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    /** Null while the recording runs. */
    val endedAt: Long? = null,
    /** The altitude counted as zero; null means the first point. */
    val zeroAltitude: Double? = null,
)

@Entity(
    tableName = "points",
    foreignKeys = [ForeignKey(Track::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("trackId", "timeMs")],
)
data class Point(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val timeMs: Long,
    /** Metres above sea level, as the altimeter showed it. */
    val altitude: Double,
    /** The barometer's reading; null on a phone without one. */
    val hpa: Double?,
    /** Where the receiver placed the phone and how fast it moved, m/s; null where it had no fix. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    val speed: Float? = null,
)
