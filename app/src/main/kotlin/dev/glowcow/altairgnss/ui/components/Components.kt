package dev.glowcow.altairgnss.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.ui.theme.AltairIcons
import dev.glowcow.altairgnss.ui.theme.AltairTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

enum class TopTab(val icon: ImageVector, val label: Int) {
    STATUS(AltairIcons.Status, R.string.tab_status),
    RECORD(AltairIcons.Record, R.string.tab_record),
    ALTIMETER(AltairIcons.Altimeter, R.string.tab_altimeter),
    INSTRUMENTS(AltairIcons.Instruments, R.string.tab_instruments),
    SETTINGS(AltairIcons.Settings, R.string.tab_settings),
}

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
    val barScroll = remember {
        object : NestedScrollConnection {
            // Only what the page really scrolled moves the bar, so a page too short to scroll keeps it.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
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
        Row(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { headerHeight = it.height }
                .frosted(page, c.groupBg) { Offset.Zero }
                // What shows through the glass is not tappable through it.
                .pointerInput(Unit) {}
                .statusBarsPadding()
                // The title is inset like a caption; what stands at the end lines up with the blocks below.
                .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(tab.label), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.weight(1f))
            trailing?.invoke()
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

/** The round button that floats over a tab's page, 20 dp above what the tab bar covers. */
@Composable
fun BoxScope.FloatingButton(icon: ImageVector, description: String, bottom: Dp, onClick: () -> Unit) {
    val c = AltairTheme.colors
    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = bottom + 20.dp)
            .size(56.dp)
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .background(c.text)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = c.bg, modifier = Modifier.size(22.dp))
    }
}

/**
 * Frosted glass: the part of [page] lying under this element, blurred and tinted with [ground].
 * [origin] is where the element sits in the page. With [fade] the glass comes in gradually over
 * that much of its top instead of starting at an edge — of its bottom when [fadeDown].
 */
@Composable
fun Modifier.frosted(page: GraphicsLayer, ground: Color, fade: Dp = 0.dp, fadeDown: Boolean = false, origin: () -> Offset): Modifier {
    val frost = rememberGraphicsLayer()
    return drawBehind {
        val radius = FROST_BLUR.toPx()
        val at = origin()
        frost.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
        // The page is redrawn over an opaque ground, so nothing sharp shows through the blur.
        frost.record {
            drawRect(ground)
            translate(-at.x, -at.y) { drawLayer(page) }
        }
        val glass = {
            // The blur spills past the bounds of its layer.
            clipRect { drawLayer(frost) }
            drawRect(ground.copy(alpha = FROST_TINT))
        }
        if (fade <= 0.dp) {
            glass()
        } else {
            drawIntoCanvas { canvas ->
                canvas.saveLayer(Rect(Offset.Zero, size), Paint())
                glass()
                val edge = (fade.toPx() / size.height).coerceIn(0f, 1f)
                val mask = if (fadeDown) {
                    Brush.verticalGradient(0f to Color.Black, 1f - edge to Color.Black, 1f to Color.Transparent)
                } else {
                    Brush.verticalGradient(0f to Color.Transparent, edge to Color.Black, 1f to Color.Black)
                }
                drawRect(mask, blendMode = BlendMode.DstIn)
                canvas.restore()
            }
        }
    }
}

private val FROST_BLUR = 20.dp

// One strength of glass for every bar and panel of the app.
private const val FROST_TINT = 0.5f

@Composable
fun BottomBar(current: TopTab, onSelect: (TopTab) -> Unit) {
    val c = AltairTheme.colors
    Column(Modifier.navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            for (tab in TopTab.entries) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    IconButton48(tab.icon, stringResource(tab.label), tint = if (tab == current) c.text else c.muted, size = 26.dp) { onSelect(tab) }
                }
            }
        }
    }
}

@Composable
fun IconButton48(
    icon: ImageVector,
    description: String,
    tint: Color = AltairTheme.colors.text,
    size: Dp = 22.dp,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(size))
    }
}
