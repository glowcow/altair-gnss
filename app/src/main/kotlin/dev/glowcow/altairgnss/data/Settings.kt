package dev.glowcow.altairgnss.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.glowcow.altairgnss.altimeter.AltitudeSource
import dev.glowcow.altairgnss.altimeter.Calibration
import dev.glowcow.altairgnss.altimeter.CalibrationKind
import dev.glowcow.altairgnss.gnss.CoordinateFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class Palette { WARM, CLASSIC }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val palette: Palette = Palette.CLASSIC,
    val coordinates: CoordinateFormat = CoordinateFormat.DD,
    val keepScreenOn: Boolean = true,
    /** Name of the tab the app opens on. */
    val startTab: String = "STATUS",
    /** The compass counts from true north; from magnetic north when off. */
    val trueNorth: Boolean = true,
    /** Look for a new version of the app once a week. */
    val appUpdate: Boolean = false,
)

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
    private val keepScreenOnKey = booleanPreferencesKey("keep_screen_on")
    private val startTabKey = stringPreferencesKey("start_tab")
    private val trueNorthKey = booleanPreferencesKey("true_north")
    private val appUpdateKey = booleanPreferencesKey("app_update")

    private val sourceKey = stringPreferencesKey("altitude_source")
    private val referenceKey = doublePreferencesKey("calibration_reference")
    private val calibratedKey = longPreferencesKey("calibration_time")
    private val accuracyKey = floatPreferencesKey("calibration_accuracy")
    private val kindKey = stringPreferencesKey("calibration_kind")

    val altimeter: Flow<AltimeterPrefs> = context.dataStore.data.map { p ->
        AltimeterPrefs(
            source = p[sourceKey]?.let { runCatching { AltitudeSource.valueOf(it) }.getOrNull() } ?: AltitudeSource.AUTO,
            calibration = p[referenceKey]?.let { reference ->
                Calibration(
                    referenceHpa = reference,
                    timeMs = p[calibratedKey] ?: 0,
                    accuracy = p[accuracyKey],
                    kind = p[kindKey]?.let { runCatching { CalibrationKind.valueOf(it) }.getOrNull() } ?: CalibrationKind.ALTITUDE,
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
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            theme = p[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            palette = p[paletteKey]?.let { runCatching { Palette.valueOf(it) }.getOrNull() } ?: Palette.CLASSIC,
            coordinates = p[coordinatesKey]?.let { runCatching { CoordinateFormat.valueOf(it) }.getOrNull() } ?: CoordinateFormat.DD,
            keepScreenOn = p[keepScreenOnKey] ?: true,
            startTab = p[startTabKey] ?: "STATUS",
            trueNorth = p[trueNorthKey] ?: true,
            appUpdate = p[appUpdateKey] ?: false,
        )
    }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[themeKey] = mode.name }

    suspend fun setPalette(palette: Palette) = context.dataStore.edit { it[paletteKey] = palette.name }

    suspend fun setCoordinates(format: CoordinateFormat) = context.dataStore.edit { it[coordinatesKey] = format.name }

    suspend fun setKeepScreenOn(on: Boolean) = context.dataStore.edit { it[keepScreenOnKey] = on }

    suspend fun setStartTab(tab: String) = context.dataStore.edit { it[startTabKey] = tab }

    suspend fun setTrueNorth(on: Boolean) = context.dataStore.edit { it[trueNorthKey] = on }

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
