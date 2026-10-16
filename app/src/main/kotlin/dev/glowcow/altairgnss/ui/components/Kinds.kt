package dev.glowcow.altairgnss.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glowcow.altairgnss.gnss.Constellation

/** Colours that tell kinds apart — a satellite system, an activity: the vivid ones of stackd's card palette. */
object KindColors {
    val Red = Color(0xFFE53935)
    val Orange = Color(0xFFF57C00)
    val Green = Color(0xFF43A047)
    val Cyan = Color(0xFF00ACC1)
    val Blue = Color(0xFF1E88E5)
    val Pink = Color(0xFFD81B60)
    val DeepPurple = Color(0xFF5E35B1)
    val Brown = Color(0xFF6D4C41)
    val BlueGrey = Color(0xFF546E7A)
    val Clay = Color(0xFFD97757)
    val Olive = Color(0xFF788C5D)

    /** Dark or light writing, whichever reads better on [colour]; stackd's rule for a card. */
    fun ink(colour: Color): Color = if (colour.luminance() > 0.18f) INK_DARK else INK_LIGHT

    private val INK_DARK = Color(0xFF141413)
    private val INK_LIGHT = Color(0xFFFAF9F5)
}

// Red, yellow and green stay off the systems: on Status they are the levels of a signal.
val Constellation.colour: Color
    get() = when (this) {
        Constellation.GPS -> KindColors.Blue
        Constellation.GLONASS -> KindColors.Pink
        Constellation.GALILEO -> KindColors.DeepPurple
        Constellation.BEIDOU -> KindColors.Clay
        Constellation.QZSS -> KindColors.Cyan
        Constellation.IRNSS -> KindColors.Olive
        Constellation.SBAS -> KindColors.BlueGrey
        Constellation.UNKNOWN -> KindColors.Brown
    }

/** The name of a kind in its colour, a label to read rather than to tap. */
@Composable
fun KindPill(text: String, colour: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(colour).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, color = KindColors.ink(colour), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
