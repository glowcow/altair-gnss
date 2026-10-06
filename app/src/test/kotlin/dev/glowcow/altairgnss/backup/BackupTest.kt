package dev.glowcow.altairgnss.backup

import dev.glowcow.altairgnss.recording.Point
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

class BackupTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val settings = BackupSettings("DARK", "WARM", "DMS", "FEET", "KNOTS", false, "RECORD", false, true, "BAROMETER")

    private fun seal(data: ByteArray, password: String) = ByteArrayOutputStream().also { out ->
        BackupCrypto.encrypt(out, password.toCharArray()).use { it.write(data) }
    }.toByteArray()

    private fun open(sealed: ByteArray, password: String) = BackupCrypto.decrypt(ByteArrayInputStream(sealed), password.toCharArray()).readBytes()

    private fun archive(): ByteArray {
        val out = ByteArrayOutputStream()
        BackupArchive.Writer(out).apply {
            manifest(BackupManifest(createdAt = 99, app = "0.2.1", tracks = listOf(BackupTrack(1000, 5000, 412.5), BackupTrack(9000, 9500)), settings = settings))
            track(
                1000,
                listOf(
                    Point(trackId = 7, timeMs = 1000, altitude = 412.5, hpa = 965.25, latitude = 46.5, longitude = 8.0, speed = 1.5f),
                    Point(trackId = 7, timeMs = 2000, altitude = 410.0, hpa = null),
                ),
            )
            track(9000, listOf(Point(trackId = 8, timeMs = 9000, altitude = -3.25, hpa = 1013.0)))
            finish()
        }
        return out.toByteArray()
    }

    @Test
    fun archiveKeepsRecordingsAndSettings() {
        val into = tmp.newFolder("restore")
        val manifest = BackupArchive.read(ByteArrayInputStream(archive()), into)

        assertEquals(listOf(1000L, 9000L), manifest.tracks.map { it.startedAt })
        assertEquals(412.5, manifest.tracks[0].zeroAltitude!!, 0.0)
        assertEquals(settings, manifest.settings)
        assertEquals("0.2.1", manifest.app)

        val points = BackupArchive.points(File(into, BackupArchive.trackFile(1000)), trackId = 42)
        assertEquals(
            listOf(
                Point(trackId = 42, timeMs = 1000, altitude = 412.5, hpa = 965.25, latitude = 46.5, longitude = 8.0, speed = 1.5f),
                Point(trackId = 42, timeMs = 2000, altitude = 410.0, hpa = null),
            ),
            points,
        )
        assertEquals(-3.25, BackupArchive.points(File(into, BackupArchive.trackFile(9000)), 1).single().altitude, 0.0)
    }

    @Test
    fun badLinesOfARecordingAreLeftOut() {
        val file = tmp.newFile("t.csv")
        file.writeText("1000,10.5,,,,\nnot a line\n2000,NaN,,,,\n3000,11.0,1000.0,95.0,8.0,2.0\n")
        val points = BackupArchive.points(file, 1)
        assertEquals(listOf(1000L, 3000L), points.map { it.timeMs })
        // A latitude beyond the pole is dropped, the rest of the line stays.
        assertEquals(null, points[1].latitude)
        assertEquals(8.0, points[1].longitude!!, 0.0)
    }

    @Test
    fun entriesABackupCannotHoldAreIgnored() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun add(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
            add(BackupArchive.MANIFEST, """{"createdAt":1,"app":"x","tracks":[]}""")
            add("../outside.csv", "1")
            add("tracks/../../escape.csv", "1")
            add("tracks/abc.csv", "1")
            add("databases/altair.db", "1")
            add("tracks/5.csv", "5,1.0,,,,\n")
        }
        val into = tmp.newFolder("restore")
        BackupArchive.read(ByteArrayInputStream(out.toByteArray()), into)
        assertEquals(listOf("tracks/5.csv"), into.walkTopDown().filter { it.isFile }.map { it.relativeTo(into).path }.toList())
        assertFalse(File(into.parentFile, "outside.csv").exists())
    }

    @Test
    fun anArchiveWithoutAManifestIsRefused() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("tracks/5.csv"))
            zip.closeEntry()
        }
        assertThrows(IOException::class.java) { BackupArchive.read(ByteArrayInputStream(out.toByteArray()), tmp.newFolder("r")) }
    }

    @Test
    fun passwordRoundTrip() {
        val data = Random(1).nextBytes(200_000)
        val sealed = seal(data, "correct horse")
        assertTrue(BackupCrypto.isEncrypted(sealed))
        assertFalse(BackupCrypto.isEncrypted(archive()))
        assertArrayEquals(data, open(sealed, "correct horse"))
        assertArrayEquals(ByteArray(0), open(seal(ByteArray(0), "p"), "p"))
    }

    @Test
    fun aWrongPasswordIsToldApartFromDamage() {
        val sealed = seal(Random(2).nextBytes(200_000), "right")
        assertThrows(WrongPasswordException::class.java) { open(sealed, "wrong") }
        // A byte flipped in a later chunk is damage, not a wrong password.
        val damaged = sealed.copyOf().also { it[it.size - 5] = (it[it.size - 5] + 1).toByte() }
        val error = assertThrows(IOException::class.java) { open(damaged, "right") }
        assertFalse(error is WrongPasswordException)
        // A file cut short does not pass for a whole one.
        assertThrows(IOException::class.java) { open(sealed.copyOf(sealed.size - 70_000), "right") }
    }
}
