package dev.mewdeko.mobile.feature.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.model.WarningRecord
import dev.mewdeko.mobile.core.theme.MonospaceStyle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.SearchField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.relativeToNow
import dev.mewdeko.mobile.util.withSeparators

private val Tabs = listOf(
    SectionTab("overview", "Overview", Icons.Default.BarChart),
    SectionTab("warnings", "Warnings", Icons.Default.Warning),
    SectionTab("punishments", "Ladder", Icons.Default.Gavel),
)

/** Warnings, the auto-punishment ladder, and the warn-log destination. */
@Composable
fun ModerationScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: ModerationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    FeatureScaffold(
        title = "Moderation",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
    ) {
        SectionTabs(
            tabs = Tabs,
            selectedId = state.section,
            onSelect = viewModel::setSection,
        )

        state.warnLogChannelName?.let { name ->
            SectionCard(contentPadding = 12) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Warn log",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "#$name",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        when (state.section) {
            "warnings" -> WarningsSection(state, viewModel)
            "punishments" -> PunishmentsSection(state)
            else -> OverviewSection(state)
        }
    }
}

@Composable
private fun OverviewSection(state: ModerationState) {
    SectionCard {
        SectionCardHeader("Warning totals", Icons.Default.BarChart)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Total", state.warnings.size.withSeparators(), Modifier.weight(1f))
            StatTile(
                label = "Active",
                value = state.activeCount.withSeparators(),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Forgiven",
                value = state.forgivenCount.withSeparators(),
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (state.punishments.isNotEmpty()) {
        SectionCard {
            SectionCardHeader("Auto-punishment ladder", Icons.Default.Shield)
            state.punishments.take(5).forEach { PunishmentRow(it, compact = true) }
        }
    }

    if (state.warnings.isNotEmpty()) {
        SectionCard {
            SectionCardHeader("Recent activity", Icons.Default.Schedule)
            state.warnings.take(5).forEach { warning ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = warning.userId ?: "Unknown user",
                            style = MonospaceStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        warning.reason?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    warning.dateAdded?.let {
                        Text(
                            text = it.relativeToNow(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningsSection(state: ModerationState, viewModel: ModerationViewModel) {
    SectionCard {
        SearchField(
            value = state.filterText,
            onValueChange = viewModel::setFilter,
            placeholder = "Filter by user ID or reason",
        )
        SwitchRow(
            title = "Active only",
            subtitle = "Hide forgiven warnings",
            checked = state.activeOnly,
            onCheckedChange = viewModel::setActiveOnly,
        )
    }

    val filtered = state.filteredWarnings
    if (filtered.isEmpty()) {
        SectionCard { EmptyState("No warnings.", icon = Icons.Default.Warning) }
    } else {
        filtered.forEach { WarningCard(it) }
    }
}

@Composable
private fun WarningCard(warning: WarningRecord) {
    SectionCard(contentPadding = 12) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = warning.userId ?: "Unknown user",
                style = MonospaceStyle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (warning.forgiven) TagChip("Forgiven")
        }
        warning.reason?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            warning.moderator?.let {
                Text(
                    text = "by $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            warning.dateAdded?.let {
                Text(
                    text = it.relativeToNow(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PunishmentsSection(state: ModerationState) {
    if (state.punishments.isEmpty()) {
        SectionCard {
            EmptyState("No automatic punishments configured.", icon = Icons.Default.Gavel)
        }
        return
    }
    state.punishments.forEach { PunishmentRow(it, compact = false) }
}

@Composable
private fun PunishmentRow(punishment: WarningPunishment, compact: Boolean) {
    val content = @Composable {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.width(if (compact) 36.dp else 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${punishment.count}w",
                    style = if (compact) MaterialTheme.typography.labelLarge
                    else MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Center,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = punishment.actionLabel,
                    style = if (compact) MaterialTheme.typography.bodySmall
                    else MaterialTheme.typography.titleSmall,
                )
                if (!compact) {
                    if (punishment.time > 0) {
                        Text(
                            text = "Duration: ${punishment.time}m",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    punishment.roleId?.let {
                        Text(
                            text = "Role: $it",
                            style = MonospaceStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (compact && punishment.time > 0) {
                Text(
                    text = "${punishment.time}m",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (compact) content() else SectionCard(contentPadding = 12) { content() }
}
