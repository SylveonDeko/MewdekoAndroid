package dev.mewdeko.mobile.feature.embed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.model.EmbedAuthor
import dev.mewdeko.mobile.core.model.EmbedField
import dev.mewdeko.mobile.core.model.EmbedFooter
import dev.mewdeko.mobile.core.model.EmbedSpec
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader

/** One category the template gallery can filter to. */
private data class TemplateCategory(val id: String, val label: String, val icon: ImageVector)

/** A ready-made embed a user can drop into the composer as a starting point. */
private data class EmbedTemplate(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val icon: ImageVector,
    val embed: EmbedSpec,
)

private val TemplateCategories = listOf(
    TemplateCategory("all", "All templates", Icons.Default.AutoAwesome),
    TemplateCategory("welcome", "Welcome", Icons.Default.Groups),
    TemplateCategory("announcements", "Announcements", Icons.Default.Campaign),
    TemplateCategory("rules", "Rules", Icons.Default.Shield),
    TemplateCategory("info", "Information", Icons.Default.Info),
    TemplateCategory("interactive", "Interactive", Icons.Default.Bolt),
    TemplateCategory("fun", "Fun", Icons.Default.EmojiEvents),
)

/** Mirrors the dashboard's TemplateGallery.svelte starter set. */
private val Templates = listOf(
    EmbedTemplate(
        id = "welcome-basic",
        name = "Basic Welcome",
        description = "Simple welcome message for new members",
        category = "welcome",
        icon = Icons.Default.Groups,
        embed = EmbedSpec(
            title = "Welcome to the Server! 👋",
            description = "Hey %user.mention%, welcome to **%server.name%**!\n\n" +
                "We're glad to have you here. Make sure to read our rules and introduce yourself!",
            color = "#5865F2",
            footer = EmbedFooter(text = "Member #%server.membercount%"),
        ),
    ),
    EmbedTemplate(
        id = "welcome-detailed",
        name = "Detailed Welcome",
        description = "Comprehensive welcome with server info",
        category = "welcome",
        icon = Icons.Default.WorkspacePremium,
        embed = EmbedSpec(
            title = "Welcome to %server.name%! ✨",
            description = "Hello %user.mention%! We're excited to have you join our community.",
            color = "#00D166",
            author = EmbedAuthor(name = "%user.name%", iconUrl = "%user.avatar%"),
            fields = listOf(
                EmbedField(
                    name = "📋 Read the Rules",
                    value = "Make sure to check out <#rules-channel> first!",
                    inline = true,
                ),
                EmbedField(
                    name = "💬 Get Started",
                    value = "Introduce yourself in <#introductions>!",
                    inline = true,
                ),
                EmbedField(
                    name = "🎯 Have Fun",
                    value = "Enjoy your stay and make new friends!",
                    inline = true,
                ),
            ),
            footer = EmbedFooter(text = "You are member #%server.membercount%"),
        ),
    ),
    EmbedTemplate(
        id = "announcement-basic",
        name = "Basic Announcement",
        description = "Simple server announcement",
        category = "announcements",
        icon = Icons.Default.Campaign,
        embed = EmbedSpec(
            title = "📢 Server Announcement",
            description = "Important information for all members of %server.name%.",
            color = "#FEE75C",
            footer = EmbedFooter(text = "Posted by %user.name%"),
        ),
    ),
    EmbedTemplate(
        id = "announcement-event",
        name = "Event Announcement",
        description = "Announce upcoming events",
        category = "announcements",
        icon = Icons.Default.Event,
        embed = EmbedSpec(
            title = "🎉 Upcoming Event!",
            description = "Join us for an exciting community event!",
            color = "#ED4245",
            fields = listOf(
                EmbedField(
                    name = "📅 Date & Time",
                    value = "Saturday, Dec 25th at 3:00 PM EST",
                ),
                EmbedField(
                    name = "📍 Location",
                    value = "Voice Channel: Community Events",
                    inline = true,
                ),
                EmbedField(
                    name = "🎁 Prizes",
                    value = "Special roles and Discord Nitro!",
                    inline = true,
                ),
            ),
            footer = EmbedFooter(text = "React with 🎉 to get notified!"),
        ),
    ),
    EmbedTemplate(
        id = "rules-basic",
        name = "Server Rules",
        description = "Basic server rules layout",
        category = "rules",
        icon = Icons.Default.Gavel,
        embed = EmbedSpec(
            title = "📜 Server Rules",
            description = "Please follow these rules to keep our community safe and fun for everyone.",
            color = "#57F287",
            fields = listOf(
                EmbedField(name = "1️⃣ Be Respectful", value = "Treat all members with kindness and respect."),
                EmbedField(name = "2️⃣ No Spam", value = "Keep messages relevant and avoid excessive posting."),
                EmbedField(name = "3️⃣ Keep it Clean", value = "No inappropriate content or language."),
            ),
            footer = EmbedFooter(text = "Breaking rules may result in warnings or bans"),
        ),
    ),
    EmbedTemplate(
        id = "info-basic",
        name = "Information Card",
        description = "General information layout",
        category = "info",
        icon = Icons.Default.Info,
        embed = EmbedSpec(
            title = "ℹ️ Information",
            description = "Here's some important information you should know.",
            color = "#3498DB",
            footer = EmbedFooter(text = "Last updated: %date%"),
        ),
    ),
    EmbedTemplate(
        id = "interactive-poll",
        name = "Poll Template",
        description = "Interactive poll with reactions",
        category = "interactive",
        icon = Icons.Default.Bolt,
        embed = EmbedSpec(
            title = "📊 Community Poll",
            description = "Vote on this important community decision!",
            color = "#9C59B6",
            fields = listOf(
                EmbedField(name = "✅ Option A", value = "React with ✅ for this choice", inline = true),
                EmbedField(name = "❌ Option B", value = "React with ❌ for this choice", inline = true),
            ),
            footer = EmbedFooter(text = "Poll ends in 24 hours"),
        ),
    ),
    EmbedTemplate(
        id = "fun-celebration",
        name = "Celebration",
        description = "Celebrate achievements and milestones",
        category = "fun",
        icon = Icons.Default.Celebration,
        embed = EmbedSpec(
            title = "🎉 Congratulations!",
            description = "Let's celebrate this amazing achievement!",
            color = "#F1C40F",
            author = EmbedAuthor(name = "Achievement Unlocked!"),
        ),
    ),
)

/**
 * Lets a user start from a pre-built embed instead of a blank one.
 *
 * Selecting a template replaces embed 1 (creating it if the message has none
 * yet) and switches to the Editor tab, matching the dashboard's behaviour.
 */
@Composable
fun TemplatesPanel(onApply: (EmbedSpec) -> Unit, onStartFromScratch: () -> Unit) {
    var category by remember { mutableStateOf("all") }
    val filtered = remember(category) {
        if (category == "all") Templates else Templates.filter { it.category == category }
    }

    SectionCard {
        SectionCardHeader("Templates", Icons.Default.AutoAwesome)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TemplateCategories.forEach { entry ->
                FilterChip(
                    selected = category == entry.id,
                    onClick = { category = entry.id },
                    label = { Text(entry.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(entry.icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
        OutlinedButton(onClick = onStartFromScratch, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Start from scratch", modifier = Modifier.padding(start = 8.dp))
        }
    }

    if (filtered.isEmpty()) {
        SectionCard { EmptyState(message = "No templates in this category.", icon = Icons.Default.AutoAwesome) }
        return
    }

    filtered.forEach { template ->
        Surface(
            onClick = { onApply(template.embed) },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(template.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(template.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        template.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
