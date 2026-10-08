package dev.glowcow.altairgnss.ui.settings

import android.Manifest
import android.app.LocaleManager
import android.net.Uri
import android.os.LocaleList
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.glowcow.altairgnss.AltairApp
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.backup.Backup
import dev.glowcow.altairgnss.backup.RestoreOutcome
import dev.glowcow.altairgnss.data.AppSettings
import dev.glowcow.altairgnss.data.LengthUnit
import dev.glowcow.altairgnss.data.Palette
import dev.glowcow.altairgnss.data.SettingsStore
import dev.glowcow.altairgnss.data.SpeedUnit
import dev.glowcow.altairgnss.data.ThemeMode
import dev.glowcow.altairgnss.data.TrackZero
import dev.glowcow.altairgnss.gnss.CoordinateFormat
import dev.glowcow.altairgnss.gnss.GnssMonitor
import dev.glowcow.altairgnss.maps.TileStore
import dev.glowcow.altairgnss.ui.components.ChoiceSheet
import dev.glowcow.altairgnss.ui.components.Group
import dev.glowcow.altairgnss.ui.components.GroupDivider
import dev.glowcow.altairgnss.ui.components.GroupRow
import dev.glowcow.altairgnss.ui.components.GroupSheet
import dev.glowcow.altairgnss.ui.components.LocalUnits
import dev.glowcow.altairgnss.ui.components.SwitchRow
import dev.glowcow.altairgnss.ui.components.TabScreen
import dev.glowcow.altairgnss.ui.components.TopTab
import dev.glowcow.altairgnss.ui.components.speedText
import dev.glowcow.altairgnss.ui.theme.AltairTheme
import dev.glowcow.altairgnss.ui.theme.AppFont
import dev.glowcow.altairgnss.update.AppRelease
import dev.glowcow.altairgnss.update.AppUpdateState
import dev.glowcow.altairgnss.update.AppUpdater
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class SettingsViewModel(
    private val store: SettingsStore,
    val updater: AppUpdater,
    val gnss: GnssMonitor,
    private val backup: Backup,
    private val tiles: TileStore,
) : ViewModel() {
    /** Bytes of downloaded map on the phone; null until counted. */
    var mapCache by mutableStateOf<Long?>(null)
        private set

    init {
        viewModelScope.launch { mapCache = tiles.size() }
    }

    fun setMapTiles(on: Boolean) = viewModelScope.launch { store.setMapTiles(on) }
    fun setMovingKmh(kmh: Int) = viewModelScope.launch { store.setMovingKmh(kmh) }
    fun setSteadyFix(seconds: Int) = viewModelScope.launch { store.setSteadyFixSeconds(seconds) }
    fun setTrackZero(zero: TrackZero) = viewModelScope.launch { store.setTrackZero(zero) }

    fun clearMapCache() = viewModelScope.launch {
        tiles.clear()
        mapCache = tiles.size()
    }

    /** A backup is being written or read. */
    var busy by mutableStateOf(false)
        private set

    fun saveBackup(uri: Uri, password: String, onDone: (Boolean) -> Unit) = work { onDone(backup.save(uri, password)) }

    /** Tells whether the file at [uri] wants a password; null when it cannot be read. */
    fun inspectBackup(uri: Uri, onDone: (Boolean?) -> Unit) = work { onDone(backup.isEncrypted(uri)) }

    fun restoreBackup(uri: Uri, password: String, overwrite: Boolean, onDone: (RestoreOutcome) -> Unit) = work {
        onDone(backup.restore(uri, password, overwrite))
    }

    private fun work(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                busy = false
            }
        }
    }

    val settings: StateFlow<AppSettings> = store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { store.setTheme(mode) }
    fun setPalette(palette: Palette) = viewModelScope.launch { store.setPalette(palette) }
    fun setCoordinates(format: CoordinateFormat) = viewModelScope.launch { store.setCoordinates(format) }
    fun setLength(unit: LengthUnit) = viewModelScope.launch { store.setLength(unit) }
    fun setSpeed(unit: SpeedUnit) = viewModelScope.launch { store.setSpeed(unit) }
    fun setKeepScreenOn(on: Boolean) = viewModelScope.launch { store.setKeepScreenOn(on) }
    fun setStartTab(tab: TopTab) = viewModelScope.launch { store.setStartTab(tab.name) }
    fun setTrueNorth(on: Boolean) = viewModelScope.launch { store.setTrueNorth(on) }
    fun setGainWatch(on: Boolean) = viewModelScope.launch { store.setGainWatch(on) }
    fun setAppUpdate(on: Boolean) = viewModelScope.launch { store.setAppUpdate(on) }
}

@Composable
fun SettingsScreen(
    onTab: (TopTab) -> Unit,
    vm: SettingsViewModel = viewModel { (this[APPLICATION_KEY] as AltairApp).container.let { SettingsViewModel(it.settings, it.appUpdater, it.gnss, it.backup, it.tiles) } },
) {
    val c = AltairTheme.colors
    val settings by vm.settings.collectAsStateWithLifecycle()
    val update by vm.updater.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The system keeps the per-app language, shared with Settings → Apps → App language.
    val locales = remember(context) { context.getSystemService(LocaleManager::class.java) }
    var language by remember { mutableStateOf(locales.applicationLocales.takeUnless { it.isEmpty }?.get(0)?.language) }
    var picker by rememberSaveable { mutableStateOf<Picker?>(null) }
    var release by remember { mutableStateOf<AppRelease?>(null) }
    // What the receiver answered to the last assistance request; null before any.
    var assisted by remember { mutableStateOf<Boolean?>(null) }
    // The weekly check reports through a notification, so turning it on asks for the permission.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun setAppUpdate(on: Boolean) {
        vm.setAppUpdate(on)
        if (on) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    fun setLanguage(tag: String?) {
        language = tag
        locales.applicationLocales = tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
    }
    val startTab = TopTab.entries.firstOrNull { it.name == settings.startTab } ?: TopTab.STATUS
    // Where a backup goes and where it comes from is the user's pick in the system file picker.
    var sheet by rememberSaveable { mutableStateOf<BackupSheet?>(null) }
    var password by remember { mutableStateOf("") }
    var source by remember { mutableStateOf<Uri?>(null) }
    var locked by remember { mutableStateOf(false) }
    var overwrite by remember { mutableStateOf(false) }
    val resources = LocalResources.current
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    fun restored(outcome: RestoreOutcome) {
        toast(
            when (outcome) {
                is RestoreOutcome.Restored -> resources.getString(R.string.backup_restored, outcome.recordings)
                RestoreOutcome.WrongPassword -> resources.getString(R.string.backup_wrong_password)
                RestoreOutcome.Invalid -> resources.getString(R.string.backup_invalid)
            },
        )
        // A mistyped password is asked for again, for the same file.
        if (outcome == RestoreOutcome.WrongPassword) sheet = BackupSheet.RESTORE
    }
    val saveTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.saveBackup(uri, password) { toast(resources.getString(if (it) R.string.backup_saved else R.string.backup_save_failed)) }
        password = ""
    }
    val restoreFrom = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.inspectBackup(uri) { encrypted ->
                if (encrypted == null) return@inspectBackup restored(RestoreOutcome.Invalid)
                source = uri
                locked = encrypted
                overwrite = false
                sheet = BackupSheet.RESTORE
            }
        }
    }

    TabScreen(TopTab.SETTINGS, onTab) { top, bottom ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = top, bottom = bottom + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Group {
                GroupRow(
                    stringResource(R.string.settings_theme),
                    value = stringResource(THEMES.first { it.first == settings.theme }.second),
                    onClick = { picker = Picker.THEME },
                )
                GroupDivider()
                GroupRow(
                    stringResource(R.string.settings_palette),
                    value = stringResource(PALETTES.first { it.first == settings.palette }.second),
                    onClick = { picker = Picker.PALETTE },
                )
                GroupDivider()
                GroupRow(
                    stringResource(R.string.settings_language),
                    value = LANGUAGES.firstOrNull { it.first == language }?.second ?: stringResource(R.string.language_system),
                    onClick = { picker = Picker.LANGUAGE },
                )
            }
            Group {
                SwitchRow(stringResource(R.string.settings_keep_screen_on), stringResource(R.string.settings_keep_screen_on_hint), settings.keepScreenOn, vm::setKeepScreenOn)
                GroupDivider()
                GroupRow(stringResource(R.string.settings_start_tab), value = stringResource(startTab.label), onClick = { picker = Picker.START_TAB })
            }
            Group {
                GroupRow(
                    stringResource(R.string.settings_coordinates),
                    value = stringResource(COORDINATES.first { it.first == settings.coordinates }.second),
                    onClick = { picker = Picker.COORDINATES },
                )
                GroupDivider()
                GroupRow(
                    stringResource(R.string.settings_length),
                    value = stringResource(LENGTHS.first { it.first == settings.length }.second),
                    onClick = { picker = Picker.LENGTH },
                )
                GroupDivider()
                GroupRow(
                    stringResource(R.string.settings_speed),
                    value = stringResource(SPEEDS.first { it.first == settings.speed }.second),
                    onClick = { picker = Picker.SPEED },
                )
                GroupDivider()
                SwitchRow(stringResource(R.string.settings_true_north), stringResource(R.string.settings_true_north_hint), settings.trueNorth, vm::setTrueNorth)
            }
            Group {
                GroupRow(stringResource(R.string.settings_source), subtitle = stringResource(R.string.settings_source_hint))
                GroupDivider()
                GroupRow(
                    stringResource(R.string.settings_assist_refresh),
                    subtitle = stringResource(R.string.settings_assist_refresh_hint),
                    onClick = { assisted = vm.gnss.assist(clear = false) },
                )
                GroupDivider()
                GroupRow(
                    stringResource(R.string.settings_assist_clear),
                    subtitle = stringResource(R.string.settings_assist_clear_hint),
                    onClick = { picker = Picker.CLEAR_ASSIST },
                )
                GroupDivider()
                SwitchRow(stringResource(R.string.settings_gain), stringResource(R.string.settings_gain_hint), settings.gainWatch, vm::setGainWatch)
            }
            assisted?.let {
                Text(
                    stringResource(if (it) R.string.assist_sent else R.string.assist_refused),
                    color = c.muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            Group {
                GroupRow(
                    stringResource(R.string.settings_moving),
                    subtitle = stringResource(R.string.settings_moving_hint),
                    value = movingText(settings.movingKmh),
                    onClick = { picker = Picker.MOVING },
                )
                GroupDivider()
                GroupRow(
                    stringResource(R.string.settings_steady_fix),
                    subtitle = stringResource(R.string.settings_steady_fix_hint),
                    value = steadyText(settings.steadyFixSeconds),
                    onClick = { picker = Picker.STEADY_FIX },
                )
                GroupDivider()
                GroupRow(
                    stringResource(R.string.settings_track_zero),
                    subtitle = stringResource(R.string.settings_track_zero_hint),
                    value = stringResource(TRACK_ZEROS.first { it.first == settings.trackZero }.second),
                    onClick = { picker = Picker.TRACK_ZERO },
                )
            }
            Group {
                SwitchRow(stringResource(R.string.settings_map), stringResource(R.string.settings_map_hint), settings.mapTiles, vm::setMapTiles)
                GroupDivider()
                GroupRow(
                    stringResource(R.string.settings_map_clear),
                    value = vm.mapCache?.let { Formatter.formatShortFileSize(context, it) },
                    onClick = vm::clearMapCache,
                    trailing = {},
                )
            }
            Group {
                GroupRow(
                    stringResource(R.string.settings_backup_save),
                    subtitle = stringResource(R.string.settings_backup_save_hint),
                    onClick = if (vm.busy) null else ({ sheet = BackupSheet.SAVE }),
                )
                GroupDivider()
                GroupRow(
                    stringResource(R.string.settings_backup_restore),
                    subtitle = stringResource(R.string.settings_backup_restore_hint),
                    onClick = if (vm.busy) null else ({ restoreFrom.launch(arrayOf("*/*")) }),
                )
            }
            Group {
                GroupRow(
                    stringResource(R.string.settings_version),
                    subtitle = when (val s = update) {
                        AppUpdateState.Idle -> if (vm.updater.supported) stringResource(R.string.app_update_check) else stringResource(R.string.app_update_dev)
                        AppUpdateState.Checking -> stringResource(R.string.app_update_checking)
                        AppUpdateState.UpToDate -> stringResource(R.string.app_update_latest)
                        AppUpdateState.Failed -> stringResource(R.string.app_update_failed)
                        is AppUpdateState.Available -> stringResource(R.string.app_update_available, s.release.version)
                        is AppUpdateState.Downloading -> stringResource(R.string.app_update_downloading, (s.progress * 100).toInt())
                    },
                    value = vm.updater.current,
                    onClick = when (val s = update) {
                        AppUpdateState.Checking, is AppUpdateState.Downloading -> null
                        is AppUpdateState.Available -> ({ release = s.release })
                        else -> if (vm.updater.supported) vm.updater::checkNow else null
                    },
                )
                if (vm.updater.supported) {
                    GroupDivider()
                    SwitchRow(stringResource(R.string.settings_app_update), stringResource(R.string.settings_app_update_hint), settings.appUpdate, ::setAppUpdate)
                }
            }
            Group {
                GroupRow(stringResource(R.string.settings_licence), value = "GPL-3.0-or-later")
                GroupDivider()
                GroupRow(stringResource(R.string.settings_map_images), value = "NASA Blue Marble")
                GroupDivider()
                GroupRow(stringResource(R.string.settings_map_data), value = "OpenStreetMap · ODbL")
                GroupDivider()
                GroupRow(stringResource(R.string.settings_font), value = "Arimo · SIL OFL 1.1")
            }
        }
    }

    release?.let { r ->
        GroupSheet(stringResource(R.string.app_update_available, r.version), onDismiss = { release = null }) { pick ->
            if (r.notes.isNotEmpty()) {
                Text(
                    r.notes,
                    color = c.muted,
                    fontSize = 14.sp,
                    modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()).padding(start = 4.dp, end = 4.dp, bottom = 14.dp),
                )
            }
            Group { GroupRow(stringResource(R.string.app_update_install), onClick = { pick { vm.updater.install(r) } }) }
        }
    }

    when (picker) {
        Picker.THEME -> ChoiceSheet(
            title = stringResource(R.string.settings_theme),
            options = THEMES.map { (mode, label) -> mode to stringResource(label) },
            selected = settings.theme,
            onSelect = { vm.setTheme(it) },
            onDismiss = { picker = null },
        )
        Picker.PALETTE -> ChoiceSheet(
            title = stringResource(R.string.settings_palette),
            options = PALETTES.map { (palette, label) -> palette to stringResource(label) },
            selected = settings.palette,
            onSelect = { vm.setPalette(it) },
            onDismiss = { picker = null },
        )
        Picker.LANGUAGE -> ChoiceSheet(
            title = stringResource(R.string.settings_language),
            options = listOf<Pair<String?, String>>(null to stringResource(R.string.language_system)) + LANGUAGES,
            selected = language,
            onSelect = ::setLanguage,
            onDismiss = { picker = null },
        )
        Picker.LENGTH -> ChoiceSheet(
            title = stringResource(R.string.settings_length),
            options = LENGTHS.map { (unit, label) -> unit to stringResource(label) },
            selected = settings.length,
            onSelect = vm::setLength,
            onDismiss = { picker = null },
        )
        Picker.SPEED -> ChoiceSheet(
            title = stringResource(R.string.settings_speed),
            options = SPEEDS.map { (unit, label) -> unit to stringResource(label) },
            selected = settings.speed,
            onSelect = vm::setSpeed,
            onDismiss = { picker = null },
        )
        Picker.MOVING -> ChoiceSheet(
            title = stringResource(R.string.settings_moving),
            options = MOVING_KMH.map { it to movingText(it) },
            selected = settings.movingKmh,
            onSelect = vm::setMovingKmh,
            onDismiss = { picker = null },
        )
        Picker.STEADY_FIX -> ChoiceSheet(
            title = stringResource(R.string.settings_steady_fix),
            options = STEADY_SECONDS.map { it to steadyText(it) },
            selected = settings.steadyFixSeconds,
            onSelect = vm::setSteadyFix,
            onDismiss = { picker = null },
        )
        Picker.TRACK_ZERO -> ChoiceSheet(
            title = stringResource(R.string.settings_track_zero),
            options = TRACK_ZEROS.map { (zero, label) -> zero to stringResource(label) },
            selected = settings.trackZero,
            onSelect = vm::setTrackZero,
            onDismiss = { picker = null },
        )
        Picker.COORDINATES -> ChoiceSheet(
            title = stringResource(R.string.settings_coordinates),
            options = COORDINATES.map { (format, label) -> format to stringResource(label) },
            selected = settings.coordinates,
            onSelect = { vm.setCoordinates(it) },
            onDismiss = { picker = null },
        )
        Picker.START_TAB -> ChoiceSheet(
            title = stringResource(R.string.settings_start_tab),
            options = TopTab.entries.filter { it != TopTab.SETTINGS }.map { it to stringResource(it.label) },
            selected = startTab,
            onSelect = { vm.setStartTab(it) },
            onDismiss = { picker = null },
        )
        // Clearing costs the next fix minutes, in every app, so it asks first.
        Picker.CLEAR_ASSIST -> GroupSheet(stringResource(R.string.assist_clear_title), onDismiss = { picker = null }) { pick ->
            Text(stringResource(R.string.assist_clear_text), color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 14.dp))
            Group { GroupRow(stringResource(R.string.assist_clear_confirm), onClick = { pick { assisted = vm.gnss.assist(clear = true) } }) }
        }
        null -> Unit
    }

    when (sheet) {
        BackupSheet.SAVE -> GroupSheet(stringResource(R.string.settings_backup_save), onDismiss = { sheet = null }) { pick ->
            Text(stringResource(R.string.backup_password_note), color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 14.dp))
            PasswordField(password) { password = it }
            Group(Modifier.padding(top = 12.dp)) {
                GroupRow(stringResource(R.string.backup_save_pick), onClick = { pick { saveTo.launch(backupName()) } })
            }
        }
        BackupSheet.RESTORE -> GroupSheet(stringResource(R.string.settings_backup_restore), onDismiss = { sheet = null }) { pick ->
            var typed by remember { mutableStateOf("") }
            if (locked) {
                Text(stringResource(R.string.backup_encrypted), color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 14.dp))
                PasswordField(typed) { typed = it }
            }
            Group(Modifier.padding(top = if (locked) 12.dp else 0.dp)) {
                SwitchRow(stringResource(R.string.backup_overwrite), stringResource(R.string.backup_overwrite_hint), overwrite) { overwrite = it }
            }
            Group(Modifier.padding(top = 12.dp)) {
                GroupRow(
                    stringResource(R.string.backup_restore),
                    onClick = { pick { source?.let { vm.restoreBackup(it, typed, overwrite, ::restored) } } },
                )
            }
        }
        null -> Unit
    }
}

private enum class BackupSheet { SAVE, RESTORE }

/** `altair-gnss-2026-10-06.altair` */
private fun backupName() = "altair-gnss-${LocalDate.now()}.altair"

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit) {
    val c = AltairTheme.colors
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(20.dp)).background(c.group).padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(stringResource(R.string.backup_password), color = c.muted)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = c.text, fontFamily = AppFont, fontSize = 15.sp),
            cursorBrush = SolidColor(c.accent),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private enum class Picker { THEME, PALETTE, LANGUAGE, COORDINATES, LENGTH, SPEED, MOVING, STEADY_FIX, TRACK_ZERO, START_TAB, CLEAR_ASSIST }

private val TRACK_ZEROS = listOf(
    TrackZero.LOWEST to R.string.track_zero_lowest,
    TrackZero.START to R.string.track_zero_start,
    TrackZero.SEA to R.string.track_zero_sea,
)

private val THEMES = listOf(
    ThemeMode.SYSTEM to R.string.theme_system,
    ThemeMode.LIGHT to R.string.theme_light,
    ThemeMode.DARK to R.string.theme_dark,
)

private val PALETTES = listOf(
    Palette.CLASSIC to R.string.palette_classic,
    Palette.WARM to R.string.palette_warm,
)

private val COORDINATES = listOf(
    CoordinateFormat.DD to R.string.coordinates_dd,
    CoordinateFormat.DDM to R.string.coordinates_ddm,
    CoordinateFormat.DMS to R.string.coordinates_dms,
)

/** A speed of so many km/h in the unit chosen: whole in km/h, to a tenth in the others. */
@Composable
private fun movingText(kmh: Int): String = speedText(kmh / 3.6f, if (LocalUnits.current.speed == SpeedUnit.KMH) 0 else 1)

@Composable
private fun steadyText(seconds: Int): String =
    if (seconds == 0) stringResource(R.string.steady_fix_off) else stringResource(R.string.unit_seconds, seconds.toString())

private val MOVING_KMH = listOf(1, 2, 3, 5, 8)

private val STEADY_SECONDS = listOf(0, 5, 10, 15, 30, 60)

private val LENGTHS = listOf(
    LengthUnit.METRES to R.string.length_metres,
    LengthUnit.FEET to R.string.length_feet,
)

private val SPEEDS = listOf(
    SpeedUnit.KMH to R.string.speed_kmh,
    SpeedUnit.MPH to R.string.speed_mph,
    SpeedUnit.KNOTS to R.string.speed_knots,
)

/** Language tags with names written in that language. */
private val LANGUAGES = listOf(
    "en" to "English",
    "be" to "Беларуская",
    "de" to "Deutsch",
    "es" to "Español",
    "fr" to "Français",
    "it" to "Italiano",
    "pl" to "Polski",
    "ru" to "Русский",
    "sr" to "Српски",
    "he" to "עברית",
)
