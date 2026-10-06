package dev.glowcow.altairgnss.ui.tools

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.container
import dev.glowcow.altairgnss.ui.components.DetailScreen
import dev.glowcow.altairgnss.ui.components.Group
import dev.glowcow.altairgnss.ui.components.GroupDivider
import dev.glowcow.altairgnss.ui.components.GroupRow
import dev.glowcow.altairgnss.ui.theme.AltairTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** The receiver's NMEA sentences as they come, and a way to record them into a file. */
@Composable
fun NmeaScreen(onBack: () -> Unit) = DetailScreen(stringResource(R.string.tools_nmea), onBack) {
    val c = AltairTheme.colors
    val context = LocalContext.current
    val container = context.container
    val log = container.gnss.nmea
    // Collecting the state keeps the receiver running while the page is open.
    val gnss by container.gnss.state.collectAsStateWithLifecycle()
    val lines by log.lines.collectAsStateWithLifecycle()
    val recording by log.recording.collectAsStateWithLifecycle()
    val saved by log.saved.collectAsStateWithLifecycle()

    val done = stringResource(R.string.export_done)
    val failed = stringResource(R.string.export_failed)
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        container.scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)!!.use { out -> log.file.inputStream().use { it.copyTo(out) } } }.isSuccess
            }
            Toast.makeText(context, if (ok) done else failed, Toast.LENGTH_SHORT).show()
        }
    }

    Group {
        val written = recording
        if (written == null) {
            GroupRow(stringResource(R.string.nmea_record), subtitle = stringResource(R.string.nmea_record_hint), onClick = log::start)
        } else {
            GroupRow(stringResource(R.string.nmea_stop), subtitle = pluralStringResource(R.plurals.nmea_lines, written, written), onClick = log::stop)
        }
        saved?.let { count ->
            GroupDivider()
            GroupRow(stringResource(R.string.nmea_save), subtitle = pluralStringResource(R.plurals.nmea_lines, count, count), onClick = {
                export.launch("nmea-" + FILE_TIME.format(LocalDateTime.now()) + ".txt")
            })
        }
    }
    if (lines.isEmpty() || !gnss.enabled) {
        Text(stringResource(R.string.nmea_empty), color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
    } else {
        Group {
            Text(
                lines.joinToString("\n"),
                color = c.text,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontFamily = FontFamily.Monospace,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
    }
}

private val FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
