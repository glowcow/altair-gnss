package dev.glowcow.altairgnss.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glowcow.altairgnss.ui.theme.AltairTheme

/** Lower bound of each C/N0 step in dB-Hz and its colour, weakest first. */
private val LEVELS = listOf(
    0f to Color(0xFFE5484D),
    10f to Color(0xFFF2913D),
    20f to Color(0xFFE6C229),
    30f to Color(0xFF8BC34A),
    40f to Color(0xFF30A46C),
)

/** Colour of a signal by its strength, from red for a weak one to green for a strong one. */
fun signalColor(cn0DbHz: Float): Color = LEVELS.last { cn0DbHz >= it.first || it.first == 0f }.second

/** The colour scale under a chart, each step labelled with its lower bound. */
@Composable
fun SignalLegend(modifier: Modifier = Modifier) {
    val c = AltairTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((from, color) in LEVELS) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
                Text(from.toInt().toString(), color = c.muted, fontSize = 10.sp, style = tabular())
            }
        }
    }
}
