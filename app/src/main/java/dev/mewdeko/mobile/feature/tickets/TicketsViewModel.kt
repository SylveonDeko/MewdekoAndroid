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

/** One button on a panel. */
@Serializable
data class PanelButton(
    val id: Int = 0,
    val label: String = "",
    val style: Int = 1,
    val emoji: String? = null,
    val customId: String? = null,
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

/** One select menu on a panel, shown read-only on mobile. */
@Serializable
data class PanelSelectMenu(
    val id: Int = 0,
    val placeholder: String? = null,
    val optionCount: Int = 0,
    val options: List<kotlinx.serialization.json.JsonElement>? = null,
) {
    /** How many options the menu offers, however the bot reported it. */
    val optionTotal: Int get() = if (optionCount > 0) optionCount else options?.size ?: 0
}

/** A case grouping several related tickets. */
@Serializable
data class TicketCase(
    val id: Int = 0,
    val title: String = "",
    val description: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val createdBy: Snowflake? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    val isClosed: Boolean = false,
    val linkedTicketIds: List<Int> = emptyList(),
    val linkedTickets: List<Int> = emptyList(),
) {
    /** The tickets attached to this case, however the bot named the field. */
    val linked: List<Int> get() = linkedTicketIds.ifEmpty { linkedTickets }
}

/** Which slice of the ticket list is showing. */
enum class TicketFilter(val label: String) {
    OPEN("Open"),
    CLAIMED("Claimed"),
    CLOSED("Closed"),
    ARCHIVED("Archived"),
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
    val section: TicketSection = TicketSection.OVERVIEW,
    val filter: TicketFilter = TicketFilter.OPEN,
    val openPanel: PanelDetail? = null,
) {
    /** The tickets matching the active filter. */
    val visible: List<TicketSummary>
        get() = when (filter) {
            TicketFilter.OPEN -> tickets.filter { it.isOpen }
            TicketFilter.CLAIMED -> tickets.filter { it.claimedBy != null && it.isOpen }
            TicketFilter.CLOSED -> tickets.filter { it.closedAt != null && !it.isArchived }
            TicketFilter.ARCHIVED -> tickets.filter { it.isArchived }
        }

    /** How many tickets fall into each filter, for the overview tiles. */
    fun countFor(filter: TicketFilter): Int = when (filter) {
        TicketFilter.OPEN -> tickets.count { it.isOpen }
        TicketFilter.CLAIMED -> tickets.count { it.claimedBy != null && it.isOpen }
        TicketFilter.CLOSED -> tickets.count { it.closedAt != null && !it.isArchived }
        TicketFilter.ARCHIVED -> tickets.count { it.isArchived }
    }
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
                )
            }
        }
    }

    /** Switches the visible section. */
    fun setSection(section: TicketSection) = _state.update { it.copy(section = section) }

    /** Narrows the ticket list. */
    fun setFilter(filter: TicketFilter) = _state.update { it.copy(filter = filter) }

    /** Assigns a ticket to the signed-in staff member. */
    fun claim(ticket: TicketSummary) = ticketAction(ticket, "claim", "Claimed.", "Failed to claim.")

    /** Releases a ticket the signed-in staff member holds. */
    fun unclaim(ticket: TicketSummary) =
        ticketAction(ticket, "unclaim", "Unclaimed.", "Failed to unclaim.")

    /** Closes a ticket. */
    fun close(ticket: TicketSummary) = ticketAction(ticket, "close", "Closed.", "Failed to close.")

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

    /** Attaches tags to a ticket. */
    fun addTags(ticket: TicketSummary, tagIds: List<String>) =
        launchAction("Failed to update tags.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Ticket/$guildId/tickets/by-channel/${ticket.channelId}/tags",
                    HttpMethod.POST,
                    jsonBody(
                        "tagIds" to JsonArray(tagIds.map { JsonPrimitive(it) }),
                        "staffId" to userId.asSnowflakeNumber(),
                    ),
                )
            )
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

    /** Posts a new ticket panel into a channel. */
    fun createPanel(channelId: Snowflake, title: String, description: String) =
        launchAction("Failed to create panel.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Ticket/$guildId/panels",
                    HttpMethod.POST,
                    jsonBody(
                        "channelId" to channelId.asSnowflakeNumber(),
                        "title" to title,
                        "description" to description,
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
    fun closePanel() = _state.update { it.copy(openPanel = null) }

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
            val loaded = PanelDetail(panel, buttons.await(), menus.await(), loading = false)
            _state.update {
                if (it.openPanel?.panel?.id == panel.id) it.copy(openPanel = loaded) else it
            }
        }
    }

    /** Adds a ticket-opening button to a panel. */
    fun addPanelButton(
        panel: TicketPanel,
        label: String,
        emoji: String?,
        style: Int,
        categoryId: Snowflake?,
        archiveCategoryId: Snowflake?,
        supportRoles: List<Snowflake>,
        viewerRoles: List<Snowflake>,
        maxActiveTickets: Int,
    ) = launchAction("Failed to add button.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Ticket/$guildId/panels/${panel.id}/buttons",
                HttpMethod.POST,
                jsonBody(
                    "label" to label,
                    "style" to style,
                    "maxActiveTickets" to maxActiveTickets,
                    "emoji" to emoji?.takeIf { it.isNotEmpty() },
                    "categoryId" to categoryId?.toLongOrNull(),
                    "archiveCategoryId" to archiveCategoryId?.toLongOrNull(),
                    "supportRoles" to supportRoles.asIdArray(),
                    "viewerRoles" to viewerRoles.asIdArray(),
                ),
            )
        )
        loadPanelDetail(panel)
        postSuccess("Button added.")
    }

    /** Removes a button from a panel. */
    fun deletePanelButton(button: PanelButton) = launchAction("Failed to delete button.") {
        api.sendIgnoringBody(
            Endpoint("api/Ticket/$guildId/buttons/${button.id}", HttpMethod.DELETE)
        )
        _state.value.openPanel?.let { loadPanelDetail(it.panel) }
        postSuccess("Button deleted.")
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

    /** Opens a new case that tickets can be linked to. */
    fun createCase(title: String, description: String) = launchAction("Failed to create case.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Ticket/$guildId/cases",
                HttpMethod.POST,
                jsonBody(
                    "title" to title.trim(),
                    "description" to description.trim(),
                    "creatorId" to userId.asSnowflakeNumber(),
                ),
            )
        )
        load()
        postSuccess("Case created.")
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

/** Packs snowflakes into the numeric array the ticket endpoints expect. */
private fun List<Snowflake>.asIdArray(): JsonArray? = takeIf { it.isNotEmpty() }
    ?.let { ids -> JsonArray(ids.mapNotNull { it.toLongOrNull() }.map { JsonPrimitive(it) }) }
