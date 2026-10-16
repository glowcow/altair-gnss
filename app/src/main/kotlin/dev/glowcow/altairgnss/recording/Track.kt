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
    /** What it was made doing, an [Activity] key; null for a recording made before there was a choice. */
    val activity: String? = null,
    /** Where it began, named by the geocoder; null until looked up, empty where nothing could name it. */
    val place: String? = null,
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
    /** Where the phone was placed and how fast it moved, m/s; null where nothing placed it. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    val speed: Float? = null,
    /** Metres the place may be off by, as its source claimed; null where it did not say or there is no place. */
    val accuracy: Float? = null,
)

/** A checkpoint: a moment of a recording the user marked, with what they wrote about it; the label may be empty. */
@Entity(
    tableName = "marks",
    foreignKeys = [ForeignKey(Track::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("trackId", "timeMs")],
)
data class Mark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val timeMs: Long,
    val label: String = "",
)
