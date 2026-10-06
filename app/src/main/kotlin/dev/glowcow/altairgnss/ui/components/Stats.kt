package dev.glowcow.altairgnss.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.ui.theme.AltairTheme
import java.util.Locale

/** Shown in place of a value the receiver has not supplied. */
const val NO_VALUE = "—"

/** Digits of equal width, so a changing number does not shift its neighbours. */
@Composable
fun tabular(): TextStyle = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")

fun Float.decimals(digits: Int): String = String.format(Locale.getDefault(), "%.${digits}f", this)

fun Double.decimals(digits: Int): String = String.format(Locale.getDefault(), "%.${digits}f", this)

/** A number with its sign, except for what rounds to zero: "-0" reads as a fault. */
fun signed(value: Double, digits: Int): String {
    val text = String.format(Locale.getDefault(), "%+.${digits}f", value)
    return if (text.none { it in '1'..'9' }) text.drop(1) else text
}

fun signed(value: Float, digits: Int): String = signed(value.toDouble(), digits)

/** The eight compass points in the user's language, north first, clockwise. */
@Composable
fun compassPoints(): List<String> {
    val (n, e, s, w) = listOf(R.string.sky_north, R.string.sky_east, R.string.sky_south, R.string.sky_west).map { stringResource(it) }
    return listOf(n, n + e, e, s + e, s, s + w, w, n + w)
}

/** A span of time as hours, minutes and seconds. */
fun clock(ms: Long): String = (ms / 1000).let { String.format(Locale.ROOT, "%d:%02d:%02d", it / 3600, it / 60 % 60, it % 60) }

/** Labelled values in two columns inside a [Group]. */
@Composable
fun StatGrid(stats: List<Pair<String, String>>) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (row in stats.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((label, value) in row) Stat(label, value, Modifier.weight(1f))
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    val c = AltairTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = c.muted, fontSize = 12.sp, maxLines = 1)
        Text(value, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, style = tabular(), maxLines = 1)
    }
}

/** A toggle in a row of filters. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = AltairTheme.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) c.accent else c.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text, color = if (selected) c.onAccent else c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
