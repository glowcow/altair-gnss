package dev.glowcow.altairgnss

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glowcow.altairgnss.data.AppSettings
import dev.glowcow.altairgnss.ui.AltairRoot
import dev.glowcow.altairgnss.ui.theme.AltairTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {

    private val openSettings = Channel<Unit>(Channel.BUFFERED)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)

        val settingsRequests = openSettings.receiveAsFlow()
        setContent {
            // Nothing is drawn until the stored settings are read: the tab to open on is one of them.
            val stored by container.settings.settings.collectAsStateWithLifecycle<AppSettings?>(null)
            val settings = stored ?: return@setContent
            AltairTheme(settings.theme, settings.palette) {
                AltairRoot(settings, settingsRequests)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    // A tap on the notification about a new version: the settings show it.
    private fun handleIntent(intent: Intent?) {
        if (intent?.hasExtra(EXTRA_APP_UPDATE) == true) openSettings.trySend(Unit)
    }

    companion object {
        const val EXTRA_APP_UPDATE = "app_update"
    }
}
