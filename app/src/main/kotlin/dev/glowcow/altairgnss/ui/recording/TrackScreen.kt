package dev.glowcow.altairgnss.ui.recording

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.container
import dev.glowcow.altairgnss.data.AppSettings
import dev.glowcow.altairgnss.data.TrackZero
import dev.glowcow.altairgnss.gnss.Scatter
import dev.glowcow.altairgnss.maps.GroundMap
import dev.glowcow.altairgnss.recording.Mark
import dev.glowcow.altairgnss.recording.Point
import dev.glowcow.altairgnss.recording.Profile
import dev.glowcow.altairgnss.recording.ProfileStats
import dev.glowcow.altairgnss.recording.Track
import dev.glowcow.altairgnss.recording.TrackPath
import dev.glowcow.altairgnss.ui.components.DetailScreen
import dev.glowcow.altairgnss.ui.components.Group
import dev.glowcow.altairgnss.ui.components.GroupDivider
import dev.glowcow.altairgnss.ui.components.GroupRow
import dev.glowcow.altairgnss.ui.components.GroupSheet
import dev.glowcow.altairgnss.ui.components.HeaderGap
import dev.glowcow.altairgnss.ui.components.IconButton48
import dev.glowcow.altairgnss.ui.components.PageTitle
import dev.glowcow.altairgnss.ui.components.StatGrid
import dev.glowcow.altairgnss.ui.components.clock
import dev.glowcow.altairgnss.ui.components.distanceText
import dev.glowcow.altairgnss.ui.components.lengthText
import dev.glowcow.altairgnss.ui.components.signedLengthText
import dev.glowcow.altairgnss.ui.components.speedText
import dev.glowcow.altairgnss.ui.theme.AltairIcons
import dev.glowcow.altairgnss.ui.theme.AltairTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the pages of a recording draw from its points. [altitudes] are the measured ones evened out;
 * [zero] is the zero of the recording and [base] the level its heights are shown from, which the
 * settings choose — sea level when [absolute].
 */
private class TrackData(
    val track: Track?,
    val points: List<Point>,
    val altitudes: List<Double>,
    val stats: ProfileStats?,
    val path: TrackPath?,
    val zero: Double,
    val base: Double,
    val counted: TrackZero,
    val marks: List<Mark>,
) {
    val absolute: Boolean get() = counted == TrackZero.SEA

    /** What stands on the flag of the [n]-th mark: its label, or its number. */
    fun flag(n: Int): String = marks[n].label.ifEmpty { (n + 1).toString() }

    val chartMarks: List<ChartMark> get() = marks.indices.map { ChartMark(marks[it].timeMs, flag(it)) }

    /** The marks that fall on the placed part of the track, each at the point nearest in time. */
    val sceneMarks: List<SceneMark>
        get() {
            val laid = path?.points ?: return emptyList()
            val start = points.firstOrNull()?.timeMs ?: return emptyList()
            return marks.indices.map { n -> SceneMark(laid.minBy { abs(it.elapsedMs - (marks[n].timeMs - start)) }, flag(n)) }
        }

    /** The altitude, evened out, at the point nearest in time to [timeMs]. */
    fun altitudeAt(timeMs: Long): Double? = points.indices.minByOrNull { abs(points[it].timeMs - timeMs) }?.let { altitudes[it] }

    // The colours run up to the fastest the track was; a track walked at a crawl still gets a scale.
    val topSpeed: Float get() = (stats?.maxSpeed ?: 0f).coerceAtLeast(MIN_TOP_SPEED)
}

@Composable
private fun rememberTrackData(id: Long): TrackData {
    val dao = LocalContext.current.container.tracks
    val container = LocalContext.current.container
    val points by remember(id) { dao.observePoints(id) }.collectAsStateWithLifecycle(emptyList())
    // What counts as moving is a setting, so the figures of an old recording follow it too.
    val settings by remember { container.settings.settings }.collectAsStateWithLifecycle(AppSettings())
    val track by remember(id) { dao.observeTrack(id) }.collectAsStateWithLifecycle(null)
    val marks by remember(id) { dao.observeMarks(id) }.collectAsStateWithLifecycle(emptyList())
    val moving = settings.movingSpeed
    val counted = settings.trackZero
    val set = track?.zeroAltitude
    return remember(track, points, moving, counted, marks) {
        val altitudes = Profile.smooth(points)
        val zero = set ?: altitudes.firstOrNull() ?: 0.0
        val base = when (counted) {
            TrackZero.LOWEST -> altitudes.minOrNull() ?: 0.0
            TrackZero.START -> zero
            TrackZero.SEA -> 0.0
        }
        TrackData(track, points, altitudes, Profile.stats(points, altitudes, moving), Profile.path(points, altitudes, moving, base), zero, base, counted, marks)
    }
}

/**
 * The map under the track of a recording, when the setting asks for one: from the cache, else
 * downloaded now. Null until it is there, and for good when it is switched off or cannot be had.
 */
@Composable
private fun rememberGroundMap(path: TrackPath?): GroundMap? {
    val container = LocalContext.current.container
    val wanted = remember { container.settings.settings }.collectAsStateWithLifecycle(AppSettings()).value.mapTiles
    val centre = path?.centre
    val radius = path?.radius
    val map by produceState<GroundMap?>(null, wanted, centre, radius) {
        value = if (wanted && centre != null && radius != null) container.maps.load(centre, maxOf(radius * GROUND_MARGIN, MIN_GROUND)) else null
    }
    return map
}

/** One recording: its track in space, its chart and figures, and what can be done with it. */
@Composable
fun TrackScreen(id: Long, onBack: () -> Unit, onExpand: () -> Unit, onExpandChart: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val data = rememberTrackData(id)
    val map = rememberGroundMap(data.path)
    var deleting by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Mark?>(null) }
    val track = data.track

    val saved = stringResource(R.string.export_done)
    val failed = stringResource(R.string.export_failed)
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        container.scope.launch {
            val done = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)!!.use { it.write(Profile.csv(data.points, data.points.map { p -> p.altitude }, data.zero).toByteArray()) }
                }.isSuccess
            }
            Toast.makeText(context, if (done) saved else failed, Toast.LENGTH_SHORT).show()
        }
    }

    DetailScreen(track?.let { startTime(context, it) }.orEmpty(), onBack) {
        val c = AltairTheme.colors
        if (track == null) return@DetailScreen
        val from = @Composable { v: Double -> if (data.absolute) lengthText(v) else signedLengthText(v - data.base) }
        val running = track.endedAt == null
        val stats = data.stats

        if (stats == null) {
            Text(
                stringResource(if (running) R.string.recording_now else R.string.recording_no_points),
                color = c.muted,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        } else {
            data.path?.let { path ->
                Group { TrackScene(path.points, data.topSpeed, c.group, Modifier.fillMaxWidth().aspectRatio(0.95f), stops = path.stops, marks = data.sceneMarks, map = map, onExpand = onExpand) }
            }
            Group {
                Box {
                    ProfileChart(data.points.map { it.timeMs }, data.altitudes.map { it - data.base }, absolute = data.absolute, marks = data.chartMarks, topInset = 28.dp)
                    Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                        IconButton48(AltairIcons.Expand, stringResource(R.string.track_fullscreen), tint = c.muted, onClick = onExpandChart)
                    }
                }
            }
            Group {
                StatGrid(
                    listOfNotNull(
                        stringResource(R.string.label_elapsed) to clock(stats.durationMs),
                        stats.movingMs?.let { stringResource(R.string.label_moving_time) to clock(it) },
                        stats.distance?.let { stringResource(R.string.label_distance) to distanceText(it) },
                        stats.averageSpeed?.let { stringResource(R.string.label_speed_average) to speedText(it) },
                        stats.maxSpeed?.let { stringResource(R.string.label_speed_max) to speedText(it) },
                        stringResource(R.string.label_start_altitude) to lengthText(stats.start),
                        stringResource(R.string.label_end) to from(stats.end),
                        // Counted from the lowest point, that point is the level itself.
                        stringResource(R.string.label_lowest) to if (data.counted == TrackZero.LOWEST) lengthText(stats.min) else from(stats.min),
                        stringResource(R.string.label_highest) to from(stats.max),
                        stringResource(R.string.label_climbed) to lengthText(stats.gain),
                        stringResource(R.string.label_descended) to lengthText(stats.loss),
                    ),
                )
            }
        }
        if (data.marks.isNotEmpty()) {
            Text(stringResource(R.string.marks), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            Group {
                data.marks.forEachIndexed { n, mark ->
                    if (n > 0) GroupDivider()
                    GroupRow(
                        mark.label.ifEmpty { stringResource(R.string.mark_default, n + 1) },
                        subtitle = clock(mark.timeMs - track.startedAt),
                        value = data.altitudeAt(mark.timeMs)?.let { from(it) },
                        onClick = { editing = mark },
                    )
                }
            }
        }
        if (running) return@DetailScreen
        Group {
            if (stats != null) {
                GroupRow(stringResource(R.string.export_csv), onClick = {
                    export.launch("track-" + FILE_TIME.format(Instant.ofEpochMilli(track.startedAt).atZone(ZoneId.systemDefault())) + ".csv")
                })
                GroupDivider()
            }
            GroupRow(stringResource(R.string.recording_delete), onClick = { deleting = true })
        }
    }

    editing?.let { mark ->
        MarkSheet(
            stringResource(R.string.mark_title, clock(mark.timeMs - (track?.startedAt ?: mark.timeMs))),
            label = mark.label,
            action = stringResource(R.string.save),
            onSave = { text -> container.scope.launch { container.recorder.labelMark(mark.id, text) } },
            onDismiss = { editing = null },
            onDelete = { container.scope.launch { container.recorder.deleteMark(mark.id) } },
        )
    }

    if (deleting) {
        GroupSheet(stringResource(R.string.recording_delete), onDismiss = { deleting = false }) { pick ->
            Text(
                stringResource(R.string.recording_delete_text),
                color = AltairTheme.colors.muted,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 14.dp),
            )
            Group {
                GroupRow(stringResource(R.string.recording_delete_confirm), onClick = {
                    pick {
                        onBack()
                        container.scope.launch { container.recorder.delete(id) }
                    }
                })
            }
        }
    }
}

/** The track of a recording alone, on the whole screen. */
@Composable
fun TrackViewScreen(id: Long, onBack: () -> Unit) {
    val c = AltairTheme.colors
    val container = LocalContext.current.container
    val data = rememberTrackData(id)
    val map = rememberGroundMap(data.path)
    Box(Modifier.fillMaxSize().background(c.groupBg)) {
        data.path?.let { path ->
            TrackScene(
                path.points,
                data.topSpeed,
                c.groupBg,
                Modifier.fillMaxSize(),
                explorable = true,
                stops = path.stops,
                marks = data.sceneMarks,
                map = map,
                closer = { east, north, reach, perPixel ->
                    container.maps.detail(path.centre, Scatter.shift(path.centre, east, north), reach, perPixel)
                },
                topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            )
        }
        Box(Modifier.statusBarsPadding().padding(4.dp)) {
            IconButton48(AltairIcons.Back, stringResource(R.string.back), onClick = onBack)
        }
    }
}

/** The height chart of a recording alone, on the whole screen. */
@Composable
fun ChartScreen(id: Long, onBack: () -> Unit) {
    val c = AltairTheme.colors
    val context = LocalContext.current
    val data = rememberTrackData(id)
    Column(Modifier.fillMaxSize().background(c.groupBg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton48(AltairIcons.Back, stringResource(R.string.back), onClick = onBack)
            PageTitle(data.track?.let { startTime(context, it) }.orEmpty(), Modifier.padding(start = 4.dp, end = 8.dp))
        }
        Group(Modifier.weight(1f).padding(start = 16.dp, top = HeaderGap, end = 16.dp, bottom = 16.dp)) {
            ProfileChart(data.points.map { it.timeMs }, data.altitudes.map { it - data.base }, absolute = data.absolute, marks = data.chartMarks, full = true)
        }
    }
}

// The least speed the colour scale ends at, m/s.
private const val MIN_TOP_SPEED = 1f

private val FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
