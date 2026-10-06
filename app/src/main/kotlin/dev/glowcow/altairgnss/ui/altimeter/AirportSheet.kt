package dev.glowcow.altairgnss.ui.altimeter

import android.text.format.DateUtils
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.airports.NearbyAirport
import dev.glowcow.altairgnss.container
import dev.glowcow.altairgnss.ui.components.Group
import dev.glowcow.altairgnss.ui.components.GroupDivider
import dev.glowcow.altairgnss.ui.components.GroupRow
import dev.glowcow.altairgnss.ui.components.GroupSheet
import dev.glowcow.altairgnss.ui.components.decimals
import dev.glowcow.altairgnss.ui.components.distanceText
import dev.glowcow.altairgnss.ui.components.signed
import dev.glowcow.altairgnss.ui.theme.AltairTheme

private sealed interface Airports {
    data object Loading : Airports
    data object NoPosition : Airports
    data object Failed : Airports
    class Found(val list: List<NearbyAirport>) : Airports
}

/** Looks up the airports around and lets the user pick whose pressure report to calibrate to. */
@Composable
fun AirportSheet(onPick: (NearbyAirport) -> Unit, onDismiss: () -> Unit) =
    GroupSheet(stringResource(R.string.airport_title), onDismiss) { pick ->
        val c = AltairTheme.colors
        val container = LocalContext.current.container
        val airports by produceState<Airports>(Airports.Loading) {
            val position = container.gnss.lastKnownPosition()
            value = if (position == null) Airports.NoPosition else {
                runCatching { container.airports.nearby(position.first, position.second) }.fold({ Airports.Found(it) }, { Airports.Failed })
            }
        }
        val now = System.currentTimeMillis()
        when (val state = airports) {
            Airports.Loading -> Note(stringResource(R.string.airport_loading))
            Airports.NoPosition -> Note(stringResource(R.string.airport_no_position))
            Airports.Failed -> Note(stringResource(R.string.airport_failed))
            is Airports.Found -> if (state.list.isEmpty()) Note(stringResource(R.string.airport_none)) else Group {
                state.list.take(MAX_SHOWN).forEachIndexed { i, airport ->
                    if (i > 0) GroupDivider()
                    GroupRow(
                        "${airport.report.name.substringBefore(',')} · ${airport.report.icao}",
                        subtitle = stringResource(
                            R.string.airport_row,
                            distanceText(airport.distanceKm * 1000, 0),
                            airport.report.qnhHpa.decimals(0),
                            DateUtils.getRelativeTimeSpanString(airport.report.timeMs, now, DateUtils.MINUTE_IN_MILLIS).toString(),
                        ) + airport.report.temperatureC?.let { " · " + stringResource(R.string.unit_celsius, signed(it, 0)) }.orEmpty(),
                        onClick = { pick { onPick(airport) } },
                    )
                }
            }
        }
        Text(stringResource(R.string.airport_hint), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp))
    }

@Composable
private fun Note(text: String) {
    Text(text, color = AltairTheme.colors.text, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
}

private const val MAX_SHOWN = 6
