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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.ui.theme.AltairIcons
import dev.glowcow.altairgnss.ui.theme.AltairTheme

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
