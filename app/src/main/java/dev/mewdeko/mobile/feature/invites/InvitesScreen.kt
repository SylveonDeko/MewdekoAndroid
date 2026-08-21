package dev.mewdeko.mobile.feature.invites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.navigation.GuildRouteArgs

/** Invite tracking configuration and leaderboard. */
@Composable
fun InvitesScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: InvitesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    FeatureScaffold(
        title = "Invites",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
    ) {
        SectionCard {
            SectionCardHeader("Overview", Icons.Default.Groups)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Total invites", "${state.totalInvites}", Modifier.weight(1f))
                StatTile("Inviters", "${state.leaderboard.size}", Modifier.weight(1f))
                StatTile(
                    label = "Average",
                    value = "%.1f".format(state.averageInvites),
                    modifier = Modifier.weight(1f),
                )
            }
            state.topInviter?.let { top ->
                Text(
                    text = "Top inviter: ${top.username} with ${top.inviteCount} invites",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard {
            SectionCardHeader("Settings", Icons.Default.Tune)
            SwitchRow(
                title = "Track invites",
                subtitle = "Record who invited each new member",
                checked = state.settings?.isEnabled == true,
                onCheckedChange = viewModel::setEnabled,
            )
            SwitchRow(
                title = "Remove credit on leave",
                subtitle = "Revoke an invite credit when the invitee leaves",
                checked = state.settings?.removeOnLeave == true,
                onCheckedChange = viewModel::setRemoveOnLeave,
            )
            MewdekoTextField(
                value = state.pendingMinAge,
                onValueChange = viewModel::setPendingMinAge,
                label = "Minimum account age",
                placeholder = "00:00:00",
                supportingText = "Accounts younger than this do not credit their inviter. " +
                    "Format hh:mm:ss.",
            )
            if (state.minAgeDirty) {
                Button(onClick = viewModel::saveMinAge, modifier = Modifier.fillMaxWidth()) {
                    Text("Save minimum age")
                }
            }
        }

        SectionCard {
            SectionCardHeader("Leaderboard", Icons.Default.Leaderboard)
            if (state.leaderboard.isEmpty()) {
                EmptyState("No invites tracked yet.", icon = Icons.Default.Group)
            } else {
                state.leaderboard.forEachIndexed { index, entry ->
                    ListItem(
                        leadingContent = {
                            Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = when (index) {
                                        0 -> MaterialTheme.colorScheme.primary
                                        1, 2 -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    textAlign = TextAlign.Center,
                                )
                            }
                        },
                        headlineContent = {
                            Text(entry.username, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(
                                text = entry.userId,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            Text(
                                text = "${entry.inviteCount}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
}
