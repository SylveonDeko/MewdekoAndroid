package dev.mewdeko.mobile.feature.tickets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.model.EmbedFooter
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.model.EmbedSpec
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.net.normalizeKeys
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelector
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.EnumOption
import dev.mewdeko.mobile.core.ui.EnumPicker
import dev.mewdeko.mobile.core.ui.FeatureLinkCard
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.FormSheet
import dev.mewdeko.mobile.core.ui.FullScreenEditor
import dev.mewdeko.mobile.core.ui.NewItemFab
import dev.mewdeko.mobile.core.ui.InfoRow
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

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

/** A blank starting embed for new panels. */
private val DefaultPanelEmbedJson: String
    get() = EmbedMessage(
        embeds = listOf(
            EmbedSpec(
                title = "Open a ticket",
                description = "Click the button below to open a new ticket.",
            )
        ),
    ).serialize()

/** Which sheet, if any, is open over the tickets screen. */
private sealed interface TicketSheet {
    /** Adding a staff note to one ticket. */
    data class Note(val ticket: TicketSummary) : TicketSheet

    /** Retagging one ticket. */
    data class Tags(val ticket: TicketSummary) : TicketSheet

    /** Setting one ticket's urgency. */
    data class Priority(val ticket: TicketSummary) : TicketSheet

    /** Closing a ticket, with an optional reason. */
    data class CloseTicket(val ticket: TicketSummary) : TicketSheet

    /** Posting a new panel. */
    data object CreatePanel : TicketSheet

    /** Replacing an existing panel's embed. */
    data class EditPanelEmbed(val panel: TicketPanel) : TicketSheet

    /** Opening a new case. */
    data object CreateCase : TicketSheet

    /** Linking more tickets into the open case. */
    data class LinkTickets(val caseId: Int) : TicketSheet

    /** Defining a new urgency level. */
    data object CreatePriority : TicketSheet

    /** Defining a new ticket tag. */
    data object CreateTag : TicketSheet

    /** Adding a button to the open panel. */
    data class AddButton(val panel: TicketPanel) : TicketSheet

    /** Adding a select menu to the open panel. */
    data class AddMenu(val panel: TicketPanel) : TicketSheet

    /** Editing one select menu's placeholder. */
    data class EditMenuPlaceholder(val panel: TicketPanel, val menu: PanelSelectMenu) : TicketSheet

    /** Adding an option to the open select menu. */
    data class AddMenuOption(val menu: PanelSelectMenu) : TicketSheet
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
    var pendingDeletePanel by remember { mutableStateOf<TicketPanel?>(null) }
    var pendingDeleteButton by remember { mutableStateOf<PanelButton?>(null) }
    var pendingDeleteMenu by remember { mutableStateOf<PanelSelectMenu?>(null) }
    var pendingDeleteOption by remember { mutableStateOf<SelectMenuOption?>(null) }
    var pendingBatchClose by remember { mutableStateOf<Int?>(null) }

    val panel = state.openPanel
    val menu = state.openMenu
    val caseDetail = state.openCase
    val drilledDown = panel != null || caseDetail != null

    /** Panels, menus, and case detail are in-screen layers, so back must unwind them first. */
    BackHandler(enabled = drilledDown) {
        when {
            menu != null -> viewModel.closeMenu()
            panel != null -> viewModel.closePanel()
            caseDetail != null -> viewModel.closeCaseDetail()
        }
    }

    val screenTitle = when {
        menu != null -> menu.menu.placeholder ?: "Menu #${menu.menu.id}"
        panel != null -> panel.panel.displayLabel
        caseDetail != null -> caseDetail.detail.title.ifEmpty { "Case #${caseDetail.detail.id}" }
        else -> "Tickets"
    }

    FeatureScaffold(
        title = screenTitle,
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = if (drilledDown) {
            {
                when {
                    menu != null -> viewModel.closeMenu()
                    panel != null -> viewModel.closePanel()
                    caseDetail != null -> viewModel.closeCaseDetail()
                }
            }
        } else onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = {
            when {
                panel != null -> viewModel.loadPanelDetail(panel.panel)
                caseDetail != null -> viewModel.loadCaseDetail(caseDetail.detail.id)
                else -> viewModel.load(true)
            }
        },
        onRetry = { viewModel.load() },
        actions = {
            if (!drilledDown && state.section == TicketSection.PANELS) {
                PanelsOverflow(onRepostAll = viewModel::recreateAllPanels)
            }
        },
        floatingActionButton = {
            when {
                menu != null -> NewItemFab(
                    label = "Add option",
                    onClick = { sheet = TicketSheet.AddMenuOption(menu.menu) },
                )

                panel != null -> NewItemFab(
                    label = "Add button",
                    onClick = { sheet = TicketSheet.AddButton(panel.panel) },
                )

                caseDetail == null && state.section == TicketSection.CASES -> NewItemFab(
                    label = "New case",
                    onClick = { sheet = TicketSheet.CreateCase },
                )

                caseDetail == null && state.section == TicketSection.PANELS -> NewItemFab(
                    label = "New panel",
                    onClick = { sheet = TicketSheet.CreatePanel },
                )
            }
        },
    ) {
        when {
            menu != null -> {
                MenuDetailSection(
                    detail = menu,
                    onEditPlaceholder = { sheet = TicketSheet.EditMenuPlaceholder(menu.panel, menu.menu) },
                    onDeleteMenu = { pendingDeleteMenu = menu.menu },
                    onEditOption = viewModel::openOptionEditor,
                    onDeleteOption = { pendingDeleteOption = it },
                )
                return@FeatureScaffold
            }

            panel != null -> {
                PanelDetailSection(
                    detail = panel,
                    onEditEmbed = { sheet = TicketSheet.EditPanelEmbed(panel.panel) },
                    onCheckStatus = { viewModel.checkPanelStatus(panel.panel) },
                    onEditButton = viewModel::openButtonEditor,
                    onDeleteButton = { pendingDeleteButton = it },
                    onOpenMenu = { viewModel.openMenu(panel.panel, it) },
                    onAddMenu = { sheet = TicketSheet.AddMenu(panel.panel) },
                )
                return@FeatureScaffold
            }

            caseDetail != null -> {
                CaseDetailSection(
                    detail = caseDetail,
                    unlinkedTickets = state.unlinkedTickets,
                    onClose = { viewModel.closeTicketCase(caseDetail.detail.id) },
                    onReopen = { viewModel.reopenTicketCase(caseDetail.detail.id) },
                    onLinkMore = { sheet = TicketSheet.LinkTickets(caseDetail.detail.id) },
                    onUnlink = { viewModel.unlinkTicket(caseDetail.detail.id, it) },
                )
                return@FeatureScaffold
            }
        }

        SectionTabs(
            tabs = TicketSection.entries.map { SectionTab(it.id, it.label, it.icon) },
            selectedId = state.section.id,
            onSelect = { id ->
                TicketSection.entries.firstOrNull { it.id == id }?.let(viewModel::setSection)
            },
        )

        when (state.section) {
            TicketSection.OVERVIEW -> OverviewSection(state, onNavigate = viewModel::setSection)
            TicketSection.TICKETS -> TicketsSection(
                guildId = guild.id,
                state = state,
                onFilter = viewModel::setFilter,
                onSearch = viewModel::setSearch,
                onClaim = viewModel::claim,
                onUnclaim = viewModel::unclaim,
                onArchive = viewModel::archive,
                onClose = { sheet = TicketSheet.CloseTicket(it) },
                onNote = { sheet = TicketSheet.Note(it) },
                onTags = { sheet = TicketSheet.Tags(it) },
                onPriority = { sheet = TicketSheet.Priority(it) },
            )

            TicketSection.PANELS -> PanelsSection(
                state = state,
                onOpen = viewModel::openPanel,
                onRepost = viewModel::recreatePanel,
                onCheckStatus = { viewModel.checkPanelStatus(it) },
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

            TicketSection.CASES -> CasesSection(
                state = state,
                onOpen = viewModel::openCase,
                onCreate = { sheet = TicketSheet.CreateCase },
            )

            TicketSection.ADVANCED -> AdvancedSection(
                state = state,
                onBatchClose = { pendingBatchClose = it },
                onBlacklist = viewModel::blacklist,
                onUnblacklist = viewModel::unblacklist,
            )
        }
    }

    pendingDeletePanel?.let { target ->
        ConfirmDialog(
            title = "Delete panel?",
            message = "${target.displayLabel} and its message will be removed.",
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

    pendingDeleteMenu?.let { target ->
        ConfirmDialog(
            title = "Delete select menu?",
            message = "This menu and all of its options will be removed from the panel.",
            onConfirm = {
                pendingDeleteMenu = null
                panel?.let { viewModel.deleteMenu(it.panel, target) }
            },
            onDismiss = { pendingDeleteMenu = null },
        )
    }

    pendingDeleteOption?.let { target ->
        ConfirmDialog(
            title = "Delete option?",
            message = "\"${target.label}\" will be removed from the menu.",
            onConfirm = { pendingDeleteOption = null; viewModel.deleteMenuOption(target.id) },
            onDismiss = { pendingDeleteOption = null },
        )
    }

    pendingBatchClose?.let { hours ->
        ConfirmDialog(
            title = "Close inactive tickets?",
            message = "Every open ticket with no activity in the last $hours hour(s) will be closed.",
            confirmLabel = "Close tickets",
            onConfirm = { pendingBatchClose = null; viewModel.batchCloseInactive(hours) },
            onDismiss = { pendingBatchClose = null },
        )
    }

    state.editingButton?.let { button ->
        EditButtonSheet(
            button = button,
            categories = state.availableCategories.map { SelectorOption(it.id, it.name) },
            roles = state.availableRoles.map { SelectorOption(it.id, it.name) },
            priorities = state.priorities,
            onDismiss = viewModel::closeButtonEditor,
            onConfirm = { form -> viewModel.updateButton(button, form) },
        )
    }

    state.editingOption?.let { option ->
        EditMenuOptionSheet(
            option = option,
            categories = state.availableCategories.map { SelectorOption(it.id, it.name) },
            roles = state.availableRoles.map { SelectorOption(it.id, it.name) },
            priorities = state.priorities,
            onDismiss = viewModel::closeOptionEditor,
            onConfirm = { form -> viewModel.updateMenuOption(option, form) },
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
            onConfirm = { original, updated ->
                sheet = null
                viewModel.updateTags(open.ticket, original, updated)
            },
        )

        is TicketSheet.Priority -> PriorityPickerSheet(
            ticket = open.ticket,
            priorities = state.priorities,
            onDismiss = { sheet = null },
            onConfirm = { sheet = null; viewModel.setPriority(open.ticket, it) },
        )

        is TicketSheet.CloseTicket -> CloseTicketSheet(
            ticket = open.ticket,
            onDismiss = { sheet = null },
            onConfirm = { reason -> sheet = null; viewModel.close(open.ticket, reason) },
        )

        TicketSheet.CreatePanel -> CreatePanelSheet(
            channels = state.availableChannels.map { SelectorOption(it.id, it.name) },
            onDismiss = { sheet = null },
            onConfirm = { channelId, embedJson ->
                sheet = null
                viewModel.createPanel(channelId, embedJson)
            },
        )

        is TicketSheet.EditPanelEmbed -> EmbedBuilderSheet(
            title = "Edit panel embed",
            confirmLabel = "Save",
            initialJson = open.panel.embedJson,
            onDismiss = { sheet = null },
            onConfirm = { json -> sheet = null; viewModel.updatePanelEmbed(open.panel, json) },
        )

        TicketSheet.CreateCase -> CreateCaseSheet(
            unlinkedTickets = state.unlinkedTickets,
            onDismiss = { sheet = null },
            onConfirm = { title, description, linkIds ->
                sheet = null
                viewModel.createCase(title, description, linkIds)
            },
        )

        is TicketSheet.LinkTickets -> LinkTicketsSheet(
            unlinkedTickets = state.unlinkedTickets,
            onDismiss = { sheet = null },
            onConfirm = { ids -> sheet = null; viewModel.linkTickets(open.caseId, ids) },
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

        is TicketSheet.AddButton -> AddButtonSheet(
            categories = state.availableCategories.map { SelectorOption(it.id, it.name) },
            roles = state.availableRoles.map { SelectorOption(it.id, it.name) },
            priorities = state.priorities,
            onDismiss = { sheet = null },
            onConfirm = { form -> sheet = null; viewModel.addPanelButton(open.panel, form) },
        )

        is TicketSheet.AddMenu -> CreateMenuSheet(
            onDismiss = { sheet = null },
            onConfirm = { placeholder, label, description, emoji ->
                sheet = null
                viewModel.createSelectMenu(open.panel, placeholder, label, description, emoji)
            },
        )

        is TicketSheet.EditMenuPlaceholder -> TextEntrySheet(
            title = "Menu placeholder",
            label = "Placeholder text",
            confirmLabel = "Save",
            minLines = 1,
            initial = open.menu.placeholder.orEmpty(),
            onDismiss = { sheet = null },
            onConfirm = { sheet = null; viewModel.updateMenuPlaceholder(open.panel, open.menu, it) },
        )

        is TicketSheet.AddMenuOption -> AddMenuOptionSheet(
            categories = state.availableCategories.map { SelectorOption(it.id, it.name) },
            roles = state.availableRoles.map { SelectorOption(it.id, it.name) },
            priorities = state.priorities,
            onDismiss = { sheet = null },
            onConfirm = { form -> sheet = null; viewModel.addMenuOption(open.menu, form) },
        )
    }
}

@Composable
private fun PanelsOverflow(onRepostAll: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Panel actions")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Repost all") },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                onClick = { open = false; onRepostAll() },
            )
        }
    }
}

@Composable
private fun OverviewSection(state: TicketsState, onNavigate: (TicketSection) -> Unit) {
    val stats = state.overview?.statistics

    SectionCard {
        SectionCardHeader("Ticket volume", Icons.Default.Insights)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Total", "${stats?.totalTickets ?: state.tickets.size}", Modifier.weight(1f))
            StatTile("Open", "${stats?.openTickets ?: state.countFor(TicketFilter.OPEN)}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Closed", "${stats?.closedTickets ?: state.countFor(TicketFilter.CLOSED)}", Modifier.weight(1f))
            StatTile("Active panels", "${state.panels.size}", Modifier.weight(1f))
        }
    }

    if (stats != null) {
        SectionCard {
            SectionCardHeader("Response times", Icons.Default.Insights)
            InfoRow("Average response", "${"%.1f".format(stats.averageResponseTime)} min")
            InfoRow("Average resolution", "${"%.1f".format(stats.averageResolutionTime)} hrs")
        }

        if (stats.ticketsByPriority.isNotEmpty()) {
            SectionCard {
                SectionCardHeader("Tickets by priority", Icons.Default.Flag)
                stats.ticketsByPriority.forEach { (name, count) ->
                    InfoRow(name.ifEmpty { "None" }, "$count")
                }
            }
        }
    }

    state.overview?.staffResponseStats?.takeIf { it.isNotEmpty() }?.let { staff ->
        SectionCard {
            SectionCardHeader("Staff performance", Icons.Default.ConfirmationNumber)
            staff.forEach { s ->
                InfoRow(s.staffName, "${"%.1f".format(s.averageResponseTimeMinutes)} min avg")
            }
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

    SectionCard {
        SectionCardHeader("Quick links", Icons.Default.ChevronRight)
        FeatureLinkCard(
            title = "Manage panels",
            subtitle = "Create and configure ticket panels",
            icon = Icons.Default.ViewCarousel,
            onClick = { onNavigate(TicketSection.PANELS) },
        )
        FeatureLinkCard(
            title = "Configuration",
            subtitle = "Set up priorities, tags, and channels",
            icon = Icons.Default.Tune,
            onClick = { onNavigate(TicketSection.CONFIGURATION) },
        )
        FeatureLinkCard(
            title = "View cases",
            subtitle = "Manage linked ticket cases",
            icon = Icons.Default.Folder,
            onClick = { onNavigate(TicketSection.CASES) },
        )
        FeatureLinkCard(
            title = "Advanced tools",
            subtitle = "Batch operations and blacklist",
            icon = Icons.Default.Build,
            onClick = { onNavigate(TicketSection.ADVANCED) },
        )
    }
}

@Composable
private fun TicketsSection(
    guildId: Snowflake,
    state: TicketsState,
    onFilter: (TicketFilter) -> Unit,
    onSearch: (String) -> Unit,
    onClaim: (TicketSummary) -> Unit,
    onUnclaim: (TicketSummary) -> Unit,
    onArchive: (TicketSummary) -> Unit,
    onClose: (TicketSummary) -> Unit,
    onNote: (TicketSummary) -> Unit,
    onTags: (TicketSummary) -> Unit,
    onPriority: (TicketSummary) -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    MewdekoTextField(
        value = state.searchQuery,
        onValueChange = onSearch,
        label = "Search",
        placeholder = "Ticket #, creator, channel, claimer, tag",
    )

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
            ticket.createdAt?.let {
                Text(
                    "Opened ${it.relativeToNow()}",
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
                ticket.caseId?.let { TagChip("Case #$it", icon = Icons.Default.Folder) }
                ticket.tags.forEach { TagChip(it, icon = Icons.AutoMirrored.Filled.Label) }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextButton(
                    onClick = {
                        uriHandler.openUri("https://discord.com/channels/$guildId/${ticket.channelId}")
                    },
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text("Discord", modifier = Modifier.padding(start = 6.dp))
                }
                ticket.transcriptUrl?.takeIf { it.isNotEmpty() }?.let { url ->
                    TextButton(onClick = { uriHandler.openUri(url) }) { Text("Transcript") }
                }
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
                            Icons.AutoMirrored.Filled.NoteAdd,
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
    onCheckStatus: (TicketPanel) -> Unit,
    onDelete: (TicketPanel) -> Unit,
    onCreate: () -> Unit,
) {
    if (state.panels.isEmpty()) {
        SectionCard {
            EmptyState(
                message = "No ticket panels yet.",
                icon = Icons.Default.ViewCarousel,
                actionLabel = "New panel",
                onAction = onCreate,
            )
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
                        panel.displayLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Panel actions")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Check status") },
                            onClick = { menuOpen = false; onCheckStatus(panel) },
                        )
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
private fun PanelDetailSection(
    detail: PanelDetail,
    onEditEmbed: () -> Unit,
    onCheckStatus: () -> Unit,
    onEditButton: (PanelButton) -> Unit,
    onDeleteButton: (PanelButton) -> Unit,
    onOpenMenu: (PanelSelectMenu) -> Unit,
    onAddMenu: () -> Unit,
) {
    SectionCard {
        SectionCardHeader(
            title = detail.panel.displayLabel,
            icon = Icons.Default.ViewCarousel,
            trailing = {
                var open by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { open = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Panel actions")
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit embed") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { open = false; onEditEmbed() },
                        )
                        DropdownMenuItem(
                            text = { Text("Check status") },
                            onClick = { open = false; onCheckStatus() },
                        )
                    }
                }
            },
        )
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
                IconButton(onClick = { onEditButton(button) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit button")
                }
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

    SectionCard {
        SectionCardHeader(
            title = "Select menus",
            icon = Icons.Default.Tune,
            trailing = {
                IconButton(onClick = onAddMenu) {
                    Icon(Icons.Default.Add, contentDescription = "Add select menu")
                }
            },
        )
        if (detail.menus.isEmpty()) {
            EmptyState(message = "No select menus yet.", icon = Icons.Default.Tune)
        }
        detail.menus.forEach { menu ->
            Row(
                modifier = Modifier.fillMaxWidth().clickableRow { onOpenMenu(menu) },
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
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MenuDetailSection(
    detail: MenuDetail,
    onEditPlaceholder: () -> Unit,
    onDeleteMenu: () -> Unit,
    onEditOption: (SelectMenuOption) -> Unit,
    onDeleteOption: (SelectMenuOption) -> Unit,
) {
    SectionCard {
        SectionCardHeader(
            title = detail.menu.placeholder ?: "Select menu #${detail.menu.id}",
            icon = Icons.Default.Tune,
            trailing = {
                var open by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { open = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu actions")
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit placeholder") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { open = false; onEditPlaceholder() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete menu") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = { open = false; onDeleteMenu() },
                        )
                    }
                }
            },
        )
    }

    SectionCard {
        SectionCardHeader("Options", Icons.AutoMirrored.Filled.Label)
        if (detail.menu.options.isEmpty()) {
            EmptyState(message = "No options yet.", icon = Icons.AutoMirrored.Filled.Label)
        }
        detail.menu.options.forEach { option ->
            Row(
                modifier = Modifier.fillMaxWidth().clickableRow { onEditOption(option) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                option.emoji?.takeIf { it.isNotEmpty() }?.let { Text(it) }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    option.description?.takeIf { it.isNotEmpty() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = { onDeleteOption(option) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete option",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun CaseDetailSection(
    detail: CaseDetailState,
    unlinkedTickets: List<TicketSummary>,
    onClose: () -> Unit,
    onReopen: () -> Unit,
    onLinkMore: () -> Unit,
    onUnlink: (Int) -> Unit,
) {
    val case = detail.detail
    SectionCard {
        SectionCardHeader(
            title = case.title.ifEmpty { "Case #${case.id}" },
            icon = Icons.Default.Folder,
            trailing = {
                if (case.isClosed) Pill("Closed", MaterialTheme.colorScheme.error)
                else Pill("Open", MaterialTheme.colorScheme.tertiary)
            },
        )
        case.description?.takeIf { it.isNotEmpty() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        case.createdAt?.let { InfoRow("Opened", it.relativeToNow()) }
        if (case.isClosed) {
            Button(onClick = onReopen, modifier = Modifier.fillMaxWidth()) { Text("Reopen case") }
        } else {
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close case") }
        }
    }

    SectionCard {
        SectionCardHeader(
            title = "Linked tickets",
            icon = Icons.Default.ConfirmationNumber,
            trailing = {
                if (unlinkedTickets.isNotEmpty()) {
                    IconButton(onClick = onLinkMore) {
                        Icon(Icons.Default.Add, contentDescription = "Link tickets")
                    }
                }
            },
        )
        if (detail.loading) {
            EmptyState(message = "Loading…", icon = Icons.Default.ConfirmationNumber)
        } else if (case.linkedTickets.isEmpty()) {
            EmptyState(message = "No tickets linked yet.", icon = Icons.Default.ConfirmationNumber)
        }
        case.linkedTickets.forEach { ticket ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "#${ticket.channelName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    ticket.creatorName?.let {
                        Text(
                            "by $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (ticket.isArchived) TagChip("Archived")
                else if (ticket.closedAt != null) TagChip("Closed")
                IconButton(onClick = { onUnlink(ticket.id) }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Unlink ticket",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (case.notes.isNotEmpty()) {
        SectionCard {
            SectionCardHeader("Notes", Icons.AutoMirrored.Filled.NoteAdd)
            case.notes.forEach { note ->
                Text(note.content, style = MaterialTheme.typography.bodyMedium)
            }
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
            icon = Icons.AutoMirrored.Filled.Label,
            trailing = {
                IconButton(onClick = onAddTag) {
                    Icon(Icons.Default.Add, contentDescription = "Add tag")
                }
            },
        )
        if (state.tags.isEmpty()) {
            EmptyState(message = "No tags defined.", icon = Icons.AutoMirrored.Filled.Label)
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
private fun CasesSection(state: TicketsState, onOpen: (TicketCase) -> Unit, onCreate: () -> Unit) {
    if (state.cases.isEmpty()) {
        SectionCard {
            EmptyState(
                message = "No cases yet.",
                icon = Icons.Default.Folder,
                actionLabel = "New case",
                onAction = onCreate,
            )
        }
        return
    }

    state.cases.forEach { case ->
        SectionCard(modifier = Modifier.clickableRow { onOpen(case) }) {
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
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                if (case.linkedTickets > 0) {
                    TagChip("${case.linkedTickets} tickets", icon = Icons.Default.ConfirmationNumber)
                }
            }
        }
    }
}

@Composable
private fun AdvancedSection(
    state: TicketsState,
    onBatchClose: (Int) -> Unit,
    onBlacklist: (Snowflake, String?) -> Unit,
    onUnblacklist: (BlacklistedUser) -> Unit,
) {
    var inactiveHours by remember { mutableStateOf(48f) }
    var targetId by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    SectionCard {
        SectionCardHeader("Batch close inactive tickets", Icons.Default.Close)
        SliderRow(
            label = "Inactive for at least",
            value = inactiveHours,
            onValueChange = { inactiveHours = it },
            valueRange = 1f..720f,
            valueLabel = "${inactiveHours.toInt()}h",
        )
        Button(onClick = { onBatchClose(inactiveHours.toInt()) }, modifier = Modifier.fillMaxWidth()) {
            Text("Close inactive tickets")
        }
    }

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

/**
 * Hosts every ticket form with the app wide rule for create and edit flows:
 * short forms open in a [FormSheet], while forms with an embed builder, a
 * nested list, or more than five inputs pass [long] and open in a
 * [FullScreenEditor].
 */
@Composable
private fun TicketForm(
    title: String,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    long: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    if (long) {
        FullScreenEditor(
            title = title,
            onClose = onDismiss,
            confirmLabel = confirmLabel,
            confirmEnabled = confirmEnabled,
            onConfirm = onConfirm,
            content = content,
        )
    } else {
        FormSheet(
            title = title,
            confirmLabel = confirmLabel,
            confirmEnabled = confirmEnabled,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            content = content,
        )
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
    initial: String = "",
) {
    var text by remember { mutableStateOf(initial) }
    TicketForm(title, confirmLabel, text.isNotBlank(), onDismiss, { onConfirm(text) }) {
        MewdekoTextField(
            value = text,
            onValueChange = { text = it },
            label = label,
            singleLine = false,
            minLines = minLines,
        )
    }
}

@Composable
private fun CloseTicketSheet(
    ticket: TicketSummary,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    TicketForm(
        title = "Close #${ticket.channelName}",
        confirmLabel = "Close ticket",
        confirmEnabled = true,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(reason.trim().takeIf { it.isNotBlank() }) },
    ) {
        Text(
            "A transcript will be saved and the ticket marked closed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MewdekoTextField(
            value = reason,
            onValueChange = { reason = it },
            label = "Reason",
            placeholder = "Optional",
            singleLine = false,
            minLines = 3,
        )
    }
}

@Composable
private fun TagPickerSheet(
    ticket: TicketSummary,
    tags: List<TicketTag>,
    onDismiss: () -> Unit,
    onConfirm: (original: List<String>, updated: List<String>) -> Unit,
) {
    val originalIds = remember(ticket, tags) {
        tags.filter { tag -> ticket.tags.any { it.equals(tag.name, ignoreCase = true) } }
            .map { it.id }
    }
    var selection by remember { mutableStateOf(originalIds) }
    TicketForm(
        title = "Tags for #${ticket.channelName}",
        confirmLabel = "Apply",
        confirmEnabled = true,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(originalIds, selection) },
    ) {
        if (tags.isEmpty()) {
            EmptyState(
                message = "No tags defined yet. Add some under Config.",
                icon = Icons.AutoMirrored.Filled.Label,
            )
        } else {
            DiscordSelector(
                kind = SelectorKind.Custom(Icons.AutoMirrored.Filled.Label),
                options = tags.map { SelectorOption(it.id, it.name, it.description) },
                placeholder = "Pick tags",
                multiple = true,
                selection = selection,
                onSelectionChange = { selection = it },
            )
        }
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
    TicketForm(
        title = "Priority for #${ticket.channelName}",
        confirmLabel = "Set priority",
        confirmEnabled = selected != null,
        onDismiss = onDismiss,
        onConfirm = { selected?.let(onConfirm) },
    ) {
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
    }
}

@Composable
private fun EmbedBuilderSheet(
    title: String,
    confirmLabel: String,
    initialJson: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val initialMessage = remember(initialJson) { EmbedMessage.parse(initialJson) }
    val initialSpec = remember(initialMessage) { initialMessage.embeds.firstOrNull() ?: EmbedSpec.Blank }
    var content by remember { mutableStateOf(initialMessage.content) }
    var embedTitle by remember { mutableStateOf(initialSpec.title) }
    var description by remember { mutableStateOf(initialSpec.description) }
    var color by remember { mutableStateOf(initialSpec.color) }
    var footerText by remember { mutableStateOf(initialSpec.footer.text) }
    var imageUrl by remember { mutableStateOf(initialSpec.imageUrl) }
    var thumbnailUrl by remember { mutableStateOf(initialSpec.thumbnailUrl) }
    var useRaw by remember { mutableStateOf(false) }
    var rawJson by remember { mutableStateOf(initialJson.orEmpty()) }

    TicketForm(
        title = title,
        confirmLabel = confirmLabel,
        confirmEnabled = if (useRaw) rawJson.isNotBlank() else true,
        onDismiss = onDismiss,
        onConfirm = {
            val json = if (useRaw) {
                rawJson.trim()
            } else {
                EmbedMessage(
                    content = content,
                    embeds = listOf(
                        EmbedSpec(
                            title = embedTitle,
                            description = description,
                            color = color,
                            footer = EmbedFooter(text = footerText),
                        ).withImage(imageUrl).withThumbnail(thumbnailUrl),
                    ),
                ).serialize()
            }
            onConfirm(json)
        },
        long = true,
    ) {
        SwitchRow(
            title = "Raw JSON",
            subtitle = "Paste a dashboard-style embed JSON payload instead",
            checked = useRaw,
            onCheckedChange = { useRaw = it },
        )
        if (useRaw) {
            MewdekoTextField(
                value = rawJson,
                onValueChange = { rawJson = it },
                label = "Embed JSON",
                singleLine = false,
                minLines = 8,
            )
        } else {
            MewdekoTextField(value = content, onValueChange = { content = it }, label = "Message text", placeholder = "Optional")
            MewdekoTextField(value = embedTitle, onValueChange = { embedTitle = it }, label = "Embed title")
            MewdekoTextField(
                value = description,
                onValueChange = { description = it },
                label = "Embed description",
                singleLine = false,
                minLines = 3,
            )
            MewdekoTextField(value = color, onValueChange = { color = it }, label = "Color", placeholder = "#3498DB")
            MewdekoTextField(value = footerText, onValueChange = { footerText = it }, label = "Footer text", placeholder = "Optional")
            MewdekoTextField(value = imageUrl, onValueChange = { imageUrl = it }, label = "Image URL", placeholder = "Optional")
            MewdekoTextField(value = thumbnailUrl, onValueChange = { thumbnailUrl = it }, label = "Thumbnail URL", placeholder = "Optional")
        }
    }
}

/** One question in a ticket-creation form. */
private data class ModalFieldDraft(
    val key: String,
    val label: String = "",
    val style: Int = 1,
    val required: Boolean = true,
    val placeholder: String = "",
)

/** Decodes the bot's `{title, fields: {key: {...}}}` modal payload. */
private fun parseModalJson(json: String?): Pair<String, List<ModalFieldDraft>> {
    if (json.isNullOrBlank()) return "Create Ticket" to emptyList()
    return runCatching {
        val root = MewdekoJson.parseToJsonElement(json).normalizeKeys() as? JsonObject
            ?: return@runCatching "Create Ticket" to emptyList()
        val title = (root["title"] as? JsonPrimitive)?.content ?: "Create Ticket"
        val fields = (root["fields"] as? JsonObject)?.entries?.map { (key, value) ->
            val obj = value as? JsonObject
            ModalFieldDraft(
                key = key,
                label = (obj?.get("label") as? JsonPrimitive)?.content ?: key,
                style = (obj?.get("style") as? JsonPrimitive)?.content?.toIntOrNull() ?: 1,
                required = (obj?.get("required") as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: true,
                placeholder = (obj?.get("placeholder") as? JsonPrimitive)?.content ?: "",
            )
        }.orEmpty()
        title to fields
    }.getOrDefault("Create Ticket" to emptyList())
}

/** Encodes a modal draft back into the bot's payload, or `null` when there are no questions. */
private fun buildModalJson(title: String, fields: List<ModalFieldDraft>): String? {
    val usable = fields.filter { it.label.isNotBlank() }
    if (usable.isEmpty()) return null
    val obj = buildJsonObject {
        put("title", JsonPrimitive(title.ifBlank { "Create Ticket" }))
        put(
            "fields",
            buildJsonObject {
                usable.forEachIndexed { index, field ->
                    val key = field.key.ifBlank { "field_${index + 1}" }
                    put(
                        key,
                        buildJsonObject {
                            put("label", JsonPrimitive(field.label))
                            put("style", JsonPrimitive(field.style))
                            put("required", JsonPrimitive(field.required))
                            if (field.placeholder.isNotBlank()) {
                                put("placeholder", JsonPrimitive(field.placeholder))
                            }
                        },
                    )
                }
            },
        )
    }
    return MewdekoJson.encodeToString(JsonObject.serializer(), obj)
}

@Composable
private fun ModalBuilderSheet(
    initialJson: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    val parsed = remember(initialJson) { parseModalJson(initialJson) }
    var title by remember { mutableStateOf(parsed.first) }
    var fields by remember {
        mutableStateOf(parsed.second.ifEmpty { listOf(ModalFieldDraft(key = "field_1")) })
    }
    var enabled by remember { mutableStateOf(!initialJson.isNullOrBlank()) }

    TicketForm(
        title = "Ticket form",
        confirmLabel = "Save",
        confirmEnabled = true,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(if (enabled) buildModalJson(title, fields) else null) },
        long = true,
    ) {
        SwitchRow(
            title = "Custom form",
            subtitle = "Ask up to 5 questions before the ticket opens",
            checked = enabled,
            onCheckedChange = { enabled = it },
        )
        if (enabled) {
            MewdekoTextField(value = title, onValueChange = { title = it }, label = "Modal title")
            fields.forEachIndexed { index, field ->
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Question ${index + 1}", style = MaterialTheme.typography.labelLarge)
                        if (fields.size > 1) {
                            IconButton(onClick = { fields = fields.toMutableList().also { it.removeAt(index) } }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove question",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    MewdekoTextField(
                        value = field.label,
                        onValueChange = { v -> fields = fields.toMutableList().also { it[index] = field.copy(label = v) } },
                        label = "Label",
                    )
                    MewdekoTextField(
                        value = field.placeholder,
                        onValueChange = { v -> fields = fields.toMutableList().also { it[index] = field.copy(placeholder = v) } },
                        label = "Placeholder",
                        placeholder = "Optional",
                    )
                    EnumPicker(
                        label = "Answer style",
                        options = ModalFieldStyleOptions,
                        selected = field.style,
                        onSelect = { v -> fields = fields.toMutableList().also { it[index] = field.copy(style = v) } },
                    )
                    SwitchRow(
                        title = "Required",
                        checked = field.required,
                        onCheckedChange = { v -> fields = fields.toMutableList().also { it[index] = field.copy(required = v) } },
                    )
                }
            }
            if (fields.size < 5) {
                OutlinedButton(
                    onClick = { fields = fields + ModalFieldDraft(key = "field_${fields.size + 1}") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Add question", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun CreatePanelSheet(
    channels: List<SelectorOption>,
    onDismiss: () -> Unit,
    onConfirm: (Snowflake, String) -> Unit,
) {
    var channelId by remember { mutableStateOf<String?>(null) }
    var embedJson by remember { mutableStateOf<String?>(null) }
    var showEmbed by remember { mutableStateOf(false) }

    TicketForm(
        title = "New panel",
        confirmLabel = "Create",
        confirmEnabled = channelId != null && !embedJson.isNullOrBlank(),
        onDismiss = onDismiss,
        onConfirm = {
            val id = channelId
            val json = embedJson
            if (id != null && json != null) onConfirm(id, json)
        },
        long = true,
    ) {
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = channels,
            placeholder = "Pick a channel",
            selectedId = channelId,
            onSelect = { channelId = it },
            label = "Channel",
        )
        OutlinedButton(onClick = { showEmbed = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (embedJson.isNullOrBlank()) "Design panel embed" else "Edit panel embed")
        }
        Text(
            "Buttons and select menus are added from the panel once it has been posted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showEmbed) {
        EmbedBuilderSheet(
            title = "Panel embed",
            confirmLabel = "Save",
            initialJson = embedJson ?: DefaultPanelEmbedJson,
            onDismiss = { showEmbed = false },
            onConfirm = { json -> embedJson = json; showEmbed = false },
        )
    }
}

@Composable
private fun CreateCaseSheet(
    unlinkedTickets: List<TicketSummary>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, List<Int>) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf(emptyList<String>()) }

    TicketForm(
        title = "New case",
        confirmLabel = "Create",
        confirmEnabled = title.isNotBlank(),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(title, description, selection.mapNotNull { it.toIntOrNull() }) },
    ) {
        MewdekoTextField(value = title, onValueChange = { title = it }, label = "Title")
        MewdekoTextField(
            value = description,
            onValueChange = { description = it },
            label = "Description",
            singleLine = false,
            minLines = 4,
        )
        if (unlinkedTickets.isNotEmpty()) {
            DiscordSelector(
                kind = SelectorKind.Custom(Icons.Default.ConfirmationNumber),
                options = unlinkedTickets.map { SelectorOption(it.id.toString(), "#${it.channelName}", "Ticket #${it.id}") },
                placeholder = "None",
                label = "Link tickets",
                multiple = true,
                selection = selection,
                onSelectionChange = { selection = it },
            )
        }
    }
}

@Composable
private fun LinkTicketsSheet(
    unlinkedTickets: List<TicketSummary>,
    onDismiss: () -> Unit,
    onConfirm: (List<Int>) -> Unit,
) {
    var selection by remember { mutableStateOf(emptyList<String>()) }
    TicketForm(
        title = "Link tickets",
        confirmLabel = "Link",
        confirmEnabled = selection.isNotEmpty(),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(selection.mapNotNull { it.toIntOrNull() }) },
    ) {
        if (unlinkedTickets.isEmpty()) {
            EmptyState(message = "Every ticket is already linked to a case.", icon = Icons.Default.ConfirmationNumber)
        } else {
            DiscordSelector(
                kind = SelectorKind.Custom(Icons.Default.ConfirmationNumber),
                options = unlinkedTickets.map { SelectorOption(it.id.toString(), "#${it.channelName}", "Ticket #${it.id}") },
                placeholder = "Pick tickets",
                multiple = true,
                selection = selection,
                onSelectionChange = { selection = it },
            )
        }
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

    TicketForm(
        title = "New priority",
        confirmLabel = "Create",
        confirmEnabled = id.isNotBlank() && name.isNotBlank(),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(id, name, emoji, level.toInt(), pingStaff, responseMinutes.toInt()) },
        long = true,
    ) {
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
    }
}

@Composable
private fun CreateTagSheet(onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    TicketForm(
        title = "New tag",
        confirmLabel = "Create",
        confirmEnabled = id.isNotBlank() && name.isNotBlank(),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(id, name, description) },
    ) {
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
    }
}

@Composable
private fun CreateMenuSheet(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?, String?) -> Unit,
) {
    var placeholder by remember { mutableStateOf("Select an option") }
    var label by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }

    TicketForm(
        title = "New select menu",
        confirmLabel = "Create",
        confirmEnabled = placeholder.isNotBlank() && label.isNotBlank(),
        onDismiss = onDismiss,
        onConfirm = {
            onConfirm(placeholder, label, description.takeIf { it.isNotBlank() }, emoji.takeIf { it.isNotBlank() })
        },
    ) {
        MewdekoTextField(value = placeholder, onValueChange = { placeholder = it }, label = "Placeholder text")
        MewdekoTextField(value = label, onValueChange = { label = it }, label = "First option label")
        MewdekoTextField(
            value = description,
            onValueChange = { description = it },
            label = "First option description",
            placeholder = "Optional",
        )
        MewdekoTextField(value = emoji, onValueChange = { emoji = it }, label = "First option emoji", placeholder = "Optional")
    }
}

/**
 * Mutable draft for a panel button or select menu option, holding the full
 * set of ticket-opening settings both share on the bot.
 */
private class ComponentDraft(
    label: String = "",
    description: String = "",
    emoji: String = "",
    style: Int = 1,
    channelFormat: String = "",
    categoryId: Snowflake? = null,
    archiveCategoryId: Snowflake? = null,
    supportRoles: List<Snowflake> = emptyList(),
    viewerRoles: List<Snowflake> = emptyList(),
    maxActiveTickets: Float = 1f,
    autoCloseHours: Float = 0f,
    requiredResponseMinutes: Float = 0f,
    allowedPriorities: List<String> = emptyList(),
    defaultPriority: String? = null,
    openMessageJson: String? = null,
    modalJson: String? = null,
    saveTranscript: Boolean = true,
    deleteOnClose: Boolean = false,
    lockOnClose: Boolean = true,
    renameOnClose: Boolean = true,
    removeCreatorOnClose: Boolean = false,
    deleteDelaySeconds: Float = 0f,
    lockOnArchive: Boolean = true,
    renameOnArchive: Boolean = true,
    removeCreatorOnArchive: Boolean = false,
    autoArchiveOnClose: Boolean = false,
) {
    var label by mutableStateOf(label)
    var description by mutableStateOf(description)
    var emoji by mutableStateOf(emoji)
    var style by mutableStateOf(style)
    var channelFormat by mutableStateOf(channelFormat)
    var categoryId by mutableStateOf(categoryId)
    var archiveCategoryId by mutableStateOf(archiveCategoryId)
    var supportRoles by mutableStateOf(supportRoles)
    var viewerRoles by mutableStateOf(viewerRoles)
    var maxActiveTickets by mutableStateOf(maxActiveTickets)
    var autoCloseHours by mutableStateOf(autoCloseHours)
    var requiredResponseMinutes by mutableStateOf(requiredResponseMinutes)
    var allowedPriorities by mutableStateOf(allowedPriorities)
    var defaultPriority by mutableStateOf(defaultPriority)
    var openMessageJson by mutableStateOf(openMessageJson)
    var modalJson by mutableStateOf(modalJson)
    var saveTranscript by mutableStateOf(saveTranscript)
    var deleteOnClose by mutableStateOf(deleteOnClose)
    var lockOnClose by mutableStateOf(lockOnClose)
    var renameOnClose by mutableStateOf(renameOnClose)
    var removeCreatorOnClose by mutableStateOf(removeCreatorOnClose)
    var deleteDelaySeconds by mutableStateOf(deleteDelaySeconds)
    var lockOnArchive by mutableStateOf(lockOnArchive)
    var renameOnArchive by mutableStateOf(renameOnArchive)
    var removeCreatorOnArchive by mutableStateOf(removeCreatorOnArchive)
    var autoArchiveOnClose by mutableStateOf(autoArchiveOnClose)

    fun toSubmission(): ComponentSubmission = ComponentSubmission(
        label = label.trim(),
        description = description.trim().takeIf { it.isNotEmpty() },
        emoji = emoji.takeIf { it.isNotBlank() },
        style = style,
        channelFormat = channelFormat.takeIf { it.isNotBlank() },
        categoryId = categoryId,
        archiveCategoryId = archiveCategoryId,
        supportRoles = supportRoles,
        viewerRoles = viewerRoles,
        maxActiveTickets = maxActiveTickets.toInt(),
        autoCloseHours = autoCloseHours.toInt().takeIf { it > 0 },
        requiredResponseMinutes = requiredResponseMinutes.toInt().takeIf { it > 0 },
        allowedPriorities = allowedPriorities,
        defaultPriority = defaultPriority,
        openMessageJson = openMessageJson,
        modalJson = modalJson,
        saveTranscript = saveTranscript,
        deleteOnClose = deleteOnClose,
        lockOnClose = lockOnClose,
        renameOnClose = renameOnClose,
        removeCreatorOnClose = removeCreatorOnClose,
        deleteDelaySeconds = deleteDelaySeconds.toInt(),
        lockOnArchive = lockOnArchive,
        renameOnArchive = renameOnArchive,
        removeCreatorOnArchive = removeCreatorOnArchive,
        autoArchiveOnClose = autoArchiveOnClose,
    )

    companion object {
        fun fromButton(button: PanelButton) = ComponentDraft(
            label = button.label,
            emoji = button.emoji.orEmpty(),
            style = button.style,
            channelFormat = button.channelNameFormat.orEmpty(),
            categoryId = button.categoryId,
            archiveCategoryId = button.archiveCategoryId,
            supportRoles = button.supportRoles,
            viewerRoles = button.viewerRoles,
            maxActiveTickets = button.maxActiveTickets.toFloat(),
            autoCloseHours = timeSpanToHours(button.autoCloseTime).toFloat(),
            requiredResponseMinutes = timeSpanToMinutes(button.requiredResponseTime).toFloat(),
            allowedPriorities = button.allowedPriorities,
            defaultPriority = button.defaultPriority,
            openMessageJson = button.openMessageJson,
            modalJson = button.modalJson,
            saveTranscript = button.saveTranscript,
            deleteOnClose = button.deleteOnClose,
            lockOnClose = button.lockOnClose,
            renameOnClose = button.renameOnClose,
            removeCreatorOnClose = button.removeCreatorOnClose,
            deleteDelaySeconds = timeSpanToSeconds(button.deleteDelay).toFloat(),
            lockOnArchive = button.lockOnArchive,
            renameOnArchive = button.renameOnArchive,
            removeCreatorOnArchive = button.removeCreatorOnArchive,
            autoArchiveOnClose = button.autoArchiveOnClose,
        )

        fun fromOption(option: SelectMenuOption) = ComponentDraft(
            label = option.label,
            description = option.description.orEmpty(),
            emoji = option.emoji.orEmpty(),
            channelFormat = option.channelNameFormat.orEmpty(),
            categoryId = option.categoryId,
            archiveCategoryId = option.archiveCategoryId,
            supportRoles = option.supportRoles,
            viewerRoles = option.viewerRoles,
            maxActiveTickets = option.maxActiveTickets.toFloat(),
            autoCloseHours = timeSpanToHours(option.autoCloseTime).toFloat(),
            requiredResponseMinutes = timeSpanToMinutes(option.requiredResponseTime).toFloat(),
            allowedPriorities = option.allowedPriorities,
            defaultPriority = option.defaultPriority,
            openMessageJson = option.openMessageJson,
            modalJson = option.modalJson,
            saveTranscript = option.saveTranscript,
            deleteOnClose = option.deleteOnClose,
            lockOnClose = option.lockOnClose,
            renameOnClose = option.renameOnClose,
            removeCreatorOnClose = option.removeCreatorOnClose,
            deleteDelaySeconds = timeSpanToSeconds(option.deleteDelay).toFloat(),
            lockOnArchive = option.lockOnArchive,
            renameOnArchive = option.renameOnArchive,
            removeCreatorOnArchive = option.removeCreatorOnArchive,
            autoArchiveOnClose = option.autoArchiveOnClose,
        )
    }
}

/** Discord's button styles, keyed by the numeric value the bot stores. */
private val ButtonStyleOptions = listOf(
    EnumOption(1, "Primary", "Blurple, the default call to action."),
    EnumOption(2, "Secondary", "Grey, for a quieter button."),
    EnumOption(3, "Success", "Green."),
    EnumOption(4, "Danger", "Red."),
)

/** Discord's modal text input styles, keyed by the numeric value the bot stores. */
private val ModalFieldStyleOptions = listOf(
    EnumOption(1, "Short", "A single line answer."),
    EnumOption(2, "Paragraph", "A multi line answer."),
)

@Composable
private fun ComponentFormFields(
    draft: ComponentDraft,
    showStyle: Boolean,
    showDescription: Boolean,
    showToggles: Boolean,
    categories: List<SelectorOption>,
    roles: List<SelectorOption>,
    priorities: List<TicketPriority>,
    onEditOpenMessage: () -> Unit,
    onEditModal: () -> Unit,
) {
    MewdekoTextField(value = draft.label, onValueChange = { draft.label = it }, label = "Label", placeholder = "Open ticket")
    if (showDescription) {
        MewdekoTextField(
            value = draft.description,
            onValueChange = { draft.description = it },
            label = "Description",
            placeholder = "Optional",
        )
    }
    MewdekoTextField(value = draft.emoji, onValueChange = { draft.emoji = it }, label = "Emoji", placeholder = "Optional")
    if (showStyle) {
        EnumPicker(
            label = "Button style",
            options = ButtonStyleOptions,
            selected = draft.style,
            onSelect = { draft.style = it },
        )
    }
    MewdekoTextField(
        value = draft.channelFormat,
        onValueChange = { draft.channelFormat = it },
        label = "Channel name format",
        placeholder = "ticket-{username}",
    )
    DiscordSelectorSingle(
        kind = SelectorKind.Custom(Icons.Default.Folder),
        options = categories,
        placeholder = "Same as panel channel",
        selectedId = draft.categoryId,
        onSelect = { draft.categoryId = it },
        label = "Ticket category",
    )
    DiscordSelectorSingle(
        kind = SelectorKind.Custom(Icons.Default.Archive),
        options = categories,
        placeholder = "Same as ticket category",
        selectedId = draft.archiveCategoryId,
        onSelect = { draft.archiveCategoryId = it },
        label = "Archive category",
    )
    DiscordSelector(
        kind = SelectorKind.Role,
        options = roles,
        placeholder = "None",
        label = "Support roles",
        multiple = true,
        selection = draft.supportRoles,
        onSelectionChange = { draft.supportRoles = it },
    )
    DiscordSelector(
        kind = SelectorKind.Role,
        options = roles,
        placeholder = "None",
        label = "Viewer roles",
        multiple = true,
        selection = draft.viewerRoles,
        onSelectionChange = { draft.viewerRoles = it },
    )
    SliderRow(
        label = "Max active tickets per user",
        value = draft.maxActiveTickets,
        onValueChange = { draft.maxActiveTickets = it },
        valueRange = 1f..50f,
    )
    SliderRow(
        label = "Auto-close after inactivity",
        value = draft.autoCloseHours,
        onValueChange = { draft.autoCloseHours = it },
        valueRange = 0f..168f,
        valueLabel = if (draft.autoCloseHours <= 0f) "Off" else "${draft.autoCloseHours.toInt()}h",
    )
    SliderRow(
        label = "Required staff response time",
        value = draft.requiredResponseMinutes,
        onValueChange = { draft.requiredResponseMinutes = it },
        valueRange = 0f..1440f,
        valueLabel = if (draft.requiredResponseMinutes <= 0f) "Off" else "${draft.requiredResponseMinutes.toInt()}m",
    )
    if (priorities.isNotEmpty()) {
        val priorityOptions = priorities.map { SelectorOption(it.id, it.name) }
        DiscordSelector(
            kind = SelectorKind.Custom(Icons.Default.Flag),
            options = priorityOptions,
            placeholder = "All priorities",
            label = "Allowed priorities",
            multiple = true,
            selection = draft.allowedPriorities,
            onSelectionChange = { draft.allowedPriorities = it },
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Flag),
            options = priorityOptions,
            placeholder = "Guild default",
            selectedId = draft.defaultPriority,
            onSelect = { draft.defaultPriority = it },
            label = "Default priority",
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onEditOpenMessage, modifier = Modifier.weight(1f)) {
            Text(if (draft.openMessageJson.isNullOrBlank()) "Set open message" else "Edit open message")
        }
        OutlinedButton(onClick = onEditModal, modifier = Modifier.weight(1f)) {
            Text(if (draft.modalJson.isNullOrBlank()) "Set ticket form" else "Edit ticket form")
        }
    }
    if (showToggles) {
        SectionCardHeader("Close behavior", Icons.Default.Close)
        SwitchRow(title = "Save transcript", checked = draft.saveTranscript, onCheckedChange = { draft.saveTranscript = it })
        SwitchRow(title = "Lock channel on close", checked = draft.lockOnClose, onCheckedChange = { draft.lockOnClose = it })
        SwitchRow(title = "Rename channel on close", checked = draft.renameOnClose, onCheckedChange = { draft.renameOnClose = it })
        SwitchRow(
            title = "Remove creator on close",
            checked = draft.removeCreatorOnClose,
            onCheckedChange = { draft.removeCreatorOnClose = it },
        )
        SwitchRow(title = "Delete channel on close", checked = draft.deleteOnClose, onCheckedChange = { draft.deleteOnClose = it })
        if (draft.deleteOnClose) {
            SliderRow(
                label = "Delete delay",
                value = draft.deleteDelaySeconds,
                onValueChange = { draft.deleteDelaySeconds = it },
                valueRange = 0f..3600f,
                valueLabel = "${draft.deleteDelaySeconds.toInt()}s",
            )
        }
        SectionCardHeader("Archive behavior", Icons.Default.Archive)
        SwitchRow(
            title = "Auto-archive on close",
            checked = draft.autoArchiveOnClose,
            onCheckedChange = { draft.autoArchiveOnClose = it },
        )
        SwitchRow(title = "Lock channel on archive", checked = draft.lockOnArchive, onCheckedChange = { draft.lockOnArchive = it })
        SwitchRow(
            title = "Rename channel on archive",
            checked = draft.renameOnArchive,
            onCheckedChange = { draft.renameOnArchive = it },
        )
        SwitchRow(
            title = "Remove creator on archive",
            checked = draft.removeCreatorOnArchive,
            onCheckedChange = { draft.removeCreatorOnArchive = it },
        )
    }
}

@Composable
private fun AddButtonSheet(
    categories: List<SelectorOption>,
    roles: List<SelectorOption>,
    priorities: List<TicketPriority>,
    onDismiss: () -> Unit,
    onConfirm: (ComponentSubmission) -> Unit,
) {
    val draft = remember { ComponentDraft() }
    var showEmbed by remember { mutableStateOf(false) }
    var showModal by remember { mutableStateOf(false) }

    TicketForm(
        title = "Add button",
        confirmLabel = "Add",
        confirmEnabled = draft.label.isNotBlank(),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(draft.toSubmission()) },
        long = true,
    ) {
        ComponentFormFields(
            draft = draft,
            showStyle = true,
            showDescription = false,
            showToggles = false,
            categories = categories,
            roles = roles,
            priorities = priorities,
            onEditOpenMessage = { showEmbed = true },
            onEditModal = { showModal = true },
        )
    }

    if (showEmbed) {
        EmbedBuilderSheet(
            title = "Open message",
            confirmLabel = "Save",
            initialJson = draft.openMessageJson,
            onDismiss = { showEmbed = false },
            onConfirm = { json -> draft.openMessageJson = json; showEmbed = false },
        )
    }
    if (showModal) {
        ModalBuilderSheet(
            initialJson = draft.modalJson,
            onDismiss = { showModal = false },
            onConfirm = { json -> draft.modalJson = json; showModal = false },
        )
    }
}

@Composable
private fun EditButtonSheet(
    button: PanelButton,
    categories: List<SelectorOption>,
    roles: List<SelectorOption>,
    priorities: List<TicketPriority>,
    onDismiss: () -> Unit,
    onConfirm: (ComponentSubmission) -> Unit,
) {
    val draft = remember(button.id) { ComponentDraft.fromButton(button) }
    var showEmbed by remember { mutableStateOf(false) }
    var showModal by remember { mutableStateOf(false) }

    TicketForm(
        title = "Edit button",
        confirmLabel = "Save",
        confirmEnabled = draft.label.isNotBlank(),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(draft.toSubmission()) },
        long = true,
    ) {
        ComponentFormFields(
            draft = draft,
            showStyle = true,
            showDescription = false,
            showToggles = true,
            categories = categories,
            roles = roles,
            priorities = priorities,
            onEditOpenMessage = { showEmbed = true },
            onEditModal = { showModal = true },
        )
    }

    if (showEmbed) {
        EmbedBuilderSheet(
            title = "Open message",
            confirmLabel = "Save",
            initialJson = draft.openMessageJson,
            onDismiss = { showEmbed = false },
            onConfirm = { json -> draft.openMessageJson = json; showEmbed = false },
        )
    }
    if (showModal) {
        ModalBuilderSheet(
            initialJson = draft.modalJson,
            onDismiss = { showModal = false },
            onConfirm = { json -> draft.modalJson = json; showModal = false },
        )
    }
}

@Composable
private fun AddMenuOptionSheet(
    categories: List<SelectorOption>,
    roles: List<SelectorOption>,
    priorities: List<TicketPriority>,
    onDismiss: () -> Unit,
    onConfirm: (ComponentSubmission) -> Unit,
) {
    val draft = remember { ComponentDraft() }
    var showEmbed by remember { mutableStateOf(false) }
    var showModal by remember { mutableStateOf(false) }

    TicketForm(
        title = "Add option",
        confirmLabel = "Add",
        confirmEnabled = draft.label.isNotBlank(),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(draft.toSubmission()) },
        long = true,
    ) {
        ComponentFormFields(
            draft = draft,
            showStyle = false,
            showDescription = true,
            showToggles = false,
            categories = categories,
            roles = roles,
            priorities = priorities,
            onEditOpenMessage = { showEmbed = true },
            onEditModal = { showModal = true },
        )
    }

    if (showEmbed) {
        EmbedBuilderSheet(
            title = "Open message",
            confirmLabel = "Save",
            initialJson = draft.openMessageJson,
            onDismiss = { showEmbed = false },
            onConfirm = { json -> draft.openMessageJson = json; showEmbed = false },
        )
    }
    if (showModal) {
        ModalBuilderSheet(
            initialJson = draft.modalJson,
            onDismiss = { showModal = false },
            onConfirm = { json -> draft.modalJson = json; showModal = false },
        )
    }
}

@Composable
private fun EditMenuOptionSheet(
    option: SelectMenuOption,
    categories: List<SelectorOption>,
    roles: List<SelectorOption>,
    priorities: List<TicketPriority>,
    onDismiss: () -> Unit,
    onConfirm: (ComponentSubmission) -> Unit,
) {
    val draft = remember(option.id) { ComponentDraft.fromOption(option) }
    var showEmbed by remember { mutableStateOf(false) }
    var showModal by remember { mutableStateOf(false) }

    TicketForm(
        title = "Edit option",
        confirmLabel = "Save",
        confirmEnabled = draft.label.isNotBlank(),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(draft.toSubmission()) },
        long = true,
    ) {
        ComponentFormFields(
            draft = draft,
            showStyle = false,
            showDescription = true,
            showToggles = true,
            categories = categories,
            roles = roles,
            priorities = priorities,
            onEditOpenMessage = { showEmbed = true },
            onEditModal = { showModal = true },
        )
    }

    if (showEmbed) {
        EmbedBuilderSheet(
            title = "Open message",
            confirmLabel = "Save",
            initialJson = draft.openMessageJson,
            onDismiss = { showEmbed = false },
            onConfirm = { json -> draft.openMessageJson = json; showEmbed = false },
        )
    }
    if (showModal) {
        ModalBuilderSheet(
            initialJson = draft.modalJson,
            onDismiss = { showModal = false },
            onConfirm = { json -> draft.modalJson = json; showModal = false },
        )
    }
}
