package dev.mewdeko.mobile.feature.patreon

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
import dev.mewdeko.mobile.core.net.InstantSerializer
import dev.mewdeko.mobile.core.net.jsonBody
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant
import javax.inject.Inject

/** Whether the guild has linked a Patreon campaign. */
@Serializable
data class PatreonOAuthStatus(
    val isConfigured: Boolean = false,
    val campaignId: String? = null,
    @Serializable(with = InstantSerializer::class) val lastSync: Instant? = null,
    @Serializable(with = InstantSerializer::class) val tokenExpiry: Instant? = null,
)

/** The URL to send an admin to in order to link a campaign. */
@Serializable
data class PatreonOAuthUrl(val authorizationUrl: String = "", val state: String = "")

/** A high-value supporter surfaced in analytics. */
@Serializable
data class PatreonTopSupporter(
    val name: String = "",
    val amount: Double = 0.0,
    val isLinked: Boolean = false,
)

/** Revenue and supporter analytics for a campaign. */
@Serializable
data class PatreonAnalytics(
    val totalSupporters: Int = 0,
    val activeSupporters: Int = 0,
    val formerSupporters: Int = 0,
    val linkedSupporters: Int = 0,
    val totalMonthlyRevenue: Double = 0.0,
    val averageSupport: Double = 0.0,
    val lifetimeRevenue: Double = 0.0,
    val newSupportersThisMonth: Int = 0,
    val tierDistribution: Map<String, Int> = emptyMap(),
    val topSupporters: List<PatreonTopSupporter> = emptyList(),
)

/** One Patreon supporter. */
@Serializable
data class PatreonSupporter(
    val id: Int = 0,
    val fullName: String = "Unknown",
    val email: String? = null,
    val amountCents: Int = 0,
    val patronStatus: String = "unknown",
    val tierId: String? = null,
    val lifetimeAmountCents: Int = 0,
    @Serializable(with = InstantSerializer::class) val pledgeRelationshipStart: Instant? = null,
    @Serializable(with = InstantSerializer::class) val lastChargeDate: Instant? = null,
) {
    /** Whether this supporter's pledge is currently active. */
    val isActive: Boolean get() = patronStatus.equals("active_patron", ignoreCase = true)
}

/**
 * One Patreon tier cached for this guild.
 *
 * Mirrors `DataModel.PatreonTier`, the flat row type `PatreonService.GetTiersAsync`
 * actually returns: there is no nested `attributes` object on the wire and a
 * tier carries a single Discord role, not a list.
 */
@Serializable
data class PatreonTier(
    val tierId: String = "",
    val tierTitle: String = "Tier",
    val amountCents: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val discordRoleId: Snowflake = "0",
    val description: String? = null,
    val isActive: Boolean = true,
) {
    /** The tier's display name. */
    val title: String get() = tierTitle

    /** Whether this tier already grants a Discord role. */
    val isMapped: Boolean get() = discordRoleId.isNotBlank() && discordRoleId != "0"
}

/** The campaign creator's public profile. */
@Serializable
data class PatreonCreator(
    val fullName: String = "",
    val url: String = "",
    val imageUrl: String = "",
    val patronCount: Int = 0,
)

/**
 * The raw shape of `GET api/patreon/creator`: a Patreon `User` JSON:API
 * resource with the display fields nested one level down, under `attributes`,
 * and keyed with the Patreon API's snake_case names rather than camelCase.
 */
@Serializable
private data class PatreonCreatorWire(
    val attributes: PatreonCreatorAttributesWire? = null,
)

/** The `attributes` object of a Patreon `User` resource. */
@Serializable
private data class PatreonCreatorAttributesWire(
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val url: String? = null,
)

/** Per-guild Patreon announcement configuration. */
@Serializable
data class PatreonConfig(
    @Serializable(with = SnowflakeSerializer::class) val patreonChannelId: Snowflake? = null,
    val patreonMessage: String? = null,
    val patreonAnnouncementDay: Int = 1,
    val patreonEnabled: Boolean = false,
    @Serializable(with = SnowflakeSerializer::class) val patreonGoalChannel: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val patreonStatsChannel: Snowflake? = null,
    val patreonRoleSync: Boolean = false,
)

/** Patreon screen state. */
data class PatreonState(
    val status: PatreonOAuthStatus? = null,
    val creator: PatreonCreator? = null,
    val analytics: PatreonAnalytics? = null,
    val supporters: List<PatreonSupporter> = emptyList(),
    val tiers: List<PatreonTier> = emptyList(),
    val config: PatreonConfig = PatreonConfig(),
    val availableChannels: List<TextChannelLite> = emptyList(),
    val availableRoles: List<GuildRole> = emptyList(),
    val oauthUrl: String? = null,
    val section: String = "overview",
) {
    /** Whether a campaign is linked. */
    val isLinked: Boolean get() = status?.isConfigured == true
}

/** Patreon supporter integration. */
@HiltViewModel
class PatreonViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(PatreonState())

    /** Observable screen state. */
    val state: StateFlow<PatreonState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads the link status and, when linked, campaign data. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        val status = runCatching {
            api.send(
                Endpoint("api/patreon/oauth/status?guildId=$guildId"),
                PatreonOAuthStatus.serializer(),
            )
        }.getOrNull()

        coroutineScope {
            val config = async {
                runCatching {
                    api.send(
                        Endpoint("api/patreon/config?guildId=$guildId"),
                        PatreonConfig.serializer(),
                    )
                }.getOrDefault(PatreonConfig())
            }
            val channels = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/textchannels/$guildId"),
                        ListSerializer(TextChannelLite.serializer()),
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
            val analytics = async {
                if (status?.isConfigured != true) null else runCatching {
                    api.send(
                        Endpoint("api/patreon/analytics?guildId=$guildId"),
                        PatreonAnalytics.serializer(),
                    )
                }.getOrNull()
            }
            val supporters = async {
                if (status?.isConfigured != true) emptyList() else runCatching {
                    api.send(
                        Endpoint("api/patreon/supporters?guildId=$guildId"),
                        ListSerializer(PatreonSupporter.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val tiers = async {
                if (status?.isConfigured != true) emptyList() else runCatching {
                    api.send(
                        Endpoint("api/patreon/tiers?guildId=$guildId"),
                        ListSerializer(PatreonTier.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val creator = async {
                if (status?.isConfigured != true) null else runCatching {
                    api.send(
                        Endpoint("api/patreon/creator?guildId=$guildId"),
                        PatreonCreatorWire.serializer(),
                    )
                }.getOrNull()
            }

            val analyticsResult = analytics.await()
            val creatorAttributes = creator.await()?.attributes

            _state.update {
                it.copy(
                    status = status,
                    config = config.await(),
                    availableChannels = channels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                    availableRoles = roles.await().sortedBy { role -> role.name.lowercase() },
                    analytics = analyticsResult,
                    supporters = supporters.await()
                        .sortedByDescending { supporter -> supporter.amountCents },
                    tiers = tiers.await().sortedBy { tier -> tier.amountCents },
                    creator = creatorAttributes?.let { attrs ->
                        PatreonCreator(
                            fullName = attrs.fullName?.takeIf { it.isNotBlank() } ?: "Creator",
                            url = attrs.url ?: "",
                            imageUrl = attrs.imageUrl ?: "",
                            patronCount = analyticsResult?.totalSupporters ?: 0,
                        )
                    },
                )
            }
        }
    }

    /** Switches the visible section. */
    fun setSection(id: String) = _state.update { it.copy(section = id) }

    /** Fetches the Patreon authorization URL to open in a browser. */
    fun requestOAuthUrl() = launchAction("Failed to start Patreon link.") {
        val response = api.send(
            Endpoint("api/patreon/oauth/url?guildId=$guildId"),
            PatreonOAuthUrl.serializer(),
        )
        _state.update { it.copy(oauthUrl = response.authorizationUrl) }
    }

    /** Clears the pending authorization URL once it has been opened. */
    fun clearOAuthUrl() = _state.update { it.copy(oauthUrl = null) }

    /** Unlinks the Patreon campaign. */
    fun disconnect() = launchAction("Failed to disconnect Patreon.") {
        api.sendIgnoringBody(
            Endpoint("api/patreon/oauth/disconnect?guildId=$guildId", HttpMethod.DELETE)
        )
        postSuccess("Patreon disconnected.")
        load(refreshing = true)
    }

    /**
     * Runs a named maintenance operation, such as a manual sync.
     *
     * The bot only recognises `sync_all`, `sync`, `refresh_token`,
     * `manual_announcement`, and `sync_roles`; anything else returns a 400.
     */
    fun runOperation(operation: String) = launchAction("Failed to run $operation.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/patreon/operations?guildId=$guildId",
                HttpMethod.POST,
                jsonBody("operation" to operation),
            )
        )
        postSuccess("Operation queued.")
        load(refreshing = true)
    }

    /** Maps a Patreon tier to a Discord role, granted when a supporter is synced. */
    fun mapTierToRole(tierId: String, roleId: Snowflake) = launchAction("Failed to map tier to role.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/patreon/tiers/map?guildId=$guildId",
                HttpMethod.POST,
                jsonBody("tierId" to tierId, "roleId" to roleId.toLongOrNull()),
            )
        )
        load(refreshing = true)
    }

    /** Applies an edit to the staged configuration. */
    fun edit(transform: (PatreonConfig) -> PatreonConfig) =
        _state.update { it.copy(config = transform(it.config)) }

    /** Sets the announcement message template. */
    fun setMessage(message: EmbedMessage) =
        edit { it.copy(patreonMessage = message.serialize()) }

    /** Writes the staged configuration. */
    fun saveConfig(toggleAnnouncements: Boolean = false, toggleRoleSync: Boolean = false) =
        launchAction("Failed to save configuration.") {
            val current = _state.value.config
            api.sendIgnoringBody(
                Endpoint(
                    "api/patreon/config?guildId=$guildId",
                    HttpMethod.POST,
                    jsonBody(
                        "toggleAnnouncements" to toggleAnnouncements,
                        "toggleRoleSync" to toggleRoleSync,
                        "channelId" to current.patreonChannelId?.toLongOrNull(),
                        "message" to current.patreonMessage,
                        "announcementDay" to current.patreonAnnouncementDay,
                    ),
                )
            )
            postSuccess("Configuration saved.")
            load(refreshing = true)
        }
}
