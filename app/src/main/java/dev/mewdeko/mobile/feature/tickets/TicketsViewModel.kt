package dev.mewdeko.mobile.feature.tickets

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.GuildRole
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.InstantSerializer
import dev.mewdeko.mobile.core.net.asSnowflakeNumber
import dev.mewdeko.mobile.core.net.jsonBody
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import javax.inject.Inject

/** One support ticket, as the ticket list renders it. */
@Serializable
data class TicketSummary(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "0",
    val channelName: String = "",
    @Serializable(with = SnowflakeSerializer::class) val creatorId: Snowflake? = null,
    val creatorName: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val claimedBy: Snowflake? = null,
    val claimedByName: String? = null,
    val priority: String? = null,
    val tags: List<String> = emptyList(),
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val closedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val lastActivityAt: Instant? = null,
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false,
    val buttonLabel: String? = null,
    val optionLabel: String? = null,
    val caseId: Int? = null,
    val transcriptUrl: String? = null,
) {
    /** Whether the ticket is still live: not closed, archived, or deleted. */
    val isOpen: Boolean get() = closedAt == null && !isArchived && !isDeleted

    /** Which panel control opened the ticket, when the bot recorded one. */
    val source: String? get() = buttonLabel ?: optionLabel
}

/** A posted panel members open tickets from. */
@Serializable
data class TicketPanel(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val messageId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "0",
    val channelName: String? = null,
    val buttonCount: Int = 0,
    val selectMenuCount: Int = 0,
    val embedJson: String? = null,
)

/** A named urgency level a ticket can be set to. */
@Serializable
data class TicketPriority(
    val id: String = "",
    val name: String = "",
    val emoji: String = "",
    val level: Int = 1,
    val pingStaff: Boolean = false,
)

/** A label staff can attach to tickets. */
@Serializable
data class TicketTag(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
)

/** A user barred from opening tickets. */
@Serializable
data class BlacklistedUser(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake? = null,
    val username: String? = null,
    val restrictedTypes: List<String> = emptyList(),
)

/** The guild-wide ticket channel settings. */
@Serializable
data class TicketSettingsResponse(
    @Serializable(with = SnowflakeSerializer::class) val transcriptChannelId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val logChannelId: Snowflake? = null,
)

/**
 * The full set of ticket-opening settings a panel button or select menu
 * option carries. The list endpoints dump the raw entity, and the single-item
 * GET endpoints add a couple of resolved display names, so one shape covers
 * both: unused fields simply keep their default.
 */
@Serializable
data class PanelButton(
    val id: Int = 0,
    val label: String = "",
    val style: Int = 1,
    val emoji: String? = null,
    val customId: String? = null,
    val channelNameFormat: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val categoryId: Snowflake? = null,
    val categoryName: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val archiveCategoryId: Snowflake? = null,
    val archiveCategoryName: String? = null,
    val supportRoles: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
    val viewerRoles: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
    val autoCloseTime: String? = null,
    val requiredResponseTime: String? = null,
    val maxActiveTickets: Int = 1,
    val allowedPriorities: List<String> = emptyList(),
    val defaultPriority: String? = null,
    val saveTranscript: Boolean = true,
    val deleteOnClose: Boolean = false,
    val lockOnClose: Boolean = true,
    val renameOnClose: Boolean = true,
    val removeCreatorOnClose: Boolean = false,
    val deleteDelay: String? = null,
    val lockOnArchive: Boolean = true,
    val renameOnArchive: Boolean = true,
    val removeCreatorOnArchive: Boolean = false,
    val autoArchiveOnClose: Boolean = false,
    val openMessageJson: String? = null,
    val modalJson: String? = null,
) {
    /** The Discord button style's display name. */
    val styleLabel: String
        get() = when (style) {
            1 -> "Primary"
            2 -> "Secondary"
            3 -> "Success"
            4 -> "Danger"
            5 -> "Link"
            else -> "Style #$style"
        }
}

/** One choice on a select menu, carrying the same ticket-opening settings as [PanelButton]. */
@Serializable
data class SelectMenuOption(
    val id: Int = 0,
    val label: String = "",
    val description: String? = null,
    val emoji: String? = null,
    val value: String? = null,
    val channelNameFormat: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val categoryId: Snowflake? = null,
    val categoryName: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val archiveCategoryId: Snowflake? = null,
    val archiveCategoryName: String? = null,
    val supportRoles: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
    val viewerRoles: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
    val autoCloseTime: String? = null,
    val requiredResponseTime: String? = null,
    val maxActiveTickets: Int = 1,
    val allowedPriorities: List<String> = emptyList(),
    val defaultPriority: String? = null,
    val saveTranscript: Boolean = true,
    val deleteOnClose: Boolean = false,
    val lockOnClose: Boolean = true,
    val renameOnClose: Boolean = true,
    val removeCreatorOnClose: Boolean = false,
    val deleteDelay: String? = null,
    val lockOnArchive: Boolean = true,
    val renameOnArchive: Boolean = true,
    val removeCreatorOnArchive: Boolean = false,
    val autoArchiveOnClose: Boolean = false,
    val openMessageJson: String? = null,
    val modalJson: String? = null,
)

/** One select menu on a panel. */
@Serializable
data class PanelSelectMenu(
    val id: Int = 0,
    val placeholder: String? = null,
    val optionCount: Int = 0,
    val options: List<SelectMenuOption> = emptyList(),
) {
    /** How many options the menu offers, however the bot reported it. */
    val optionTotal: Int get() = if (optionCount > 0) optionCount else options.size
}

/** A case grouping several related tickets. */
@Serializable
data class TicketCase(
    val id: Int = 0,
    val title: String = "",
    val description: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val createdBy: Snowflake? = null,
    val createdByName: String? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val closedAt: Instant? = null,
    val linkedTickets: Int = 0,
) {
    /** A case with a close timestamp is closed; the bot sends no separate flag. */
    val isClosed: Boolean get() = closedAt != null
}

/** One ticket linked to a case, as the case detail endpoint renders it. */
@Serializable
data class CaseLinkedTicket(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "0",
    val channelName: String = "",
    @Serializable(with = SnowflakeSerializer::class) val creatorId: Snowflake? = null,
    val creatorName: String? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val closedAt: Instant? = null,
    val isArchived: Boolean = false,
)

/** A staff-only note left on a case. */
@Serializable
data class CaseNote(
    val id: Int = 0,
    val content: String = "",
    @Serializable(with = SnowflakeSerializer::class) val authorId: Snowflake? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
)

/** The full detail view of one case. */
@Serializable
data class CaseDetailResponse(
    val id: Int = 0,
    val title: String = "",
    val description: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val createdBy: Snowflake? = null,
    val createdByName: String? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val closedAt: Instant? = null,
    val linkedTickets: List<CaseLinkedTicket> = emptyList(),
    val notes: List<CaseNote> = emptyList(),
) {
    /** A case with a close timestamp is closed; the bot sends no separate flag. */
    val isClosed: Boolean get() = closedAt != null
}

/** Guild-wide ticket counters and timing averages, from the overview endpoint. */
@Serializable
data class OverviewStatistics(
    val totalTickets: Int = 0,
    val openTickets: Int = 0,
    val closedTickets: Int = 0,
    val averageResponseTime: Double = 0.0,
    val averageResolutionTime: Double = 0.0,
    val ticketsByPriority: Map<String, Int> = emptyMap(),
)

/** One staff member's average first-response time. */
@Serializable
data class StaffResponseStat(
    @Serializable(with = SnowflakeSerializer::class) val staffId: Snowflake? = null,
    val staffName: String = "Unknown User",
    val averageResponseTimeMinutes: Double = 0.0,
)

/** The dashboard-optimised overview payload. */
@Serializable
data class TicketOverview(
    val statistics: OverviewStatistics? = null,
    val staffResponseStats: List<StaffResponseStat> = emptyList(),
)

/** Which slice of the ticket list is showing. */
enum class TicketFilter(val label: String) {
    OPEN("Open"),
    CLAIMED("Claimed"),
    CLOSED("Closed"),
    ARCHIVED("Archived"),
    ALL("All"),
}

/** Which part of the tickets screen is showing. */
enum class TicketSection(val id: String, val label: String) {
    OVERVIEW("overview", "Overview"),
    TICKETS("tickets", "Tickets"),
    PANELS("panels", "Panels"),
    CONFIGURATION("configuration", "Config"),
    CASES("cases", "Cases"),
    ADVANCED("advanced", "Advanced"),
}

/** The buttons and menus attached to one open panel. */
data class PanelDetail(
    val panel: TicketPanel,
    val buttons: List<PanelButton> = emptyList(),
    val menus: List<PanelSelectMenu> = emptyList(),
    val loading: Boolean = true,
)

/** One select menu, opened to manage its options. */
data class MenuDetail(
    val panel: TicketPanel,
    val menu: PanelSelectMenu,
)

/** One case, opened to its full detail view. */
data class CaseDetailState(
    val detail: CaseDetailResponse,
    val loading: Boolean = true,
)

/** Tickets screen state. */
data class TicketsState(
    val tickets: List<TicketSummary> = emptyList(),
    val panels: List<TicketPanel> = emptyList(),
    val cases: List<TicketCase> = emptyList(),
    val priorities: List<TicketPriority> = emptyList(),
    val tags: List<TicketTag> = emptyList(),
    val blacklist: List<BlacklistedUser> = emptyList(),
    val availableChannels: List<TextChannelLite> = emptyList(),
    val availableCategories: List<TextChannelLite> = emptyList(),
    val availableRoles: List<GuildRole> = emptyList(),
    val transcriptChannelId: Snowflake? = null,
    val logChannelId: Snowflake? = null,
    val overview: TicketOverview? = null,
    val section: TicketSection = TicketSection.OVERVIEW,
    val filter: TicketFilter = TicketFilter.OPEN,
    val searchQuery: String = "",
    val openPanel: PanelDetail? = null,
    val openMenu: MenuDetail? = null,
    val editingButton: PanelButton? = null,
    val editingButtonLoading: Boolean = false,
    val editingOption: SelectMenuOption? = null,
    val editingOptionLoading: Boolean = false,
    val openCase: CaseDetailState? = null,
) {
    /** The tickets matching the active filter and search query. */
    val visible: List<TicketSummary>
        get() {
            val base = when (filter) {
                TicketFilter.OPEN -> tickets.filter { it.isOpen }
                TicketFilter.CLAIMED -> tickets.filter { it.claimedBy != null && it.isOpen }
                TicketFilter.CLOSED -> tickets.filter { it.closedAt != null && !it.isArchived }
                TicketFilter.ARCHIVED -> tickets.filter { it.isArchived }
                TicketFilter.ALL -> tickets
            }
            val query = searchQuery.trim()
            if (query.isEmpty()) return base
            return base.filter { ticket ->
                ticket.id.toString().contains(query, ignoreCase = true) ||
                    ticket.channelName.contains(query, ignoreCase = true) ||
                    ticket.creatorName?.contains(query, ignoreCase = true) == true ||
                    ticket.claimedByName?.contains(query, ignoreCase = true) == true ||
                    ticket.tags.any { it.contains(query, ignoreCase = true) }
            }
        }

    /** How many tickets fall into each filter, for the filter chips. */
    fun countFor(filter: TicketFilter): Int = when (filter) {
        TicketFilter.OPEN -> tickets.count { it.isOpen }
        TicketFilter.CLAIMED -> tickets.count { it.claimedBy != null && it.isOpen }
        TicketFilter.CLOSED -> tickets.count { it.closedAt != null && !it.isArchived }
        TicketFilter.ARCHIVED -> tickets.count { it.isArchived }
        TicketFilter.ALL -> tickets.size
    }

    /** Tickets not yet attached to any case, for the case-linking pickers. */
    val unlinkedTickets: List<TicketSummary> get() = tickets.filter { it.caseId == null }
}

/** The support ticket system: tickets, panels, priorities, tags, and cases. */
@HiltViewModel
class TicketsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(TicketsState())

    /** Observable screen state. */
    val state: StateFlow<TicketsState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads every ticket collection along with the channel and role pickers. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val tickets = async {
                list(
                    "api/Ticket/$guildId/tickets?includeArchived=true&includeClosed=true",
                    TicketSummary.serializer(),
                )
            }
            val panels = async { list("api/Ticket/$guildId/panels", TicketPanel.serializer()) }
            val cases = async { list("api/Ticket/$guildId/cases", TicketCase.serializer()) }
            val priorities = async {
                list("api/Ticket/$guildId/priorities", TicketPriority.serializer())
            }
            val tags = async { list("api/Ticket/$guildId/tags", TicketTag.serializer()) }
            val blacklist = async {
                list("api/Ticket/$guildId/blacklist", BlacklistedUser.serializer())
            }
            val channels = async {
                list(
                    "api/ClientOperations/textchannels/$guildId",
                    TextChannelLite.serializer(),
                )
            }
            val categories = async {
                list("api/ClientOperations/channels/$guildId/4", TextChannelLite.serializer())
            }
            val roles = async {
                list("api/ClientOperations/roles/$guildId", GuildRole.serializer())
            }
            val settings = async {
                runCatching {
                    api.send(
                        Endpoint("api/Ticket/$guildId/settings"),
                        TicketSettingsResponse.serializer(),
                    )
                }.getOrNull()
            }
            val overview = async {
                runCatching {
                    api.send(
                        Endpoint("api/Ticket/$guildId/overview?activityDays=30"),
                        TicketOverview.serializer(),
                    )
                }.getOrNull()
            }

            val loaded = settings.await()
            _state.update {
                it.copy(
                    tickets = tickets.await().sortedByDescending { ticket ->
                        ticket.lastActivityAt ?: Instant.EPOCH
                    },
                    panels = panels.await(),
                    cases = cases.await(),
                    priorities = priorities.await().sortedByDescending { p -> p.level },
                    tags = tags.await().sortedBy { tag -> tag.name.lowercase() },
                    blacklist = blacklist.await(),
                    availableChannels = channels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                    availableCategories = categories.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                    availableRoles = roles.await()
                        .filter { role -> role.id != guildId }
                        .sortedBy { role -> role.name.lowercase() },
                    transcriptChannelId = loaded?.transcriptChannelId,
                    logChannelId = loaded?.logChannelId,
                    overview = overview.await(),
                )
            }
        }
    }

    /** Switches the visible section. */
    fun setSection(section: TicketSection) = _state.update { it.copy(section = section) }

    /** Narrows the ticket list. */
    fun setFilter(filter: TicketFilter) = _state.update { it.copy(filter = filter) }

    /** Filters the ticket list by id, creator, channel, claimer, or tag. */
    fun setSearch(query: String) = _state.update { it.copy(searchQuery = query) }

    /** Assigns a ticket to the signed-in staff member. */
    fun claim(ticket: TicketSummary) = ticketAction(ticket, "claim", "Claimed.", "Failed to claim.")

    /** Releases a ticket the signed-in staff member holds. */
    fun unclaim(ticket: TicketSummary) =
        ticketAction(ticket, "unclaim", "Unclaimed.", "Failed to unclaim.")

    /** Closes a ticket, optionally recording why. */
    fun close(ticket: TicketSummary, reason: String?) = launchAction("Failed to close.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Ticket/$guildId/tickets/by-channel/${ticket.channelId}/close",
                HttpMethod.POST,
                jsonBody(
                    "reason" to reason?.takeIf { it.isNotBlank() },
                    "staffId" to userId.asSnowflakeNumber(),
                ),
            )
        )
        load()
        postSuccess("Closed.")
    }

    /** Moves a closed ticket into the archive category. */
    fun archive(ticket: TicketSummary) = launchAction("Failed to archive.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Ticket/$guildId/tickets/${ticket.id}/archive",
                HttpMethod.POST,
                jsonBody("staffId" to userId.asSnowflakeNumber()),
            )
        )
        load()
        postSuccess("Archived.")
    }

    /** Sets a ticket's urgency. */
    fun setPriority(ticket: TicketSummary, priorityId: String) =
        launchAction("Failed to set priority.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Ticket/$guildId/tickets/by-channel/${ticket.channelId}/priority",
                    HttpMethod.POST,
                    jsonBody(
                        "priorityId" to priorityId,
                        "staffId" to userId.asSnowflakeNumber(),
                    ),
                )
            )
            load()
            postSuccess("Priority set.")
        }

    /**
     * Reconciles a ticket's tags against a new selection: tags dropped from
     * the selection are removed, tags newly picked are added.
     */
    fun updateTags(ticket: TicketSummary, originalIds: List<String>, newIds: List<String>) =
        launchAction("Failed to update tags.") {
            val added = newIds - originalIds.toSet()
            val removed = originalIds - newIds.toSet()
            if (added.isNotEmpty()) {
                api.sendIgnoringBody(
                    Endpoint(
                        "api/Ticket/$guildId/tickets/by-channel/${ticket.channelId}/tags",
                        HttpMethod.POST,
                        jsonBody(
                            "tagIds" to JsonArray(added.map { JsonPrimitive(it) }),
                            "staffId" to userId.asSnowflakeNumber(),
                        ),
                    )
                )
            }
            if (removed.isNotEmpty()) {
                api.sendIgnoringBody(
                    Endpoint(
                        "api/Ticket/$guildId/tickets/by-channel/${ticket.channelId}/tags",
                        HttpMethod.DELETE,
                        jsonBody(
                            "tagIds" to JsonArray(removed.map { JsonPrimitive(it) }),
                            "staffId" to userId.asSnowflakeNumber(),
                        ),
                    )
                )
            }
            load()
            postSuccess("Tags updated.")
        }

    /** Writes a staff-only note into a ticket. */
    fun addNote(ticket: TicketSummary, content: String) = launchAction("Failed to add note.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Ticket/$guildId/tickets/by-channel/${ticket.channelId}/notes",
                HttpMethod.POST,
                jsonBody(
                    "content" to content.trim(),
                    "authorId" to userId.asSnowflakeNumber(),
                ),
            )
        )
        postSuccess("Note added.")
    }

    /** Posts a new ticket panel into a channel from a ready-made embed payload. */
    fun createPanel(channelId: Snowflake, embedJson: String) =
        launchAction("Failed to create panel.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Ticket/$guildId/panels",
                    HttpMethod.POST,
                    jsonBody(
                        "channelId" to channelId.asSnowflakeNumber(),
                        "embedJson" to embedJson,
                    ),
                )
            )
            load()
            postSuccess("Panel created.")
        }

    /** Deletes a panel and its message. */
    fun deletePanel(panel: TicketPanel) = launchAction("Failed to delete panel.") {
        api.sendIgnoringBody(
            Endpoint("api/Ticket/$guildId/panels/${panel.id}", HttpMethod.DELETE)
        )
        _state.update {
            it.copy(
                panels = it.panels.filterNot { entry -> entry.id == panel.id },
                openPanel = it.openPanel?.takeIf { open -> open.panel.id != panel.id },
            )
        }
        postSuccess("Panel deleted.")
    }

    /** Replaces a panel's embed. */
    fun updatePanelEmbed(panel: TicketPanel, embedJson: String) =
        launchAction("Failed to update embed.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Ticket/$guildId/panels/${panel.id}/embed",
                    HttpMethod.PUT,
                    jsonBody("embedJson" to embedJson),
                )
            )
            load()
            postSuccess("Embed updated.")
        }

    /** Checks whether a panel's message and channel still exist. */
    fun checkPanelStatus(panel: TicketPanel) = launchAction("Failed to check panel status.") {
        val result = api.send(
            Endpoint("api/Ticket/$guildId/panels/${panel.id}/status"),
            PanelStatusResponse.serializer(),
        )
        when (result.status) {
            0 -> postSuccess("Panel #${panel.id} is healthy.")
            1 -> postError("Panel #${panel.id}'s message was deleted. Use Repost to recreate it.")
            2 -> postError("Panel #${panel.id}'s channel was deleted.")
            else -> postError("Panel #${panel.id} status is unknown.")
        }
    }

    /** Reposts one panel's message. */
    fun recreatePanel(panel: TicketPanel) = launchAction("Failed to repost panel.") {
        api.sendIgnoringBody(
            Endpoint("api/Ticket/$guildId/panels/${panel.id}/recreate", HttpMethod.POST)
        )
        load()
        postSuccess("Panel reposted.")
    }

    /** Reposts every panel's message. */
    fun recreateAllPanels() = launchAction("Failed to repost panels.") {
        api.sendIgnoringBody(
            Endpoint("api/Ticket/$guildId/panels/recreate-all", HttpMethod.POST)
        )
        postSuccess("All panels reposted.")
    }

    /** Opens a panel's button and menu list. */
    fun openPanel(panel: TicketPanel) {
        _state.update { it.copy(openPanel = PanelDetail(panel = panel)) }
        loadPanelDetail(panel)
    }

    /** Returns from a panel's detail view. */
    fun closePanel() = _state.update { it.copy(openPanel = null, openMenu = null) }

    /** Reloads the open panel's buttons and menus. */
    fun loadPanelDetail(panel: TicketPanel) = launchAction("Failed to load panel.") {
        coroutineScope {
            val buttons = async {
                list("api/Ticket/$guildId/panels/${panel.id}/buttons", PanelButton.serializer())
            }
            val menus = async {
                list(
                    "api/Ticket/$guildId/panels/${panel.id}/selectmenus",
                    PanelSelectMenu.serializer(),
                )
            }
            val loadedMenus = menus.await()
            val loaded = PanelDetail(panel, buttons.await(), loadedMenus, loading = false)
            _state.update {
                val refreshedMenu = it.openMenu
                    ?.takeIf { open -> open.panel.id == panel.id }
                    ?.let { open -> loadedMenus.firstOrNull { m -> m.id == open.menu.id } }
                    ?.let { menu -> it.openMenu?.copy(menu = menu) }
                if (it.openPanel?.panel?.id == panel.id) {
                    it.copy(openPanel = loaded, openMenu = refreshedMenu ?: it.openMenu)
                } else {
                    it
                }
            }
        }
    }

    /** Adds a ticket-opening button to a panel. */
    fun addPanelButton(panel: TicketPanel, form: ComponentSubmission) =
        launchAction("Failed to add button.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Ticket/$guildId/panels/${panel.id}/buttons",
                    HttpMethod.POST,
                    jsonBody(*form.toButtonFields()),
                )
            )
            loadPanelDetail(panel)
            postSuccess("Button added.")
        }

    /** Loads one button's full configuration for editing. */
    fun openButtonEditor(button: PanelButton) = launchAction("Failed to load button.") {
        _state.update { it.copy(editingButton = button, editingButtonLoading = true) }
        val detail = api.send(
            Endpoint("api/Ticket/$guildId/buttons/${button.id}"),
            PanelButton.serializer(),
        )
        _state.update { it.copy(editingButton = detail, editingButtonLoading = false) }
    }

    /** Closes the button editor without saving. */
    fun closeButtonEditor() = _state.update { it.copy(editingButton = null) }

    /** Saves changes to an existing button. */
    fun updateButton(button: PanelButton, form: ComponentSubmission) =
        launchAction("Failed to update button.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Ticket/$guildId/buttons/${button.id}",
                    HttpMethod.PUT,
                    jsonBody(*form.toUpdateFields()),
                )
            )
            closeButtonEditor()
            _state.value.openPanel?.let { loadPanelDetail(it.panel) }
            postSuccess("Button updated.")
        }

    /** Removes a button from a panel. */
    fun deletePanelButton(button: PanelButton) = launchAction("Failed to delete button.") {
        api.sendIgnoringBody(
            Endpoint("api/Ticket/$guildId/buttons/${button.id}", HttpMethod.DELETE)
        )
        _state.value.openPanel?.let { loadPanelDetail(it.panel) }
        postSuccess("Button deleted.")
    }

    /** Adds a select menu to a panel with its first option. */
    fun createSelectMenu(
        panel: TicketPanel,
        placeholder: String,
        firstOptionLabel: String,
        firstOptionDescription: String?,
        firstOptionEmoji: String?,
    ) = launchAction("Failed to add select menu.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Ticket/$guildId/panels/${panel.id}/selectmenus",
                HttpMethod.POST,
                jsonBody(
                    "placeholder" to placeholder,
                    "firstOptionLabel" to firstOptionLabel,
                    "firstOptionDescription" to firstOptionDescription?.takeIf { it.isNotBlank() },
                    "firstOptionEmoji" to firstOptionEmoji?.takeIf { it.isNotBlank() },
                ),
            )
        )
        loadPanelDetail(panel)
        postSuccess("Select menu added.")
    }

    /** Updates a select menu's placeholder text. */
    fun updateMenuPlaceholder(panel: TicketPanel, menu: PanelSelectMenu, placeholder: String) =
        launchAction("Failed to update menu.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Ticket/$guildId/selectmenus/${menu.id}/placeholder",
                    HttpMethod.PUT,
                    jsonBody("placeholder" to placeholder),
                )
            )
            loadPanelDetail(panel)
            postSuccess("Placeholder updated.")
        }

    /** Deletes a select menu from a panel. */
    fun deleteMenu(panel: TicketPanel, menu: PanelSelectMenu) = launchAction("Failed to delete menu.") {
        api.sendIgnoringBody(
            Endpoint("api/Ticket/$guildId/selectmenus/${menu.id}", HttpMethod.DELETE)
        )
        _state.update { it.copy(openMenu = it.openMenu?.takeIf { open -> open.menu.id != menu.id }) }
        loadPanelDetail(panel)
        postSuccess("Menu deleted.")
    }

    /** Opens a select menu's option list. */
    fun openMenu(panel: TicketPanel, menu: PanelSelectMenu) =
        _state.update { it.copy(openMenu = MenuDetail(panel, menu)) }

    /** Returns from a select menu's option list. */
    fun closeMenu() = _state.update { it.copy(openMenu = null) }

    /** Adds an option to a select menu. */
    fun addMenuOption(menu: PanelSelectMenu, form: ComponentSubmission) =
        launchAction("Failed to add option.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Ticket/$guildId/selectmenus/${menu.id}/options",
                    HttpMethod.POST,
                    jsonBody(*form.toButtonFields()),
                )
            )
            _state.value.openPanel?.let { loadPanelDetail(it.panel) }
            postSuccess("Option added.")
        }

    /** Loads one select option's full configuration for editing. */
    fun openOptionEditor(option: SelectMenuOption) = launchAction("Failed to load option.") {
        _state.update { it.copy(editingOption = option, editingOptionLoading = true) }
        val detail = api.send(
            Endpoint("api/Ticket/$guildId/selectmenus/options/${option.id}"),
            SelectMenuOption.serializer(),
        )
        _state.update { it.copy(editingOption = detail, editingOptionLoading = false) }
    }

    /** Closes the select option editor without saving. */
    fun closeOptionEditor() = _state.update { it.copy(editingOption = null) }

    /** Saves changes to an existing select option. */
    fun updateMenuOption(option: SelectMenuOption, form: ComponentSubmission) =
        launchAction("Failed to update option.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Ticket/$guildId/selectmenus/options/${option.id}",
                    HttpMethod.PUT,
                    jsonBody(*form.toUpdateFields()),
                )
            )
            closeOptionEditor()
            _state.value.openPanel?.let { loadPanelDetail(it.panel) }
            postSuccess("Option updated.")
        }

    /** Removes an option from a select menu. */
    fun deleteMenuOption(optionId: Int) = launchAction("Failed to delete option.") {
        api.sendIgnoringBody(
            Endpoint("api/Ticket/$guildId/selectmenus/options/$optionId", HttpMethod.DELETE)
        )
        _state.value.openPanel?.let { loadPanelDetail(it.panel) }
        postSuccess("Option deleted.")
    }

    /** Defines a new urgency level. */
    fun createPriority(
        id: String,
        name: String,
        emoji: String,
        level: Int,
        pingStaff: Boolean,
        responseMinutes: Int,
    ) = launchAction("Failed to create priority.") {
        val response = "%02d:%02d:00".format(responseMinutes / 60, responseMinutes % 60)
        api.sendIgnoringBody(
            Endpoint(
                "api/Ticket/$guildId/priorities",
                HttpMethod.POST,
                jsonBody(
                    "id" to id.trim(),
                    "name" to name.trim(),
                    "emoji" to emoji,
                    "level" to level,
                    "pingStaff" to pingStaff,
                    "responseTime" to response,
                ),
            )
        )
        load()
        postSuccess("Priority created.")
    }

    /** Removes an urgency level. */
    fun deletePriority(priority: TicketPriority) = launchAction("Failed to delete priority.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Ticket/$guildId/priorities/${Uri.encode(priority.id)}",
                HttpMethod.DELETE,
            )
        )
        _state.update { it.copy(priorities = it.priorities.filterNot { p -> p.id == priority.id }) }
        postSuccess("Priority deleted.")
    }

    /** Defines a new ticket tag. */
    fun createTag(id: String, name: String, description: String) =
        launchAction("Failed to create tag.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Ticket/$guildId/tags",
                    HttpMethod.POST,
                    jsonBody(
                        "id" to id.trim(),
                        "name" to name.trim(),
                        "description" to description.trim(),
                    ),
                )
            )
            load()
            postSuccess("Tag created.")
        }

    /** Removes a ticket tag. */
    fun deleteTag(tag: TicketTag) = launchAction("Failed to delete tag.") {
        api.sendIgnoringBody(
            Endpoint("api/Ticket/$guildId/tags/${Uri.encode(tag.id)}", HttpMethod.DELETE)
        )
        _state.update { it.copy(tags = it.tags.filterNot { t -> t.id == tag.id }) }
        postSuccess("Tag deleted.")
    }

    /** Points ticket transcripts at a channel, or clears it with null. */
    fun setTranscriptChannel(channelId: Snowflake?) = launchAction("Failed to save channel.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Ticket/$guildId/settings/transcript-channel",
                HttpMethod.PUT,
                jsonBody("channelId" to (channelId?.asSnowflakeNumber() ?: 0L)),
            )
        )
        _state.update { it.copy(transcriptChannelId = channelId) }
        postSuccess("Transcript channel saved.")
    }

    /** Points ticket logs at a channel, or clears it with null. */
    fun setLogChannel(channelId: Snowflake?) = launchAction("Failed to save channel.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Ticket/$guildId/settings/log-channel",
                HttpMethod.PUT,
                jsonBody("channelId" to (channelId?.asSnowflakeNumber() ?: 0L)),
            )
        )
        _state.update { it.copy(logChannelId = channelId) }
        postSuccess("Log channel saved.")
    }

    /** Bars a user from opening tickets. */
    fun blacklist(targetId: Snowflake, reason: String?) = launchAction("Failed to blacklist.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Ticket/$guildId/blacklist/$targetId",
                HttpMethod.POST,
                jsonBody("reason" to reason.orEmpty()),
            )
        )
        load()
        postSuccess("Blacklisted.")
    }

    /** Lets a blacklisted user open tickets again. */
    fun unblacklist(user: BlacklistedUser) = launchAction("Failed to unblacklist.") {
        val target = user.userId ?: return@launchAction
        api.sendIgnoringBody(
            Endpoint("api/Ticket/$guildId/blacklist/$target", HttpMethod.DELETE)
        )
        _state.update {
            it.copy(blacklist = it.blacklist.filterNot { entry -> entry.userId == target })
        }
        postSuccess("Removed from blacklist.")
    }

    /** Closes every ticket that has been inactive for at least the given hours. */
    fun batchCloseInactive(hours: Int) = launchAction("Failed to close inactive tickets.") {
        val result = api.send(
            Endpoint("api/Ticket/$guildId/batch/close-inactive?hours=$hours", HttpMethod.POST),
            BatchCloseResult.serializer(),
        )
        load()
        postSuccess("Closed ${result.closed} inactive ticket(s).")
    }

    /** Opens a new case that tickets can be linked to. */
    fun createCase(title: String, description: String, linkTicketIds: List<Int>) =
        launchAction("Failed to create case.") {
            val result = api.send(
                Endpoint(
                    "api/Ticket/$guildId/cases",
                    HttpMethod.POST,
                    jsonBody(
                        "title" to title.trim(),
                        "description" to description.trim(),
                        "creatorId" to userId.asSnowflakeNumber(),
                    ),
                ),
                CreateCaseResult.serializer(),
            )
            if (linkTicketIds.isNotEmpty() && result.id != 0) {
                api.sendIgnoringBody(
                    Endpoint(
                        "api/Ticket/$guildId/cases/${result.id}/link-tickets",
                        HttpMethod.POST,
                        jsonBody("ticketIds" to JsonArray(linkTicketIds.map { JsonPrimitive(it) })),
                    )
                )
            }
            load()
            postSuccess("Case created.")
        }

    /** Opens a case's full detail view. */
    fun openCase(case: TicketCase) {
        _state.update {
            it.copy(
                openCase = CaseDetailState(
                    CaseDetailResponse(
                        id = case.id,
                        title = case.title,
                        description = case.description,
                        createdBy = case.createdBy,
                        createdByName = case.createdByName,
                        createdAt = case.createdAt,
                        closedAt = case.closedAt,
                    ),
                    loading = true,
                )
            )
        }
        loadCaseDetail(case.id)
    }

    /** Returns from a case's detail view. */
    fun closeCaseDetail() = _state.update { it.copy(openCase = null) }

    /** Reloads the open case's linked tickets and notes. */
    fun loadCaseDetail(caseId: Int) = launchAction("Failed to load case.") {
        val detail = api.send(
            Endpoint("api/Ticket/$guildId/cases/$caseId"),
            CaseDetailResponse.serializer(),
        )
        _state.update {
            if (it.openCase?.detail?.id == caseId) it.copy(openCase = CaseDetailState(detail, false)) else it
        }
    }

    /** Marks a case closed. */
    fun closeTicketCase(caseId: Int) = launchAction("Failed to close case.") {
        api.sendIgnoringBody(
            Endpoint("api/Ticket/$guildId/cases/$caseId/close", HttpMethod.POST)
        )
        loadCaseDetail(caseId)
        load()
        postSuccess("Case closed.")
    }

    /** Reopens a closed case. */
    fun reopenTicketCase(caseId: Int) = launchAction("Failed to reopen case.") {
        api.sendIgnoringBody(
            Endpoint("api/Ticket/$guildId/cases/$caseId/reopen", HttpMethod.POST)
        )
        loadCaseDetail(caseId)
        load()
        postSuccess("Case reopened.")
    }

    /** Links more tickets into an existing case. */
    fun linkTickets(caseId: Int, ticketIds: List<Int>) = launchAction("Failed to link tickets.") {
        if (ticketIds.isEmpty()) return@launchAction
        api.sendIgnoringBody(
            Endpoint(
                "api/Ticket/$guildId/cases/$caseId/link-tickets",
                HttpMethod.POST,
                jsonBody("ticketIds" to JsonArray(ticketIds.map { JsonPrimitive(it) })),
            )
        )
        loadCaseDetail(caseId)
        load()
        postSuccess("Tickets linked.")
    }

    /** Detaches one ticket from its case. */
    fun unlinkTicket(caseId: Int, ticketId: Int) = launchAction("Failed to unlink ticket.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Ticket/$guildId/unlink-tickets",
                HttpMethod.POST,
                jsonBody("ticketIds" to JsonArray(listOf(JsonPrimitive(ticketId)))),
            )
        )
        loadCaseDetail(caseId)
        load()
    }

    private fun ticketAction(
        ticket: TicketSummary,
        tail: String,
        success: String,
        failure: String,
    ) = launchAction(failure) {
        api.sendIgnoringBody(
            Endpoint(
                "api/Ticket/$guildId/tickets/by-channel/${ticket.channelId}/$tail",
                HttpMethod.POST,
                jsonBody("staffId" to userId.asSnowflakeNumber()),
            )
        )
        load()
        postSuccess(success)
    }

    private suspend fun <T> list(
        path: String,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): List<T> = runCatching {
        api.send(Endpoint(path), ListSerializer(serializer))
    }.getOrDefault(emptyList())
}

/** `{ panelId, status }`; status 0 is healthy, 1 is a deleted message, 2 a deleted channel. */
@Serializable
private data class PanelStatusResponse(val status: Int = 0)

/** `{ closed, failed, inactiveHours }` from the batch-close endpoint. */
@Serializable
private data class BatchCloseResult(val closed: Int = 0, val failed: Int = 0)

/** `{ id, title, description, createdBy, createdAt }` from case creation. */
@Serializable
private data class CreateCaseResult(val id: Int = 0)

/**
 * Everything a panel button or select menu option's create/update form can
 * carry, shared because both use the same request shape on the bot.
 */
data class ComponentSubmission(
    val label: String,
    val description: String? = null,
    val emoji: String?,
    val style: Int? = null,
    val channelFormat: String?,
    val categoryId: Snowflake?,
    val archiveCategoryId: Snowflake?,
    val supportRoles: List<Snowflake>?,
    val viewerRoles: List<Snowflake>?,
    val maxActiveTickets: Int?,
    val autoCloseHours: Int?,
    val requiredResponseMinutes: Int?,
    val allowedPriorities: List<String>?,
    val defaultPriority: String?,
    val openMessageJson: String?,
    val modalJson: String?,
    val saveTranscript: Boolean? = null,
    val deleteOnClose: Boolean? = null,
    val lockOnClose: Boolean? = null,
    val renameOnClose: Boolean? = null,
    val removeCreatorOnClose: Boolean? = null,
    val deleteDelaySeconds: Int? = null,
    val lockOnArchive: Boolean? = null,
    val renameOnArchive: Boolean? = null,
    val removeCreatorOnArchive: Boolean? = null,
    val autoArchiveOnClose: Boolean? = null,
) {
    /** The fields the create endpoints (`AddTicketComponentRequestBase`) accept. */
    fun toButtonFields(): Array<Pair<String, Any?>> = arrayOf(
        "label" to label,
        "description" to description?.takeIf { it.isNotBlank() },
        "style" to style,
        "maxActiveTickets" to maxActiveTickets,
        "emoji" to emoji?.takeIf { it.isNotEmpty() },
        "channelFormat" to channelFormat?.takeIf { it.isNotBlank() },
        "categoryId" to categoryId?.toLongOrNull(),
        "archiveCategoryId" to archiveCategoryId?.toLongOrNull(),
        "supportRoles" to supportRoles?.asIdArray(),
        "viewerRoles" to viewerRoles?.asIdArray(),
        "autoCloseTime" to autoCloseHours?.takeIf { it > 0 }?.let(::hoursToTimeSpan),
        "requiredResponseTime" to requiredResponseMinutes?.takeIf { it > 0 }?.let(::minutesToTimeSpan),
        "allowedPriorities" to allowedPriorities?.takeIf { it.isNotEmpty() }
            ?.let { ids -> JsonArray(ids.map { JsonPrimitive(it) }) },
        "defaultPriority" to defaultPriority?.takeIf { it.isNotBlank() },
        "openMessageJson" to openMessageJson?.takeIf { it.isNotBlank() },
        "modalJson" to modalJson?.takeIf { it.isNotBlank() },
    )

    /** The fields the update endpoints (`UpdateTicketComponentRequestBase`) accept. */
    fun toUpdateFields(): Array<Pair<String, Any?>> = arrayOf(
        *toButtonFields(),
        "saveTranscript" to saveTranscript,
        "deleteOnClose" to deleteOnClose,
        "lockOnClose" to lockOnClose,
        "renameOnClose" to renameOnClose,
        "removeCreatorOnClose" to removeCreatorOnClose,
        "deleteDelay" to deleteDelaySeconds?.let(::secondsToTimeSpan),
        "lockOnArchive" to lockOnArchive,
        "renameOnArchive" to renameOnArchive,
        "removeCreatorOnArchive" to removeCreatorOnArchive,
        "autoArchiveOnClose" to autoArchiveOnClose,
    )
}

/** Packs snowflakes into the numeric array the ticket endpoints expect. */
fun List<Snowflake>.asIdArray(): JsonArray? = takeIf { it.isNotEmpty() }
    ?.let { ids -> JsonArray(ids.mapNotNull { it.toLongOrNull() }.map { JsonPrimitive(it) }) }

/** Encodes whole hours as the `hh:mm:ss` string .NET's `TimeSpan` expects. */
fun hoursToTimeSpan(hours: Int): String = "%02d:00:00".format(hours)

/** Encodes whole minutes as the `hh:mm:ss` string .NET's `TimeSpan` expects. */
fun minutesToTimeSpan(minutes: Int): String = "%02d:%02d:00".format(minutes / 60, minutes % 60)

/** Encodes whole seconds as the `hh:mm:ss` string .NET's `TimeSpan` expects. */
fun secondsToTimeSpan(seconds: Int): String =
    "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)

/** Reads the whole-minutes value out of a `hh:mm:ss` `TimeSpan` string. */
fun timeSpanToMinutes(value: String?): Int {
    if (value.isNullOrBlank()) return 0
    val parts = value.split(":")
    val hours = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minutes = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return hours * 60 + minutes
}

/** Reads the whole-hours value out of a `hh:mm:ss` `TimeSpan` string. */
fun timeSpanToHours(value: String?): Int = timeSpanToMinutes(value) / 60

/** Reads the whole-seconds value out of a `hh:mm:ss` `TimeSpan` string. */
fun timeSpanToSeconds(value: String?): Int {
    if (value.isNullOrBlank()) return 0
    val parts = value.split(":")
    val hours = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minutes = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val seconds = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return hours * 3600 + minutes * 60 + seconds
}
