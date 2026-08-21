package dev.mewdeko.mobile.feature.chattriggers

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
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import javax.inject.Inject

/** How a trigger's prefix is resolved. */
enum class ChatTriggerPrefixType(val raw: Int, val label: String) {
    GUILD_OR_GLOBAL(0, "Guild prefix"),
    NONE(1, "No prefix"),
    CUSTOM(2, "Custom prefix"),
    MENTION(3, "Mention only");

    companion object {
        /** Maps a wire value onto a prefix type, defaulting to [GUILD_OR_GLOBAL]. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: GUILD_OR_GLOBAL
    }
}

/** Who receives the roles a trigger grants or removes. */
enum class ChatTriggerRoleGrantType(val raw: Int, val label: String) {
    SENDER(0, "Sender"),
    MENTIONED(1, "Mentioned"),
    BOTH(2, "Both");

    companion object {
        /** Maps a wire value onto a grant type, defaulting to [SENDER]. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: SENDER
    }
}

/** A custom keyword reaction. */
@Serializable
data class ChatTriggerModel(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val guildId: Snowflake? = null,
    val trigger: String = "",
    val response: String = "",
    @Serializable(with = SnowflakeSerializer::class) val useCount: String = "0",
    val isRegex: Boolean = false,
    val ownerOnly: Boolean = false,
    val prefixType: Int = 0,
    val customPrefix: String? = null,
    val autoDeleteTrigger: Boolean = false,
    val reactToTrigger: Boolean = false,
    val noRespond: Boolean = false,
    val dmResponse: Boolean = false,
    val containsAnywhere: Boolean = false,
    val allowTarget: Boolean = false,
    val reactions: String? = null,
    val grantedRoles: String? = null,
    val removedRoles: String? = null,
    val roleGrantType: Int = 0,
    val validTriggerTypes: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val applicationCommandId: Snowflake? = null,
    val applicationCommandName: String? = null,
    val applicationCommandDescription: String? = null,
    val applicationCommandType: Int = 0,
    val ephemeralResponse: Boolean = false,
    @Serializable(with = SnowflakeSerializer::class) val crosspostingChannelId: Snowflake? = null,
    val crosspostingWebhookUrl: String? = null,
) {
    /** How often this trigger has fired. */
    val uses: Long get() = useCount.toLongOrNull() ?: 0L

    /** The typed form of [prefixType]. */
    val prefix: ChatTriggerPrefixType get() = ChatTriggerPrefixType.from(prefixType)

    /** The typed form of [roleGrantType]. */
    val grantType: ChatTriggerRoleGrantType get() = ChatTriggerRoleGrantType.from(roleGrantType)

    /** Roles granted when the trigger fires. */
    val grantedRoleIds: List<Snowflake>
        get() = grantedRoles.orEmpty().split(' ', '@').map { it.trim() }.filter { it.isNotEmpty() }

    /** Roles removed when the trigger fires. */
    val removedRoleIds: List<Snowflake>
        get() = removedRoles.orEmpty().split(' ', '@').map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        /** A trigger with every field at its default, scoped to [guildId]. */
        fun blank(guildId: Snowflake) = ChatTriggerModel(guildId = guildId)
    }
}

/** Chat triggers screen state. */
data class ChatTriggersState(
    val triggers: List<ChatTriggerModel> = emptyList(),
    val availableRoles: List<GuildRole> = emptyList(),
    val availableChannels: List<TextChannelLite> = emptyList(),
    val query: String = "",
) {
    /** Triggers matching the current search query. */
    val filtered: List<ChatTriggerModel>
        get() {
            val q = query.trim().lowercase()
            return if (q.isEmpty()) triggers
            else triggers.filter {
                it.trigger.lowercase().contains(q) || it.response.lowercase().contains(q)
            }
        }

    /** Total fires across every trigger. */
    val totalUses: Long get() = triggers.sumOf { it.uses }
}

/** Custom keyword reactions. */
@HiltViewModel
class ChatTriggersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(ChatTriggersState())

    /** Observable screen state. */
    val state: StateFlow<ChatTriggersState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads triggers plus role and channel options. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val triggers = async {
                runCatching {
                    api.send(
                        Endpoint("api/ChatTriggers/$guildId"),
                        ListSerializer(ChatTriggerModel.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val roles = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/roles/$guildId"),
                        ListSerializer(GuildRole.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val channels = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/textchannels/$guildId"),
                        ListSerializer(TextChannelLite.serializer()),
                    )
                }.getOrDefault(emptyList())
            }

            _state.update {
                it.copy(
                    triggers = triggers.await().sortedBy { entry -> entry.trigger.lowercase() },
                    availableRoles = roles.await()
                        .filter { role -> role.id != guildId }
                        .sortedBy { role -> role.name.lowercase() },
                    availableChannels = channels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                )
            }
        }
    }

    /** Updates the search query. */
    fun setQuery(value: String) = _state.update { it.copy(query = value) }

    /** Creates a trigger. */
    fun add(trigger: ChatTriggerModel) = launchAction("Failed to add trigger.") {
        val added = api.send(
            Endpoint(
                "api/ChatTriggers/$guildId",
                HttpMethod.POST,
                MewdekoJson.encodeToString(trigger.copy(guildId = guildId)),
            ),
            ChatTriggerModel.serializer(),
        )
        _state.update {
            it.copy(
                triggers = (it.triggers + added).sortedBy { entry -> entry.trigger.lowercase() },
            )
        }
        postSuccess("Trigger added.")
    }

    /** Saves an edited trigger. */
    fun update(trigger: ChatTriggerModel) = launchAction("Failed to update trigger.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/ChatTriggers/$guildId",
                HttpMethod.PATCH,
                MewdekoJson.encodeToString(trigger.copy(guildId = guildId)),
            )
        )
        _state.update { current ->
            current.copy(
                triggers = current.triggers.map { if (it.id == trigger.id) trigger else it },
            )
        }
        postSuccess("Trigger saved.")
    }

    /** Deletes a trigger. */
    fun remove(id: Int) = launchAction("Failed to remove trigger.") {
        api.sendIgnoringBody(
            Endpoint("api/ChatTriggers/$guildId/$id", HttpMethod.DELETE)
        )
        _state.update { it.copy(triggers = it.triggers.filterNot { entry -> entry.id == id }) }
        postSuccess("Trigger removed.")
    }
}
