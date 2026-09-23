package dev.mewdeko.mobile.feature.xp

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Renders the rank card the way [XpCardGenerator][
 * dev.mewdeko.mobile.feature.xp.XpTemplate] would, close enough for a live
 * preview: absolutely positioned composables scaled from the template's
 * pixel coordinates rather than a literal canvas.
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun XpCardPreview(
    template: XpTemplate,
    customElements: List<XpCustomElement>,
    builtInOrder: List<String>,
    backgroundUrl: String?,
    viewer: XpUserPreview?,
    modifier: Modifier = Modifier,
) {
    val ratio = (template.outputSizeX.coerceAtLeast(1)).toFloat() /
        template.outputSizeY.coerceAtLeast(1).toFloat()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF23272A)),
    ) {
        val scale = with(density) { maxWidth.toPx() } / template.outputSizeX.coerceAtLeast(1).toFloat()
        fun px(value: Number): Dp = with(density) { (value.toFloat() * scale).toDp() }

        if (!backgroundUrl.isNullOrBlank()) {
            AsyncImage(
                model = backgroundUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        val progress = if (viewer != null && viewer.requiredXp > 0) {
            (viewer.levelXp.toFloat() / viewer.requiredXp.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        builtInOrder.forEach { id ->
            when (id) {
                "user-icon" -> if (template.templateUser.showIcon) {
                    Box(
                        modifier = Modifier
                            .offset(px(template.templateUser.iconX), px(template.templateUser.iconY))
                            .size(px(template.templateUser.iconSizeX), px(template.templateUser.iconSizeY))
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        if (!viewer?.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = viewer?.avatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                "user-text" -> if (template.templateUser.showText) {
                    Text(
                        text = viewer?.username ?: "Username",
                        color = parseArgbHex(template.templateUser.textColor),
                        fontSize = (template.templateUser.fontSize * scale).sp,
                        modifier = Modifier.offset(
                            px(template.templateUser.textX),
                            px(template.templateUser.textY),
                        ),
                    )
                }

                "progress-bar" -> if (template.templateBar.showBar) {
                    val bar = template.templateBar
                    val left = minOf(bar.barPointAx, bar.barPointBx)
                    val top = minOf(bar.barPointAy, bar.barPointBy)
                    val thickness = 20
                    Box(
                        modifier = Modifier
                            .offset(px(left), px(top))
                            .size(px(bar.barLength), px(thickness))
                            .clip(RoundedCornerShape(px(thickness / 2)))
                            .background(Color.White.copy(alpha = 0.18f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .fillMaxSize()
                                .clip(RoundedCornerShape(px(thickness / 2)))
                                .background(
                                    parseArgbHex(bar.barColor).copy(alpha = bar.barTransparency / 255f),
                                ),
                        )
                    }
                }

                "guild-rank" -> if (template.templateGuild.showGuildRank) {
                    Text(
                        text = "#${viewer?.rank ?: 1}",
                        color = parseArgbHex(template.templateGuild.guildRankColor),
                        fontSize = (template.templateGuild.guildRankFontSize * scale).sp,
                        modifier = Modifier.offset(
                            px(template.templateGuild.guildRankX),
                            px(template.templateGuild.guildRankY),
                        ),
                    )
                }

                "guild-level" -> if (template.templateGuild.showGuildLevel) {
                    Text(
                        text = "${viewer?.level ?: 1}",
                        color = parseArgbHex(template.templateGuild.guildLevelColor),
                        fontSize = (template.templateGuild.guildLevelFontSize * scale).sp,
                        modifier = Modifier.offset(
                            px(template.templateGuild.guildLevelX),
                            px(template.templateGuild.guildLevelY),
                        ),
                    )
                }

                "time-on-level" -> if (template.showTimeOnLevel) {
                    val t = viewer?.timeOnLevel
                    Text(
                        text = "${t?.days ?: 0}d${t?.hours ?: 0}h${t?.minutes ?: 0}m",
                        color = parseArgbHex(template.timeOnLevelColor),
                        fontSize = (template.timeOnLevelFontSize * scale).sp,
                        modifier = Modifier.offset(px(template.timeOnLevelX), px(template.timeOnLevelY)),
                    )
                }

                "awarded" -> if (template.showAwarded) {
                    Text(
                        text = "+${viewer?.bonusXp ?: 0} XP",
                        color = parseArgbHex(template.awardedColor),
                        fontSize = (template.awardedFontSize * scale).sp,
                        modifier = Modifier.offset(px(template.awardedX), px(template.awardedY)),
                    )
                }
            }
        }

        customElements.filter { it.visible }.sortedBy { it.zIndex }.forEach { element ->
            CustomElementPreview(element, viewer, progress, ::px, scale)
        }
    }
}

@Composable
private fun CustomElementPreview(
    element: XpCustomElement,
    viewer: XpUserPreview?,
    progress: Float,
    px: (Number) -> Dp,
    scale: Float,
) {
    val baseModifier = Modifier
        .offset(px(element.x), px(element.y))
        .alpha(element.opacity.toFloat())
        .rotate(element.rotation.toFloat())

    when (element.type) {
        "rectangle" -> Box(
            modifier = baseModifier
                .size(px(element.width), px(element.height))
                .clip(RoundedCornerShape(px(element.cornerRadius)))
                .background(parseCssHex(element.fill))
                .then(
                    if (element.strokeWidth > 0) {
                        Modifier.border(
                            px(element.strokeWidth),
                            parseCssHex(element.stroke),
                            RoundedCornerShape(px(element.cornerRadius)),
                        )
                    } else {
                        Modifier
                    },
                ),
        )

        "ellipse" -> Box(
            modifier = baseModifier
                .size(px(element.width), px(element.height))
                .clip(CircleShape)
                .background(parseCssHex(element.fill))
                .then(
                    if (element.strokeWidth > 0) {
                        Modifier.border(px(element.strokeWidth), parseCssHex(element.stroke), CircleShape)
                    } else {
                        Modifier
                    },
                ),
        )

        "line" -> Box(
            modifier = baseModifier
                .size(px(element.width), px(element.strokeWidth.coerceAtLeast(1.0)))
                .background(parseCssHex(element.fill)),
        )

        "text" -> Text(
            text = resolveXpPlaceholders(element.text, viewer),
            color = parseCssHex(element.fill),
            fontSize = (element.fontSize * scale).sp,
            textAlign = when (element.textAlign) {
                "center" -> TextAlign.Center
                "right" -> TextAlign.End
                else -> TextAlign.Start
            },
            modifier = baseModifier.size(px(element.width), px(element.height)),
        )

        "image" -> if (element.url.isNotBlank()) {
            AsyncImage(
                model = element.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = baseModifier
                    .size(px(element.width), px(element.height))
                    .clip(RoundedCornerShape(px(element.cornerRadius))),
            )
        }

        "progress" -> Box(
            modifier = baseModifier
                .size(px(element.width), px(element.height))
                .clip(RoundedCornerShape(px(element.cornerRadius)))
                .background(parseCssHex(element.trackFill)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxSize()
                    .clip(RoundedCornerShape(px(element.cornerRadius)))
                    .background(parseCssHex(element.fill)),
            )
        }
    }
}

/** Resolves the `%xp.*%` placeholders the server's rank card renderer understands. */
fun resolveXpPlaceholders(text: String, viewer: XpUserPreview?): String {
    val progressPercent = if (viewer != null && viewer.requiredXp > 0) {
        (viewer.levelXp * 100.0 / viewer.requiredXp).toInt()
    } else {
        0
    }
    return text
        .replace("%xp.user%", viewer?.username ?: "Username", ignoreCase = true)
        .replace("%xp.user.name%", viewer?.username ?: "Username", ignoreCase = true)
        .replace("%xp.user.displayname%", viewer?.username ?: "Display name", ignoreCase = true)
        .replace("%xp.level.current%", "${viewer?.level ?: 1}", ignoreCase = true)
        .replace("%xp.level.next%", "${(viewer?.level ?: 1) + 1}", ignoreCase = true)
        .replace("%xp.rank%", "${viewer?.rank ?: 1}", ignoreCase = true)
        .replace("%xp.current%", "${viewer?.levelXp ?: 0}", ignoreCase = true)
        .replace("%xp.needed%", "${viewer?.requiredXp ?: 100}", ignoreCase = true)
        .replace("%xp.total%", "${viewer?.totalXp ?: 0}", ignoreCase = true)
        .replace("%xp.progress%", "$progressPercent%", ignoreCase = true)
}
