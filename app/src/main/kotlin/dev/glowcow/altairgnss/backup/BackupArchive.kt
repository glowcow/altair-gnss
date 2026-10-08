package dev.glowcow.altairgnss.backup

import dev.glowcow.altairgnss.recording.Point
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class BackupSettings(
    val theme: String,
    val palette: String,
    val coordinates: String,
    val length: String,
    val speed: String,
    val keepScreenOn: Boolean,
    val startTab: String,
    val trueNorth: Boolean,
    val appUpdate: Boolean,
    val altitudeSource: String,
    val mapTiles: Boolean = false,
    val recordPosition: Boolean = true,
    val movingKmh: Int = 3,
    val steadyFixSeconds: Int = 15,
    val trackZero: String = "LOWEST",
    val gainWatch: Boolean = false,
    val networkPosition: Boolean = false,
)

/** A recording as the manifest lists it; its points are in `tracks/<startedAt>.csv`. */
@Serializable
data class BackupTrack(val startedAt: Long, val endedAt: Long? = null, val zeroAltitude: Double? = null, val marks: List<BackupMark> = emptyList())

/** A marked moment of a recording. */
@Serializable
data class BackupMark(val timeMs: Long, val label: String = "")

/** `backup.json` of an archive. */
@Serializable
data class BackupManifest(
    val version: Int = 1,
    val createdAt: Long,
    val app: String,
    val tracks: List<BackupTrack>,
    val settings: BackupSettings? = null,
)

/**
 * A backup is a zip: `backup.json` and the points of each recording as `tracks/<startedAt>.csv`, a
 * line a point: time, altitude, pressure, latitude, longitude, speed, accuracy, the unknown ones
 * left empty; a backup made before points kept their accuracy has six cells to a line.
 */
object BackupArchive {
    const val MANIFEST = "backup.json"

    private val json = Json { ignoreUnknownKeys = true }
    private val TRACK_FILE = Regex("""tracks/(\d{1,19})\.csv""")

    private const val MAX_MANIFEST = 20L * 1024 * 1024
    private const val MAX_TOTAL = 2L * 1024 * 1024 * 1024
    private const val MAX_ENTRIES = 50_000

    fun trackFile(startedAt: Long) = "tracks/$startedAt.csv"

    /** Writes an archive piece by piece, so the points of one recording are held at a time. */
    class Writer(out: OutputStream) {
        private val zip = ZipOutputStream(out)

        fun manifest(manifest: BackupManifest) {
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(json.encodeToString(manifest).toByteArray())
            zip.closeEntry()
        }

        fun track(startedAt: Long, points: List<Point>) {
            zip.putNextEntry(ZipEntry(trackFile(startedAt)))
            val text = StringBuilder()
            for (p in points) {
                text.append(p.timeMs).append(',').append(p.altitude).append(',')
                text.append(p.hpa ?: "").append(',').append(p.latitude ?: "").append(',').append(p.longitude ?: "").append(',')
                text.append(p.speed ?: "").append(',').append(p.accuracy ?: "").append('\n')
            }
            zip.write(text.toString().toByteArray())
            zip.closeEntry()
        }

        /** Ends the archive; the caller closes the stream. */
        fun finish() = zip.finish()
    }

    /** Unpacks a backup into the empty directory [into], keeping only entries a backup can hold. */
    fun read(input: InputStream, into: File): BackupManifest {
        val root = into.canonicalFile
        var manifest: BackupManifest? = null
        var total = 0L
        var entries = 0
        val zip = ZipInputStream(input)
        while (true) {
            val entry = zip.nextEntry ?: break
            if (++entries > MAX_ENTRIES) throw IOException("too many entries")
            val name = entry.name
            if (name == MANIFEST) {
                val bytes = ByteArrayOutputStream()
                copy(zip, bytes, MAX_MANIFEST)
                manifest = json.decodeFromString<BackupManifest>(bytes.toString(Charsets.UTF_8.name()))
                continue
            }
            if (entry.isDirectory || !TRACK_FILE.matches(name)) continue
            val target = File(root, name).canonicalFile
            if (!target.path.startsWith(root.path + File.separator)) continue
            target.parentFile?.mkdirs()
            total += target.outputStream().use { copy(zip, it, MAX_TOTAL - total) }
        }
        return manifest ?: throw IOException("no $MANIFEST in the archive")
    }

    /** The points of an unpacked recording, for the track with [trackId]; a line that does not parse is left out. */
    fun points(file: File, trackId: Long): List<Point> = file.useLines { lines ->
        lines.mapNotNull { line ->
            val cells = line.split(',')
            if (cells.size !in 6..7) return@mapNotNull null
            Point(
                trackId = trackId,
                timeMs = cells[0].toLongOrNull() ?: return@mapNotNull null,
                altitude = cells[1].toDoubleOrNull()?.takeIf { it.isFinite() } ?: return@mapNotNull null,
                hpa = cells[2].toDoubleOrNull()?.takeIf { it.isFinite() },
                latitude = cells[3].toDoubleOrNull()?.takeIf { it in -90.0..90.0 },
                longitude = cells[4].toDoubleOrNull()?.takeIf { it in -180.0..180.0 },
                speed = cells[5].toFloatOrNull()?.takeIf { it.isFinite() },
                accuracy = cells.getOrNull(6)?.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f },
            )
        }.toList()
    }

    private fun copy(from: InputStream, to: OutputStream, limit: Long): Long {
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        while (true) {
            val n = from.read(buffer)
            if (n < 0) return copied
            copied += n
            if (copied > limit) throw IOException("the backup is too large")
            to.write(buffer, 0, n)
        }
    }
}
