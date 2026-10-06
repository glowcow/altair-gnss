package dev.glowcow.altairgnss.ui.instruments

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.container
import dev.glowcow.altairgnss.gnss.Fix
import dev.glowcow.altairgnss.gnss.GnssState
import dev.glowcow.altairgnss.instruments.Moon
import dev.glowcow.altairgnss.instruments.Sun
import dev.glowcow.altairgnss.ui.components.Group
import dev.glowcow.altairgnss.ui.components.LocationGate
import dev.glowcow.altairgnss.ui.components.NO_VALUE
import dev.glowcow.altairgnss.ui.components.StatGrid
import dev.glowcow.altairgnss.ui.components.TabScreen
import dev.glowcow.altairgnss.ui.components.TopTab
import dev.glowcow.altairgnss.ui.components.decimals
import dev.glowcow.altairgnss.ui.components.rememberScreenHeading
import dev.glowcow.altairgnss.ui.theme.AltairTheme
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun InstrumentsScreen(onTab: (TopTab) -> Unit, onCompass: () -> Unit, onSpeed: () -> Unit) =
    TabScreen(TopTab.INSTRUMENTS, onTab) { top, bottom ->
        LocationGate(top) { InstrumentsContent(top, bottom, onCompass, onSpeed) }
    }

@Composable
private fun InstrumentsContent(top: Dp, bottom: Dp, onCompass: () -> Unit, onSpeed: () -> Unit) {
    val container = LocalContext.current.container
    val gnss by container.gnss.state.collectAsStateWithLifecycle()
    val heading = rememberScreenHeading()
    // The clocks tick on their own, between fixes too.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(CLOCK_TICK_MS)
            value = System.currentTimeMillis()
        }
    }
    val fix = gnss.fix?.takeIf { gnss.hasFix }
    // The last known position is good enough for the sun and the map.
    val position = gnss.fix
    val dial = Modifier.clip(RoundedCornerShape(20.dp))

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = top, bottom = bottom + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Each dial opens full screen with more about it.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Group(Modifier.weight(1f).then(dial).clickable(onClick = onCompass)) {
                Compass(heading.degrees)
                DialNote(
                    stringResource(
                        when {
                            !heading.available -> R.string.compass_missing
                            !heading.reliable -> R.string.compass_unreliable
                            heading.isTrue -> R.string.compass_true
                            else -> R.string.compass_magnetic
                        },
                    ),
                )
            }
            Group(Modifier.weight(1f).then(dial).clickable(onClick = onSpeed)) {
                Speedometer(fix?.speed)
                DialNote(
                    stringResource(
                        R.string.speed_course,
                        fix?.bearing?.let { stringResource(R.string.unit_degrees, it.decimals(0)) } ?: NO_VALUE,
                    ),
                )
            }
        }
        Group { StatGrid(clockStats(gnss, now)) }
        Group { StatGrid(skyStats(position, now)) }
        Group { DaylightMap(position, now) }
    }
}

/** The line under a dial; two lines tall whatever the text, so neighbouring dials stay level. */
@Composable
private fun DialNote(text: String) {
    Text(
        text,
        color = AltairTheme.colors.muted,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        textAlign = TextAlign.Center,
        minLines = 2,
        maxLines = 2,
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 12.dp),
    )
}

@Composable
private fun clockStats(gnss: GnssState, now: Long): List<Pair<String, String>> {
    val fix = gnss.fix?.takeIf { gnss.hasFix }
    // GNSS time now: the time of the fix plus what the boot clock has counted since.
    val gnssNow = fix?.let { it.timeMs + (SystemClock.elapsedRealtime() - it.elapsedRealtimeMs) }
    val offset = gnssNow?.let { (now - it) / 1000f }
    val local = CLOCK.withZone(ZoneId.systemDefault())
    return listOf(
        stringResource(R.string.label_gnss_time) to (gnssNow?.let { CLOCK.withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(it)) } ?: NO_VALUE),
        stringResource(R.string.label_gnss_local_time) to (gnssNow?.let { local.format(Instant.ofEpochMilli(it)) } ?: NO_VALUE),
        stringResource(R.string.label_phone_time) to local.format(Instant.ofEpochMilli(now)),
        stringResource(R.string.label_clock_offset) to (
            offset?.let { stringResource(if (it >= 0) R.string.clock_ahead else R.string.clock_behind, abs(it).decimals(1)) } ?: NO_VALUE
            ),
    )
}

@Composable
private fun skyStats(position: Fix?, now: Long): List<Pair<String, String>> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
    val latitude = position?.latitude
    val longitude = position?.longitude
    val sun = remember(today, latitude?.roundToInt(), longitude?.roundToInt()) {
        if (latitude != null && longitude != null) Sun.times(today, latitude, longitude) else null
    }
    // In a polar day or night the next sunset or sunrise is days away.
    val wait = remember(today, latitude?.roundToInt(), longitude?.roundToInt()) {
        if (latitude != null && longitude != null) Sun.daysUntilCrossing(today, latitude, longitude) else null
    }
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone)
    val inDays = wait?.let { pluralStringResource(R.plurals.in_days, it, it) }
    val rise = sun?.rise
    val set = sun?.set
    val polar = sun != null && rise == null
    return listOf(
        stringResource(R.string.label_sunrise) to when {
            rise != null -> time.format(rise)
            polar && sun.alwaysUp -> stringResource(R.string.sun_always_up)
            polar -> inDays ?: stringResource(R.string.sun_always_down)
            else -> NO_VALUE
        },
        stringResource(R.string.label_sunset) to when {
            set != null -> time.format(set)
            polar && sun.alwaysUp -> inDays ?: stringResource(R.string.sun_always_up)
            polar -> stringResource(R.string.sun_always_down)
            else -> NO_VALUE
        },
        stringResource(R.string.label_solar_noon) to (sun?.let { time.format(it.noon) } ?: NO_VALUE),
        stringResource(R.string.label_day_length) to when {
            rise != null && set != null -> Duration.between(rise, set).let { stringResource(R.string.day_length, it.toHours().toInt(), it.toMinutesPart()) }
            polar -> stringResource(R.string.day_length_hours, if (sun.alwaysUp) 24 else 0)
            else -> NO_VALUE
        },
        stringResource(R.string.label_moon) to stringArrayResource(R.array.moon_phases)[Moon.phase(now).ordinal],
        stringResource(R.string.label_moon_lit) to stringResource(R.string.unit_percent, (Moon.illumination(now) * 100).roundToInt()),
    )
}

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss")

private const val CLOCK_TICK_MS = 250L
