package dev.mewdeko.mobile.feature.patreon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.theme.MonospaceStyle
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader

/** One `%...%` placeholder the announcement message can reference. */
private data class PatreonPlaceholder(val name: String, val description: String)

/**
 * Mirrors the dashboard's `additionalPlaceholders` list on the Patreon
 * announcement message field.
 *
 * [EmbedMessageEditor][dev.mewdeko.mobile.feature.embed.EmbedMessageEditor] only
 * exposes the global placeholder catalog, so this is shown as a read-only
 * reference card alongside it rather than wired into its picker sheet.
 */
private val PatreonPlaceholders = listOf(
    PatreonPlaceholder("%month%", "Current month name"),
    PatreonPlaceholder("%year%", "Current year"),
    PatreonPlaceholder("%patreon.link%", "Patreon link"),
    PatreonPlaceholder("%patron.name%", "Highest pledging supporter's name"),
    PatreonPlaceholder("%patron.tier%", "Highest pledging supporter's tier"),
    PatreonPlaceholder("%patron.amount%", "Highest pledging supporter's monthly pledge"),
    PatreonPlaceholder("%supporter.count%", "Active supporters"),
    PatreonPlaceholder("%supporter.total%", "All supporters ever recorded"),
    PatreonPlaceholder("%supporter.new%", "New supporters this month"),
    PatreonPlaceholder("%supporter.former%", "Former supporters"),
    PatreonPlaceholder("%supporter.linked%", "Supporters with a linked Discord account"),
    PatreonPlaceholder("%revenue.monthly%", "Current monthly pledges"),
    PatreonPlaceholder("%revenue.average%", "Average pledge"),
    PatreonPlaceholder("%revenue.lifetime%", "Lifetime revenue"),
    PatreonPlaceholder("%tiers.count%", "Number of tiers"),
    PatreonPlaceholder("%tier.popular%", "Tier with the most supporters"),
    PatreonPlaceholder("%tier.popular.count%", "How many are on the most popular tier"),
    PatreonPlaceholder("%supporters.summary%", "Ready made sentence about supporter count"),
    PatreonPlaceholder("%revenue.summary%", "Ready made sentence about revenue"),
    PatreonPlaceholder("%growth.summary%", "Ready made sentence about growth"),
)

/** Read-only reference card listing every Patreon-specific announcement placeholder. */
@Composable
fun PatreonPlaceholderCard() {
    SectionCard {
        SectionCardHeader("Patreon placeholders", Icons.Default.DataObject)
        Text(
            text = "Type these into the announcement message; the bot fills them in when it posts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PatreonPlaceholders.forEach { entry ->
                Column {
                    Text(entry.name, style = MonospaceStyle)
                    Text(
                        entry.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
