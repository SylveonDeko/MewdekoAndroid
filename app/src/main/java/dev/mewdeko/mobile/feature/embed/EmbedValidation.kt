package dev.mewdeko.mobile.feature.embed

import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.model.MessageComponent

/** How serious a validation finding is. */
enum class IssueLevel { ERROR, WARNING }

/** One thing wrong, or nearly wrong, with the composed message. */
data class ValidationIssue(val level: IssueLevel, val message: String)

/**
 * Discord's documented limits for a message payload.
 *
 * These are the caps the API rejects on, so the composer reports them before a
 * send rather than surfacing a raw 400.
 */
private object Limits {
    const val CONTENT = 2000
    const val EMBEDS = 10
    const val TITLE = 256
    const val DESCRIPTION = 4096
    const val FOOTER = 2048
    const val AUTHOR = 256
    const val FIELDS = 25
    const val FIELD_NAME = 256
    const val FIELD_VALUE = 1024
    const val TOTAL = 6000
    const val BUTTON_LABEL = 80
    const val SELECT_PLACEHOLDER = 150
}

/**
 * Checks a composed message against Discord's limits.
 *
 * Errors would be rejected outright; warnings are things that will send but
 * probably are not what was intended, such as an embed with no content.
 */
fun EmbedMessage.validate(): List<ValidationIssue> {
    val issues = mutableListOf<ValidationIssue>()
    fun error(text: String) = issues.add(ValidationIssue(IssueLevel.ERROR, text))
    fun warn(text: String) = issues.add(ValidationIssue(IssueLevel.WARNING, text))

    if (content.length > Limits.CONTENT) {
        error("Message content is ${content.length} characters, over the ${Limits.CONTENT} limit.")
    }
    if (embeds.size > Limits.EMBEDS) {
        error("${embeds.size} embeds, over the limit of ${Limits.EMBEDS}.")
    }

    var total = content.length
    embeds.forEachIndexed { index, embed ->
        val label = "Embed ${index + 1}"
        if (embed.title.length > Limits.TITLE) {
            error("$label title is over ${Limits.TITLE} characters.")
        }
        if (embed.description.length > Limits.DESCRIPTION) {
            error("$label description is over ${Limits.DESCRIPTION} characters.")
        }
        if (embed.footer.text.length > Limits.FOOTER) {
            error("$label footer is over ${Limits.FOOTER} characters.")
        }
        if (embed.author.name.length > Limits.AUTHOR) {
            error("$label author name is over ${Limits.AUTHOR} characters.")
        }
        if (embed.fields.size > Limits.FIELDS) {
            error("$label has ${embed.fields.size} fields, over the limit of ${Limits.FIELDS}.")
        }
        embed.fields.forEachIndexed { fieldIndex, field ->
            if (field.name.length > Limits.FIELD_NAME) {
                error("$label field ${fieldIndex + 1} name is over ${Limits.FIELD_NAME} characters.")
            }
            if (field.value.length > Limits.FIELD_VALUE) {
                error("$label field ${fieldIndex + 1} value is over ${Limits.FIELD_VALUE} characters.")
            }
            if (field.name.isBlank() || field.value.isBlank()) {
                error("$label field ${fieldIndex + 1} needs both a name and a value.")
            }
        }
        if (embed.isEmpty) warn("$label is empty and will not render.")

        total += embed.title.length + embed.description.length + embed.footer.text.length +
            embed.author.name.length +
            embed.fields.sumOf { it.name.length + it.value.length }
    }

    if (total > Limits.TOTAL) {
        error("All embed text totals $total characters, over the ${Limits.TOTAL} limit.")
    }

    val rows = rows
    if (rows.size > MessageComponent.MaxRows) {
        error("${rows.size} component rows, over the limit of ${MessageComponent.MaxRows}.")
    }
    rows.forEachIndexed { index, row ->
        val label = "Row ${index + 1}"
        val selects = row.count { it.isSelect }
        if (selects > 0 && row.size > 1) {
            error("$label mixes a select menu with other components; a select needs its own row.")
        }
        if (selects == 0 && row.size > MessageComponent.MaxButtonsPerRow) {
            error("$label has ${row.size} buttons, over the limit of ${MessageComponent.MaxButtonsPerRow}.")
        }
        row.forEach { component ->
            when {
                component.isSelect -> {
                    if (component.options.isEmpty()) {
                        error("$label select menu has no options.")
                    }
                    if (component.options.size > MessageComponent.MaxOptions) {
                        error("$label select menu has over ${MessageComponent.MaxOptions} options.")
                    }
                    if (component.options.any { it.name.isBlank() }) {
                        error("$label select menu has an option with no label.")
                    }
                    if (component.displayName.length > Limits.SELECT_PLACEHOLDER) {
                        error("$label placeholder is over ${Limits.SELECT_PLACEHOLDER} characters.")
                    }
                }

                component.displayName.isBlank() -> error("$label has a button with no label.")
                component.displayName.length > Limits.BUTTON_LABEL ->
                    error("$label button label is over ${Limits.BUTTON_LABEL} characters.")

                component.isLink && component.url.isBlank() ->
                    error("$label link button has no URL.")
            }
        }
    }

    if (isEmpty) warn("Nothing to send yet.")
    return issues
}
