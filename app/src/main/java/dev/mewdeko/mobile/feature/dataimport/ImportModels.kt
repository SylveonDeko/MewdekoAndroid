package dev.mewdeko.mobile.feature.dataimport

import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.ui.FaGlyph
import kotlinx.serialization.Serializable

/** The bots and file formats data can be imported from. [raw] matches the bot's `ImportSource`. */
enum class ImportSource(
    val raw: Int,
    val label: String,
    val glyph: FaGlyph,
    val input: Input,
    val brings: String,
    val how: String,
    val linkLabel: String? = null,
    val linkUrl: String? = null,
) {
    MEE6(
        0, "MEE6", FaGlyph.Crown, Input.NONE, "XP, levels and level roles",
        "Make the leaderboard public in MEE6's dashboard (Leveling, Leaderboard), then read it here. Nothing else is needed.",
    ),
    MEE6_SETTINGS(
        8, "MEE6 settings", FaGlyph.Sliders, Input.FILE,
        "Welcome messages, level settings, custom commands, reaction roles, filters, Twitch alerts and the shop",
        "On a computer, open the Import page on Mewdeko's dashboard and pick MEE6 settings to get the export script. Run it on your server's MEE6 dashboard, then pick the file it downloads here.",
    ),
    LURKR(
        1, "Lurkr", FaGlyph.ChartSimple, Input.FILE, "XP and levels",
        "Export your levels from Lurkr and pick the JSON file it gives you.",
    ),
    POLARIS(
        2, "Polaris", FaGlyph.Star, Input.FILE, "XP, plus level roles and your curve with the Everything export",
        "On Polaris' website, open your server and download your XP. Any format works. Pick Everything to bring level roles too.",
    ),
    ARCANE(
        3, "Arcane", FaGlyph.WandMagicSparkles, Input.FILE, "Levels",
        "Arcane has no export. On a computer, open your server's leaderboard on arcane.bot, load every member, run the export script in the browser console and pick the JSON it saves.",
        "Export script", "https://gist.github.com/SomeAspy/2b27a6d66b97db6bd1b62afac5343285",
    ),
    AMARI(
        4, "Amari", FaGlyph.Trophy, Input.KEY, "XP and levels",
        "Apply for an Amari API key. Amari sends it to you in a DM, usually within a day. Paste it below.",
        "Get a key", "https://amaribot.com/developer",
    ),
    TATSU(
        5, "Tatsu", FaGlyph.Sparkles, Input.KEY, "XP",
        "Run t!apikey create in any channel. Tatsu sends you a key in a DM. Paste it below.",
    ),
    UNBELIEVABOAT(
        7, "UnbelievaBoat", FaGlyph.MoneyBill, Input.KEY, "Cash and bank balances",
        "Create an application on UnbelievaBoat's site, authorize it on this server from the application's page, then paste its token below.",
        "Applications", "https://unbelievaboat.com/applications",
    ),
    FILE(
        6, "Other file", FaGlyph.File, Input.FILE, "XP, levels or balances",
        "Any JSON or CSV with a user ID for each member and an XP, level, cash or bank value.",
    );

    /** How a source's data is handed over. */
    enum class Input { NONE, FILE, KEY }

    companion object {
        /** Maps a wire value onto a source. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw }
    }
}

/** How imported values combine with what members already have. [raw] matches the bot's `ImportMergeMode`. */
enum class ImportMergeMode(val raw: Int, val label: String) {
    REPLACE(0, "Replace what members have"),
    KEEP_HIGHER(1, "Keep whichever is higher"),
    ADD(2, "Add on top"),
}

/** One member in an import preview. */
@Serializable
data class ImportPreviewMember(
    val userId: Snowflake = "",
    val name: String? = null,
    val avatarUrl: String? = null,
    val xp: Long? = null,
    val level: Int? = null,
    val cash: Long? = null,
    val bank: Long? = null,
)

/** A level role found in an import. */
@Serializable
data class ImportPreviewReward(
    val level: Int = 0,
    val roleId: Snowflake = "",
    val roleName: String? = null,
    val exists: Boolean = false,
)

/** A job's progress, and what it would write once the data is read. Status: 0 reading, 1 ready, 2 failed. */
@Serializable
data class ImportPreview(
    val jobId: String = "",
    val status: Int = 0,
    val progress: Int = 0,
    val error: String? = null,
    val source: Int = 0,
    val kind: Int = 0,
    val memberCount: Int = 0,
    val existingCount: Int = 0,
    val top: List<ImportPreviewMember> = emptyList(),
    val roleRewards: List<ImportPreviewReward> = emptyList(),
    val nativeCurve: Int? = null,
    val currentCurve: Int = 0,
    val sections: List<ImportSettingsSection> = emptyList(),
) {
    val isReading: Boolean get() = status == 0
    val isFailed: Boolean get() = status == 2
    val isCurrency: Boolean get() = kind == 1
    val isSettings: Boolean get() = kind == 2
}

/** One section of a settings import, with what it would write. */
@Serializable
data class ImportSettingsSection(
    val key: String = "",
    val count: Int = 0,
    val details: List<String> = emptyList(),
) {
    /** The section's name as shown on the dashboard. */
    val title: String get() = sectionTitle(key)

    companion object {
        /** Section keys in the order the bot applies them. */
        val ORDER = listOf("welcome", "levels", "birthdays", "commands", "reactionRoles", "automod", "twitch", "economy")

        /** The name of a section key. */
        fun sectionTitle(key: String) = when (key) {
            "welcome" -> "Welcome and goodbye"
            "levels" -> "Levels"
            "birthdays" -> "Birthdays"
            "commands" -> "Custom commands"
            "reactionRoles" -> "Reaction roles"
            "automod" -> "Auto-moderation"
            "twitch" -> "Twitch alerts"
            "economy" -> "Economy"
            else -> key
        }
    }
}

/** What one settings section wrote. */
@Serializable
data class ImportSettingsSectionResult(
    val section: String = "",
    val written: Int = 0,
    val skipped: Int = 0,
    val failed: Boolean = false,
)

/** The outcome of a settings import. */
@Serializable
data class ImportSettingsResult(
    val importId: Int = 0,
    val sections: List<ImportSettingsSectionResult> = emptyList(),
)

/** The outcome of writing an import. */
@Serializable
data class ImportResult(
    val importId: Int = 0,
    val members: Int = 0,
    val skipped: Int = 0,
    val roleRewards: Int = 0,
    val curveChanged: Int? = null,
)

/** A past import. */
@Serializable
data class ImportHistoryEntry(
    val id: Int = 0,
    val source: Int = 0,
    val kind: Int = 0,
    val memberCount: Int = 0,
    val roleRewardCount: Int = 0,
    val dateAdded: String = "",
    val undoneAt: String? = null,
    val canUndo: Boolean = false,
)
