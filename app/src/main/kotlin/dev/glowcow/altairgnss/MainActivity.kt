package dev.glowcow.altairgnss

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glowcow.altairgnss.data.AppSettings
import dev.glowcow.altairgnss.ui.AltairRoot
import dev.glowcow.altairgnss.ui.components.TopTab
import dev.glowcow.altairgnss.ui.components.LocalUnits
import dev.glowcow.altairgnss.ui.components.Units
import dev.glowcow.altairgnss.ui.theme.AltairTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {

    private val openTab = Channel<TopTab>(Channel.BUFFERED)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)

        val tabRequests = openTab.receiveAsFlow()
        setContent {
            // Nothing is drawn until the stored settings are read: the tab to open on is one of them.
            val stored by container.settings.settings.collectAsStateWithLifecycle<AppSettings?>(null)
            val settings = stored ?: return@setContent
            AltairTheme(settings.theme, settings.palette) {
                CompositionLocalProvider(LocalUnits provides Units(settings.length, settings.speed)) {
                    AltairRoot(settings, tabRequests)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    // A tap on a notification leads to where it can be acted on: the settings for a new version, the tab for a recording.
    private fun handleIntent(intent: Intent?) {
        if (intent?.hasExtra(EXTRA_APP_UPDATE) == true) openTab.trySend(TopTab.SETTINGS)
        if (intent?.hasExtra(EXTRA_RECORDING) == true) openTab.trySend(TopTab.RECORD)
    }

    companion object {
        const val EXTRA_APP_UPDATE = "app_update"
        const val EXTRA_RECORDING = "recording"
    }
}
