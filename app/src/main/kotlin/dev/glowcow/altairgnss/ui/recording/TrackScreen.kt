package dev.glowcow.altairgnss.ui.recording

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.container
import dev.glowcow.altairgnss.maps.GroundMap
import dev.glowcow.altairgnss.recording.Point
import dev.glowcow.altairgnss.recording.Profile
import dev.glowcow.altairgnss.recording.ProfileStats
import dev.glowcow.altairgnss.recording.TrackPath
import dev.glowcow.altairgnss.ui.components.DetailScreen
import dev.glowcow.altairgnss.ui.components.Group
import dev.glowcow.altairgnss.ui.components.GroupDivider
import dev.glowcow.altairgnss.ui.components.GroupRow
import dev.glowcow.altairgnss.ui.components.GroupSheet
import dev.glowcow.altairgnss.ui.components.IconButton48
import dev.glowcow.altairgnss.ui.components.StatGrid
import dev.glowcow.altairgnss.ui.components.clock
import dev.glowcow.altairgnss.ui.components.distanceText
import dev.glowcow.altairgnss.ui.components.lengthText
import dev.glowcow.altairgnss.ui.components.signedLengthText
import dev.glowcow.altairgnss.ui.components.speedText
import dev.glowcow.altairgnss.ui.theme.AltairIcons
import dev.glowcow.altairgnss.ui.theme.AltairTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What the pages of a recording draw from its points. */
private class TrackData(val points: List<Point>, val altitudes: List<Double>, val stats: ProfileStats?, val path: TrackPath?) {
    // The colours run up to the fastest the track was; a track walked at a crawl still gets a scale.
    val topSpeed: Float get() = (stats?.maxSpeed ?: 0f).coerceAtLeast(MIN_TOP_SPEED)
}

@Composable
private fun rememberTrackData(id: Long): TrackData {
    val dao = LocalContext.current.container.tracks
    val container = LocalContext.current.container
    val points by remember(id) { dao.observePoints(id) }.collectAsStateWithLifecycle(emptyList())
    // What counts as moving is a setting, so the figures of an old recording follow it too.
    val moving by remember { container.settings.settings.map { it.movingSpeed } }.collectAsStateWithLifecycle(Profile.MOVING)
    return remember(points, moving) {
        val altitudes = points.map { it.altitude }
        TrackData(points, altitudes, Profile.stats(points, altitudes, moving), Profile.path(points, altitudes, moving))
    }
}

/**
 * The map under the track of a recording, when the setting asks for one: from the cache, else
 * downloaded now. Null until it is there, and for good when it is switched off or cannot be had.
 */
@Composable
private fun rememberGroundMap(path: TrackPath?): GroundMap? {
    val container = LocalContext.current.container
    val wanted by remember { container.settings.settings.map { it.mapTiles } }.collectAsStateWithLifecycle(false)
    val centre = path?.centre
    val radius = path?.radius
    val map by produceState<GroundMap?>(null, wanted, centre, radius) {
        value = if (wanted && centre != null && radius != null) container.maps.load(centre, maxOf(radius * GROUND_MARGIN, MIN_GROUND)) else null
    }
    return map
}

/** One recording: its track in space, its chart and figures, and what can be done with it. */
@Composable
fun TrackScreen(id: Long, onBack: () -> Unit, onExpand: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val loaded by remember(id) { container.tracks.observeTrack(id) }.collectAsStateWithLifecycle(null)
    val data = rememberTrackData(id)
    val map = rememberGroundMap(data.path)
    var deleting by rememberSaveable { mutableStateOf(false) }
    val track = loaded
    val zero = track?.zeroAltitude ?: data.altitudes.firstOrNull() ?: 0.0

    val saved = stringResource(R.string.export_done)
    val failed = stringResource(R.string.export_failed)
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        container.scope.launch {
            val done = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)!!.use { it.write(Profile.csv(data.points, data.altitudes, zero).toByteArray()) }
                }.isSuccess
            }
            Toast.makeText(context, if (done) saved else failed, Toast.LENGTH_SHORT).show()
        }
    }

    DetailScreen(track?.let { startTime(context, it) }.orEmpty(), onBack) {
        val c = AltairTheme.colors
        if (track == null) return@DetailScreen
        val from = @Composable { v: Double -> signedLengthText(v - zero) }
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
                Group { TrackScene(path.points, data.topSpeed, c.group, Modifier.fillMaxWidth().aspectRatio(0.95f), stops = path.stops, map = map, onExpand = onExpand) }
            }
            Group { ProfileChart(data.points.map { it.timeMs }, data.altitudes.map { it - zero }) }
            Group {
                StatGrid(
                    listOfNotNull(
                        stringResource(R.string.label_elapsed) to clock(stats.durationMs),
                        stats.movingMs?.let { stringResource(R.string.label_moving_time) to clock(it) },
                        stats.distance?.let { stringResource(R.string.label_distance) to distanceText(it) },
                        stats.averageSpeed?.let { stringResource(R.string.label_speed_average) to speedText(it) },
                        stats.maxSpeed?.let { stringResource(R.string.label_speed_max) to speedText(it) },
                        stringResource(R.string.label_start_altitude) to lengthText(stats.start),
                        stringResource(R.string.label_end_from_zero) to from(stats.end),
                        stringResource(R.string.label_lowest) to from(stats.min),
                        stringResource(R.string.label_highest) to from(stats.max),
                        stringResource(R.string.label_climbed) to lengthText(stats.gain),
                        stringResource(R.string.label_descended) to lengthText(stats.loss),
                    ),
                )
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
                map = map,
                topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            )
        }
        Box(Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
            IconButton48(AltairIcons.Back, stringResource(R.string.back), onClick = onBack)
        }
    }
}

// The least speed the colour scale ends at, m/s.
private const val MIN_TOP_SPEED = 1f

private val FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
