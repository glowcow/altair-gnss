package dev.glowcow.altairgnss.backup

import android.content.Context
import android.net.Uri
import dev.glowcow.altairgnss.altimeter.AltitudeSource
import dev.glowcow.altairgnss.data.AppSettings
import dev.glowcow.altairgnss.data.LengthUnit
import dev.glowcow.altairgnss.data.Palette
import dev.glowcow.altairgnss.data.SettingsStore
import dev.glowcow.altairgnss.data.SpeedUnit
import dev.glowcow.altairgnss.data.ThemeMode
import dev.glowcow.altairgnss.data.TrackZero
import dev.glowcow.altairgnss.gnss.CoordinateFormat
import dev.glowcow.altairgnss.recording.Mark
import dev.glowcow.altairgnss.recording.Track
import dev.glowcow.altairgnss.recording.TrackDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream

sealed interface RestoreOutcome {
    data class Restored(val recordings: Int) : RestoreOutcome
    data object WrongPassword : RestoreOutcome
    data object Invalid : RestoreOutcome
}

/** Saves the recordings and the settings to a file the user picks, and reads them back. */
class Backup(private val context: Context, private val tracks: TrackDao, private val settings: SettingsStore) {

    /** Writes a backup to [uri], encrypted when [password] is given. A recording that still runs is left out. */
    suspend fun save(uri: Uri, password: String?): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val finished = tracks.finished()
            val s = settings.settings.first()
            val source = settings.altimeter.first().source
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("no stream")
            (if (password.isNullOrEmpty()) out else BackupCrypto.encrypt(out, password.toCharArray())).use {
                val archive = BackupArchive.Writer(it)
                archive.manifest(
                    BackupManifest(
                        createdAt = System.currentTimeMillis(),
                        app = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
                        tracks = finished.map { t ->
                            BackupTrack(t.startedAt, t.endedAt, t.zeroAltitude, tracks.marks(t.id).map { m -> BackupMark(m.timeMs, m.label) })
                        },
                        settings = BackupSettings(
                            s.theme.name, s.palette.name, s.coordinates.name, s.length.name, s.speed.name,
                            s.keepScreenOn, s.startTab, s.trueNorth, s.appUpdate, source.name, s.mapTiles, s.recordPosition, s.movingKmh, s.steadyFixSeconds, s.trackZero.name, s.gainWatch, s.networkPosition,
                        ),
                    ),
                )
                for (track in finished) archive.track(track.startedAt, tracks.points(track.id))
                archive.finish()
            }
        }.isSuccess
    }

    /** Whether the file at [uri] asks for a password; null when it cannot be read. */
    suspend fun isEncrypted(uri: Uri): Boolean? = withContext(Dispatchers.IO) {
        runCatching { open(uri).use { BackupCrypto.isEncrypted(it.readNBytes(BackupCrypto.MAGIC.size)) } }.getOrNull()
    }

    /**
     * Adds the recordings of the backup at [uri] to the ones here; a recording is known by when it
     * began. With [overwrite] one already here is replaced by its copy and the settings are taken
     * from the backup; without it both are left alone, and the settings are restored only into an
     * app that holds no recordings.
     */
    suspend fun restore(uri: Uri, password: String?, overwrite: Boolean): RestoreOutcome = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val manifest = open(uri).use { raw ->
                val input = BufferedInputStream(raw)
                input.mark(BackupCrypto.MAGIC.size)
                val encrypted = BackupCrypto.isEncrypted(input.readNBytes(BackupCrypto.MAGIC.size))
                input.reset()
                if (encrypted && password.isNullOrEmpty()) return@withContext RestoreOutcome.WrongPassword
                BackupArchive.read(if (encrypted) BackupCrypto.decrypt(input, password!!.toCharArray()) else input, staging)
            }
            val empty = tracks.finished().isEmpty()
            var placed = 0
            for (track in manifest.tracks) if (place(track, staging, overwrite)) placed++
            if (overwrite || empty) manifest.settings?.let { restoreSettings(it) }
            RestoreOutcome.Restored(placed)
        } catch (e: WrongPasswordException) {
            RestoreOutcome.WrongPassword
        } catch (e: Exception) {
            RestoreOutcome.Invalid
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun open(uri: Uri): InputStream = context.contentResolver.openInputStream(uri) ?: error("no stream")

    /** True when the recording was written. */
    private suspend fun place(track: BackupTrack, staging: File, overwrite: Boolean): Boolean {
        val file = File(staging, BackupArchive.trackFile(track.startedAt)).takeIf { it.isFile } ?: return false
        val ended = track.endedAt ?: return false
        val known = tracks.startedAt(track.startedAt)
        if (known != null) {
            // The one that runs now is never touched.
            if (!overwrite || known.endedAt == null) return false
            tracks.delete(known.id)
        }
        val id = tracks.insert(Track(startedAt = track.startedAt, endedAt = ended, zeroAltitude = track.zeroAltitude))
        val points = BackupArchive.points(file, id)
        if (points.isEmpty()) {
            tracks.delete(id)
            return false
        }
        points.chunked(BATCH).forEach { tracks.insertAll(it) }
        tracks.insertMarks(track.marks.take(MAX_MARKS).map { Mark(trackId = id, timeMs = it.timeMs, label = it.label.take(MAX_LABEL)) })
        return true
    }

    private suspend fun restoreSettings(b: BackupSettings) {
        val d = AppSettings()
        settings.restore(
            AppSettings(
                theme = runCatching { ThemeMode.valueOf(b.theme) }.getOrDefault(d.theme),
                palette = runCatching { Palette.valueOf(b.palette) }.getOrDefault(d.palette),
                coordinates = runCatching { CoordinateFormat.valueOf(b.coordinates) }.getOrDefault(d.coordinates),
                length = runCatching { LengthUnit.valueOf(b.length) }.getOrDefault(d.length),
                speed = runCatching { SpeedUnit.valueOf(b.speed) }.getOrDefault(d.speed),
                keepScreenOn = b.keepScreenOn,
                startTab = b.startTab,
                trueNorth = b.trueNorth,
                appUpdate = b.appUpdate,
                mapTiles = b.mapTiles,
                recordPosition = b.recordPosition,
                movingKmh = b.movingKmh.coerceIn(1, 20),
                steadyFixSeconds = b.steadyFixSeconds.coerceIn(0, 300),
                trackZero = runCatching { TrackZero.valueOf(b.trackZero) }.getOrDefault(d.trackZero),
                gainWatch = b.gainWatch,
                networkPosition = b.networkPosition,
            ),
        )
        runCatching { AltitudeSource.valueOf(b.altitudeSource) }.getOrNull()?.let { settings.setAltitudeSource(it) }
    }

    private companion object {
        const val MAX_MARKS = 10_000
        const val MAX_LABEL = 80
        const val BATCH = 2_000
    }
}
