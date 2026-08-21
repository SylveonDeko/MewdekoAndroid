package dev.mewdeko.mobile.feature.statusroles

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.model.GuildRole
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.jsonString
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import javax.inject.Inject

/** A rule that grants or revokes roles based on a member's custom status. */
@Serializable
data class StatusRoleConfig(
    val id: Int = 0,
    val status: String? = null,
    val toAdd: String? = null,
    val toRemove: String? = null,
    val statusEmbed: String? = null,
    val readdRemoved: Boolean = false,
    val removeAdded: Boolean = false,
    @Serializable(with = SnowflakeSerializer::class) val statusChannelId: Snowflake? = null,
) {
    /** Roles granted while the status matches, parsed from the space-delimited field. */
    val addRoleIds: List<Snowflake>
        get() = toAdd.orEmpty().split(' ').filter { it.isNotBlank() }

    /** Roles revoked while the status matches. */
    val removeRoleIds: List<Snowflake>
        get() = toRemove.orEmpty().split(' ').filter { it.isNotBlank() }
}

/** Status roles screen state. */
data class StatusRolesState(
    val configs: List<StatusRoleConfig> = emptyList(),
    val availableRoles: List<GuildRole> = emptyList(),
    val availableChannels: List<TextChannelLite> = emptyList(),
) {
    /** Resolves a role id to its name, falling back to the raw id. */
    fun roleName(id: Snowflake): String =
        availableRoles.firstOrNull { it.id == id }?.name ?: id
}

/** Roles applied while a member's custom status contains a phrase. */
@HiltViewModel
class StatusRolesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(StatusRolesState())

    /** Observable screen state. */
    val state: StateFlow<StatusRolesState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads the rules plus role and channel options. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val configs = async {
                runCatching {
                    api.send(
                        Endpoint("api/StatusRoles/$guildId"),
                        ListSerializer(StatusRoleConfig.serializer()),
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
                    configs = configs.await(),
                    availableRoles = roles.await()
                        .filter { role -> role.id != guildId }
                        .sortedBy { role -> role.name.lowercase() },
                    availableChannels = channels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                )
            }
        }
    }

    /** Creates a rule triggered by the given status text. */
    fun add(status: String) = launchAction("Failed to add status role.") {
        api.sendIgnoringBody(
            Endpoint("api/StatusRoles/$guildId", HttpMethod.POST, jsonString(status))
        )
        postSuccess("Status role added.")
        load(refreshing = true)
    }

    /** Deletes a rule. */
    fun remove(id: Int) = launchAction("Failed to remove status role.") {
        api.sendIgnoringBody(Endpoint("api/StatusRoles/$guildId/$id", HttpMethod.DELETE))
        _state.update { it.copy(configs = it.configs.filterNot { config -> config.id == id }) }
        postSuccess("Status role removed.")
    }

    /** Sets the roles granted while the status matches. */
    fun setAddRoles(id: Int, roleIds: List<Snowflake>) = launchAction("Failed to update roles.") {
        post(id, "addRoles", jsonString(roleIds.joinToString(" ")))
        _state.update { current ->
            current.copy(
                configs = current.configs.map {
                    if (it.id == id) it.copy(toAdd = roleIds.joinToString(" ")) else it
                },
            )
        }
    }

    /** Sets the roles revoked while the status matches. */
    fun setRemoveRoles(id: Int, roleIds: List<Snowflake>) = launchAction("Failed to update roles.") {
        post(id, "removeRoles", jsonString(roleIds.joinToString(" ")))
        _state.update { current ->
            current.copy(
                configs = current.configs.map {
                    if (it.id == id) it.copy(toRemove = roleIds.joinToString(" ")) else it
                },
            )
        }
    }

    /** Sets the channel the rule announces into. */
    fun setChannel(id: Int, channelId: Snowflake) = launchAction("Failed to set channel.") {
        post(id, "channel", (channelId.toLongOrNull() ?: 0L).toString())
        _state.update { current ->
            current.copy(
                configs = current.configs.map {
                    if (it.id == id) it.copy(statusChannelId = channelId) else it
                },
            )
        }
    }

    /** Sets the announcement embed for the rule. */
    fun setEmbed(id: Int, embed: EmbedMessage) = launchAction("Failed to save embed.") {
        post(id, "embed", jsonString(embed.serialize()))
        _state.update { current ->
            current.copy(
                configs = current.configs.map {
                    if (it.id == id) it.copy(statusEmbed = embed.serialize()) else it
                },
            )
        }
        postSuccess("Embed saved.")
    }

    /** Toggles whether granted roles are revoked once the status changes. */
    fun toggleRemoveAdded(id: Int) = launchAction("Failed to update setting.") {
        api.sendIgnoringBody(
            Endpoint("api/StatusRoles/$guildId/$id/toggleRemoveAdded", HttpMethod.POST)
        )
        _state.update { current ->
            current.copy(
                configs = current.configs.map {
                    if (it.id == id) it.copy(removeAdded = !it.removeAdded) else it
                },
            )
        }
    }

    /** Toggles whether revoked roles are restored once the status changes. */
    fun toggleReaddRemoved(id: Int) = launchAction("Failed to update setting.") {
        api.sendIgnoringBody(
            Endpoint("api/StatusRoles/$guildId/$id/toggleReaddRemoved", HttpMethod.POST)
        )
        _state.update { current ->
            current.copy(
                configs = current.configs.map {
                    if (it.id == id) it.copy(readdRemoved = !it.readdRemoved) else it
                },
            )
        }
    }

    private suspend fun post(id: Int, tail: String, body: String) =
        api.sendIgnoringBody(
            Endpoint("api/StatusRoles/$guildId/$id/$tail", HttpMethod.POST, body)
        )
}
