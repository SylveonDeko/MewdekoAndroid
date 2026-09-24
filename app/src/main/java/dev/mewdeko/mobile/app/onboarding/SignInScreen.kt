package dev.mewdeko.mobile.app.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.R
import dev.mewdeko.mobile.app.hostOrSelf
import dev.mewdeko.mobile.core.store.ServerConfig
import dev.mewdeko.mobile.core.ui.GlyphOrb
import dev.mewdeko.mobile.core.ui.HaloLogo
import dev.mewdeko.mobile.core.ui.LuminousButton
import dev.mewdeko.mobile.core.ui.LuminousButtonVariant
import dev.mewdeko.mobile.core.ui.ShellCallout
import dev.mewdeko.mobile.core.ui.ShellDimens
import dev.mewdeko.mobile.core.ui.ShellField
import dev.mewdeko.mobile.core.ui.ShellOverline
import dev.mewdeko.mobile.core.ui.ShellScreen
import dev.mewdeko.mobile.core.ui.ShellTextAction
import dev.mewdeko.mobile.core.ui.ShellTone
import dev.mewdeko.mobile.core.ui.ShellType
import dev.mewdeko.mobile.core.ui.SurfaceLevel
import dev.mewdeko.mobile.core.ui.glassSurface
import dev.mewdeko.mobile.core.ui.pressFeedback
import dev.mewdeko.mobile.core.ui.rememberShellRoles

/**
 * Sign in, lit by the house palette on a drifting hero canvas, matching the
 * iOS sign in screen.
 *
 * A leading aligned column: the haloed logo, the pitch, and one glass card
 * holding the dashboard chip (which opens the dashboard picker), any error,
 * and the Discord button. A demo code field expands in place below it.
 *
 * @param server The active dashboard, shown in the chip.
 */
@Composable
fun SignInScreen(
    server: ServerConfig?,
    errorMessage: String?,
    isAuthorizing: Boolean,
    onSignIn: () -> Unit,
    onChooseServer: () -> Unit,
    onDemoCode: (String) -> Unit,
) {
    var showDemoEntry by rememberSaveable { mutableStateOf(false) }
    var demoCode by rememberSaveable { mutableStateOf("") }
    val scheme = MaterialTheme.colorScheme
    val submitDemo = {
        if (demoCode.isNotBlank() && !isAuthorizing) onDemoCode(demoCode.trim())
    }

    ShellScreen(drifts = true, topPadding = ShellDimens.hero) {
        HaloLogo(
            painter = painterResource(R.drawable.mewdeko_logo),
            modifier = Modifier.padding(bottom = ShellDimens.xxs),
        )
        Column(verticalArrangement = Arrangement.spacedBy(ShellDimens.s)) {
            ShellOverline("Mewdeko")
            Text(
                text = "Your server, wherever you are.",
                style = ShellType.display,
                color = scheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Manage the parts of your Discord community that need your attention.",
                style = ShellType.meta,
                color = scheme.onSurfaceVariant,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassSurface(SurfaceLevel.Card)
                .padding(ShellDimens.l),
            verticalArrangement = Arrangement.spacedBy(ShellDimens.m),
        ) {
            if (server != null) {
                DashboardChip(server = server, onClick = onChooseServer)
            }
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ShellCallout(text = errorMessage.orEmpty(), tone = ShellTone.Negative)
            }
            LuminousButton(
                text = if (isAuthorizing) "Opening Discord" else "Continue with Discord",
                onClick = onSignIn,
                enabled = !isAuthorizing && server != null,
                loading = isAuthorizing,
                icon = Icons.Default.Forum,
                fullWidth = true,
            )
            Text(
                text = "Discord opens securely so you can choose the account you want to use.",
                style = ShellType.caption,
                color = scheme.onSurfaceVariant,
            )
        }

        if (server == null) {
            ShellTextAction(text = "Choose a dashboard", onClick = onChooseServer)
        }

        AnimatedContent(
            targetState = showDemoEntry,
            transitionSpec = {
                (fadeIn() + slideInVertically { -it / 4 }) togetherWith fadeOut()
            },
            label = "demoEntry",
        ) { expanded ->
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassSurface(SurfaceLevel.Group, shape = RoundedCornerShape(ShellDimens.tileRadius))
                        .padding(ShellDimens.m),
                    verticalArrangement = Arrangement.spacedBy(ShellDimens.s),
                ) {
                    ShellOverline("Demo code")
                    ShellField(
                        value = demoCode,
                        onValueChange = { demoCode = it },
                        placeholder = "Enter your demo code",
                        label = "Demo code",
                        imeAction = ImeAction.Go,
                        autoCorrect = false,
                        onImeAction = submitDemo,
                    )
                    LuminousButton(
                        text = "Open the demo",
                        onClick = submitDemo,
                        variant = LuminousButtonVariant.Glass,
                        enabled = demoCode.isNotBlank() && !isAuthorizing,
                        fullWidth = true,
                    )
                }
            } else {
                ShellTextAction(text = "Have a demo code?", onClick = { showDemoEntry = true })
            }
        }
    }
}

/**
 * The current dashboard as a tappable chip on the quiet fill: a small orb,
 * the label, the host, and a swap glyph. Opens the dashboard picker.
 */
@Composable
private fun DashboardChip(server: ServerConfig, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val roles = rememberShellRoles()
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(ShellDimens.controlRadius)
    val host = server.baseUrl.hostOrSelf()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressFeedback(interaction)
            .clip(shape)
            .background(roles.fillQuiet, shape)
            .border(1.dp, roles.hairline, shape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = "Change the dashboard",
                onClick = onClick,
            )
            .semantics { contentDescription = "Dashboard, ${server.label}, $host" }
            .padding(ShellDimens.s),
        horizontalArrangement = Arrangement.spacedBy(ShellDimens.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphOrb(icon = Icons.Default.Dns, tint = scheme.primary)
        Column(
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics { },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = server.label,
                style = ShellType.rowTitle,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = host,
                style = ShellType.caption,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.Default.SyncAlt,
            contentDescription = null,
            tint = roles.textTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}
