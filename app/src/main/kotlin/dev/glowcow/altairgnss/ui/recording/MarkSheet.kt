package dev.glowcow.altairgnss.ui.recording

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.glowcow.altairgnss.R
import dev.glowcow.altairgnss.ui.components.Group
import dev.glowcow.altairgnss.ui.components.GroupDivider
import dev.glowcow.altairgnss.ui.components.GroupField
import dev.glowcow.altairgnss.ui.components.GroupRow
import dev.glowcow.altairgnss.ui.components.GroupSheet

/**
 * What is written on a checkpoint. [action] names the row that takes the words: adding a new
 * checkpoint, or saving one that is there; closing the sheet does neither. [onDelete], where given,
 * takes the checkpoint away.
 */
@Composable
fun MarkSheet(title: String, label: String, action: String, onSave: (String) -> Unit, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null) =
    GroupSheet(title, onDismiss) { pick ->
        var text by rememberSaveable { mutableStateOf(label) }
        Group { GroupField(stringResource(R.string.mark_label), text, { text = it.take(80) }, capitalize = true, focused = true) }
        Spacer(Modifier.height(12.dp))
        Group {
            GroupRow(action, onClick = { pick { onSave(text) } })
            if (onDelete != null) {
                GroupDivider()
                GroupRow(stringResource(R.string.mark_delete), onClick = { pick(onDelete) })
            }
        }
    }
