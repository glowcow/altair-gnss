package dev.glowcow.altairgnss.recording

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.DeleteColumn
import androidx.room3.DeleteTable
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [Track::class, Point::class, Mark::class],
    version = 7,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = AltairDatabase.DropUnused::class),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7, spec = AltairDatabase.Geotag::class),
    ],
)
abstract class AltairDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao

    /** Marks and the drift correction were dropped before any release kept them. */
    @DeleteTable(tableName = "marks")
    @DeleteColumn(tableName = "tracks", columnName = "endAltitude")
    class DropUnused : AutoMigrationSpec

    /** What the switch was when an older recording began is not known: one that already has a place keeps it. */
    class Geotag : AutoMigrationSpec {
        override suspend fun onPostMigrate(connection: SQLiteConnection) = connection.execSQL("UPDATE tracks SET geotag = 1 WHERE place IS NOT NULL")
    }

    companion object {
        fun create(context: Context): AltairDatabase =
            Room.databaseBuilder(context, AltairDatabase::class.java, "altair.db")
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
    }
}
