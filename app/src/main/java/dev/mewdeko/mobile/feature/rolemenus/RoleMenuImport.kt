package dev.mewdeko.mobile.feature.rolemenus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EnumPicker
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.readableInk

/** The Move older setups tab: each older emoji role setup with the controls to move it to a role menu. */
@Composable
fun RoleMenuImport(state: RoleMenusState, viewModel: RoleMenusViewModel) {
    Text(
        text = "Older setups had members add an emoji under a message to get a role. Move one to a role " +
            "menu and members pick from a dropdown or buttons instead.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )

    if (state.importSources.isEmpty()) {
        SectionCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(48.dp)
                        .alpha(0.5f),
                )
                Text(
                    text = "Nothing to move",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Older emoji role setups made with the old command show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    state.importSources.forEach { source ->
        ImportCard(
            source = source,
            state = state,
            busy = source.id in state.busySourceIds,
            onMove = { style, channelId, name, copy, retire ->
                viewModel.importSetup(source, style, channelId, name, copy, retire)
            },
        )
    }
}

/** One older setup: its channel, pairs, and the move controls. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportCard(
    source: RoleMenuImportSource,
    state: RoleMenusState,
    busy: Boolean,
    onMove: (RoleMenuStyle, Snowflake?, String, Boolean, Boolean) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var styleValue by rememberSaveable(source.id) { mutableIntStateOf(RoleMenuStyle.DROPDOWN.value) }
    var channelId by rememberSaveable(source.id) {
        mutableStateOf(source.channelId.takeIf { it.isNotBlank() && it != "0" })
    }
    var name by rememberSaveable(source.id) { mutableStateOf("") }
    var copyMessage by rememberSaveable(source.id) { mutableStateOf(true) }
    var retire by rememberSaveable(source.id) { mutableStateOf(true) }
    val primary = MaterialTheme.colorScheme.primary

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = source.channelName?.let { "#$it" } ?: "Deleted channel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (source.jumpUrl.isNotBlank()) {
                TextButton(onClick = { runCatching { uriHandler.openUri(source.jumpUrl) } }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("  Open in Discord")
                }
            }
        }

        if (source.exclusive) {
            Badge("Pick one")
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            source.pairs.forEach { pair ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = primary.copy(alpha = if (pair.roleExists) DashAlpha.Hex20 else DashAlpha.Hex08),
                    border = BorderStroke(1.dp, primary.copy(alpha = DashAlpha.Hex30)),
                    modifier = Modifier.heightIn(min = 28.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoleMenuEmoji(pair.emoji, size = 14.dp)
                        if (pair.roleExists) {
                            Text(
                                text = "@" + (pair.roleName ?: pair.roleId),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = readableInk(primary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            Text(
                                text = "deleted role",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textDecoration = TextDecoration.LineThrough,
                            )
                        }
                    }
                }
            }
        }

        EnumPicker(
            label = "Menu type",
            options = StyleOptions,
            selected = RoleMenuStyle.from(styleValue),
            onSelect = { styleValue = it.value },
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = state.postableChannels.map { SelectorOption(it.id, it.name, subtitle = it.categoryName) },
            placeholder = "Pick a channel",
            label = "Post in",
            selectedId = channelId,
            onSelect = { channelId = it },
        )
        MewdekoTextField(
            value = name,
            onValueChange = { name = it.take(RoleMenuLimits.NameLength) },
            label = "Name",
            placeholder = RoleMenuLimits.DefaultName,
        )
        SwitchRow(
            title = "Copy the original message",
            subtitle = "Uses the text and embeds from the old message",
            checked = copyMessage,
            onCheckedChange = { copyMessage = it },
        )
        SwitchRow(
            title = "Retire the old setup",
            subtitle = "Stops the old emoji setup and clears its emojis. If the bot posted the original " +
                "message, it's deleted.",
            checked = retire,
            onCheckedChange = { retire = it },
        )
        RoleMenuActionButton(
            text = "Move to a role menu",
            icon = Icons.Default.SwapHoriz,
            onClick = { onMove(RoleMenuStyle.from(styleValue), channelId, name, copyMessage, retire) },
            solid = true,
            loading = busy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A small badge at the primary `20` wash with solid text. */
@Composable
private fun Badge(text: String) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(50),
        color = primary.copy(alpha = DashAlpha.Hex20),
        border = BorderStroke(1.dp, primary.copy(alpha = DashAlpha.Hex30)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = readableInk(primary),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
