package dev.glowcow.altairgnss.recording

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.DeleteColumn
import androidx.room3.DeleteTable
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.AutoMigrationSpec
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [Track::class, Point::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3, spec = AltairDatabase.DropUnused::class)],
)
abstract class AltairDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao

    /** Marks and the drift correction were dropped before any release kept them. */
    @DeleteTable(tableName = "marks")
    @DeleteColumn(tableName = "tracks", columnName = "endAltitude")
    class DropUnused : AutoMigrationSpec

    companion object {
        fun create(context: Context): AltairDatabase =
            Room.databaseBuilder(context, AltairDatabase::class.java, "altair.db")
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
    }
}
