package dev.mewdeko.mobile.core.model

import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.net.normalizeKeys
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.util.UUID

/** The `{ url: "..." }` wrapper Discord uses for thumbnail and image slots. */
@Serializable
data class UrlBox(val url: String = "")

/** The author block of an embed. */
@Serializable
data class EmbedAuthor(
    val name: String = "",
    val url: String = "",
    @SerialName("icon_url") val iconUrl: String = "",
) {
    /** Whether every author slot is blank. */
    val isEmpty: Boolean get() = name.isEmpty() && url.isEmpty() && iconUrl.isEmpty()
}

/** The footer block of an embed. */
@Serializable
data class EmbedFooter(
    val text: String = "",
    @SerialName("icon_url") val iconUrl: String = "",
) {
    /** Whether every footer slot is blank. */
    val isEmpty: Boolean get() = text.isEmpty() && iconUrl.isEmpty()
}

/** A name/value field within an embed. */
@Serializable
data class EmbedField(
    val name: String = "",
    val value: String = "",
    val inline: Boolean = false,
) {
    /** Stable local identity for list keying; not part of the wire form. */
    @kotlinx.serialization.Transient
    val localId: String = UUID.randomUUID().toString()
}

/** A single embed within an [EmbedMessage]. */
@Serializable
data class EmbedSpec(
    val title: String = "",
    val description: String = "",
    val url: String = "",
    val color: String = "",
    val author: EmbedAuthor = EmbedAuthor(),
    val footer: EmbedFooter = EmbedFooter(),
    val thumbnail: UrlBox? = null,
    val image: UrlBox? = null,
    val fields: List<EmbedField> = emptyList(),
) {
    /** Stable local identity for list keying; not part of the wire form. */
    @kotlinx.serialization.Transient
    val localId: String = UUID.randomUUID().toString()

    /** The thumbnail URL, flattened out of its wrapper. */
    val thumbnailUrl: String get() = thumbnail?.url.orEmpty()

    /** The main image URL, flattened out of its wrapper. */
    val imageUrl: String get() = image?.url.orEmpty()

    /** Whether every slot in this embed is blank. */
    val isEmpty: Boolean
        get() = title.isEmpty() && description.isEmpty() && url.isEmpty() &&
            author.isEmpty && footer.isEmpty && thumbnailUrl.isEmpty() &&
            imageUrl.isEmpty() && fields.isEmpty()

    /** Returns a copy with the thumbnail URL replaced. */
    fun withThumbnail(url: String) = copy(thumbnail = url.takeIf { it.isNotEmpty() }?.let(::UrlBox))

    /** Returns a copy with the main image URL replaced. */
    fun withImage(url: String) = copy(image = url.takeIf { it.isNotEmpty() }?.let(::UrlBox))

    /**
     * Serialises this embed, omitting blank slots so the bot's smart-embed
     * parser sees the same minimal shape the dashboard produces.
     */
    fun toJsonObject(): JsonObject = buildJsonObject {
        if (title.isNotEmpty()) put("title", JsonPrimitive(title))
        if (description.isNotEmpty()) put("description", JsonPrimitive(description))
        if (url.isNotEmpty()) put("url", JsonPrimitive(url))
        if (color.isNotEmpty()) put("color", JsonPrimitive(color))
        if (!author.isEmpty) put("author", MewdekoJson.encodeToJsonElement(EmbedAuthor.serializer(), author))
        if (!footer.isEmpty) put("footer", MewdekoJson.encodeToJsonElement(EmbedFooter.serializer(), footer))
        if (thumbnailUrl.isNotEmpty()) put("thumbnail", buildJsonObject { put("url", JsonPrimitive(thumbnailUrl)) })
        if (imageUrl.isNotEmpty()) put("image", buildJsonObject { put("url", JsonPrimitive(imageUrl)) })
        if (fields.isNotEmpty()) {
            put("fields", buildJsonArray {
                fields.forEach { add(MewdekoJson.encodeToJsonElement(EmbedField.serializer(), it)) }
            })
        }
    }

    companion object {
        /** An embed with every slot blank. */
        val Blank = EmbedSpec()

        /**
         * Decodes one embed, tolerating a `color` sent as either a hex string
         * or a packed integer.
         */
        fun fromJson(obj: JsonObject): EmbedSpec {
            val normalizedColor = obj["color"]?.let { element ->
                val primitive = element as? JsonPrimitive
                when {
                    primitive == null -> ""
                    primitive.isString -> primitive.content
                    else -> primitive.intOrNull
                        ?.let { "#%06X".format(it and 0xFFFFFF) }
                        .orEmpty()
                }
            }.orEmpty()

            val patched = buildJsonObject {
                obj.forEach { (key, value) -> if (key != "color") put(key, value) }
                if (normalizedColor.isNotEmpty()) put("color", JsonPrimitive(normalizedColor))
            }
            return runCatching {
                MewdekoJson.decodeFromJsonElement(serializer(), patched)
            }.getOrDefault(EmbedSpec())
        }
    }
}

/**
 * Top-level embed-message payload sent to and parsed from the bot's
 * smart-embed format.
 */
@Serializable
data class EmbedMessage(
    val content: String = "",
    val embeds: List<EmbedSpec> = emptyList(),
    val components: List<MessageComponent> = emptyList(),
) {
    /** Whether there is nothing to send at all. */
    val isEmpty: Boolean
        get() = content.isBlank() && embeds.isEmpty() && components.isEmpty()

    /** The components grouped into the action rows Discord renders them in. */
    val rows: List<List<MessageComponent>>
        get() = components.groupBy { it.row }.toSortedMap().values.toList()

    /**
     * Encodes back into the dashboard JSON shape. Returns `"-"` when empty,
     * matching the dashboard's reset-to-default convention, and plain text
     * when there are no embeds.
     */
    fun serialize(): String {
        if (isEmpty) return "-"
        if (embeds.isEmpty() && components.isEmpty()) return content
        val obj = buildJsonObject {
            put("content", JsonPrimitive(content))
            if (embeds.isNotEmpty()) {
                put("embeds", buildJsonArray { embeds.forEach { add(it.toJsonObject()) } })
            }
            if (components.isNotEmpty()) {
                put("components", buildJsonArray { components.forEach { add(it.toJsonObject()) } })
            }
        }
        return MewdekoJson.encodeToString(JsonObject.serializer(), obj)
    }

    companion object {
        /**
         * Decodes from the dashboard's JSON form, from a bare embed object, or
         * from a plain string for legacy plaintext values.
         */
        fun parse(raw: String?): EmbedMessage {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty() || trimmed == "-") return EmbedMessage()
            if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                return EmbedMessage(content = trimmed)
            }

            val root = runCatching {
                MewdekoJson.parseToJsonElement(trimmed).normalizeKeys()
            }.getOrNull() as? JsonObject ?: return EmbedMessage(content = trimmed)

            val content = (root["content"] as? JsonPrimitive)?.content.orEmpty()
            val embedsArray = root["embeds"]?.let { runCatching { it.jsonArray }.getOrNull() }
            val components = root["components"]
                ?.let { runCatching { it.jsonArray }.getOrNull() }
                ?.mapNotNull { element ->
                    runCatching { MessageComponent.fromJson(element.jsonObject) }.getOrNull()
                }
                .orEmpty()

            return when {
                embedsArray != null -> EmbedMessage(
                    content = content,
                    embeds = embedsArray.mapNotNull { element ->
                        runCatching { EmbedSpec.fromJson(element.jsonObject) }.getOrNull()
                    },
                    components = components,
                )

                root.containsKey("title") || root.containsKey("description") -> EmbedMessage(
                    content = content,
                    embeds = listOf(EmbedSpec.fromJson(root)),
                    components = components,
                )

                else -> EmbedMessage(content = content, components = components)
            }
        }
    }
}

/** One choice on a select-menu component. */
@Serializable
data class ComponentOption(
    val id: String? = null,
    val name: String = "",
    val emoji: String = "",
    val description: String = "",
)

/**
 * A button or select menu attached to a message.
 *
 * The bot stores components flat with a `row` index rather than nested in
 * action rows, so [EmbedMessage.rows] regroups them for display.
 */
@Serializable
data class MessageComponent(
    val id: String? = null,
    val row: Int = 0,
    val displayName: String = "",
    val style: Int = 1,
    val url: String = "",
    val emoji: String = "",
    val isSelect: Boolean = false,
    val minOptions: Int = 1,
    val maxOptions: Int = 1,
    val options: List<ComponentOption> = emptyList(),
) {
    /** Whether this is a link button, which carries a URL instead of an id. */
    val isLink: Boolean get() = !isSelect && style == LinkStyle

    /**
     * Whether Discord would accept this component.
     *
     * A button needs a label; a link button also needs a URL; a select needs
     * at least one fully described option.
     */
    val isValid: Boolean
        get() = when {
            isSelect -> options.isNotEmpty() &&
                options.all { it.name.isNotBlank() && it.description.isNotBlank() }

            displayName.isBlank() -> false
            isLink -> url.isNotBlank()
            else -> true
        }

    /** Encodes into the flat shape the bot stores, omitting irrelevant fields. */
    fun toJsonObject(): JsonObject = buildJsonObject {
        put("row", JsonPrimitive(row))
        put("displayName", JsonPrimitive(displayName))
        put("style", JsonPrimitive(style))
        put("isSelect", JsonPrimitive(isSelect))
        id?.takeIf { it.isNotBlank() }?.let { put("id", JsonPrimitive(it)) }
        if (isLink && url.isNotBlank()) put("url", JsonPrimitive(url))
        if (emoji.isNotBlank()) put("emoji", JsonPrimitive(emoji))
        if (isSelect) {
            put("minOptions", JsonPrimitive(minOptions))
            put("maxOptions", JsonPrimitive(maxOptions))
            if (options.isNotEmpty()) {
                put("options", buildJsonArray {
                    options.forEach { option ->
                        add(buildJsonObject {
                            put("name", JsonPrimitive(option.name))
                            option.id?.takeIf { it.isNotBlank() }
                                ?.let { put("id", JsonPrimitive(it)) }
                            if (option.emoji.isNotBlank()) {
                                put("emoji", JsonPrimitive(option.emoji))
                            }
                            if (option.description.isNotBlank()) {
                                put("description", JsonPrimitive(option.description))
                            }
                        })
                    }
                })
            }
        }
    }

    companion object {
        /** Discord's link button style, which swaps the custom id for a URL. */
        const val LinkStyle = 5

        /** The most action rows a message may carry. */
        const val MaxRows = 5

        /** The most buttons one row may carry. */
        const val MaxButtonsPerRow = 5

        /** The most options one select menu may offer. */
        const val MaxOptions = 25

        /** Decodes one component from the bot's flat representation. */
        fun fromJson(obj: JsonObject): MessageComponent {
            fun text(key: String) = (obj[key] as? JsonPrimitive)?.content.orEmpty()
            fun int(key: String, fallback: Int) =
                (obj[key] as? JsonPrimitive)?.content?.toIntOrNull() ?: fallback
            fun flag(key: String) =
                (obj[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false

            return MessageComponent(
                id = text("id").takeIf { it.isNotBlank() },
                row = int("row", 0),
                displayName = text("displayName"),
                style = int("style", 1),
                url = text("url"),
                emoji = text("emoji"),
                isSelect = flag("isSelect"),
                minOptions = int("minOptions", 1),
                maxOptions = int("maxOptions", 1),
                options = obj["options"]
                    ?.let { runCatching { it.jsonArray }.getOrNull() }
                    ?.mapNotNull { element ->
                        val option = element as? JsonObject ?: return@mapNotNull null
                        fun field(key: String) =
                            (option[key] as? JsonPrimitive)?.content.orEmpty()
                        ComponentOption(
                            id = field("id").takeIf { it.isNotBlank() },
                            name = field("name"),
                            emoji = field("emoji"),
                            description = field("description"),
                        )
                    }
                    .orEmpty(),
            )
        }
    }
}
