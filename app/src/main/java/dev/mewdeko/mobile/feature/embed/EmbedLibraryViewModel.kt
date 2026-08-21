package dev.mewdeko.mobile.feature.embed

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.asSnowflakeNumber
import dev.mewdeko.mobile.core.net.jsonBody
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import javax.inject.Inject

/** The saved embeds, personas, and channels available to the composer. */
data class EmbedLibraryState(
    val userEmbeds: List<SavedEmbed> = emptyList(),
    val guildEmbeds: List<SavedEmbed> = emptyList(),
    val personas: List<EmbedPersona> = emptyList(),
    val channels: List<SendableChannel> = emptyList(),
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val lastSend: SendEmbedResult? = null,
) {
    /** Every saved embed, personal first, each labelled by its scope. */
    val allSaved: List<SavedEmbed> get() = userEmbeds + guildEmbeds

    /** Channels the message can actually be delivered to. */
    val usableChannels: List<SendableChannel> get() = channels.filter { it.isUsable }
}

/**
 * Backs the composer's library: saved embeds, send-as personas, and the
 * channels a message can be delivered to.
 *
 * Shared by the standalone embed screen and the in-place composer that feature
 * pages open, so a saved embed written on one is immediately available on the
 * other.
 */
@HiltViewModel
class EmbedLibraryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _library = MutableStateFlow(EmbedLibraryState())

    /** Observable library state. */
    val library: StateFlow<EmbedLibraryState> = _library.asStateFlow()

    init {
        refresh()
    }

    /** Reloads saved embeds, personas, and sendable channels. */
    fun refresh() = viewModelScope.launch {
        _library.update { it.copy(isLoading = true) }
        coroutineScope {
            val user = async { list("api/Embeds/user/$userId", SavedEmbed.serializer()) }
            val guild = async { list("api/Embeds/guild/$guildId", SavedEmbed.serializer()) }
            val userPersonas = async {
                list("api/Embeds/personas/user/$userId", EmbedPersona.serializer())
            }
            val guildPersonas = async {
                list("api/Embeds/personas/guild/$guildId", EmbedPersona.serializer())
            }
            val channels = async {
                list("api/Embeds/channels/$guildId?userId=$userId", SendableChannel.serializer())
            }
            _library.update {
                it.copy(
                    userEmbeds = user.await(),
                    guildEmbeds = guild.await(),
                    personas = userPersonas.await() + guildPersonas.await(),
                    channels = channels.await().sortedBy { channel -> channel.position },
                    isLoading = false,
                )
            }
        }
    }

    /** Saves the current message under a name, optionally sharing it guild-wide. */
    fun save(name: String, jsonCode: String, shared: Boolean) =
        launchAction("Failed to save embed.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Embeds",
                    HttpMethod.POST,
                    jsonBody(
                        "userId" to userId.asSnowflakeNumber(),
                        "guildId" to if (shared) guildId.asSnowflakeNumber() else null,
                        "embedName" to name.trim(),
                        "jsonCode" to jsonCode,
                        "isGuildShared" to shared,
                    ),
                )
            )
            refresh()
            postSuccess("Saved as \"${name.trim()}\".")
        }

    /** Renames or rewrites an existing saved embed. */
    fun update(embed: SavedEmbed, name: String, jsonCode: String) =
        launchAction("Failed to update embed.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Embeds/${embed.id}",
                    HttpMethod.PUT,
                    jsonBody(
                        "userId" to userId.asSnowflakeNumber(),
                        "embedName" to name.trim(),
                        "jsonCode" to jsonCode,
                    ),
                )
            )
            refresh()
            postSuccess("Updated.")
        }

    /** Deletes a saved embed the signed-in user owns. */
    fun delete(embed: SavedEmbed) = launchAction("Failed to delete embed.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Embeds/${embed.id}?userId=${userId.asSnowflakeNumber()}",
                HttpMethod.DELETE,
            )
        )
        _library.update {
            it.copy(
                userEmbeds = it.userEmbeds.filterNot { saved -> saved.id == embed.id },
                guildEmbeds = it.guildEmbeds.filterNot { saved -> saved.id == embed.id },
            )
        }
        postSuccess("Deleted.")
    }

    /**
     * Delivers the message to a channel.
     *
     * A persona, or an ad-hoc name and avatar, routes the message through a
     * webhook so it posts under that identity instead of the bot's.
     */
    fun send(
        channelId: Snowflake,
        jsonCode: String,
        useWebhook: Boolean,
        personaId: Int?,
        webhookUsername: String?,
        webhookAvatarUrl: String?,
    ) = launchAction("Failed to send message.") {
        _library.update { it.copy(isSending = true) }
        try {
            val result = api.send(
                Endpoint(
                    "api/Embeds/send/$guildId",
                    HttpMethod.POST,
                    jsonBody(
                        "userId" to userId.asSnowflakeNumber(),
                        "channelId" to channelId.asSnowflakeNumber(),
                        "jsonCode" to jsonCode,
                        "useWebhook" to useWebhook,
                        "personaId" to personaId,
                        "webhookUsername" to webhookUsername?.takeIf { it.isNotBlank() },
                        "webhookAvatarUrl" to webhookAvatarUrl?.takeIf { it.isNotBlank() },
                    ),
                ),
                SendEmbedResult.serializer(),
            )
            _library.update { it.copy(lastSend = result) }
            postSuccess("Sent to #${result.channelName}.")
        } finally {
            _library.update { it.copy(isSending = false) }
        }
    }

    /** Clears the record of the last delivery. */
    fun clearLastSend() = _library.update { it.copy(lastSend = null) }

    private suspend fun <T> list(path: String, serializer: KSerializer<T>): List<T> =
        runCatching {
            api.send(Endpoint(path), ListSerializer(serializer))
        }.getOrDefault(emptyList())
}
