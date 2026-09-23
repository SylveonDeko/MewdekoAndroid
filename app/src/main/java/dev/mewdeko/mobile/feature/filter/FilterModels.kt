package dev.mewdeko.mobile.feature.filter

import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import kotlinx.serialization.Serializable

/** Server-wide filter switches and warning toggles from `GET Filter/{guildId}/settings`. */
@Serializable
data class FilterServerSettings(
    val filterWords: Boolean = false,
    val filterInvites: Boolean = false,
    val filterLinks: Boolean = false,
    val warnOnFilteredWord: Boolean = false,
    val warnOnInvite: Boolean = false,
)

/** Channels where each filter is enabled individually. */
@Serializable
data class FilterChannelSettings(
    val wordFilterChannels: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
    val inviteFilterChannels: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
    val linkFilterChannels: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
)

/** Full filter configuration returned by `GET Filter/{guildId}/settings`. */
@Serializable
data class FilterSettings(
    val serverSettings: FilterServerSettings = FilterServerSettings(),
    val filteredWords: List<String> = emptyList(),
    val autoBanWords: List<String> = emptyList(),
    val channelSettings: FilterChannelSettings = FilterChannelSettings(),
)

/** Result of toggling a filtered or auto-ban word. */
@Serializable
data class FilterWordToggleResult(
    val added: Boolean = false,
    val word: String = "",
)

/** Result of toggling a per-channel filter override. */
@Serializable
data class FilterChannelToggleResult(
    val enabled: Boolean = false,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "",
)

/** The three filters that can be enabled server-wide or per channel. */
enum class FilterKind(
    val pathSegment: String,
    val title: String,
    val hint: String,
) {
    WORD("word", "Word filter", "Delete messages containing any filtered word"),
    INVITE("invite", "Invite filter", "Delete Discord invite links posted by members"),
    LINK("link", "Link filter", "Delete any message containing a URL");

    /** Whether this filter is on server-wide in [settings]. */
    fun serverEnabled(settings: FilterServerSettings): Boolean = when (this) {
        WORD -> settings.filterWords
        INVITE -> settings.filterInvites
        LINK -> settings.filterLinks
    }

    /** The channel override list for this filter in [settings]. */
    fun channels(settings: FilterChannelSettings): List<Snowflake> = when (this) {
        WORD -> settings.wordFilterChannels
        INVITE -> settings.inviteFilterChannels
        LINK -> settings.linkFilterChannels
    }

    /** Returns [settings] with this filter's channel list replaced by [ids]. */
    fun withChannels(settings: FilterChannelSettings, ids: List<Snowflake>): FilterChannelSettings = when (this) {
        WORD -> settings.copy(wordFilterChannels = ids)
        INVITE -> settings.copy(inviteFilterChannels = ids)
        LINK -> settings.copy(linkFilterChannels = ids)
    }
}

/** The two warning toggles that accompany filter deletions. */
enum class FilterWarning(val key: String, val title: String) {
    FILTERED_WORD("warnOnFilteredWord", "Warn on filtered word"),
    INVITE("warnOnInvite", "Warn on invite link");

    /** Current value of this warning in [settings]. */
    fun value(settings: FilterServerSettings): Boolean = when (this) {
        FILTERED_WORD -> settings.warnOnFilteredWord
        INVITE -> settings.warnOnInvite
    }

    /** Returns [settings] with this warning set to [value]. */
    fun apply(settings: FilterServerSettings, value: Boolean): FilterServerSettings = when (this) {
        FILTERED_WORD -> settings.copy(warnOnFilteredWord = value)
        INVITE -> settings.copy(warnOnInvite = value)
    }
}
