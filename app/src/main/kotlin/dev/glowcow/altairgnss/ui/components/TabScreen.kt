package dev.glowcow.altairgnss.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glowcow.altairgnss.ui.theme.AltairTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * A tab's page. The content runs under the status bar and the title, which blur it like frosted
 * glass, and under the tab bar, frosted too, which slides away while the page scrolls down and
 * comes back when it scrolls up. [content] gets the heights the two cover, to pad its ends.
 * [trailing] stands at the end of the title row: a word about the state of what the tab shows.
 * [floating] lies over the page and outside its blur, and gets what the tab bar covers now.
 */
@Composable
fun TabScreen(
    tab: TopTab,
    onTab: (TopTab) -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    floating: @Composable BoxScope.(bottom: Dp) -> Unit = {},
    content: @Composable (top: Dp, bottom: Dp) -> Unit,
) {
    val c = AltairTheme.colors
    val density = LocalDensity.current
    val page = rememberGraphicsLayer()
    var headerHeight by remember { mutableIntStateOf(0) }
    val pageHeight = remember { mutableIntStateOf(0) }
    val barHeight = remember { mutableIntStateOf(0) }
    // How far the tab bar has slid down, pixels: 0 is in place.
    val barShift = remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    // How far the page has gone under the title, pixels: the title gets a line under it while that is more than nothing.
    var under by rememberSaveable { mutableFloatStateOf(0f) }
    val barScroll = remember {
        object : NestedScrollConnection {
            // Only what the page really scrolled moves the bar, so a page too short to scroll keeps it.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Pulled down with nothing left to scroll, the page is at its top whatever was counted.
                under = if (available.y > 0f && consumed.y == 0f) 0f else (under - consumed.y).coerceAtLeast(0f)
                barShift.floatValue = (barShift.floatValue - consumed.y).coerceIn(0f, barHeight.intValue.toFloat())
                return Offset.Zero
            }

            // The bar settles on its own: waiting for it here would hold up the end of the fling.
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val hidden = barHeight.intValue.toFloat()
                val target = if (barShift.floatValue > hidden / 2) hidden else 0f
                if (barShift.floatValue != target) {
                    scope.launch { animate(barShift.floatValue, target) { value, _ -> barShift.floatValue = value } }
                }
                return Velocity.Zero
            }
        }
    }
    Box(Modifier.fillMaxSize().background(c.groupBg).onSizeChanged { pageHeight.intValue = it.height }.nestedScroll(barScroll)) {
        Box(
            Modifier.fillMaxSize().drawWithContent {
                page.record { this@drawWithContent.drawContent() }
                drawLayer(page)
            },
        ) {
            content(with(density) { headerHeight.toDp() }, with(density) { barHeight.intValue.toDp() })
        }
        floating(with(density) { (barHeight.intValue - barShift.floatValue).coerceAtLeast(0f).toDp() })
        Column(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { headerHeight = it.height }
                .frosted(page, c.groupBg) { Offset.Zero }
                // What shows through the glass is not tappable through it.
                .pointerInput(Unit) {}
                .statusBarsPadding(),
        ) {
            Row(
                // The title is inset like a caption; what stands at the end lines up with the blocks below.
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(tab.label), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            HeaderLine(shown = under > 0f)
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { barHeight.intValue = it.height }
                .offset { IntOffset(0, barShift.floatValue.roundToInt()) }
                .frosted(page, c.bg) { Offset(0f, pageHeight.intValue - barHeight.intValue + barShift.floatValue) }
                .pointerInput(Unit) {},
        ) {
            BottomBar(tab, onTab)
        }
    }
}
