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
import androidx.compose.ui.platform.LocalView
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
import dev.glowcow.altairgnss.ui.settings.SettingsScreen
import dev.glowcow.altairgnss.ui.sky.SkyScreen
import dev.glowcow.altairgnss.ui.status.StatusScreen
import dev.glowcow.altairgnss.ui.theme.AltairTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable data object StatusRoute : NavKey
@Serializable data object SkyRoute : NavKey
@Serializable data object AltimeterRoute : NavKey
@Serializable data object InstrumentsRoute : NavKey
@Serializable data object SettingsRoute : NavKey
@Serializable data object CompassRoute : NavKey
@Serializable data object SpeedRoute : NavKey

private fun TopTab.route(): NavKey = when (this) {
    TopTab.STATUS -> StatusRoute
    TopTab.SKY -> SkyRoute
    TopTab.ALTIMETER -> AltimeterRoute
    TopTab.INSTRUMENTS -> InstrumentsRoute
    TopTab.SETTINGS -> SettingsRoute
}

@Composable
fun AltairRoot(settings: AppSettings, openSettings: Flow<Unit>) {
    // The tab chosen in the settings opens first, with Status under it.
    val start = remember { TopTab.entries.firstOrNull { it.name == settings.startTab } ?: TopTab.STATUS }
    val backStack = rememberNavBackStack(*listOfNotNull(StatusRoute, start.route().takeIf { it != StatusRoute }).toTypedArray())

    // Status is the root: any other tab sits on top of it, so back returns there.
    fun selectTab(tab: TopTab) {
        backStack.retainAll { it == StatusRoute }
        if (backStack.isEmpty()) backStack.add(StatusRoute)
        if (tab != TopTab.STATUS) backStack.add(tab.route())
    }

    LaunchedEffect(openSettings) { openSettings.collect { selectTab(TopTab.SETTINGS) } }

    val lightIcons = !AltairTheme.colors.isDark
    val view = LocalView.current
    // A dial opened full screen is meant to be watched, whatever the setting says.
    val keepOn = settings.keepScreenOn || backStack.lastOrNull().let { it == CompassRoute || it == SpeedRoute }
    SideEffect {
        view.keepScreenOn = keepOn
        val window = (view.context as? Activity)?.window ?: return@SideEffect
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
            entry<StatusRoute> { StatusScreen(onTab = ::selectTab) }
            entry<SkyRoute> { SkyScreen(onTab = ::selectTab) }
            entry<AltimeterRoute> { AltimeterScreen(onTab = ::selectTab) }
            entry<InstrumentsRoute> {
                InstrumentsScreen(onTab = ::selectTab, onCompass = { backStack.add(CompassRoute) }, onSpeed = { backStack.add(SpeedRoute) })
            }
            entry<CompassRoute> { CompassScreen(onBack = { backStack.removeLastOrNull() }) }
            entry<SpeedRoute> { SpeedScreen(onBack = { backStack.removeLastOrNull() }) }
            entry<SettingsRoute> { SettingsScreen(onTab = ::selectTab) }
        },
    )
}

private const val FADE_MS = 180
