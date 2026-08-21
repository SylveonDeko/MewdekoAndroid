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

    /** The searchable catalog of every feature for a guild. */
    const val FEATURE_BROWSER = "guild/{guildId}/{guildName}/{guildIcon}/features"

    /** A single feature page for a guild. */
    const val FEATURE = "guild/{guildId}/{guildName}/{guildIcon}/feature/{featureId}"

    /** Builds a [GUILD_DETAIL] route for a concrete guild. */
    fun guildDetail(id: String, name: String, icon: String?) =
        "guild/$id/${name.encode()}/${(icon ?: "-").encode()}"

    /** Builds a [FEATURE_BROWSER] route for a concrete guild. */
    fun featureBrowser(id: String, name: String, icon: String?) =
        "${guildDetail(id, name, icon)}/features"

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
