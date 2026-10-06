package dev.glowcow.altairgnss.ui

import android.app.Activity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.glowcow.altairgnss.data.AppSettings
import dev.glowcow.altairgnss.ui.altimeter.AltimeterScreen
import dev.glowcow.altairgnss.ui.instruments.CompassScreen
import dev.glowcow.altairgnss.ui.instruments.InstrumentsScreen
import dev.glowcow.altairgnss.ui.instruments.SpeedScreen
import dev.glowcow.altairgnss.ui.components.TopTab
import dev.glowcow.altairgnss.ui.recording.RecordScreen
import dev.glowcow.altairgnss.ui.recording.TrackScreen
import dev.glowcow.altairgnss.ui.recording.TrackViewScreen
import dev.glowcow.altairgnss.ui.settings.SettingsScreen
import dev.glowcow.altairgnss.ui.sky.SkyScreen
import dev.glowcow.altairgnss.ui.status.StatusScreen
import dev.glowcow.altairgnss.ui.theme.AltairTheme
import dev.glowcow.altairgnss.ui.tools.NmeaScreen
import dev.glowcow.altairgnss.ui.tools.ScatterScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable data object StatusRoute : NavKey
@Serializable data object RecordRoute : NavKey
@Serializable data object SkyRoute : NavKey
@Serializable data object AltimeterRoute : NavKey
@Serializable data object InstrumentsRoute : NavKey
@Serializable data object SettingsRoute : NavKey
@Serializable data object CompassRoute : NavKey
@Serializable data object SpeedRoute : NavKey
@Serializable data class TrackRoute(val id: Long) : NavKey
@Serializable data class TrackViewRoute(val id: Long) : NavKey
@Serializable data object NmeaRoute : NavKey
@Serializable data object ScatterRoute : NavKey

private fun TopTab.route(): NavKey = when (this) {
    TopTab.STATUS -> StatusRoute
    TopTab.RECORD -> RecordRoute
    TopTab.ALTIMETER -> AltimeterRoute
    TopTab.INSTRUMENTS -> InstrumentsRoute
    TopTab.SETTINGS -> SettingsRoute
}

@Composable
fun AltairRoot(settings: AppSettings, openTab: Flow<TopTab>) {
    // The tab chosen in the settings opens first, with Status under it.
    val start = remember { TopTab.entries.firstOrNull { it.name == settings.startTab } ?: TopTab.STATUS }
    val backStack = rememberNavBackStack(*listOfNotNull(StatusRoute, start.route().takeIf { it != StatusRoute }).toTypedArray())

    // Status is the root: any other tab sits on top of it, so back returns there.
    fun selectTab(tab: TopTab) {
        backStack.retainAll { it == StatusRoute }
        if (backStack.isEmpty()) backStack.add(StatusRoute)
        if (tab != TopTab.STATUS) backStack.add(tab.route())
    }

    LaunchedEffect(openTab) { openTab.collect { selectTab(it) } }

    val lightIcons = !AltairTheme.colors.isDark
    val ground = AltairTheme.colors.groupBg
    val view = LocalView.current
    // A dial opened full screen is meant to be watched, whatever the setting says.
    val keepOn = settings.keepScreenOn || backStack.lastOrNull().let { it == CompassRoute || it == SpeedRoute }
    SideEffect {
        view.keepScreenOn = keepOn
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        // Two pages cross-fading let the window show through; it has to be the theme's ground, not the system's.
        window.setBackgroundDrawable(ground.toArgb().toDrawable())
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightIcons
            isAppearanceLightNavigationBars = lightIcons
        }
    }

    val fade = fadeIn(tween(FADE_MS)) togetherWith fadeOut(tween(FADE_MS))
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = { fade },
        popTransitionSpec = { fade },
        predictivePopTransitionSpec = { fade },
        entryProvider = entryProvider {
            entry<StatusRoute> {
                StatusScreen(
                    onTab = ::selectTab,
                    onSky = { backStack.add(SkyRoute) },
                    onNmea = { backStack.add(NmeaRoute) },
                    onScatter = { backStack.add(ScatterRoute) },
                )
            }
            entry<SkyRoute> { SkyScreen(onBack = { backStack.removeLastOrNull() }) }
            entry<RecordRoute> { RecordScreen(onTab = ::selectTab, onOpen = { backStack.add(TrackRoute(it)) }) }
            entry<AltimeterRoute> { AltimeterScreen(onTab = ::selectTab) }
            entry<InstrumentsRoute> {
                InstrumentsScreen(onTab = ::selectTab, onCompass = { backStack.add(CompassRoute) }, onSpeed = { backStack.add(SpeedRoute) })
            }
            entry<CompassRoute> { CompassScreen(onBack = { backStack.removeLastOrNull() }) }
            entry<SpeedRoute> { SpeedScreen(onBack = { backStack.removeLastOrNull() }) }
            entry<TrackRoute> { route ->
                TrackScreen(route.id, onBack = { backStack.removeLastOrNull() }, onExpand = { backStack.add(TrackViewRoute(route.id)) })
            }
            entry<TrackViewRoute> { route -> TrackViewScreen(route.id, onBack = { backStack.removeLastOrNull() }) }
            entry<NmeaRoute> { NmeaScreen(onBack = { backStack.removeLastOrNull() }) }
            entry<ScatterRoute> { ScatterScreen(onBack = { backStack.removeLastOrNull() }) }
            entry<SettingsRoute> { SettingsScreen(onTab = ::selectTab) }
        },
    )
}

private const val FADE_MS = 180
