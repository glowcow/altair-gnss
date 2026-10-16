package dev.glowcow.altairgnss.ui.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.text.format.DateUtils
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.altimeter.AltitudeSource
import dev.glowcow.altairgnss.container
import dev.glowcow.altairgnss.data.AppSettings
import dev.glowcow.altairgnss.recording.Activity
import dev.glowcow.altairgnss.recording.Recorder
import dev.glowcow.altairgnss.recording.Track
import dev.glowcow.altairgnss.ui.components.Chip
import dev.glowcow.altairgnss.ui.components.ChoiceSheet
import dev.glowcow.altairgnss.ui.components.FloatingButton
import dev.glowcow.altairgnss.ui.components.Group
import dev.glowcow.altairgnss.ui.components.GroupDivider
import dev.glowcow.altairgnss.ui.components.GroupRow
import dev.glowcow.altairgnss.ui.components.GroupSheet
import dev.glowcow.altairgnss.ui.components.KindPill
import dev.glowcow.altairgnss.ui.components.LocationGate
import dev.glowcow.altairgnss.ui.components.NO_VALUE
import dev.glowcow.altairgnss.ui.components.SectionHeading
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
import dev.glowcow.altairgnss.ui.theme.AltairIcons
import dev.glowcow.altairgnss.ui.theme.AltairTheme
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

/** The tab of recordings: what is measured now, the two buttons that start and stop a recording, and those made before. */
@Composable
fun RecordScreen(onTab: (TopTab) -> Unit, onOpen: (Long) -> Unit, onYear: (Int) -> Unit) {
    val container = LocalContext.current.container
    val view = LocalView.current
    val progress by container.recorder.live.collectAsStateWithLifecycle()
    // The moment a checkpoint was asked for, while its sheet is open, and how far into the recording that was.
    var marking by remember { mutableStateOf<Long?>(null) }
    var markedAt by remember { mutableStateOf(0L) }
    TabScreen(
        TopTab.RECORD,
        onTab,
        floating = { bottom ->
            // Only a recording that writes takes checkpoints: the moment is kept at the tap, the words come after.
            val running = progress?.takeIf { it.waitingSeconds == null }
            if (running != null) {
                FloatingButton(AltairIcons.Plus, stringResource(R.string.mark_add), bottom) {
                    markedAt = running.elapsedMs
                    marking = System.currentTimeMillis()
                }
            }
        },
    ) { top, bottom ->
        LocationGate(top) { RecordContent(top, bottom, onOpen, onYear) }
    }
    marking?.let { at ->
        MarkSheet(
            stringResource(R.string.mark_title, clock(markedAt)),
            label = "",
            action = stringResource(R.string.mark_add),
            onSave = { text ->
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                container.scope.launch { container.recorder.mark(at, text) }
            },
            onDismiss = { marking = null },
        )
    }
}

@Composable
private fun RecordContent(top: Dp, bottom: Dp, onOpen: (Long) -> Unit, onYear: (Int) -> Unit) {
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
    // A recording starts once the user has said what it is; it shows itself in a notification, and starts whatever the answer to that is.
    var choosing by rememberSaveable { mutableStateOf(false) }
    var chosen by rememberSaveable { mutableStateOf(Activity.WALK) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { recorder.start(chosen) }
    val begin = { activity: Activity ->
        chosen = activity
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) recorder.start(activity)
        else askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    var picked by rememberSaveable { mutableStateOf(emptySet<Activity>()) }
    val recording = active != null
    val settings by remember { container.settings.settings }.collectAsStateWithLifecycle(AppSettings())
    // Places are named only while network positioning is on, and as soon as it is turned on.
    LaunchedEffect(settings.networkPosition) { container.places.fill() }
    var choosingSource by rememberSaveable { mutableStateOf(false) }
    val sourceName = stringResource(SOURCES.first { it.first == altimeter.source }.second)
    val fix = gnss.fix?.takeIf { gnss.hasFix }

    val from = @Composable { v: Double? -> v?.let { signedLengthText(it) } ?: NO_VALUE }
    val metres = @Composable { v: Double? -> v?.let { lengthText(it) } ?: NO_VALUE }
    val speed = @Composable { v: Float? -> v?.let { speedText(it) } ?: NO_VALUE }

    Column(
        // While it records, the page ends clear of the button that floats over it.
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = top, bottom = bottom + if (live != null) 92.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // What is measured now comes first; what a recording adds up fills in once it runs.
        Group {
            StatGrid(
                listOf(
                    stringResource(R.string.label_elapsed) to (live?.let { clock(it.elapsedMs) } ?: NO_VALUE),
                    stringResource(R.string.label_moving_time) to (live?.movingMs?.let { clock(it) } ?: NO_VALUE),
                    stringResource(R.string.label_distance) to (live?.let { distanceText(it.distance) } ?: NO_VALUE),
                    stringResource(R.string.label_speed) to speed(live?.speed ?: fix?.speed),
                    stringResource(R.string.label_speed_average) to speed(live?.averageSpeed),
                    stringResource(R.string.label_speed_max) to speed(live?.maxSpeed),
                    stringResource(R.string.label_altitude) to metres(altimeter.altitude),
                    stringResource(R.string.label_vertical_speed) to (altimeter.verticalSpeed?.let { verticalSpeedText(it) } ?: NO_VALUE),
                    stringResource(R.string.label_climbed) to metres(live?.gain),
                    stringResource(R.string.label_descended) to metres(live?.loss),
                    stringResource(R.string.label_lowest) to from(live?.lowest),
                    stringResource(R.string.label_highest) to from(live?.highest),
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
            ) { choosing = true }
            RecordButton(stringResource(R.string.recording_stop_short), enabled = recording, mark = Mark.STOP, Modifier.weight(1f), recorder::stop)
        }
        progress?.waitingSeconds?.let { left ->
            Text(stringResource(R.string.recording_waiting, left), color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
        }
        // A position there is, but too coarse to start a track on: say so, or the wait looks stuck.
        progress?.coarse?.let { off ->
            Text(
                stringResource(R.string.recording_waiting_coarse, lengthText(off.toDouble(), 0), lengthText(Recorder.BLEND_ACCURACY.toDouble(), 0)),
                color = c.muted,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
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
            if (settings.recordPosition) {
                GroupDivider()
                SwitchRow(
                    stringResource(R.string.recording_network),
                    stringResource(R.string.recording_network_hint),
                    settings.networkPosition,
                ) { on -> container.scope.launch { container.settings.setNetworkPosition(on) } }
            }
            if (live != null) {
                GroupDivider()
                GroupRow(stringResource(R.string.recording_zero), subtitle = stringResource(R.string.recording_zero_hint), onClick = recorder::zeroHere)
            }
        }

        val list = tracks ?: return@Column
        // The list is never shorter than the screen, so a filter that leaves fewer rows does not pull the page back up.
        val screen = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
        Column(Modifier.heightIn(min = screen - top - bottom - 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading(stringResource(R.string.recordings))
            if (list.isEmpty()) {
                Text(stringResource(R.string.recordings_empty), color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
                return@Column
            }
            // The kinds there are, the most used first; picking some shows only them, picking none shows all.
            val kinds = list.mapNotNull { Activity.of(it.activity) }.groupingBy { it }.eachCount()
                .entries.sortedWith(compareByDescending<Map.Entry<Activity, Int>> { it.value }.thenBy { it.key.ordinal }).map { it.key }
            val only = picked.filterTo(mutableSetOf()) { it in kinds }
            if (kinds.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (kind in kinds) {
                        Chip(stringResource(kind.label), selected = only.isEmpty() || kind in only, colour = kind.colour) {
                            picked = when {
                                only.isEmpty() -> setOf(kind)
                                kind in only -> only - kind
                                else -> only + kind
                            }
                        }
                    }
                }
            }
            val shown = if (only.isEmpty()) list else list.filter { Activity.of(it.activity) in only }
            val zone = ZoneId.systemDefault()
            for ((year, yearTracks) in shown.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).year }) {
                YearHeading(year) { onYear(year) }
                Group {
                    yearTracks.forEachIndexed { i, track ->
                        if (i > 0) GroupDivider()
                        TrackRow(track) { onOpen(track.id) }
                    }
                }
            }
        }
    }

    if (choosing) {
        GroupSheet(stringResource(R.string.activity), onDismiss = { choosing = false }) { pick ->
            Group {
                Activity.entries.forEachIndexed { i, activity ->
                    if (i > 0) GroupDivider()
                    // Picking one starts the recording, so the row leads nowhere and has no chevron.
                    GroupRow(stringResource(activity.label), icon = activity.icon, onClick = { pick { begin(activity) } }, trailing = {})
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
 * is the quiet one with a square on the colour of a block, its label dimmed while there is nothing to stop.
 */
@Composable
private fun RecordButton(text: String, enabled: Boolean, mark: Mark, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = AltairTheme.colors
    val primary = mark != Mark.STOP
    // Stop with nothing to stop keeps its ground and only quiets what is written on it.
    val content = if (primary) c.bg else if (enabled) c.text else c.muted.copy(alpha = 0.6f)
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
            .clip(RoundedCornerShape(28.dp))
            .background(if (primary) c.text else c.group)
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
                        Mark.WAITING -> c.bright(WAITING_AMBER)
                        Mark.STOP -> content
                        else -> c.bright(RECORD_RED)
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

/** The year over its recordings, larger than a heading; a tap opens what it added up to. */
@Composable
private fun YearHeading(year: Int, onClick: () -> Unit) {
    val c = AltairTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(year.toString(), Modifier.weight(1f), color = c.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Icon(
            AltairIcons.Chevron,
            stringResource(R.string.year_open, year),
            tint = c.muted,
            modifier = Modifier.size(18.dp).scale(if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f, 1f),
        )
    }
}

/** A recording in the list: its kind and when it began, then where and for how long. */
@Composable
private fun TrackRow(track: Track, onClick: () -> Unit) {
    val c = AltairTheme.colors
    val context = LocalContext.current
    val activity = Activity.of(track.activity)
    val ended = track.endedAt
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 58.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                activity?.let { KindPill(stringResource(it.label), it.colour) }
                // The year stands over the block; a recording that ran past midnight names both days.
                Text(
                    DateUtils.formatDateRange(context, track.startedAt, ended ?: track.startedAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR),
                    color = c.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
            Text(
                if (ended == null) stringResource(R.string.recording_now)
                // The length first, so a long place is what gets cut; with no place looked up, the length alone.
                else listOfNotNull(clock(ended - track.startedAt), track.place?.let { it.ifEmpty { stringResource(R.string.place_none) } }).joinToString(" | "),
                color = c.muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // The chevron points along the reading direction.
        Icon(
            AltairIcons.Chevron,
            null,
            tint = c.muted,
            modifier = Modifier.size(18.dp).scale(if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f, 1f),
        )
    }
}

internal fun startTime(context: Context, track: Track): String =
    DateUtils.formatDateTime(context, track.startedAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH)
