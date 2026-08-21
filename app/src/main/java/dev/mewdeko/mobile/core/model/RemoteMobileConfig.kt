package dev.mewdeko.mobile.core.model

import kotlinx.serialization.Serializable

/** OAuth and instance metadata published by a dashboard for its mobile clients. */
@Serializable
data class RemoteMobileConfig(
    val discord: Discord = Discord(),
    val instance: Instance = Instance(),
    val api: Api = Api(),
) {
    /** Discord OAuth parameters this dashboard is configured with. */
    @Serializable
    data class Discord(
        val clientId: String = "",
        val redirectUri: String = "mewdeko-mobile://oauth/callback",
        val scopes: String = "identify guilds",
        val authorizeUrl: String = "https://discord.com/api/oauth2/authorize",
    )

    /** Branding for the dashboard's bot instance. */
    @Serializable
    data class Instance(
        val name: String = "Mewdeko",
        val inviteUrl: String? = null,
    )

    /** Version handshake for the mobile API surface. */
    @Serializable
    data class Api(val version: Int = 1)
}
