package dev.glowcow.altairgnss.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.glowcow.altairgnss.altimeter.AltitudeSource
import dev.glowcow.altairgnss.altimeter.Calibration
import dev.glowcow.altairgnss.altimeter.CalibrationKind
import dev.glowcow.altairgnss.gnss.CoordinateFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class Palette { WARM, CLASSIC }

/** Where the heights of a recording are counted from: its lowest point, its start or the zero set in it, or sea level. */
enum class TrackZero { LOWEST, START, SEA }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val palette: Palette = Palette.CLASSIC,
    val coordinates: CoordinateFormat = CoordinateFormat.DD,
    val length: LengthUnit = LengthUnit.METRES,
    val speed: SpeedUnit = SpeedUnit.KMH,
    val keepScreenOn: Boolean = true,
    /** Name of the tab the app opens on. */
    val startTab: String = "STATUS",
    /** The compass counts from true north; from magnetic north when off. */
    val trueNorth: Boolean = true,
    /** Look for a new version of the app once a week. */
    val appUpdate: Boolean = false,
    /** A recording keeps the position and the speed of its points; off, it is altitude alone and leaves the receiver be. */
    val recordPosition: Boolean = true,
    /** A recording places its points by the phone's blend of satellites, Wi-Fi and cell towers. */
    val networkPosition: Boolean = false,
    /** Slower than this many km/h a recording counts as standing. */
    val movingKmh: Int = 3,
    /** A recording with positions starts once the receiver has held a fix for this many seconds; 0 starts at once. */
    val steadyFixSeconds: Int = 15,
    val trackZero: TrackZero = TrackZero.LOWEST,
    /** Listen to the receiver's gain to tell interference sooner. */
    val gainWatch: Boolean = false,
    /** Draw a recording's track on a map downloaded for it. */
    val mapTiles: Boolean = false,
) {
    /** [movingKmh] in metres per second. */
    val movingSpeed: Float get() = movingKmh / 3.6f
}

/** What the altimeter remembers between launches. */
data class AltimeterPrefs(
    val source: AltitudeSource = AltitudeSource.AUTO,
    val calibration: Calibration? = null,
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val paletteKey = stringPreferencesKey("palette")
    private val coordinatesKey = stringPreferencesKey("coordinates")
    private val lengthKey = stringPreferencesKey("length_unit")
    private val speedKey = stringPreferencesKey("speed_unit")
    private val keepScreenOnKey = booleanPreferencesKey("keep_screen_on")
    private val startTabKey = stringPreferencesKey("start_tab")
    private val trueNorthKey = booleanPreferencesKey("true_north")
    private val appUpdateKey = booleanPreferencesKey("app_update")
    private val mapTilesKey = booleanPreferencesKey("map_tiles")
    private val recordPositionKey = booleanPreferencesKey("record_position")
    private val networkPositionKey = booleanPreferencesKey("network_position")
    private val movingKey = intPreferencesKey("moving_kmh")
    private val steadyFixKey = intPreferencesKey("steady_fix_seconds")
    private val trackZeroKey = stringPreferencesKey("track_zero")
    private val gainWatchKey = booleanPreferencesKey("gain_watch")
    private val gainBaselineKey = stringPreferencesKey("gain_baseline")

    private val sourceKey = stringPreferencesKey("altitude_source")
    private val referenceKey = doublePreferencesKey("calibration_reference")
    private val calibratedKey = longPreferencesKey("calibration_time")
    private val accuracyKey = floatPreferencesKey("calibration_accuracy")
    private val kindKey = stringPreferencesKey("calibration_kind")
    private val temperatureKey = doublePreferencesKey("calibration_temperature")
    private val baseKey = doublePreferencesKey("calibration_base")

    val altimeter: Flow<AltimeterPrefs> = context.dataStore.data.map { p ->
        AltimeterPrefs(
            source = p[sourceKey]?.let { runCatching { AltitudeSource.valueOf(it) }.getOrNull() } ?: AltitudeSource.AUTO,
            calibration = p[referenceKey]?.let { reference ->
                Calibration(
                    referenceHpa = reference,
                    timeMs = p[calibratedKey] ?: 0,
                    accuracy = p[accuracyKey],
                    kind = p[kindKey]?.let { runCatching { CalibrationKind.valueOf(it) }.getOrNull() } ?: CalibrationKind.ALTITUDE,
                    temperatureC = p[temperatureKey],
                    baseAltitude = p[baseKey],
                )
            },
        )
    }.distinctUntilChanged()

    suspend fun setAltitudeSource(source: AltitudeSource) = context.dataStore.edit { it[sourceKey] = source.name }

    suspend fun setCalibration(calibration: Calibration) = context.dataStore.edit {
        it[referenceKey] = calibration.referenceHpa
        it[calibratedKey] = calibration.timeMs
        it[kindKey] = calibration.kind.name
        if (calibration.accuracy != null) it[accuracyKey] = calibration.accuracy else it.remove(accuracyKey)
        if (calibration.temperatureC != null) it[temperatureKey] = calibration.temperatureC else it.remove(temperatureKey)
        if (calibration.baseAltitude != null) it[baseKey] = calibration.baseAltitude else it.remove(baseKey)
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            theme = p[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            palette = p[paletteKey]?.let { runCatching { Palette.valueOf(it) }.getOrNull() } ?: Palette.CLASSIC,
            coordinates = p[coordinatesKey]?.let { runCatching { CoordinateFormat.valueOf(it) }.getOrNull() } ?: CoordinateFormat.DD,
            length = p[lengthKey]?.let { runCatching { LengthUnit.valueOf(it) }.getOrNull() } ?: LengthUnit.METRES,
            speed = p[speedKey]?.let { runCatching { SpeedUnit.valueOf(it) }.getOrNull() } ?: SpeedUnit.KMH,
            keepScreenOn = p[keepScreenOnKey] ?: true,
            startTab = p[startTabKey] ?: "STATUS",
            trueNorth = p[trueNorthKey] ?: true,
            appUpdate = p[appUpdateKey] ?: false,
            mapTiles = p[mapTilesKey] ?: false,
            recordPosition = p[recordPositionKey] ?: true,
            networkPosition = p[networkPositionKey] ?: false,
            movingKmh = p[movingKey] ?: 3,
            steadyFixSeconds = p[steadyFixKey] ?: 15,
            trackZero = p[trackZeroKey]?.let { runCatching { TrackZero.valueOf(it) }.getOrNull() } ?: TrackZero.LOWEST,
            gainWatch = p[gainWatchKey] ?: false,
        )
    }

    /** Replaces every setting at once, as when a backup is restored. */
    suspend fun restore(s: AppSettings) = context.dataStore.edit {
        it[themeKey] = s.theme.name
        it[paletteKey] = s.palette.name
        it[coordinatesKey] = s.coordinates.name
        it[lengthKey] = s.length.name
        it[speedKey] = s.speed.name
        it[keepScreenOnKey] = s.keepScreenOn
        it[startTabKey] = s.startTab
        it[trueNorthKey] = s.trueNorth
        it[appUpdateKey] = s.appUpdate
        it[mapTilesKey] = s.mapTiles
        it[recordPositionKey] = s.recordPosition
        it[networkPositionKey] = s.networkPosition
        it[movingKey] = s.movingKmh
        it[steadyFixKey] = s.steadyFixSeconds
        it[trackZeroKey] = s.trackZero.name
        it[gainWatchKey] = s.gainWatch
    }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[themeKey] = mode.name }

    suspend fun setPalette(palette: Palette) = context.dataStore.edit { it[paletteKey] = palette.name }

    suspend fun setCoordinates(format: CoordinateFormat) = context.dataStore.edit { it[coordinatesKey] = format.name }

    suspend fun setLength(unit: LengthUnit) = context.dataStore.edit { it[lengthKey] = unit.name }

    suspend fun setSpeed(unit: SpeedUnit) = context.dataStore.edit { it[speedKey] = unit.name }

    suspend fun setKeepScreenOn(on: Boolean) = context.dataStore.edit { it[keepScreenOnKey] = on }

    suspend fun setStartTab(tab: String) = context.dataStore.edit { it[startTabKey] = tab }

    suspend fun setTrueNorth(on: Boolean) = context.dataStore.edit { it[trueNorthKey] = on }

    suspend fun setRecordPosition(on: Boolean) = context.dataStore.edit { it[recordPositionKey] = on }

    suspend fun setNetworkPosition(on: Boolean) = context.dataStore.edit { it[networkPositionKey] = on }

    suspend fun setMovingKmh(kmh: Int) = context.dataStore.edit { it[movingKey] = kmh }

    suspend fun setSteadyFixSeconds(seconds: Int) = context.dataStore.edit { it[steadyFixKey] = seconds }

    suspend fun setTrackZero(zero: TrackZero) = context.dataStore.edit { it[trackZeroKey] = zero.name }

    suspend fun setGainWatch(on: Boolean) = context.dataStore.edit { it[gainWatchKey] = on }

    /** The receiver's gain under a quiet sky, by band, as [dev.glowcow.altairgnss.gnss.GainWatch] learnt it. */
    suspend fun gainBaseline(): Map<String, Double> = context.dataStore.data.first()[gainBaselineKey].orEmpty()
        .split(';').mapNotNull { pair -> pair.split('=').takeIf { it.size == 2 }?.let { (band, level) -> level.toDoubleOrNull()?.let { band to it } } }.toMap()

    suspend fun setGainBaseline(levels: Map<String, Double>) = context.dataStore.edit {
        it[gainBaselineKey] = levels.entries.joinToString(";") { (band, level) -> "$band=$level" }
    }

    suspend fun setMapTiles(on: Boolean) = context.dataStore.edit { it[mapTilesKey] = on }

    suspend fun setAppUpdate(on: Boolean) = context.dataStore.edit { it[appUpdateKey] = on }

    /** True the first time it is called for [task]. */
    suspend fun firstRun(task: String): Boolean {
        val key = booleanPreferencesKey("done_$task")
        var first = false
        context.dataStore.edit {
            first = it[key] != true
            it[key] = true
        }
        return first
    }
}
