package dev.glowcow.altairgnss.ui.settings

import android.Manifest
import android.app.LocaleManager
import android.os.LocaleList
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.glowcow.altairgnss.AltairApp
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.data.AppSettings
import dev.glowcow.altairgnss.data.Palette
import dev.glowcow.altairgnss.data.SettingsStore
import dev.glowcow.altairgnss.data.ThemeMode
import dev.glowcow.altairgnss.gnss.CoordinateFormat
import dev.glowcow.altairgnss.gnss.GnssMonitor
import dev.glowcow.altairgnss.ui.components.ChoiceSheet
import dev.glowcow.altairgnss.ui.components.Group
import dev.glowcow.altairgnss.ui.components.GroupDivider
import dev.glowcow.altairgnss.ui.components.GroupRow
import dev.glowcow.altairgnss.ui.components.GroupSheet
import dev.glowcow.altairgnss.ui.components.SwitchRow
import dev.glowcow.altairgnss.ui.components.TabScreen
import dev.glowcow.altairgnss.ui.components.TopTab
import dev.glowcow.altairgnss.ui.theme.AltairTheme
import dev.glowcow.altairgnss.update.AppRelease
import dev.glowcow.altairgnss.update.AppUpdateState
import dev.glowcow.altairgnss.update.AppUpdater
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val store: SettingsStore, val updater: AppUpdater, val gnss: GnssMonitor) : ViewModel() {
    val settings: StateFlow<AppSettings> = store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { store.setTheme(mode) }
    fun setPalette(palette: Palette) = viewModelScope.launch { store.setPalette(palette) }
    fun setCoordinates(format: CoordinateFormat) = viewModelScope.launch { store.setCoordinates(format) }
    fun setKeepScreenOn(on: Boolean) = viewModelScope.launch { store.setKeepScreenOn(on) }
    fun setStartTab(tab: TopTab) = viewModelScope.launch { store.setStartTab(tab.name) }
    fun setTrueNorth(on: Boolean) = viewModelScope.launch { store.setTrueNorth(on) }
    fun setAppUpdate(on: Boolean) = viewModelScope.launch { store.setAppUpdate(on) }
}

@Composable
fun SettingsScreen(
    onTab: (TopTab) -> Unit,
    vm: SettingsViewModel = viewModel { (this[APPLICATION_KEY] as AltairApp).container.let { SettingsViewModel(it.settings, it.appUpdater, it.gnss) } },
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
}

private enum class Picker { THEME, PALETTE, LANGUAGE, COORDINATES, START_TAB, CLEAR_ASSIST }

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

/** Language tags with names written in that language. */
private val LANGUAGES = listOf(
    "en" to "English",
    "ru" to "Русский",
)
