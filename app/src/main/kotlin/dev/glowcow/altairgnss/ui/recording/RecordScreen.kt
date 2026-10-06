package dev.glowcow.altairgnss.ui.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.altimeter.AltitudeSource
import dev.glowcow.altairgnss.container
import dev.glowcow.altairgnss.data.AppSettings
import dev.glowcow.altairgnss.recording.Track
import dev.glowcow.altairgnss.ui.components.ChoiceSheet
import dev.glowcow.altairgnss.ui.components.Group
import dev.glowcow.altairgnss.ui.components.GroupDivider
import dev.glowcow.altairgnss.ui.components.GroupRow
import dev.glowcow.altairgnss.ui.components.LocationGate
import dev.glowcow.altairgnss.ui.components.NO_VALUE
import dev.glowcow.altairgnss.ui.components.StatGrid
import dev.glowcow.altairgnss.ui.components.SwitchRow
import dev.glowcow.altairgnss.ui.components.TabScreen
import dev.glowcow.altairgnss.ui.components.TopTab
import dev.glowcow.altairgnss.ui.components.clock
import dev.glowcow.altairgnss.ui.components.distanceText
import dev.glowcow.altairgnss.ui.components.lengthText
import dev.glowcow.altairgnss.ui.components.signedLengthText
import dev.glowcow.altairgnss.ui.components.speedText
import dev.glowcow.altairgnss.ui.components.verticalSpeedText
import dev.glowcow.altairgnss.ui.theme.AltairTheme
import kotlinx.coroutines.launch

/** The tab of recordings: what is measured now, the two buttons that start and stop a recording, and those made before. */
@Composable
fun RecordScreen(onTab: (TopTab) -> Unit, onOpen: (Long) -> Unit) = TabScreen(TopTab.RECORD, onTab) { top, bottom ->
    LocationGate(top) { RecordContent(top, bottom, onOpen) }
}

@Composable
private fun RecordContent(top: Dp, bottom: Dp, onOpen: (Long) -> Unit) {
    val c = AltairTheme.colors
    val context = LocalContext.current
    val container = context.container
    val recorder = container.recorder
    // Collecting both keeps the sensors and the receiver running while the tab is open.
    val altimeter by container.altimeter.state.collectAsStateWithLifecycle()
    val gnss by container.gnss.state.collectAsStateWithLifecycle()
    val active by recorder.active.collectAsStateWithLifecycle()
    val progress by recorder.live.collectAsStateWithLifecycle()
    // While the recording waits for the receiver it has no figures yet.
    val live = progress?.takeIf { it.waitingSeconds == null }
    val tracks by remember { container.tracks.observeTracks() }.collectAsStateWithLifecycle(null)
    // The recording shows itself in a notification; it starts whatever the answer is.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { recorder.start() }
    val recording = active != null
    val settings by remember { container.settings.settings }.collectAsStateWithLifecycle(AppSettings())
    var choosingSource by rememberSaveable { mutableStateOf(false) }
    val sourceName = stringResource(SOURCES.first { it.first == altimeter.source }.second)
    val fix = gnss.fix?.takeIf { gnss.hasFix }

    val from = @Composable { v: Double? -> v?.let { signedLengthText(it) } ?: NO_VALUE }
    val metres = @Composable { v: Double? -> v?.let { lengthText(it) } ?: NO_VALUE }
    val speed = @Composable { v: Float? -> v?.let { speedText(it) } ?: NO_VALUE }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = top, bottom = bottom + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // What is measured now comes first; what a recording adds up fills in once it runs.
        Group {
            StatGrid(
                listOf(
                    stringResource(R.string.label_elapsed) to (live?.let { clock(it.elapsedMs) } ?: NO_VALUE),
                    stringResource(R.string.label_moving_time) to (live?.movingMs?.let { clock(it) } ?: NO_VALUE),
                    stringResource(R.string.label_distance) to (live?.let { distanceText(it.distance) } ?: NO_VALUE),
                    stringResource(R.string.label_speed) to speed(fix?.speed),
                    stringResource(R.string.label_speed_average) to speed(live?.averageSpeed),
                    stringResource(R.string.label_speed_max) to speed(live?.maxSpeed),
                    stringResource(R.string.label_altitude) to metres(altimeter.altitude),
                    stringResource(R.string.label_vertical_speed) to (altimeter.verticalSpeed?.let { verticalSpeedText(it) } ?: NO_VALUE),
                    stringResource(R.string.label_climbed) to metres(live?.gain),
                    stringResource(R.string.label_descended) to metres(live?.loss),
                    stringResource(R.string.label_lowest) to from(live?.lowest),
                    stringResource(R.string.label_highest) to from(live?.highest),
                    stringResource(R.string.label_from_zero) to from(live?.fromZero),
                ),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // The button says what the recording is doing: waiting for the receiver, or running.
            val waiting = progress?.waitingSeconds != null
            RecordButton(
                stringResource(
                    when {
                        waiting -> R.string.recording_waiting_short
                        recording -> R.string.recording_running
                        else -> R.string.recording_record
                    },
                ),
                enabled = !recording,
                mark = if (waiting) Mark.WAITING else if (recording) Mark.RUNNING else Mark.RECORD,
                Modifier.weight(if (recording) 1.5f else 1f),
            ) {
                if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) recorder.start()
                else askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            RecordButton(stringResource(R.string.recording_stop_short), enabled = recording, mark = Mark.STOP, Modifier.weight(1f), recorder::stop)
        }
        progress?.waitingSeconds?.let { left ->
            Text(stringResource(R.string.recording_waiting, left), color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
        }
        // What the recording is made from: where the altitude comes from, and whether points get a place.
        Group {
            GroupRow(
                stringResource(R.string.altitude_source),
                subtitle = if (altimeter.hasBarometer) null else stringResource(R.string.altitude_no_barometer),
                value = sourceName,
                onClick = if (altimeter.hasBarometer) ({ choosingSource = true }) else null,
            )
            GroupDivider()
            SwitchRow(
                stringResource(R.string.recording_position),
                stringResource(R.string.recording_position_hint),
                settings.recordPosition,
            ) { on -> container.scope.launch { container.settings.setRecordPosition(on) } }
            if (live != null) {
                GroupDivider()
                GroupRow(stringResource(R.string.recording_zero), subtitle = stringResource(R.string.recording_zero_hint), onClick = recorder::zeroHere)
            }
        }

        val list = tracks ?: return@Column
        Text(stringResource(R.string.recordings), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        if (list.isEmpty()) {
            Text(stringResource(R.string.recordings_empty), color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
        } else {
            Group {
                list.forEachIndexed { i, track ->
                    if (i > 0) GroupDivider()
                    GroupRow(
                        startTime(context, track),
                        subtitle = track.endedAt?.let { clock(it - track.startedAt) } ?: stringResource(R.string.recording_now),
                        onClick = { onOpen(track.id) },
                    )
                }
            }
        }
    }

    if (choosingSource) {
        ChoiceSheet(
            title = stringResource(R.string.altitude_source),
            options = SOURCES.map { (source, label) -> source to stringResource(label) },
            selected = altimeter.source,
            onSelect = container.altimeter::setSource,
            onDismiss = { choosingSource = false },
        )
    }
}

private val SOURCES = listOf(
    AltitudeSource.AUTO to R.string.source_auto,
    AltitudeSource.BAROMETER to R.string.source_barometer,
    AltitudeSource.GNSS to R.string.source_gnss,
)

/** What stands before the text of a button of the tab. */
private enum class Mark { RECORD, WAITING, RUNNING, STOP }

/**
 * One of the two buttons of the tab. Record is the dark pill with a red dot; while the recording
 * waits for the receiver the dot is amber and pulses, and once it runs it is red and pulses. Stop
 * is the quiet one with a square, dimmed while there is nothing to stop.
 */
@Composable
private fun RecordButton(text: String, enabled: Boolean, mark: Mark, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = AltairTheme.colors
    val primary = mark != Mark.STOP
    val content = if (primary) c.bg else c.text
    val pulsing = mark == Mark.WAITING || mark == Mark.RUNNING
    val beat by rememberInfiniteTransition(label = "recording").animateFloat(
        initialValue = 1f,
        targetValue = if (pulsing) 0.25f else 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse),
        label = "beat",
    )
    Row(
        modifier
            .height(56.dp)
            .alpha(if (enabled || primary) 1f else 0.35f)
            .clip(RoundedCornerShape(28.dp))
            .background(if (primary) c.text else c.chip)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(12.dp)
                .alpha(beat)
                .background(
                    when (mark) {
                        Mark.WAITING -> WAITING_AMBER
                        Mark.STOP -> content
                        else -> RECORD_RED
                    },
                    if (primary) CircleShape else RoundedCornerShape(2.dp),
                ),
        )
        Text(text, color = content, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** The dot of the record button: the colour a recording light has everywhere. */
private val RECORD_RED = Color(0xFFE5484D)

/** The dot while the recording waits for the receiver. */
private val WAITING_AMBER = Color(0xFFF2A91E)

private const val PULSE_MS = 700

internal fun startTime(context: Context, track: Track): String =
    DateUtils.formatDateTime(context, track.startedAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH)
