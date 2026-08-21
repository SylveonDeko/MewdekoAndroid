package dev.mewdeko.mobile.feature.tickets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelector
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SliderRow
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.core.ui.clickableRow
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.relativeToNow

/** The Material icon standing in for each section. */
private val TicketSection.icon: ImageVector
    get() = when (this) {
        TicketSection.OVERVIEW -> Icons.Default.Insights
        TicketSection.TICKETS -> Icons.Default.ConfirmationNumber
        TicketSection.PANELS -> Icons.Default.ViewCarousel
        TicketSection.CONFIGURATION -> Icons.Default.Tune
        TicketSection.CASES -> Icons.Default.Folder
        TicketSection.ADVANCED -> Icons.Default.Build
    }

/** Which sheet, if any, is open over the tickets screen. */
private sealed interface TicketSheet {
    /** Adding a staff note to one ticket. */
    data class Note(val ticket: TicketSummary) : TicketSheet

    /** Retagging one ticket. */
    data class Tags(val ticket: TicketSummary) : TicketSheet

    /** Setting one ticket's urgency. */
    data class Priority(val ticket: TicketSummary) : TicketSheet

    /** Posting a new panel. */
    data object CreatePanel : TicketSheet

    /** Opening a new case. */
    data object CreateCase : TicketSheet

    /** Defining a new urgency level. */
    data object CreatePriority : TicketSheet

    /** Defining a new ticket tag. */
    data object CreateTag : TicketSheet

    /** Adding a button to the open panel. */
    data class AddButton(val panel: TicketPanel) : TicketSheet
}

/** The support ticket system. */
@Composable
fun TicketsScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: TicketsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var sheet by remember { mutableStateOf<TicketSheet?>(null) }
    var pendingClose by remember { mutableStateOf<TicketSummary?>(null) }
    var pendingDeletePanel by remember { mutableStateOf<TicketPanel?>(null) }
    var pendingDeleteButton by remember { mutableStateOf<PanelButton?>(null) }

    val panel = state.openPanel

    /** The open panel is an in-screen layer, so system back must close it first. */
    BackHandler(enabled = panel != null) { viewModel.closePanel() }

    FeatureScaffold(
        title = if (panel != null) "Panel #${panel.panel.id}" else "Tickets",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = if (panel != null) viewModel::closePanel else onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = {
            if (panel != null) viewModel.loadPanelDetail(panel.panel) else viewModel.load(true)
        },
        onRetry = { viewModel.load() },
        actions = {
            if (panel == null && state.section == TicketSection.PANELS) {
                PanelsOverflow(
                    onCreate = { sheet = TicketSheet.CreatePanel },
                    onRepostAll = viewModel::recreateAllPanels,
                )
            }
        },
        floatingActionButton = {
            when {
                panel != null -> ExtendedFloatingActionButton(
                    onClick = { sheet = TicketSheet.AddButton(panel.panel) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add button") },
                )

                state.section == TicketSection.CASES -> ExtendedFloatingActionButton(
                    onClick = { sheet = TicketSheet.CreateCase },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New case") },
                )
            }
        },
    ) {
        if (panel != null) {
            PanelDetailSection(
                detail = panel,
                onDeleteButton = { pendingDeleteButton = it },
            )
            return@FeatureScaffold
        }

        SectionTabs(
            tabs = TicketSection.entries.map { SectionTab(it.id, it.label, it.icon) },
            selectedId = state.section.id,
            onSelect = { id ->
                TicketSection.entries.firstOrNull { it.id == id }?.let(viewModel::setSection)
            },
        )

        when (state.section) {
            TicketSection.OVERVIEW -> OverviewSection(state)
            TicketSection.TICKETS -> TicketsSection(
                state = state,
                onFilter = viewModel::setFilter,
                onClaim = viewModel::claim,
                onUnclaim = viewModel::unclaim,
                onArchive = viewModel::archive,
                onClose = { pendingClose = it },
                onNote = { sheet = TicketSheet.Note(it) },
                onTags = { sheet = TicketSheet.Tags(it) },
                onPriority = { sheet = TicketSheet.Priority(it) },
            )

            TicketSection.PANELS -> PanelsSection(
                state = state,
                onOpen = viewModel::openPanel,
                onRepost = viewModel::recreatePanel,
                onDelete = { pendingDeletePanel = it },
                onCreate = { sheet = TicketSheet.CreatePanel },
            )

            TicketSection.CONFIGURATION -> ConfigurationSection(
                state = state,
                onTranscriptChannel = viewModel::setTranscriptChannel,
                onLogChannel = viewModel::setLogChannel,
                onAddPriority = { sheet = TicketSheet.CreatePriority },
                onDeletePriority = viewModel::deletePriority,
                onAddTag = { sheet = TicketSheet.CreateTag },
                onDeleteTag = viewModel::deleteTag,
            )

            TicketSection.CASES -> CasesSection(state, onCreate = { sheet = TicketSheet.CreateCase })
            TicketSection.ADVANCED -> AdvancedSection(
                state = state,
                onBlacklist = viewModel::blacklist,
                onUnblacklist = viewModel::unblacklist,
            )
        }
    }

    pendingClose?.let { ticket ->
        ConfirmDialog(
            title = "Close ticket?",
            message = "#${ticket.channelName} will be closed and a transcript saved.",
            confirmLabel = "Close ticket",
            onConfirm = { pendingClose = null; viewModel.close(ticket) },
            onDismiss = { pendingClose = null },
        )
    }

    pendingDeletePanel?.let { target ->
        ConfirmDialog(
            title = "Delete panel?",
            message = "Panel #${target.id} and its message will be removed.",
            onConfirm = { pendingDeletePanel = null; viewModel.deletePanel(target) },
            onDismiss = { pendingDeletePanel = null },
        )
    }

    pendingDeleteButton?.let { target ->
        ConfirmDialog(
            title = "Delete button?",
            message = "\"${target.label}\" will be removed from the panel.",
            onConfirm = { pendingDeleteButton = null; viewModel.deletePanelButton(target) },
            onDismiss = { pendingDeleteButton = null },
        )
    }

    when (val open = sheet) {
        null -> Unit
        is TicketSheet.Note -> TextEntrySheet(
            title = "Add note to #${open.ticket.channelName}",
            label = "Note",
            confirmLabel = "Add note",
            minLines = 4,
            onDismiss = { sheet = null },
            onConfirm = { sheet = null; viewModel.addNote(open.ticket, it) },
        )

        is TicketSheet.Tags -> TagPickerSheet(
            ticket = open.ticket,
            tags = state.tags,
            onDismiss = { sheet = null },
            onConfirm = { sheet = null; viewModel.addTags(open.ticket, it) },
        )

        is TicketSheet.Priority -> PriorityPickerSheet(
            ticket = open.ticket,
            priorities = state.priorities,
            onDismiss = { sheet = null },
            onConfirm = { sheet = null; viewModel.setPriority(open.ticket, it) },
        )

        TicketSheet.CreatePanel -> CreatePanelSheet(
            channels = state.availableChannels.map { SelectorOption(it.id, it.name) },
            onDismiss = { sheet = null },
            onConfirm = { channelId, title, description ->
                sheet = null
                viewModel.createPanel(channelId, title, description)
            },
        )

        TicketSheet.CreateCase -> CreateCaseSheet(
            onDismiss = { sheet = null },
            onConfirm = { title, description ->
                sheet = null
                viewModel.createCase(title, description)
            },
        )

        TicketSheet.CreatePriority -> CreatePrioritySheet(
            onDismiss = { sheet = null },
            onConfirm = { id, name, emoji, level, ping, minutes ->
                sheet = null
                viewModel.createPriority(id, name, emoji, level, ping, minutes)
            },
        )

        TicketSheet.CreateTag -> CreateTagSheet(
            onDismiss = { sheet = null },
            onConfirm = { id, name, description ->
                sheet = null
                viewModel.createTag(id, name, description)
            },
        )

        is TicketSheet.AddButton -> AddPanelButtonSheet(
            categories = state.availableCategories.map { SelectorOption(it.id, it.name) },
            roles = state.availableRoles.map { SelectorOption(it.id, it.name) },
            onDismiss = { sheet = null },
            onConfirm = { label, emoji, style, category, archive, support, viewer, max ->
                sheet = null
                viewModel.addPanelButton(
                    panel = open.panel,
                    label = label,
                    emoji = emoji,
                    style = style,
                    categoryId = category,
                    archiveCategoryId = archive,
                    supportRoles = support,
                    viewerRoles = viewer,
                    maxActiveTickets = max,
                )
            },
        )
    }
}

@Composable
private fun PanelsOverflow(onCreate: () -> Unit, onRepostAll: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Panel actions")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("New panel") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = { open = false; onCreate() },
            )
            DropdownMenuItem(
                text = { Text("Repost all") },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                onClick = { open = false; onRepostAll() },
            )
        }
    }
}

@Composable
private fun OverviewSection(state: TicketsState) {
    SectionCard {
        SectionCardHeader("Ticket volume", Icons.Default.Insights)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Open", "${state.countFor(TicketFilter.OPEN)}", Modifier.weight(1f))
            StatTile("Claimed", "${state.countFor(TicketFilter.CLAIMED)}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Closed", "${state.countFor(TicketFilter.CLOSED)}", Modifier.weight(1f))
            StatTile("Archived", "${state.countFor(TicketFilter.ARCHIVED)}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Panels", "${state.panels.size}", Modifier.weight(1f))
            StatTile("Cases", "${state.cases.size}", Modifier.weight(1f))
        }
    }

    SectionCard {
        SectionCardHeader("Configuration", Icons.Default.Tune)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Priorities", "${state.priorities.size}", Modifier.weight(1f))
            StatTile("Tags", "${state.tags.size}", Modifier.weight(1f))
            StatTile("Blacklisted", "${state.blacklist.size}", Modifier.weight(1f))
        }
    }

    state.tickets.firstOrNull()?.let { recent ->
        SectionCard {
            SectionCardHeader("Most recent ticket", Icons.Default.ConfirmationNumber)
            Text(
                "#${recent.channelName}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            recent.creatorName?.let {
                Text(
                    "Created by $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            recent.lastActivityAt?.let {
                Text(
                    "Last activity ${it.relativeToNow()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TicketsSection(
    state: TicketsState,
    onFilter: (TicketFilter) -> Unit,
    onClaim: (TicketSummary) -> Unit,
    onUnclaim: (TicketSummary) -> Unit,
    onArchive: (TicketSummary) -> Unit,
    onClose: (TicketSummary) -> Unit,
    onNote: (TicketSummary) -> Unit,
    onTags: (TicketSummary) -> Unit,
    onPriority: (TicketSummary) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TicketFilter.entries.forEach { filter ->
            FilterChip(
                selected = state.filter == filter,
                onClick = { onFilter(filter) },
                label = { Text("${filter.label} ${state.countFor(filter)}") },
            )
        }
    }

    if (state.visible.isEmpty()) {
        SectionCard {
            EmptyState(message = "No tickets here.", icon = Icons.Default.ConfirmationNumber)
        }
        return
    }

    state.visible.forEach { ticket ->
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.ConfirmationNumber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "#${ticket.channelName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TicketStatePill(ticket)
            }
            ticket.creatorName?.let {
                Text(
                    "Created by $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ticket.claimedByName?.let {
                Text(
                    "Claimed by $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ticket.lastActivityAt?.let {
                Text(
                    "Last activity ${it.relativeToNow()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ticket.priority?.takeIf { it.isNotEmpty() }?.let {
                    TagChip(it, icon = Icons.Default.Flag)
                }
                ticket.source?.let { TagChip(it, icon = Icons.Default.SmartButton) }
                ticket.tags.forEach { TagChip(it, icon = Icons.Default.Label) }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (ticket.isOpen) {
                    if (ticket.claimedBy == null) {
                        TextButton(onClick = { onClaim(ticket) }) {
                            Icon(
                                Icons.Default.PanTool,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text("Claim", modifier = Modifier.padding(start = 6.dp))
                        }
                    } else {
                        TextButton(onClick = { onUnclaim(ticket) }) { Text("Unclaim") }
                    }
                    TextButton(onClick = { onNote(ticket) }) {
                        Icon(
                            Icons.Default.NoteAdd,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text("Note", modifier = Modifier.padding(start = 6.dp))
                    }
                    TextButton(onClick = { onPriority(ticket) }) { Text("Priority") }
                    TextButton(onClick = { onTags(ticket) }) { Text("Tags") }
                    TextButton(onClick = { onClose(ticket) }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "Close",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                } else if (!ticket.isArchived) {
                    TextButton(onClick = { onArchive(ticket) }) {
                        Icon(
                            Icons.Default.Archive,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text("Archive", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketStatePill(ticket: TicketSummary) {
    val scheme = MaterialTheme.colorScheme
    val (label, color) = when {
        ticket.isOpen -> "Open" to scheme.tertiary
        ticket.isArchived -> "Archived" to scheme.onSurfaceVariant
        ticket.closedAt != null -> "Closed" to scheme.error
        else -> "Unknown" to scheme.onSurfaceVariant
    }
    Pill(label, color)
}

@Composable
private fun Pill(label: String, color: Color) {
    Surface(shape = CircleShape, color = color.copy(alpha = 0.16f)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun PanelsSection(
    state: TicketsState,
    onOpen: (TicketPanel) -> Unit,
    onRepost: (TicketPanel) -> Unit,
    onDelete: (TicketPanel) -> Unit,
    onCreate: () -> Unit,
) {
    if (state.panels.isEmpty()) {
        SectionCard {
            EmptyState(message = "No ticket panels yet.", icon = Icons.Default.ViewCarousel)
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Create panel", modifier = Modifier.padding(start = 8.dp))
            }
        }
        return
    }

    state.panels.forEach { panel ->
        SectionCard(modifier = Modifier.clickableRow { onOpen(panel) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.ViewCarousel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Panel #${panel.id}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "#${panel.channelName ?: panel.channelId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Panel actions")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Repost panel") },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = { menuOpen = false; onRepost(panel) },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete panel") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = { menuOpen = false; onDelete(panel) },
                        )
                    }
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TagChip("${panel.buttonCount} buttons", icon = Icons.Default.SmartButton)
                TagChip("${panel.selectMenuCount} menus", icon = Icons.Default.Tune)
            }
        }
    }
}

@Composable
private fun PanelDetailSection(detail: PanelDetail, onDeleteButton: (PanelButton) -> Unit) {
    SectionCard {
        SectionCardHeader("Panel #${detail.panel.id}", Icons.Default.ViewCarousel)
        Text(
            "#${detail.panel.channelName ?: detail.panel.channelId}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TagChip("${detail.buttons.size} buttons", icon = Icons.Default.SmartButton)
            TagChip("${detail.menus.size} menus", icon = Icons.Default.Tune)
        }
    }

    SectionCard {
        SectionCardHeader("Buttons", Icons.Default.SmartButton)
        if (detail.buttons.isEmpty()) {
            EmptyState(
                message = if (detail.loading) {
                    "Loading buttons…"
                } else {
                    "No buttons yet. Add one so members can open tickets."
                },
                icon = Icons.Default.SmartButton,
            )
        }
        detail.buttons.forEach { button ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill(button.styleLabel, buttonStyleColor(button.style))
                button.emoji?.takeIf { it.isNotEmpty() }?.let { Text(it) }
                Text(
                    button.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onDeleteButton(button) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete button",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (detail.menus.isNotEmpty()) {
        SectionCard {
            SectionCardHeader("Select menus", Icons.Default.Tune)
            detail.menus.forEach { menu ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        menu.placeholder ?: "Select menu #${menu.id}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${menu.optionTotal} options",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "Menu options are edited from the web dashboard or with /tickets commands.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The theme color matching a Discord button style. */
@Composable
private fun buttonStyleColor(style: Int): Color {
    val scheme = MaterialTheme.colorScheme
    return when (style) {
        1 -> scheme.primary
        2 -> scheme.onSurfaceVariant
        3 -> scheme.tertiary
        4 -> scheme.error
        else -> scheme.secondary
    }
}

@Composable
private fun ConfigurationSection(
    state: TicketsState,
    onTranscriptChannel: (Snowflake?) -> Unit,
    onLogChannel: (Snowflake?) -> Unit,
    onAddPriority: () -> Unit,
    onDeletePriority: (TicketPriority) -> Unit,
    onAddTag: () -> Unit,
    onDeleteTag: (TicketTag) -> Unit,
) {
    val channelOptions = state.availableChannels.map { SelectorOption(it.id, it.name) }

    SectionCard {
        SectionCardHeader("Channels", Icons.Default.Tag)
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = channelOptions,
            placeholder = "Disabled",
            selectedId = state.transcriptChannelId,
            onSelect = onTranscriptChannel,
            label = "Transcript channel",
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = channelOptions,
            placeholder = "Disabled",
            selectedId = state.logChannelId,
            onSelect = onLogChannel,
            label = "Log channel",
        )
    }

    SectionCard {
        SectionCardHeader(
            title = "Priorities",
            icon = Icons.Default.Flag,
            trailing = {
                IconButton(onClick = onAddPriority) {
                    Icon(Icons.Default.Add, contentDescription = "Add priority")
                }
            },
        )
        if (state.priorities.isEmpty()) {
            EmptyState(message = "No priorities defined.", icon = Icons.Default.Flag)
        }
        state.priorities.forEach { priority ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(priority.emoji.ifEmpty { "•" })
                Text(
                    priority.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (priority.pingStaff) TagChip("Pings staff")
                Text(
                    "Lv ${priority.level}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(onClick = { onDeletePriority(priority) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete priority",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    SectionCard {
        SectionCardHeader(
            title = "Tags",
            icon = Icons.Default.Label,
            trailing = {
                IconButton(onClick = onAddTag) {
                    Icon(Icons.Default.Add, contentDescription = "Add tag")
                }
            },
        )
        if (state.tags.isEmpty()) {
            EmptyState(message = "No tags defined.", icon = Icons.Default.Label)
        }
        state.tags.forEach { tag ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        tag.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    tag.description?.takeIf { it.isNotEmpty() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = { onDeleteTag(tag) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete tag",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun CasesSection(state: TicketsState, onCreate: () -> Unit) {
    if (state.cases.isEmpty()) {
        SectionCard {
            EmptyState(message = "No cases yet.", icon = Icons.Default.Folder)
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Create case", modifier = Modifier.padding(start = 8.dp))
            }
        }
        return
    }

    state.cases.forEach { case ->
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    case.title.ifEmpty { "Case #${case.id}" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (case.isClosed) Pill("Closed", MaterialTheme.colorScheme.error)
            }
            case.description?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                case.createdAt?.let { TagChip("Opened ${it.relativeToNow()}") }
                if (case.linked.isNotEmpty()) {
                    TagChip(
                        "${case.linked.size} tickets",
                        icon = Icons.Default.ConfirmationNumber,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedSection(
    state: TicketsState,
    onBlacklist: (Snowflake, String?) -> Unit,
    onUnblacklist: (BlacklistedUser) -> Unit,
) {
    var targetId by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    SectionCard {
        SectionCardHeader("Blacklist a user", Icons.Default.Block)
        MewdekoTextField(
            value = targetId,
            onValueChange = { targetId = it },
            label = "User ID",
            numeric = true,
        )
        MewdekoTextField(
            value = reason,
            onValueChange = { reason = it },
            label = "Reason",
            placeholder = "Optional",
        )
        Button(
            onClick = {
                onBlacklist(targetId.trim(), reason.trim().takeIf { it.isNotEmpty() })
                targetId = ""
                reason = ""
            },
            enabled = targetId.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Blacklist") }
    }

    SectionCard {
        SectionCardHeader("Currently blacklisted", Icons.Default.Block)
        if (state.blacklist.isEmpty()) {
            EmptyState(message = "No blacklisted users.", icon = Icons.Default.Block)
        }
        state.blacklist.forEach { user ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        user.username ?: user.userId ?: "Unknown",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    if (user.restrictedTypes.isNotEmpty()) {
                        Text(
                            user.restrictedTypes.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = { onUnblacklist(user) }) { Text("Remove") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetBody(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleMedium)
                content()
            },
        )
    }
}

@Composable
private fun SheetActions(
    confirmLabel: String,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
        Button(onClick = onConfirm, enabled = enabled, modifier = Modifier.weight(1f)) {
            Text(confirmLabel)
        }
    }
}

@Composable
private fun TextEntrySheet(
    title: String,
    label: String,
    confirmLabel: String,
    minLines: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    SheetBody(title, onDismiss) {
        MewdekoTextField(
            value = text,
            onValueChange = { text = it },
            label = label,
            singleLine = false,
            minLines = minLines,
        )
        SheetActions(confirmLabel, text.isNotBlank(), onDismiss) { onConfirm(text) }
    }
}

@Composable
private fun TagPickerSheet(
    ticket: TicketSummary,
    tags: List<TicketTag>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var selection by remember {
        mutableStateOf(
            tags.filter { tag -> ticket.tags.any { it.equals(tag.name, ignoreCase = true) } }
                .map { it.id }
        )
    }
    SheetBody("Tags for #${ticket.channelName}", onDismiss) {
        if (tags.isEmpty()) {
            EmptyState(
                message = "No tags defined yet. Add some under Config.",
                icon = Icons.Default.Label,
            )
        } else {
            DiscordSelector(
                kind = SelectorKind.Custom(Icons.Default.Label),
                options = tags.map { SelectorOption(it.id, it.name, it.description) },
                placeholder = "Pick tags",
                multiple = true,
                selection = selection,
                onSelectionChange = { selection = it },
            )
        }
        SheetActions("Apply", selection.isNotEmpty(), onDismiss) { onConfirm(selection) }
    }
}

@Composable
private fun PriorityPickerSheet(
    ticket: TicketSummary,
    priorities: List<TicketPriority>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var selected by remember { mutableStateOf<String?>(null) }
    SheetBody("Priority for #${ticket.channelName}", onDismiss) {
        if (priorities.isEmpty()) {
            EmptyState(
                message = "No priorities defined yet. Add some under Config.",
                icon = Icons.Default.Flag,
            )
        } else {
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.Default.Flag),
                options = priorities.map {
                    SelectorOption(it.id, "${it.emoji} ${it.name}".trim(), "Level ${it.level}")
                },
                placeholder = "Pick a priority",
                selectedId = selected,
                onSelect = { selected = it },
            )
        }
        SheetActions("Set priority", selected != null, onDismiss) {
            selected?.let(onConfirm)
        }
    }
}

@Composable
private fun CreatePanelSheet(
    channels: List<SelectorOption>,
    onDismiss: () -> Unit,
    onConfirm: (Snowflake, String, String) -> Unit,
) {
    var channelId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("Open a ticket") }
    var description by remember {
        mutableStateOf("Click the button below to open a new ticket.")
    }

    SheetBody("New panel", onDismiss) {
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = channels,
            placeholder = "Pick a channel",
            selectedId = channelId,
            onSelect = { channelId = it },
            label = "Channel",
        )
        MewdekoTextField(
            value = title,
            onValueChange = { title = it },
            label = "Embed title",
        )
        MewdekoTextField(
            value = description,
            onValueChange = { description = it },
            label = "Embed description",
            singleLine = false,
            minLines = 4,
        )
        Text(
            "Buttons and select menus are added from the panel once it has been posted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SheetActions("Create", channelId != null && title.isNotBlank(), onDismiss) {
            channelId?.let { onConfirm(it, title, description) }
        }
    }
}

@Composable
private fun CreateCaseSheet(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    SheetBody("New case", onDismiss) {
        MewdekoTextField(value = title, onValueChange = { title = it }, label = "Title")
        MewdekoTextField(
            value = description,
            onValueChange = { description = it },
            label = "Description",
            singleLine = false,
            minLines = 4,
        )
        SheetActions("Create", title.isNotBlank(), onDismiss) { onConfirm(title, description) }
    }
}

@Composable
private fun CreatePrioritySheet(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Int, Boolean, Int) -> Unit,
) {
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    var level by remember { mutableStateOf(1f) }
    var pingStaff by remember { mutableStateOf(false) }
    var responseMinutes by remember { mutableStateOf(60f) }

    SheetBody("New priority", onDismiss) {
        MewdekoTextField(
            value = id,
            onValueChange = { id = it },
            label = "ID",
            placeholder = "urgent",
            supportingText = "Lowercase slug the bot stores this priority under.",
        )
        MewdekoTextField(value = name, onValueChange = { name = it }, label = "Name")
        MewdekoTextField(
            value = emoji,
            onValueChange = { emoji = it },
            label = "Emoji",
            placeholder = "🔥",
        )
        SliderRow(
            label = "Level",
            value = level,
            onValueChange = { level = it },
            valueRange = 1f..5f,
            steps = 3,
        )
        SwitchRow(
            title = "Ping staff",
            subtitle = "Mention support roles when a ticket takes this priority",
            checked = pingStaff,
            onCheckedChange = { pingStaff = it },
        )
        SliderRow(
            label = "Response time",
            value = responseMinutes,
            onValueChange = { responseMinutes = it },
            valueRange = 5f..1440f,
            valueLabel = "${responseMinutes.toInt()}m",
        )
        SheetActions("Create", id.isNotBlank() && name.isNotBlank(), onDismiss) {
            onConfirm(id, name, emoji, level.toInt(), pingStaff, responseMinutes.toInt())
        }
    }
}

@Composable
private fun CreateTagSheet(onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    SheetBody("New tag", onDismiss) {
        MewdekoTextField(
            value = id,
            onValueChange = { id = it },
            label = "ID",
            placeholder = "billing",
            supportingText = "Lowercase slug the bot stores this tag under.",
        )
        MewdekoTextField(value = name, onValueChange = { name = it }, label = "Name")
        MewdekoTextField(
            value = description,
            onValueChange = { description = it },
            label = "Description",
        )
        SheetActions("Create", id.isNotBlank() && name.isNotBlank(), onDismiss) {
            onConfirm(id, name, description)
        }
    }
}

@Composable
private fun AddPanelButtonSheet(
    categories: List<SelectorOption>,
    roles: List<SelectorOption>,
    onDismiss: () -> Unit,
    onConfirm: (
        String, String?, Int, Snowflake?, Snowflake?, List<Snowflake>, List<Snowflake>, Int,
    ) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    var style by remember { mutableStateOf(1) }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var archiveCategoryId by remember { mutableStateOf<String?>(null) }
    var supportRoles by remember { mutableStateOf(emptyList<String>()) }
    var viewerRoles by remember { mutableStateOf(emptyList<String>()) }
    var maxActive by remember { mutableStateOf(1f) }

    SheetBody("Add button", onDismiss) {
        MewdekoTextField(
            value = label,
            onValueChange = { label = it },
            label = "Label",
            placeholder = "Open ticket",
        )
        MewdekoTextField(
            value = emoji,
            onValueChange = { emoji = it },
            label = "Emoji",
            placeholder = "Optional",
        )
        SectionTabs(
            tabs = listOf(
                SectionTab("1", "Primary"),
                SectionTab("2", "Secondary"),
                SectionTab("3", "Success"),
                SectionTab("4", "Danger"),
            ),
            selectedId = style.toString(),
            onSelect = { style = it.toIntOrNull() ?: 1 },
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Folder),
            options = categories,
            placeholder = "Same as panel channel",
            selectedId = categoryId,
            onSelect = { categoryId = it },
            label = "Ticket category",
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Archive),
            options = categories,
            placeholder = "Same as ticket category",
            selectedId = archiveCategoryId,
            onSelect = { archiveCategoryId = it },
            label = "Archive category",
        )
        DiscordSelector(
            kind = SelectorKind.Role,
            options = roles,
            placeholder = "None",
            label = "Support roles",
            multiple = true,
            selection = supportRoles,
            onSelectionChange = { supportRoles = it },
        )
        DiscordSelector(
            kind = SelectorKind.Role,
            options = roles,
            placeholder = "None",
            label = "Viewer roles",
            multiple = true,
            selection = viewerRoles,
            onSelectionChange = { viewerRoles = it },
        )
        SliderRow(
            label = "Max active tickets per user",
            value = maxActive,
            onValueChange = { maxActive = it },
            valueRange = 1f..50f,
        )
        SheetActions("Add", label.isNotBlank(), onDismiss) {
            onConfirm(
                label,
                emoji.takeIf { it.isNotEmpty() },
                style,
                categoryId,
                archiveCategoryId,
                supportRoles,
                viewerRoles,
                maxActive.toInt(),
            )
        }
    }
}
