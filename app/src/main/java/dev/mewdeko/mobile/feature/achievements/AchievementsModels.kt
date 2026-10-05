package dev.mewdeko.mobile.feature.achievements

import androidx.compose.ui.graphics.Color
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/** What unlocks an achievement. Values match the bot. */
enum class AchievementTriggerKind(val value: Int, val title: String, val blurb: String) {
    METRIC(0, "Reach a number", "Messages, voice hours, invites, level, and more."),
    KEYWORD(1, "Say a phrase", "Unlocks when a message contains the phrase."),
    REACTION(2, "React with an emoji", "Unlocks the first time they react with it."),
    MANUAL(3, "Staff hand it out", "Only given by staff."),
    FEAT(4, "A one time moment", "Built in moments the bot watches for."),
    COMPLETION(5, "Finish a category", "Unlocking everything in a category.");

    companion object {
        /** Triggers a server can pick for its own achievements. */
        val customChoices = listOf(METRIC, KEYWORD, REACTION, MANUAL)

        /** Resolves a stored value, defaulting to a metric. */
        fun from(value: Int): AchievementTriggerKind = entries.firstOrNull { it.value == value } ?: METRIC
    }
}

/** Where unlocks are announced. Values match the bot. */
enum class AchievementAnnounceMode(val value: Int, val title: String, val blurb: String) {
    AUTO(0, "Automatic", "The log channel when one is set, otherwise the channel it happened in."),
    HERE(1, "Where it happened", "In the channel the member was active in, falling back to the log channel."),
    LOG_CHANNEL(2, "Log channel only", "Only in the log channel. Nothing is sent without one."),
    DM_ONLY(3, "DMs only", "Only in members' DMs, for those who allow it."),
    SILENT(4, "Silent", "Achievements unlock quietly with no message.");

    companion object {
        /** Resolves a stored value, defaulting to automatic. */
        fun from(value: Int): AchievementAnnounceMode = entries.firstOrNull { it.value == value } ?: AUTO
    }
}

/** A grade, as the catalog reports it. */
@Serializable
data class AchievementGradeInfo(
    val value: Int = 0,
    val name: String = "",
    val points: Int = 0,
    val color: String = "#CD7F32",
) {
    /** The grade color. */
    val uiColor: Color
        get() = runCatching { Color(android.graphics.Color.parseColor(color)) }.getOrDefault(Color(0xFFCD7F32))

    companion object {
        /** Grades used before the catalog loads; they match the bot's. */
        val Fallback = listOf(
            AchievementGradeInfo(0, "Bronze", 10, "#CD7F32"),
            AchievementGradeInfo(1, "Silver", 25, "#C0C7D0"),
            AchievementGradeInfo(2, "Gold", 50, "#F5C542"),
            AchievementGradeInfo(3, "Emerald", 100, "#34D399"),
            AchievementGradeInfo(4, "Amethyst", 250, "#A78BFA"),
            AchievementGradeInfo(5, "Champion", 500, "#FF5D73"),
        )
    }
}

/** A metric achievements can count. */
@Serializable
data class AchievementMetricInfo(
    val value: Int = 0,
    val key: String = "",
    val label: String = "",
    val unit: String = "",
    val unitPlural: String = "",
    val description: String = "",
    val source: String = "",
    val allowCustom: Boolean = false,
)

/** A rank. */
@Serializable
data class AchievementTierInfo(
    val name: String = "",
    val minPoints: Int = 0,
    val grade: Int? = null,
)

/** An unlock message placeholder. */
@Serializable
data class AchievementPlaceholderInfo(
    val name: String = "",
    val description: String = "",
)

/** Size limits. */
@Serializable
data class AchievementLimits(
    val maxCustomAchievements: Int = 200,
    val maxCustomCategories: Int = 25,
    val nameLength: Int = 80,
    val descriptionLength: Int = 200,
    val keywordLength: Int = 100,
    val messageLength: Int = 6000,
    val maxPoints: Int = 10000,
    val maxReward: Long = 1_000_000_000,
    val badgeSlots: Int = 4,
)

/** One achievement as a server sees it. */
@Serializable
data class AchievementItem(
    val key: String = "",
    val categoryKey: String = "custom",
    val name: String = "",
    val description: String = "",
    /** Icon it shows, its own or its category's: fa:name, a custom emoji, an https URL, or upload:id. */
    val icon: String = "fa:trophy",
    /** The icon's image, null for glyph icons; uploads without a public URL give an API path. */
    val iconUrl: String? = null,
    val grade: Int = 0,
    val points: Int = 0,
    val hidden: Boolean = false,
    val enabled: Boolean = true,
    val selfEnabled: Boolean = true,
    val trigger: Int = 0,
    val metric: Int = 0,
    val threshold: Long = 0,
    val keyword: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val roleRewardId: Snowflake? = null,
    val roleRewardName: String? = null,
    val currencyReward: Long = 0,
    val xpReward: Int = 0,
    val isCustom: Boolean = false,
    val customId: Int? = null,
    val isGlobal: Boolean = false,
    val isOverridden: Boolean = false,
    val unlockCount: Int = 0,
    val defaultName: String? = null,
    val defaultDescription: String? = null,
    val defaultPoints: Int? = null,
    val defaultHidden: Boolean? = null,
    val rawName: String? = null,
    val rawDescription: String? = null,
    /** Stored icon, null when it uses its category's. */
    val rawIcon: String? = null,
    val rawPoints: Int? = null,
) {
    /** The trigger as an enum. */
    val triggerKind: AchievementTriggerKind get() = AchievementTriggerKind.from(trigger)

    /** Whether any reward is set. */
    val hasRewards: Boolean get() = roleRewardId != null || currencyReward > 0 || xpReward > 0
}

/** A category with counts. */
@Serializable
data class AchievementCategoryItem(
    val key: String = "",
    val name: String = "",
    /** Icon, in the same forms as an achievement's. */
    val icon: String = "fa:folder",
    /** The icon's image, null for glyph icons. */
    val iconUrl: String? = null,
    val description: String = "",
    val isBuiltIn: Boolean = true,
    val hasBadges: Boolean = false,
    val enabled: Boolean = true,
    val id: Int? = null,
    val achievementCount: Int = 0,
    val enabledCount: Int = 0,
)

/** Everything a server can earn, plus reference data. */
@Serializable
data class AchievementCatalog(
    val categories: List<AchievementCategoryItem> = emptyList(),
    val achievements: List<AchievementItem> = emptyList(),
    val grades: List<AchievementGradeInfo> = AchievementGradeInfo.Fallback,
    val metrics: List<AchievementMetricInfo> = emptyList(),
    val tiers: List<AchievementTierInfo> = emptyList(),
    val placeholders: List<AchievementPlaceholderInfo> = emptyList(),
    val limits: AchievementLimits = AchievementLimits(),
    val uploads: List<AchievementIconUpload> = emptyList(),
)

/** An image a server uploaded for icons. */
@Serializable
data class AchievementIconUpload(
    val id: Int = 0,
    /** The icon value that uses it: upload:id. */
    val icon: String = "",
    /** A public URL, or an API path when the instance has neither a CDN nor a dashboard URL. */
    val url: String = "",
)

/** A drawn achievement image, as a data URI. */
@Serializable
data class AchievementImageResponse(val image: String = "") {
    /** The PNG bytes. */
    val bytes: ByteArray?
        get() = image.substringAfter(',', "").takeIf { it.isNotEmpty() }
            ?.let { runCatching { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }.getOrNull() }
}

/** An image upload request. */
@Serializable
data class AchievementIconUploadRequest(val data: String)

/** A server's achievement settings. */
@Serializable
data class AchievementSettingsInfo(
    val enabled: Boolean = false,
    val announceMode: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val logChannelId: Snowflake? = null,
    val dmByDefault: Boolean = false,
    val mentionUsers: Boolean = true,
    val unlockMessage: String? = null,
    val xpPerPoint: Int = 0,
    val revealHidden: Boolean = false,
    /** Unlock messages carry a generated image. */
    val unlockImage: Boolean = true,
    /** Seconds after which unlock messages in channels are deleted; 0 keeps them. */
    val deleteAfter: Int = 5,
    val disabledCategories: List<String> = emptyList(),
    val categoryOrder: List<String> = emptyList(),
    val excludedRoleIds: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
    val excludedChannelIds: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
    /** Channels where achievements are earned but unlocks are never announced. */
    val quietChannelIds: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
    /** Unlocks stay out of channels the member can't send messages in. */
    val requireSendPermission: Boolean = true,
    @Serializable(with = InstantSerializer::class) val backfilledAt: Instant? = null,
)

/** A recent unlock. */
@Serializable
data class AchievementRecentUnlock(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val username: String = "",
    val avatarUrl: String? = null,
    val key: String = "",
    val name: String = "",
    val icon: String = "fa:trophy",
    val iconUrl: String? = null,
    val grade: Int = 0,
    @Serializable(with = InstantSerializer::class) val unlockedAt: Instant? = null,
)

/** An achievement and how many unlocked it. */
@Serializable
data class AchievementRarity(
    val key: String = "",
    val name: String = "",
    val icon: String = "fa:trophy",
    val iconUrl: String? = null,
    val grade: Int = 0,
    val count: Int = 0,
)

/** The dashboard overview. */
@Serializable
data class AchievementOverview(
    val settings: AchievementSettingsInfo = AchievementSettingsInfo(),
    val dataSources: Map<String, Boolean> = emptyMap(),
    val members: Int = 0,
    val unlocks: Int = 0,
    val unlocksThisWeek: Int = 0,
    val earnable: Int = 0,
    val total: Int = 0,
    val customCount: Int = 0,
    val recent: List<AchievementRecentUnlock> = emptyList(),
    val mostCommon: List<AchievementRarity> = emptyList(),
    val rarest: List<AchievementRarity> = emptyList(),
)

/** A channel for selectors. */
@Serializable
data class AchievementChannelLookup(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "",
    val name: String = "",
    val categoryName: String? = null,
    val type: Int = 0,
    val canSend: Boolean = false,
)

/** A role for selectors. */
@Serializable
data class AchievementRoleLookup(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "",
    val name: String = "",
    val color: Long = 0,
    val position: Int = 0,
    val assignable: Boolean = false,
)

/** Channels, roles, and emojis for editors. */
@Serializable
data class AchievementLookups(
    val channels: List<AchievementChannelLookup> = emptyList(),
    val roles: List<AchievementRoleLookup> = emptyList(),
    val emojis: List<AchievementEmojiLookup> = emptyList(),
    val botCanManageRoles: Boolean = true,
)

/** One of the server's own emojis. */
@Serializable
data class AchievementEmojiLookup(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "",
    val name: String = "",
    /** The emoji as Discord writes it, such as <:name:id>. */
    val formatted: String = "",
    val url: String = "",
)

/** A member's totals. */
@Serializable
data class AchievementMemberRow(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val username: String = "",
    val displayName: String = "",
    /** Their account's display name, when they set one. */
    val globalName: String? = null,
    val avatarUrl: String? = null,
    val points: Int = 0,
    val unlocked: Int = 0,
    val tier: String = "",
    val tierGrade: Int? = null,
    @Serializable(with = InstantSerializer::class) val lastUnlockAt: Instant? = null,
    val rank: Int = 0,
    val inServer: Boolean = true,
)

/** A page of members. */
@Serializable
data class AchievementMembersResult(
    val total: Int = 0,
    val members: List<AchievementMemberRow> = emptyList(),
)

/** Where a member stands on one achievement. */
@Serializable
data class AchievementProgressItem(
    val key: String = "",
    @Serializable(with = InstantSerializer::class) val unlockedAt: Instant? = null,
    val current: Long? = null,
)

/** A badge. */
@Serializable
data class AchievementBadgeItem(
    val key: String = "",
    val name: String = "",
    val icon: String = "fa:trophy",
    val iconUrl: String? = null,
    val grade: Int = 0,
    val source: String = "",
    val short: String = "",
)

/** One member in detail. */
@Serializable
data class AchievementMemberDetail(
    val member: AchievementMemberRow = AchievementMemberRow(),
    val total: Int = 0,
    val progress: List<AchievementProgressItem> = emptyList(),
    val badges: List<AchievementBadgeItem> = emptyList(),
    val equipped: List<String?> = emptyList(),
)

/** The editable fields of an achievement, for both built in changes and server made achievements. */
data class AchievementDraft(
    val key: String? = null,
    val customId: Int? = null,
    val isBuiltIn: Boolean = false,
    val enabled: Boolean = true,
    val name: String = "",
    val description: String = "",
    /** Icon, null for the category's. */
    val icon: String? = null,
    val points: Int? = null,
    val hidden: Boolean = false,
    val categoryKey: String = "custom",
    val grade: Int = 0,
    val trigger: AchievementTriggerKind = AchievementTriggerKind.METRIC,
    val metric: Int = 1,
    val threshold: Long = 100,
    val keyword: String = "",
    val channelId: Snowflake? = null,
    val roleRewardId: Snowflake? = null,
    val currencyReward: Long = 0,
    val xpReward: Int = 0,
) {
    /** True for a server achievement that has not been saved. */
    val isNew: Boolean get() = key == null

    /** Whether the trigger watches messages or reactions. */
    val watchesText: Boolean get() = trigger == AchievementTriggerKind.KEYWORD || trigger == AchievementTriggerKind.REACTION

    companion object {
        /** A draft seeded from a stored achievement. */
        fun from(item: AchievementItem): AchievementDraft = AchievementDraft(
            key = item.key,
            customId = item.customId,
            isBuiltIn = !item.isCustom,
            enabled = item.selfEnabled,
            name = if (item.isCustom) item.name else item.rawName.orEmpty(),
            description = item.rawDescription.orEmpty(),
            icon = item.rawIcon,
            points = item.rawPoints,
            hidden = item.hidden,
            categoryKey = item.categoryKey,
            grade = item.grade,
            trigger = item.triggerKind,
            metric = if (item.metric == 0) 1 else item.metric,
            threshold = if (item.threshold == 0L) 100 else item.threshold,
            keyword = item.keyword.orEmpty(),
            channelId = item.channelId,
            roleRewardId = item.roleRewardId,
            currencyReward = item.currencyReward,
            xpReward = item.xpReward,
        )
    }
}

/** The editable announcement and server settings. */
data class AchievementSettingsDraft(
    val announceMode: AchievementAnnounceMode = AchievementAnnounceMode.AUTO,
    val logChannelId: Snowflake? = null,
    val dmByDefault: Boolean = false,
    val mentionUsers: Boolean = true,
    val message: EmbedMessage = EmbedMessage(),
    val xpPerPoint: Int = 0,
    val revealHidden: Boolean = false,
    val unlockImage: Boolean = true,
    val deleteAfter: Int = 5,
    val quietChannelIds: List<Snowflake> = emptyList(),
    val requireSendPermission: Boolean = true,
    val excludedRoleIds: List<Snowflake> = emptyList(),
    val excludedChannelIds: List<Snowflake> = emptyList(),
) {
    companion object {
        /** Settings seeded from the bot's. */
        fun from(settings: AchievementSettingsInfo) = AchievementSettingsDraft(
            announceMode = AchievementAnnounceMode.from(settings.announceMode),
            logChannelId = settings.logChannelId,
            dmByDefault = settings.dmByDefault,
            mentionUsers = settings.mentionUsers,
            message = EmbedMessage.parse(settings.unlockMessage),
            xpPerPoint = settings.xpPerPoint,
            revealHidden = settings.revealHidden,
            unlockImage = settings.unlockImage,
            deleteAfter = settings.deleteAfter,
            quietChannelIds = settings.quietChannelIds,
            requireSendPermission = settings.requireSendPermission,
            excludedRoleIds = settings.excludedRoleIds,
            excludedChannelIds = settings.excludedChannelIds,
        )
    }
}

/** Library filters. */
enum class AchievementFilter(val title: String) {
    ALL("All"), ON("Active"), OFF("Inactive"), CUSTOM("Made here"), CHANGED("Customized"), SECRET("Secret"), REWARDS("Has rewards")
}

/** Library orderings. */
enum class AchievementSort(val title: String) {
    ORDER("Category order"), NAME("Name"), POPULAR("Most unlocked"), RARE("Rarest"), POINTS("Most points")
}
