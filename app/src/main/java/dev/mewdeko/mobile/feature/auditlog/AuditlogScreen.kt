package dev.mewdeko.mobile.feature.auditlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.shortDateTime
import dev.mewdeko.mobile.util.withSeparators

/** Sentinel selector id standing for "no filter". */
private const val ALL_OPTION_ID = "__all__"

/** Who accessed the dashboard, what they changed, and what they viewed. */
@Composable
fun AuditlogScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: AuditlogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    val actionOptions = remember {
        listOf(SelectorOption(ALL_OPTION_ID, "All actions")) +
            AuditAction.entries.map { SelectorOption(it.value.toString(), it.label) }
    }
    val sectionOptions = listOf(SelectorOption(ALL_OPTION_ID, "All sections")) +
        state.sections.map { SelectorOption(it, auditSectionLabel(it)) }

    FeatureScaffold(
        title = "Audit Log",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        actions = {
            IconButton(onClick = { viewModel.load() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        },
    ) {
        SectionCard {
            SectionCardHeader("Filters", Icons.Default.FilterList)
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.Default.FilterList),
                options = actionOptions,
                placeholder = "All actions",
                label = "Action",
                selectedId = state.actionFilter?.value?.toString() ?: ALL_OPTION_ID,
                onSelect = { id ->
                    val action = id
                        ?.takeIf { it != ALL_OPTION_ID }
                        ?.toIntOrNull()
                        ?.let(AuditAction::fromValue)
                    if (action != state.actionFilter) viewModel.setActionFilter(action)
                },
            )
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.Default.Layers),
                options = sectionOptions,
                placeholder = "All sections",
                label = "Section",
                selectedId = state.sectionFilter ?: ALL_OPTION_ID,
                onSelect = { id ->
                    val section = id?.takeIf { it != ALL_OPTION_ID }
                    if (section != state.sectionFilter) viewModel.setSectionFilter(section)
                },
            )
        }

        SectionCard {
            SectionCardHeader(
                title = "Entries",
                icon = Icons.Default.History,
                trailing = {
                    Text(
                        text = "${state.total.withSeparators()} total",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            when {
                loadState.isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                loadState.error != null -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Failed to load the audit log.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { viewModel.load() }) { Text("Try again") }
                }

                state.entries.isEmpty() -> EmptyState(
                    message = "No audit log entries match the current filters.",
                    icon = Icons.Default.History,
                )

                else -> state.entries.forEachIndexed { index, entry ->
                    if (index > 0) HorizontalDivider()
                    AuditEntryRow(
                        entry = entry,
                        expanded = state.expandedId == entry.id,
                        onToggle = { viewModel.toggleExpanded(entry.id) },
                    )
                }
            }
        }

        if (state.entries.isNotEmpty() && loadState.error == null) {
            Pagination(
                page = state.page,
                totalPages = state.totalPages,
                total = state.total,
                enabled = !loadState.isLoading,
                onPrevious = { viewModel.goToPage(state.page - 1) },
                onNext = { viewModel.goToPage(state.page + 1) },
            )
        }
    }
}

@Composable
private fun AuditEntryRow(
    entry: AuditLogEntry,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val rows = remember(entry.id, entry.changes) { summarizeAuditChanges(entry.changes) }
    val path = entry.endpointPath

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionBadge(entry)
            Spacer(Modifier.weight(1f))
            Text(
                text = entry.dateAdded?.shortDateTime() ?: "Unknown",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = entry.displayUser,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = auditSectionLabel(entry.section),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (path.isNotEmpty()) {
            Text(
                text = "${entry.httpMethod} $path".trim(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (rows.isNotEmpty()) {
            TextButton(onClick = onToggle) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(if (expanded) "Hide details" else "Show details")
            }
            if (expanded) {
                ChangeDetails(rows)
            }
        }
    }
}

@Composable
private fun ActionBadge(entry: AuditLogEntry) {
    val action = entry.auditAction
    val tint = action?.tone?.let { toneColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = 0.15f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                action?.icon ?: Icons.Default.HelpOutline,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = action?.label ?: "Action ${entry.action}",
                style = MaterialTheme.typography.labelMedium,
                color = tint,
            )
        }
    }
}

@Composable
private fun toneColor(tone: AuditTone): Color = when (tone) {
    AuditTone.PRIMARY -> MaterialTheme.colorScheme.primary
    AuditTone.SECONDARY -> MaterialTheme.colorScheme.secondary
    AuditTone.ACCENT -> MaterialTheme.colorScheme.tertiary
}

@Composable
private fun ChangeDetails(rows: List<AuditChangeRow>) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rows.forEach { row ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = buildAnnotatedString {
                            if (row.before != null) {
                                withStyle(SpanStyle(color = muted)) { append(row.before) }
                                withStyle(SpanStyle(color = muted)) { append("  →  ") }
                            }
                            append(row.after)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun Pagination(
    page: Int,
    totalPages: Int,
    total: Int,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    SectionCard {
        Text(
            text = "${total.withSeparators()} entries, page $page of $totalPages",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onPrevious,
                enabled = enabled && page > 1,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Previous")
            }
            OutlinedButton(
                onClick = onNext,
                enabled = enabled && page < totalPages,
                modifier = Modifier.weight(1f),
            ) {
                Text("Next")
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
