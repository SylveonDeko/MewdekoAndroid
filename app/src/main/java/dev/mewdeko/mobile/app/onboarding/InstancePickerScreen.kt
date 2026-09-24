package dev.mewdeko.mobile.app.onboarding

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.model.MobileInstance
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.LuminousButton
import dev.mewdeko.mobile.core.ui.LuminousButtonVariant
import dev.mewdeko.mobile.core.ui.ShellDimens
import dev.mewdeko.mobile.core.ui.ShellEmptyState
import dev.mewdeko.mobile.core.ui.ShellHero
import dev.mewdeko.mobile.core.ui.ShellScreen
import dev.mewdeko.mobile.core.ui.ShellTextAction
import dev.mewdeko.mobile.core.ui.ShellType
import dev.mewdeko.mobile.core.ui.StatePillOff
import dev.mewdeko.mobile.core.ui.StatePillOn
import dev.mewdeko.mobile.core.ui.SurfaceLevel
import dev.mewdeko.mobile.core.ui.glassSurface
import dev.mewdeko.mobile.core.ui.pressFeedback
import dev.mewdeko.mobile.core.ui.rememberShellRoles
import dev.mewdeko.mobile.feature.guilddetail.home.EnteredKeys
import dev.mewdeko.mobile.feature.guilddetail.home.riseOnce
import kotlin.math.min

/** The per-card delay of the staggered rise-in, in milliseconds. */
private const val RiseStaggerMillis = 45

/** The most cards that wait on the stagger; later ones rise with the last. */
private const val RiseStaggerCap = 8

/** The size of a bot avatar on its card. */
private const val BotAvatarSize = 64

/**
 * The bot picker, lit by the house palette, matching the iOS "Choose a bot"
 * screen.
 *
 * A hero naming the dashboard host, then one glass card per bot with its
 * glowing avatar. The tapped card swaps its chevron for progress while the
 * app connects and the others are disabled meanwhile. Under the list, quiet
 * actions change the dashboard or sign out.
 *
 * @param dashboardHost The dashboard's host, shown as the hero overline.
 * @param onRetry Loads the bots again, for the empty state.
 */
@Composable
fun InstancePickerScreen(
    instances: List<MobileInstance>,
    errorMessage: String?,
    dashboardHost: String?,
    onSelect: (MobileInstance) -> Unit,
    onRetry: () -> Unit,
    onChooseServer: () -> Unit,
    onSignOut: () -> Unit,
) {
    var selecting by rememberSaveable { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current
    val entered = remember { EnteredKeys() }

    ShellScreen(title = "Choose a bot") {
        ShellHero(
            icon = Icons.Default.Memory,
            overline = dashboardHost ?: "Dashboard",
            title = "Choose a bot",
            message = if (instances.isEmpty()) null else "Pick the Mewdeko instance you want to manage.",
        )

        if (instances.isEmpty()) {
            ShellEmptyState(
                title = "No bots available",
                icon = Icons.Default.FlashOff,
                message = errorMessage ?: "This dashboard has no active bot instances.",
                modifier = Modifier
                    .glassSurface(SurfaceLevel.Card)
                    .padding(ShellDimens.m),
            ) {
                LuminousButton(text = "Try again", onClick = onRetry)
                LuminousButton(text = "Sign out", onClick = onSignOut, variant = LuminousButtonVariant.Glass)
            }
            QuietActions(onChooseServer = onChooseServer, onSignOut = null)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(ShellDimens.s)) {
                instances.forEachIndexed { index, instance ->
                    InstanceCard(
                        instance = instance,
                        isSelecting = selecting == instance.botId,
                        enabled = selecting == null || selecting == instance.botId,
                        onClick = {
                            if (selecting == null) {
                                selecting = instance.botId
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelect(instance)
                            }
                        },
                        modifier = Modifier.riseOnce(
                            key = "bot:${instance.botId}",
                            entered = entered,
                            delayMillis = min(index, RiseStaggerCap) * RiseStaggerMillis,
                        ),
                    )
                }
            }
            QuietActions(onChooseServer = onChooseServer, onSignOut = onSignOut)
        }
    }
}

/**
 * One bot as a tappable glass card: its avatar over a soft primary glow, its
 * name on up to two lines, an Active or Inactive pill, and a chevron that
 * becomes progress while connecting.
 */
@Composable
private fun InstanceCard(
    instance: MobileInstance,
    isSelecting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val roles = rememberShellRoles()
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(ShellDimens.cardRadius)
    val glow = scheme.primary.copy(alpha = 0.35f)
    val state = when {
        isSelecting -> "Connecting"
        instance.isActive -> "Active"
        else -> "Inactive"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressFeedback(interaction)
            .graphicsLayer { alpha = if (enabled) 1f else 0.6f }
            .clip(shape)
            .glassSurface(SurfaceLevel.Card, shape = shape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "Manage this bot",
                onClick = onClick,
            )
            .semantics {
                contentDescription = instance.botName
                stateDescription = state
            }
            .padding(ShellDimens.m),
        horizontalArrangement = Arrangement.spacedBy(ShellDimens.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            url = instance.avatarUrl,
            contentDescription = null,
            size = BotAvatarSize,
            fallbackIcon = Icons.Default.Memory,
            fallbackText = instance.botName,
            modifier = Modifier
                .drawBehind {
                    val radius = size.minDimension / 2f + 14.dp.toPx()
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(glow, glow.copy(alpha = 0f)),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                    )
                }
                .clearAndSetSemantics { },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics { },
            verticalArrangement = Arrangement.spacedBy(ShellDimens.xxs),
        ) {
            Text(
                text = instance.botName,
                style = ShellType.heading,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (instance.isActive) StatePillOn(text = "Active") else StatePillOff(text = "Inactive")
        }
        Spacer(Modifier.width(ShellDimens.xs))
        if (isSelecting) {
            CircularProgressIndicator(
                color = scheme.primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = roles.textTertiary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * The quiet text actions under the list: change the dashboard, and sign out
 * when [onSignOut] is set (the empty state already offers it as a button).
 */
@Composable
private fun QuietActions(onChooseServer: () -> Unit, onSignOut: (() -> Unit)?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShellTextAction(text = "Choose a different dashboard", onClick = onChooseServer)
        if (onSignOut != null) ShellTextAction(text = "Sign out", onClick = onSignOut)
    }
}
