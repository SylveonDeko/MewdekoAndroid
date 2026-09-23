package dev.mewdeko.mobile.feature.featurerequests

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.ui.graphics.vector.ImageVector
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/** A feature request as returned by `FeatureRequestsController`. */
@Serializable
data class FeatureRequestEntry(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val userName: String = "",
    @Serializable(with = SnowflakeSerializer::class) val guildId: Snowflake? = null,
    val guildName: String? = null,
    val category: String = "feature",
    val title: String = "",
    val body: String = "",
    val status: String = "open",
    val ownerNote: String? = null,
    val votes: Int = 0,
    val voted: Boolean = false,
    val mine: Boolean = false,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
)

/** One page of requests from `GET FeatureRequests`. */
@Serializable
data class FeatureRequestPage(
    val items: List<FeatureRequestEntry> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 25,
)

/** Counts by status and category from `GET FeatureRequests/stats` (bot owner only). */
@Serializable
data class FeatureRequestStats(
    val total: Int = 0,
    val byStatus: Map<String, Int> = emptyMap(),
    val byCategory: Map<String, Int> = emptyMap(),
)

/** Bot wide report channel settings from `GET FeatureRequests/settings` (bot owner only). */
@Serializable
data class FeatureRequestSettings(
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "0",
    @Serializable(with = SnowflakeSerializer::class) val effectiveChannelId: Snowflake = "0",
    val usingFallback: Boolean = false,
    val channelName: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val guildId: Snowflake = "0",
    val guildName: String? = null,
    val reachable: Boolean = false,
) {
    /** The configured channel as editable text, blank when the fallback is in use. */
    val channelInputText: String
        get() = channelId.takeIf { it.isNotBlank() && it != "0" }.orEmpty()

    /** Whether no channel resolves at all, so requests are only stored. */
    val hasNoEffectiveChannel: Boolean
        get() = effectiveChannelId.isBlank() || effectiveChannelId == "0"
}

/** The vote result from `POST FeatureRequests/{id}/vote`. */
@Serializable
data class FeatureRequestVoteResult(
    val votes: Int = 0,
    val voted: Boolean = false,
)

/** The request lifecycle states the bot accepts. */
enum class FeatureRequestStatus(val key: String, val label: String) {
    OPEN("open", "Open"),
    PLANNED("planned", "Planned"),
    DONE("done", "Done"),
    DECLINED("declined", "Declined");

    companion object {
        /** The label for a status key, falling back to the raw key. */
        fun labelFor(key: String): String = entries.firstOrNull { it.key == key }?.label ?: key
    }
}

/** The request categories the bot accepts. */
enum class FeatureRequestCategory(val key: String, val label: String, val icon: ImageVector) {
    FEATURE("feature", "Feature idea", Icons.Default.Lightbulb),
    BUG("bug", "Bug report", Icons.Default.BugReport),
    OTHER("other", "Something else", Icons.Default.Forum);

    companion object {
        /** The category for a key, defaulting to [FEATURE]. */
        fun of(key: String): FeatureRequestCategory = entries.firstOrNull { it.key == key } ?: FEATURE
    }
}

/** The in-progress contents of the Suggest form. */
data class FeatureRequestDraft(
    val category: String = FeatureRequestCategory.FEATURE.key,
    val title: String = "",
    val body: String = "",
    val attachGuild: Boolean = true,
) {
    /** Characters left before the title limit. */
    val titleRemaining: Int get() = TITLE_MAX - title.length

    /** Characters left before the body limit. */
    val bodyRemaining: Int get() = BODY_MAX - body.length

    /** Whether the title and body meet the bot's length rules. */
    val isValid: Boolean
        get() = title.trim().length >= TITLE_MIN &&
            body.trim().length >= BODY_MIN &&
            titleRemaining >= 0 &&
            bodyRemaining >= 0

    companion object {
        /** Shortest accepted title. */
        const val TITLE_MIN = 3

        /** Longest accepted title. */
        const val TITLE_MAX = 120

        /** Shortest accepted body. */
        const val BODY_MIN = 10

        /** Longest accepted body. */
        const val BODY_MAX = 2000

        /** Longest owner note the dashboard allows. */
        const val NOTE_MAX = 1000
    }
}
