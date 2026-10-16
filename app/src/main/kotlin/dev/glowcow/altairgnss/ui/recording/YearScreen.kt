package dev.glowcow.altairgnss.ui.recording

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.container
import dev.glowcow.altairgnss.data.AppSettings
import dev.glowcow.altairgnss.recording.Activity
import dev.glowcow.altairgnss.recording.Summary
import dev.glowcow.altairgnss.ui.components.Chip
import dev.glowcow.altairgnss.ui.components.DetailScreen
import dev.glowcow.altairgnss.ui.components.Group
import dev.glowcow.altairgnss.ui.components.GroupDivider
import dev.glowcow.altairgnss.ui.components.KindPill
import dev.glowcow.altairgnss.ui.components.NO_VALUE
import dev.glowcow.altairgnss.ui.components.SectionHeading
import dev.glowcow.altairgnss.ui.components.clock
import dev.glowcow.altairgnss.ui.components.distanceText
import dev.glowcow.altairgnss.ui.components.lengthText
import dev.glowcow.altairgnss.ui.components.speedText
import dev.glowcow.altairgnss.ui.components.tabular
import dev.glowcow.altairgnss.ui.theme.AltairIcons
import dev.glowcow.altairgnss.ui.theme.AltairTheme
import dev.glowcow.altairgnss.ui.theme.inked
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import kotlin.math.roundToInt

/** What a year of recordings added up to, against the year before, month by month and by activity. */
@Composable
fun YearScreen(year: Int, onBack: () -> Unit) {
    val c = AltairTheme.colors
    val container = LocalContext.current.container
    val tracks by remember { container.tracks.observeTracks() }.collectAsStateWithLifecycle(null)
    val settings by remember { container.settings.settings }.collectAsStateWithLifecycle(AppSettings())
    val zone = ZoneId.systemDefault()
    val summaries by produceState<List<Summary>?>(null, tracks, settings.movingSpeed) {
        val wanted = tracks?.filter { yearOf(it.startedAt, zone) in (year - 1)..year } ?: return@produceState
        value = container.summaries.of(wanted, settings.movingSpeed)
    }
    var metric by rememberSaveable { mutableStateOf(Metric.DISTANCE) }
    // Activities picked to count alone, by key ("" for none set); none picked counts all.
    var picked by rememberSaveable { mutableStateOf(emptySet<String>()) }

    DetailScreen(year.toString(), onBack) {
        val all = summaries
        if (all == null) {
            Text(stringResource(R.string.year_counting), color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
            return@DetailScreen
        }
        val key = { s: Summary -> s.track.activity.orEmpty() }
        val year0 = all.filter { yearOf(it.track.startedAt, zone) == year }
        if (year0.isEmpty()) {
            Text(stringResource(R.string.recordings_empty), color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
            return@DetailScreen
        }
        // The activities of the year, the most used first, as on the list.
        val kinds = year0.groupBy(key).entries.sortedWith(
            compareByDescending<Map.Entry<String, List<Summary>>> { it.value.size }.thenBy { Activity.of(it.key)?.ordinal ?: Int.MAX_VALUE },
        )
        val only = picked.filterTo(mutableSetOf()) { k -> kinds.any { it.key == k } }
        fun toggle(k: String) {
            picked = when {
                only.isEmpty() -> setOf(k)
                k in only -> only - k
                else -> only + k
            }
        }
        val counted = { s: Summary -> only.isEmpty() || key(s) in only }
        val now = year0.filter(counted)
        val before = all.filter { yearOf(it.track.startedAt, zone) == year - 1 && counted(it) }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((k, _) in kinds) {
                val activity = Activity.of(k)
                Chip(stringResource(activity?.label ?: R.string.activity_none), selected = only.isEmpty() || k in only, colour = activity?.colour ?: c.muted) { toggle(k) }
            }
        }

        val days = { list: List<Summary> -> list.map { Instant.ofEpochMilli(it.track.startedAt).atZone(zone).toLocalDate() }.distinct().size.toDouble() }
        val moving = { list: List<Summary> -> list.sumOf { it.movingMs }.toDouble() }
        val distance = { list: List<Summary> -> list.sumOf { it.distance } }
        // Metres a second over the time spent moving.
        val pace = { list: List<Summary> -> if (moving(list) > 0) distance(list) / moving(list) * 1000 else 0.0 }
        // How a figure compares with the year before, when that year had any.
        val change = @Composable { pick: (List<Summary>) -> Double ->
            val then = pick(before)
            if (then <= 0.0) null else {
                val percent = ((pick(now) - then) / then * 100).roundToInt()
                (if (percent < 0) "−" else "+") + stringResource(R.string.unit_percent, kotlin.math.abs(percent))
            }
        }
        val cells = listOf(
            Triple(stringResource(R.string.recordings), now.size.toString(), change { it.size.toDouble() }),
            Triple(stringResource(R.string.year_days), days(now).toInt().toString(), change(days)),
            Triple(stringResource(R.string.label_elapsed), clock(now.sumOf { it.durationMs }), change { l -> l.sumOf { it.durationMs }.toDouble() }),
            Triple(stringResource(R.string.label_moving_time), clock(moving(now).toLong()), change(moving)),
            Triple(stringResource(R.string.label_distance), distanceText(distance(now), 1), change(distance)),
            Triple(stringResource(R.string.label_speed_average), if (pace(now) > 0) speedText(pace(now).toFloat()) else NO_VALUE, change(pace)),
            Triple(stringResource(R.string.label_climbed), lengthText(now.sumOf { it.gain }, 0), change { l -> l.sumOf { it.gain } }),
            Triple(stringResource(R.string.label_descended), lengthText(now.sumOf { it.loss }, 0), change { l -> l.sumOf { it.loss } }),
        )
        Group {
            NotedGrid(cells)
            if (cells.any { it.third != null }) {
                Text(stringResource(R.string.year_change, year - 1), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp))
            }
        }

        SectionHeading(stringResource(R.string.year_by_month))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (m in Metric.entries) Chip(stringResource(m.label), selected = m == metric) { metric = m }
        }
        Group { MonthBars(now, before, metric, year - 1) }

        // Every activity of the year stays here; a tap counts it alone, or with those already picked.
        SectionHeading(stringResource(R.string.year_by_activity))
        Group {
            kinds.forEachIndexed { i, (k, list) ->
                val activity = Activity.of(k)
                if (i > 0) GroupDivider()
                Row(
                    Modifier.fillMaxWidth().clickable { toggle(k) }.heightIn(min = 58.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        KindPill(stringResource(activity?.label ?: R.string.activity_none), activity?.colour ?: c.muted)
                        Text(
                            pluralStringResource(R.plurals.recordings_count, list.size, list.size) + " | " + clock(list.sumOf { it.movingMs }),
                            color = c.muted,
                            fontSize = 13.sp,
                        )
                    }
                    Text(distanceText(list.sumOf { it.distance }, 1), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, style = tabular())
                    if (k in only) Icon(AltairIcons.Check, null, tint = c.accent, modifier = Modifier.size(20.dp).inked(c.accent))
                }
            }
        }
    }
}

/** What the bars of the months show. */
private enum class Metric(val label: Int, val of: (Summary) -> Double) {
    DISTANCE(R.string.label_distance, { it.distance }),
    MOVING(R.string.label_moving_time, { it.movingMs.toDouble() }),
    CLIMB(R.string.label_climbed, { it.gain }),
    COUNT(R.string.recordings, { 1.0 }),
}

/**
 * A bar a month, stacked in the colours of the activities, with a dash where the same month of the
 * year before stood; the tallest of either sets the scale, written over the bars.
 */
@Composable
private fun MonthBars(now: List<Summary>, before: List<Summary>, metric: Metric, last: Int) {
    val c = AltairTheme.colors
    val zone = ZoneId.systemDefault()
    val month = { s: Summary -> Instant.ofEpochMilli(s.track.startedAt).atZone(zone).monthValue - 1 }
    val stacks = (0 until 12).map { m ->
        now.filter { month(it) == m }.groupBy { Activity.of(it.track.activity) }
            .entries.sortedBy { it.key?.ordinal ?: Int.MAX_VALUE }
            .map { (activity, list) -> (activity?.colour ?: c.muted) to list.sumOf(metric.of) }
    }
    val previous = (0 until 12).map { m -> before.filter { month(it) == m }.sumOf(metric.of) }
    val top = maxOf(stacks.maxOf { s -> s.sumOf { it.second } }, previous.max()).takeIf { it > 0 } ?: 1.0
    val locale = LocalConfiguration.current.locales[0]
    val dash = c.text.copy(alpha = 0.55f)

    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(metricText(metric, top), color = c.muted, fontSize = 12.sp, style = tabular())
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val slot = size.width / 12
            val bar = slot * 0.56f
            val corner = CornerRadius(3.dp.toPx())
            for (m in 0 until 12) {
                val left = slot * m + (slot - bar) / 2
                val total = stacks[m].sumOf { it.second }
                if (total > 0) {
                    val height = (total / top * size.height).toFloat()
                    val shape = Path().apply { addRoundRect(RoundRect(Rect(left, size.height - height, left + bar, size.height), corner, corner, CornerRadius.Zero, CornerRadius.Zero)) }
                    clipPath(shape) {
                        var y = size.height
                        for ((colour, value) in stacks[m]) {
                            val h = (value / top * size.height).toFloat()
                            drawRect(colour, Offset(left, y - h), Size(bar, h))
                            y -= h
                        }
                    }
                }
                if (previous[m] > 0) {
                    val y = size.height - (previous[m] / top * size.height).toFloat()
                    drawLine(dash, Offset(slot * m + slot * 0.1f, y), Offset(slot * (m + 1) - slot * 0.1f, y), 2.dp.toPx())
                }
            }
            drawLine(c.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        }
        Row {
            for (m in java.time.Month.entries) {
                Text(m.getDisplayName(TextStyle.NARROW_STANDALONE, locale), Modifier.weight(1f), color = c.muted, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
        if (previous.any { it > 0 }) Text(stringResource(R.string.year_dashes, last), color = c.muted, fontSize = 12.sp)
    }
}

@Composable
private fun metricText(metric: Metric, value: Double): String = when (metric) {
    Metric.DISTANCE -> distanceText(value, 1)
    Metric.MOVING -> clock(value.toLong())
    Metric.CLIMB -> lengthText(value, 0)
    Metric.COUNT -> value.roundToInt().toString()
}

/** Labelled values in two columns, as [dev.glowcow.altairgnss.ui.components.StatGrid], each with a small muted note after it. */
@Composable
private fun NotedGrid(cells: List<Triple<String, String, String?>>) {
    val c = AltairTheme.colors
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (row in cells.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((label, value, note) in row) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(label, color = c.muted, fontSize = 12.sp, maxLines = 1)
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(value, Modifier.alignByBaseline(), color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, style = tabular(), maxLines = 1)
                            if (note != null) Text(note, Modifier.alignByBaseline(), color = c.muted, fontSize = 12.sp, style = tabular(), maxLines = 1)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun yearOf(ms: Long, zone: ZoneId): Int = Instant.ofEpochMilli(ms).atZone(zone).year
