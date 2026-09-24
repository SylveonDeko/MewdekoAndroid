package dev.mewdeko.mobile.feature.xp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader

/**
 * The rank card tab: a live preview drawn by the same renderer as the
 * designer (with the viewer's own stats when they have an XP row), the
 * entry to the full-screen Card Designer, and save or discard for staged
 * card changes.
 */
@Composable
fun XpTemplateTab(state: XpState, viewModel: XpViewModel) {
    var confirmDiscard by remember { mutableStateOf(false) }
    val data = state.realCardData(viewModel.guildName, viewModel.guildId)
        ?: XpCardData.sample(viewModel.guildName, viewModel.guildId, viewModel.userId)

    SectionCard {
        SectionCardHeader("Rank card", Icons.Default.Image)
        XpCardPreview(
            template = state.template,
            customElements = state.customElements,
            builtInOrder = state.builtInOrder,
            backgroundUrl = state.settings.customXpImageUrl,
            data = data,
            onBackgroundSize = viewModel::syncOutputSizeToBackground,
        )
        val layers = state.customElements.size
        Text(
            text = buildString {
                append("${state.template.outputSizeX} x ${state.template.outputSizeY} px")
                append(if (layers == 1) " · 1 custom layer" else " · $layers custom layers")
                if (state.settings.customXpImageUrl.isBlank()) append(" · default background")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                viewModel.designer.open = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Brush, contentDescription = null)
            Text("Open Card Designer", modifier = Modifier.padding(start = 6.dp))
        }
        if (state.hasUnsavedTemplate) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { confirmDiscard = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Text("Discard", modifier = Modifier.padding(start = 6.dp))
                }
                Button(onClick = viewModel::saveTemplate, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Text("Save card", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard card changes?",
            message = "Every unsaved change to the card, its layers, and its background is lost.",
            confirmLabel = "Discard",
            onConfirm = viewModel::resetTemplateChanges,
            onDismiss = { confirmDiscard = false },
        )
    }
}
