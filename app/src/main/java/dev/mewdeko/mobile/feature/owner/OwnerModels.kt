package dev.mewdeko.mobile.feature.owner

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.vector.ImageVector
import dev.mewdeko.mobile.navigation.Routes

/**
 * One owner tool, mirroring an entry of the dashboard's `ownerFeatures` list:
 * the label, description, and route are the dashboard's, and the icon is the
 * closest Material glyph to its Font Awesome one.
 */
enum class OwnerPage(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val route: String,
) {
    /** `/owner/docker`, `fa-box`. */
    Docker(
        label = "Docker",
        description = "Containers and compose projects on the bot's host",
        icon = Icons.Default.Inventory2,
        route = Routes.OWNER_DOCKER,
    ),

    /** `/owner/bot-hells`, `fa-robot`. */
    BotHells(
        label = "Bot Hells",
        description = "Servers littered with bots, with bulk leave",
        icon = Icons.Default.SmartToy,
        route = Routes.OWNER_BOT_HELLS,
    ),

    /** `/owner/leave-feedback`, `fa-comments`. */
    LeaveFeedback(
        label = "Leave Feedback",
        description = "Why servers removed the bot, answered by their owners",
        icon = Icons.Default.Forum,
        route = Routes.OWNER_LEAVE_FEEDBACK,
    ),

    /** `/owner/analytics`, `fa-chart-simple`. */
    Analytics(
        label = "Analytics",
        description = "Fleet telemetry, commands, events, errors, growth and alerts",
        icon = Icons.Default.BarChart,
        route = Routes.OWNER_ANALYTICS,
    ),

    /** `/owner/performance`, `fa-clock`. Opens the existing performance screen. */
    Performance(
        label = "Performance",
        description = "Bot performance metrics",
        icon = Icons.Default.Schedule,
        route = Routes.OWNER_PERFORMANCE,
    ),

    /** `/owner/process-logs`, `fa-rectangle-code`. */
    ProcessLogs(
        label = "Process Logs",
        description = "Read and follow the pm2 logs on the bot's host",
        icon = Icons.Default.Terminal,
        route = Routes.OWNER_PROCESS_LOGS,
    ),
    ;

    companion object {
        /** The panel's order: alphabetical by label, as the dashboard sorts its cards. */
        val panelOrder: List<OwnerPage> = entries.sortedBy { it.label.lowercase() }
    }
}
