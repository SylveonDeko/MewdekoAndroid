package dev.mewdeko.mobile.feature.currency

import dev.mewdeko.mobile.core.model.LenientDoubleSerializer
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * A guild's economy settings as returned by `GET /Currency/{guildId}/config`.
 *
 * Defaults mirror the bot's `CurrencyConfig` entity so a partially populated
 * payload still renders sensible values.
 */
@Serializable
data class EconomyConfig(
    val gamblingEnabled: Boolean = true,
    val minBet: Long = 1,
    val maxBet: Long = 0,
    @Serializable(with = LenientDoubleSerializer::class) val payoutMultiplier: Double = 1.0,
    val gameCooldownSeconds: Int = 0,
    val lossLimitPerDay: Long = 0,
    val payEnabled: Boolean = true,
    val payTaxPercent: Int = 0,
    val payCooldownSeconds: Int = 0,
    val payMinimum: Long = 1,
    val bankEnabled: Boolean = true,
    val bankCapacity: Long = 0,
    @Serializable(with = LenientDoubleSerializer::class) val bankInterestPercent: Double = 0.0,
    val bankInterestHours: Int = 24,
    val robEnabled: Boolean = false,
    val robSuccessChance: Int = 35,
    val robMaxStealPercent: Int = 20,
    val robFinePercent: Int = 15,
    val robMinimumWallet: Long = 100,
    val robCooldownSeconds: Int = 3600,
    val workEnabled: Boolean = true,
    val workMinReward: Long = 50,
    val workMaxReward: Long = 250,
    val workCooldownSeconds: Int = 1800,
    val crimeEnabled: Boolean = true,
    val crimeMinReward: Long = 200,
    val crimeMaxReward: Long = 800,
    val crimeSuccessChance: Int = 45,
    val crimeFineMin: Long = 100,
    val crimeFineMax: Long = 500,
    val crimeCooldownSeconds: Int = 3600,
    val dailyStreakEnabled: Boolean = true,
    val dailyStreakBonus: Long = 0,
    val dailyStreakMaxBonus: Long = 0,
)

/** Current supply and how concentrated it is. */
@Serializable
data class EconomySnapshot(
    val moneySupply: Long = 0,
    val inWallets: Long = 0,
    val inBanks: Long = 0,
    val holders: Int = 0,
    val mean: Long = 0,
    val median: Long = 0,
    @Serializable(with = LenientDoubleSerializer::class) val gini: Double = 0.0,
    @Serializable(with = LenientDoubleSerializer::class) val topTenPercentShare: Double = 0.0,
    val netChange: Long = 0,
)

/** Net currency created or destroyed by one ledger category. */
@Serializable
data class FlowBucket(
    val category: String = "",
    val `in`: Long = 0,
    val out: Long = 0,
    val net: Long = 0,
    val entries: Int = 0,
)

/** Realized performance of one game over the analytics window. */
@Serializable
data class GamePerformance(
    val game: String = "",
    val wagered: Long = 0,
    val returned: Long = 0,
    @Serializable(with = LenientDoubleSerializer::class) val actualRtp: Double = 0.0,
    val houseTake: Long = 0,
    val plays: Int = 0,
    val players: Int = 0,
)

/** Net change in the money supply on one UTC day. */
@Serializable
data class SupplyPoint(
    @Serializable(with = InstantSerializer::class) val date: Instant = Instant.EPOCH,
    val net: Long = 0,
)

/** Everything the analytics section needs, from `GET /Currency/{guildId}/analytics`. */
@Serializable
data class EconomyAnalytics(
    val snapshot: EconomySnapshot = EconomySnapshot(),
    val flow: List<FlowBucket> = emptyList(),
    val games: List<GamePerformance> = emptyList(),
    val supplyHistory: List<SupplyPoint> = emptyList(),
    val transferTax: Long = 0,
    val windowDays: Int = 30,
)

/** What a shop item delivers to its buyer, matching the bot's `ShopItemType`. */
enum class ShopItemType(val raw: Int, val label: String, val blurb: String) {
    ROLE(0, "Role", "Grants a Discord role"),
    COLLECTIBLE(1, "Collectible", "Inventory only"),
    TEXT(2, "Text", "DMs the buyer some content");

    companion object {
        /** Resolves a raw value, falling back to [COLLECTIBLE] for unknown values. */
        fun from(raw: Int): ShopItemType = entries.firstOrNull { it.raw == raw } ?: COLLECTIBLE
    }
}

/** A shop item as `GET /Currency/{guildId}/shop` returns it, with totals. */
@Serializable
data class ShopItem(
    val id: Int = 0,
    val name: String = "",
    val description: String? = null,
    val price: Long = 0,
    val itemType: Int = 1,
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake? = null,
    val roleName: String? = null,
    val textContent: String? = null,
    val stock: Int = -1,
    val maxPerUser: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val requiredRoleId: Snowflake? = null,
    val requiredRoleName: String? = null,
    val consumable: Boolean = false,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val owned: Int = 0,
    val revenue: Long = 0,
) {
    /** The item's type as an enum. */
    val type: ShopItemType get() = ShopItemType.from(itemType)
}

/** One row of the currency leaderboard. */
@Serializable
data class CurrencyLeaderboardEntry(
    val rank: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val username: String? = null,
    val avatarUrl: String? = null,
    val wallet: Long = 0,
    val bank: Long = 0,
    val netWorth: Long = 0,
    @Serializable(with = LenientDoubleSerializer::class) val shareOfSupply: Double = 0.0,
)

/** A page of the leaderboard plus the totals needed to paginate it. */
@Serializable
data class CurrencyLeaderboardPage(
    val entries: List<CurrencyLeaderboardEntry> = emptyList(),
    val total: Int = 0,
    val supply: Long = 0,
)

/** Holdings returned by `POST /Currency/{guildId}/balance` after an adjustment. */
@Serializable
data class AdjustBalanceResult(
    val wallet: Long = 0,
    val bank: Long = 0,
    val netWorth: Long = 0,
)

/** The four top-level sections of the currency screen. */
enum class CurrencySection(val id: String, val title: String) {
    ANALYTICS("analytics", "Analytics"),
    CONFIG("config", "Config"),
    SHOP("shop", "Shop"),
    LEADERBOARD("leaderboard", "Richest");

    companion object {
        /** Resolves a section from its tab id. */
        fun from(id: String): CurrencySection = entries.firstOrNull { it.id == id } ?: ANALYTICS
    }
}

/** Analytics windows offered by the dashboard, in days. */
val AnalyticsWindows: List<Int> = listOf(7, 30, 90, 365)

/**
 * Editable copy of a shop item. [originalName] is the name the item is
 * currently stored under, or `null` when creating a new item.
 */
data class ShopItemDraft(
    val originalName: String? = null,
    val name: String = "",
    val description: String = "",
    val price: Long = 100,
    val type: ShopItemType = ShopItemType.COLLECTIBLE,
    val roleId: Snowflake? = null,
    val textContent: String = "",
    val stock: Int = -1,
    val maxPerUser: Int = 0,
    val requiredRoleId: Snowflake? = null,
    val consumable: Boolean = false,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
) {
    /** Whether this draft edits an existing item rather than creating one. */
    val isEditing: Boolean get() = originalName != null

    companion object {
        /** Builds a draft pre-filled from an existing item. */
        fun from(item: ShopItem) = ShopItemDraft(
            originalName = item.name,
            name = item.name,
            description = item.description.orEmpty(),
            price = item.price,
            type = item.type,
            roleId = item.roleId?.takeIf { it.isNotEmpty() && it != "0" },
            textContent = item.textContent.orEmpty(),
            stock = item.stock,
            maxPerUser = item.maxPerUser,
            requiredRoleId = item.requiredRoleId?.takeIf { it.isNotEmpty() && it != "0" },
            consumable = item.consumable,
            enabled = item.enabled,
            sortOrder = item.sortOrder,
        )
    }
}
