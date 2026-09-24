package dev.mewdeko.mobile.navigation

import android.net.Uri

/** Every navigable destination in the app. */
object Routes {

    /** The signed-in user's guild list. */
    const val GUILD_LIST = "guilds"

    /** The signed-in user's cross-guild profile tab. */
    const val ACCOUNT = "me"

    /** A guild's overview dashboard. */
    const val GUILD_DETAIL = "guild/{guildId}/{guildName}/{guildIcon}"

    /** The searchable catalog of every feature for a guild, optionally pre-filtered by category. */
    const val FEATURE_BROWSER = "guild/{guildId}/{guildName}/{guildIcon}/features?category={category}"

    /** A single feature page for a guild. */
    const val FEATURE = "guild/{guildId}/{guildName}/{guildIcon}/feature/{featureId}"

    /**
     * The owner panel home, pushed from the Me tab. Owner routes are fleet
     * level: they carry no guild and act on the selected bot instance. Paths
     * mirror the dashboard's `/owner` hrefs.
     */
    const val OWNER_PANEL = "owner"

    /** Containers and compose projects on the bot's host. */
    const val OWNER_DOCKER = "owner/docker"

    /** Servers littered with bots, with bulk leave. */
    const val OWNER_BOT_HELLS = "owner/bot-hells"

    /** Why servers removed the bot, answered by their owners. */
    const val OWNER_LEAVE_FEEDBACK = "owner/leave-feedback"

    /** Fleet telemetry, commands, events, errors, growth and alerts. */
    const val OWNER_ANALYTICS = "owner/analytics"

    /** Bot performance metrics. */
    const val OWNER_PERFORMANCE = "owner/performance"

    /** The pm2 logs on the bot's host. */
    const val OWNER_PROCESS_LOGS = "owner/process-logs"

    /** Builds a [GUILD_DETAIL] route for a concrete guild. */
    fun guildDetail(id: String, name: String, icon: String?) =
        "guild/$id/${name.encode()}/${(icon ?: "-").encode()}"

    /** Builds a [FEATURE_BROWSER] route for a concrete guild, optionally starting on one category. */
    fun featureBrowser(id: String, name: String, icon: String?, category: String? = null) =
        "${guildDetail(id, name, icon)}/features?category=${(category ?: "-").encode()}"

    /** Builds a [FEATURE] route for a concrete guild and feature. */
    fun feature(id: String, name: String, icon: String?, featureId: String) =
        "${guildDetail(id, name, icon)}/feature/$featureId"

    private fun String.encode(): String = Uri.encode(this.ifEmpty { "-" })
}

/**
 * The guild identity carried through the navigation graph.
 *
 * Guild name and icon travel in the route rather than being refetched, so a
 * deep-linked feature page can render its app bar and derive its palette
 * before any network call completes.
 */
data class GuildRouteArgs(
    val id: String,
    val name: String,
    val iconUrl: String?,
) {
    companion object {
        /** Reconstructs the args from decoded navigation arguments. */
        fun from(id: String?, name: String?, icon: String?): GuildRouteArgs = GuildRouteArgs(
            id = id.orEmpty(),
            name = name?.takeIf { it != "-" }.orEmpty(),
            iconUrl = icon?.takeIf { it != "-" && it.isNotEmpty() },
        )
    }
}
