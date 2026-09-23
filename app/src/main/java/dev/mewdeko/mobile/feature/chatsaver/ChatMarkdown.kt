package dev.mewdeko.mobile.feature.chatsaver

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import dev.mewdeko.mobile.core.model.GuildMember
import dev.mewdeko.mobile.core.model.TextChannelLite
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Matches one styled token at a time: code, emphasis, mentions, a `[text](url)` markdown
 * link, or a bare link.
 */
private val MessageTokenRegex = Regex(
    "```(?:\\w+\\n)?([\\s\\S]*?)```" +
        "|`([^`]+)`" +
        "|\\*\\*([^*]+)\\*\\*" +
        "|~~([^~]+)~~" +
        "|\\*([^*]+)\\*" +
        "|_([^_]+)_" +
        "|<@!?(\\d+)>" +
        "|<#(\\d+)>" +
        "|<@&(\\d+)>" +
        "|\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)" +
        "|(https?://\\S+)",
)

/**
 * Renders message content as a lightly styled [AnnotatedString]: bold, italic,
 * strikethrough, inline code, links, and user/channel/role mentions resolved
 * against the guild's member and channel lists.
 */
@Composable
fun rememberChatMessageText(
    content: String,
    members: List<GuildMember>,
    channels: List<TextChannelLite>,
): AnnotatedString {
    val mentionColor = MaterialTheme.colorScheme.primary
    val mentionBackground = mentionColor.copy(alpha = 0.15f)
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    return remember(content, members, channels, mentionColor, codeBackground) {
        buildAnnotatedString {
            var lastIndex = 0
            for (match in MessageTokenRegex.findAll(content)) {
                if (match.range.first > lastIndex) {
                    append(content.substring(lastIndex, match.range.first))
                }
                val g = match.groupValues
                when {
                    g[1].isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                        append(g[1].trim())
                    }

                    g[2].isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                        append(g[2])
                    }

                    g[3].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[3]) }
                    g[4].isNotEmpty() -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(g[4]) }
                    g[5].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[5]) }
                    g[6].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[6]) }
                    g[7].isNotEmpty() -> {
                        val name = members.firstOrNull { it.id == g[7] }?.username ?: "Unknown User"
                        withStyle(SpanStyle(color = mentionColor, background = mentionBackground)) { append("@$name") }
                    }

                    g[8].isNotEmpty() -> {
                        val name = channels.firstOrNull { it.id == g[8] }?.name ?: "unknown-channel"
                        withStyle(SpanStyle(color = mentionColor, background = mentionBackground)) { append("#$name") }
                    }

                    g[9].isNotEmpty() -> withStyle(SpanStyle(color = mentionColor, background = mentionBackground)) {
                        append("@Role")
                    }

                    g[10].isNotEmpty() -> withLink(
                        LinkAnnotation.Url(
                            g[11],
                            TextLinkStyles(SpanStyle(color = mentionColor, textDecoration = TextDecoration.Underline)),
                        ),
                    ) {
                        append(g[10])
                    }

                    g[12].isNotEmpty() -> withLink(
                        LinkAnnotation.Url(
                            g[12],
                            TextLinkStyles(SpanStyle(color = mentionColor, textDecoration = TextDecoration.Underline)),
                        ),
                    ) {
                        append(g[12])
                    }
                }
                lastIndex = match.range.last + 1
            }
            if (lastIndex < content.length) append(content.substring(lastIndex))
        }
    }
}

/** Whether an attachment or embed URL points at a renderable image. */
fun isImageUrl(url: String): Boolean =
    Regex(".*\\.(jpeg|jpg|gif|png|webp)$", RegexOption.IGNORE_CASE).matches(url.substringBefore('?'))

/** Formats a byte count the way the dashboard does: `B`, `KB`, or `MB`. */
fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

/**
 * Splits messages into consecutive runs from the same author, breaking the run
 * whenever more than five minutes pass between messages. Mirrors the
 * dashboard's `groupMessagesByAuthor`.
 */
fun groupMessagesByAuthor(messages: List<ChatLogMessage>): List<List<ChatLogMessage>> {
    if (messages.isEmpty()) return emptyList()
    val maxGap = Duration.ofMinutes(5)
    val result = mutableListOf<MutableList<ChatLogMessage>>()
    var current = mutableListOf(messages.first())
    for (i in 1 until messages.size) {
        val message = messages[i]
        val previous = messages[i - 1]
        val sameAuthor = message.author.id == previous.author.id
        val gap = Duration.between(previous.timestamp, message.timestamp).abs()
        if (sameAuthor && gap <= maxGap) {
            current.add(message)
        } else {
            result.add(current)
            current = mutableListOf(message)
        }
    }
    result.add(current)
    return result
}

/** Groups messages by local calendar day, ordered chronologically. */
fun groupMessagesByDay(messages: List<ChatLogMessage>): List<Pair<LocalDate, List<ChatLogMessage>>> {
    val zone = ZoneId.systemDefault()
    return messages
        .sortedBy { it.timestamp }
        .groupBy { it.timestamp.atZone(zone).toLocalDate() }
        .toSortedMap()
        .map { (date, msgs) -> date to msgs }
}

private fun escapeHtml(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#039;")

private fun renderMarkdownHtml(raw: String, members: List<GuildMember>, channels: List<TextChannelLite>): String {
    var text = escapeHtml(raw)
    text = Regex("\\[([^\\]]+)]\\(([^)]+)\\)").replace(text) { m ->
        "<a href=\"${m.groupValues[2]}\" target=\"_blank\">${m.groupValues[1]}</a>"
    }
    text = Regex("\\*\\*([^*]+)\\*\\*").replace(text) { "<strong>${it.groupValues[1]}</strong>" }
    text = Regex("\\*([^*]+)\\*").replace(text) { "<em>${it.groupValues[1]}</em>" }
    text = Regex("_([^_]+)_").replace(text) { "<em>${it.groupValues[1]}</em>" }
    text = Regex("~~([^~]+)~~").replace(text) { "<del>${it.groupValues[1]}</del>" }
    text = Regex("```(?:(\\w+)\\n)?([^`]+)```").replace(text) { m ->
        "<pre class=\"code-block\"><code>${m.groupValues[2]}</code></pre>"
    }
    text = Regex("`([^`]+)`").replace(text) { "<code>${it.groupValues[1]}</code>" }
    text = Regex("<@!?(\\d+)>").replace(text) { m ->
        val name = members.firstOrNull { it.id == m.groupValues[1] }?.username ?: "Unknown User"
        "<span class=\"mention user\">@${escapeHtml(name)}</span>"
    }
    text = Regex("<#(\\d+)>").replace(text) { m ->
        val name = channels.firstOrNull { it.id == m.groupValues[1] }?.name ?: "unknown-channel"
        "<span class=\"mention channel\">#${escapeHtml(name)}</span>"
    }
    text = Regex("<@&(\\d+)>").replace(text) { "<span class=\"mention role\">@Role</span>" }
    text = Regex("(https?://[^\\s<]+)").replace(text) { "<a href=\"${it.groupValues[1]}\" target=\"_blank\">${it.groupValues[1]}</a>" }
    return text.replace("\n", "<br>")
}

/**
 * Builds a standalone HTML transcript, styled like a Discord channel, for the
 * export/share action. Mirrors the dashboard's client-side generator.
 */
fun buildChatTranscriptHtml(
    guildName: String,
    channelName: String,
    messages: List<ChatLogMessage>,
    members: List<GuildMember>,
    channels: List<TextChannelLite>,
): String {
    val body = StringBuilder()
    for ((date, dayMessages) in groupMessagesByDay(messages)) {
        body.append(
            "<div class=\"day-divider\"><span>${
                date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))
            }</span></div>\n",
        )
        for (group in groupMessagesByAuthor(dayMessages)) {
            val first = group.first()
            val zone = ZoneId.systemDefault()
            val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
            body.append("<div class=\"message\">\n")
            body.append("<img class=\"avatar\" src=\"${first.author.avatarUrl.orEmpty()}\" alt=\"${escapeHtml(first.author.username)}\">\n")
            body.append("<div class=\"message-content\">\n")
            body.append("<div class=\"message-header\">")
            body.append("<span class=\"username\">${escapeHtml(first.author.username)}</span>")
            body.append("<span class=\"timestamp\">${timeFormatter.format(first.timestamp.atZone(zone))}</span>")
            body.append("</div>\n")
            for ((index, message) in group.withIndex()) {
                if (index > 0) {
                    body.append(
                        "<div class=\"timestamp\" style=\"margin-top:8px;margin-bottom:2px;\">${
                            timeFormatter.format(message.timestamp.atZone(zone))
                        }</div>\n",
                    )
                }
                message.content?.takeIf { it.isNotBlank() }?.let {
                    body.append("<div class=\"content\">${renderMarkdownHtml(it, members, channels)}</div>\n")
                }
                if (message.attachments.isNotEmpty()) {
                    body.append("<div class=\"attachments\">\n")
                    message.attachments.forEach { attachment ->
                        if (isImageUrl(attachment.url)) {
                            body.append(
                                "<div class=\"image-attachment\"><a href=\"${attachment.url}\" target=\"_blank\">" +
                                    "<img src=\"${attachment.proxyUrl.ifBlank { attachment.url }}\" alt=\"${escapeHtml(attachment.filename)}\"></a></div>\n",
                            )
                        } else {
                            body.append(
                                "<div class=\"file-attachment\"><a href=\"${attachment.url}\" target=\"_blank\">" +
                                    "${escapeHtml(attachment.filename)} (${formatFileSize(attachment.fileSize)})</a></div>\n",
                            )
                        }
                    }
                    body.append("</div>\n")
                }
                if (message.embeds.isNotEmpty()) {
                    body.append("<div class=\"embeds\">\n")
                    message.embeds.forEach { embed ->
                        body.append("<div class=\"embed\">\n")
                        embed.thumbnail?.let {
                            body.append("<div class=\"embed-thumbnail\"><img src=\"$it\" alt=\"Thumbnail\"></div>\n")
                        }
                        embed.author?.let { author ->
                            body.append("<div class=\"embed-author\">")
                            author.iconUrl?.let { body.append("<img src=\"$it\" alt=\"Author icon\">") }
                            body.append("<span>${escapeHtml(author.name)}</span></div>\n")
                        }
                        embed.title?.let { title ->
                            val rendered = renderMarkdownHtml(title, members, channels)
                            body.append(
                                if (embed.url != null) {
                                    "<div class=\"embed-title\"><a href=\"${embed.url}\" target=\"_blank\">$rendered</a></div>\n"
                                } else {
                                    "<div class=\"embed-title\">$rendered</div>\n"
                                },
                            )
                        }
                        embed.description?.let {
                            body.append("<div class=\"embed-description\">${renderMarkdownHtml(it, members, channels)}</div>\n")
                        }
                        body.append("</div>\n")
                    }
                    body.append("</div>\n")
                }
                if (index < group.size - 1) {
                    body.append("<hr style=\"border:0;border-top:1px dashed #4f545c;margin:8px 0;\">\n")
                }
            }
            body.append("</div>\n</div>\n")
        }
    }

    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Chat Log - #$channelName - $guildName</title>
        <style>
        body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; background-color: #36393f; color: #fff; margin: 0; padding: 0; }
        header { background-color: rgba(0,0,0,0.3); padding: 20px; text-align: center; border-bottom: 1px solid #4f545c; }
        h1 { margin: 0; color: #5865f2; }
        .guild-name { font-size: 18px; margin-top: 5px; }
        header .timestamp { color: #b9bbbe; margin-top: 10px; font-size: 14px; }
        .messages { padding: 20px; max-width: 1200px; margin: 0 auto; }
        .day-divider { text-align: center; margin: 30px 0 15px; color: #b9bbbe; font-size: 12px; }
        .message { display: flex; margin-bottom: 16px; }
        .avatar { width: 40px; height: 40px; border-radius: 50%; margin-right: 16px; }
        .message-content { flex: 1; }
        .message-header { display: flex; align-items: center; margin-bottom: 4px; }
        .username { font-weight: bold; margin-right: 8px; }
        .timestamp { color: #b9bbbe; font-size: 12px; }
        .content { word-wrap: break-word; white-space: pre-wrap; }
        .image-attachment img { max-width: 400px; max-height: 300px; border-radius: 4px; margin-top: 4px; }
        .file-attachment { background-color: rgba(0,0,0,0.2); padding: 8px 12px; border-radius: 4px; display: inline-block; margin-top: 4px; }
        .file-attachment a { color: #5865f2; text-decoration: none; }
        .embed { border-left: 4px solid #5865f2; background-color: rgba(0,0,0,0.2); padding: 8px 12px; border-radius: 0 4px 4px 0; margin-top: 4px; }
        .embed-title { font-weight: bold; margin-bottom: 4px; }
        .embed-title a { color: #5865f2; text-decoration: none; }
        .embed-author { display: flex; align-items: center; margin-bottom: 8px; }
        .embed-author img { width: 24px; height: 24px; border-radius: 50%; margin-right: 8px; }
        .embed-description { word-wrap: break-word; white-space: pre-wrap; margin-bottom: 8px; }
        .embed-thumbnail img { max-width: 80px; max-height: 80px; border-radius: 4px; float: right; margin-left: 10px; }
        .mention { background-color: rgba(88,101,242,0.3); color: #c9cdfb; border-radius: 3px; padding: 0 2px; }
        .mention.channel { color: #8e9297; }
        .code-block { background-color: #2f3136; border-radius: 3px; padding: 8px; font-family: Consolas, monospace; overflow-x: auto; }
        code { background-color: #2f3136; border-radius: 3px; padding: 0 4px; font-family: Consolas, monospace; }
        footer { text-align: center; padding: 20px; color: #b9bbbe; border-top: 1px solid #4f545c; font-size: 12px; }
        </style>
        </head>
        <body>
        <header>
        <h1>#$channelName</h1>
        <div class="guild-name">$guildName</div>
        <div class="timestamp">${messages.size} messages</div>
        </header>
        <main class="messages">
        $body
        </main>
        <footer><p>Generated by Mewdeko</p></footer>
        </body>
        </html>
    """.trimIndent()
}
