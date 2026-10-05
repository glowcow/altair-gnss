package dev.glowcow.altairgnss.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.ui.theme.AltairTheme

private fun Context.hasFineLocation() =
    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Shows [content] once the precise location permission is granted, and the way to grant it until then. */
@Composable
fun LocationGate(top: Dp, content: @Composable () -> Unit) {
    val c = AltairTheme.colors
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasFineLocation()) }
    // After a refusal the system may stop showing its dialog, so the button leads to the app settings.
    var refused by rememberSaveable { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = context.hasFineLocation()
        refused = !granted
    }
    LifecycleResumeEffect(Unit) {
        granted = context.hasFineLocation()
        onPauseOrDispose {}
    }

    if (granted) {
        content()
        return
    }
    Column(Modifier.padding(horizontal = 16.dp).padding(top = top), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.location_title), color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.location_text), color = c.muted, fontSize = 14.sp)
        }
        Group {
            if (refused) {
                GroupRow(stringResource(R.string.location_open_settings), onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                })
            } else {
                GroupRow(stringResource(R.string.location_allow), onClick = {
                    request.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                })
            }
        }
    }
}
