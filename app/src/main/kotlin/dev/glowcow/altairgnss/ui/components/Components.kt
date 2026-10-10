package dev.glowcow.altairgnss.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.ui.theme.AltairIcons
import dev.glowcow.altairgnss.ui.theme.AltairTheme
import dev.glowcow.altairgnss.ui.theme.AppFont
import dev.glowcow.altairgnss.ui.theme.TitleFont

enum class TopTab(val icon: ImageVector, val label: Int) {
    STATUS(AltairIcons.Status, R.string.tab_status),
    RECORD(AltairIcons.Record, R.string.tab_record),
    ALTIMETER(AltairIcons.Altimeter, R.string.tab_altimeter),
    INSTRUMENTS(AltairIcons.Instruments, R.string.tab_instruments),
    SETTINGS(AltairIcons.Settings, R.string.tab_settings),
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

@Composable
fun BottomBar(current: TopTab, onSelect: (TopTab) -> Unit) {
    val c = AltairTheme.colors
    Column(Modifier.navigationBarsPadding()) {
        GlassLine()
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            for (tab in TopTab.entries) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    IconButton48(tab.icon, stringResource(tab.label), tint = if (tab == current) c.text else c.muted, size = 26.dp) { onSelect(tab) }
                }
            }
        }
    }
}

/**
 * The title of a page, on one line: Cormorant Garamond, [large] on a tab and the size a title was
 * before in a row with buttons; the app's own bold face where the language is Hebrew, which that face lacks.
 */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier, large: Boolean = false) {
    val own = LocalConfiguration.current.locales[0].language.let { it == "iw" || it == "he" }
    Text(
        text,
        modifier,
        color = AltairTheme.colors.text,
        fontFamily = if (own) AppFont else TitleFont,
        fontSize = when {
            own -> 22.sp
            large -> TAB_TITLE
            else -> PAGE_TITLE
        },
        fontWeight = if (own) FontWeight.Bold else FontWeight.Normal,
        style = TextStyle(fontFeatureSettings = "lnum"),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private val TAB_TITLE = 36.sp

// Cormorant's capitals are lower than Arimo's: at this size they stand as tall as a 22 sp title did.
private val PAGE_TITLE = 25.sp

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
