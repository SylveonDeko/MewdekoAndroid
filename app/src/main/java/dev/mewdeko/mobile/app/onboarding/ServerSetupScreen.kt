package dev.mewdeko.mobile.app.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.R
import dev.mewdeko.mobile.app.hostOrSelf
import dev.mewdeko.mobile.core.store.ServerConfig
import dev.mewdeko.mobile.core.ui.ChoiceCard
import dev.mewdeko.mobile.core.ui.GlyphOrb
import dev.mewdeko.mobile.core.ui.LuminousButton
import dev.mewdeko.mobile.core.ui.MonogramOrb
import dev.mewdeko.mobile.core.ui.ShellCallout
import dev.mewdeko.mobile.core.ui.ShellConfirmDialog
import dev.mewdeko.mobile.core.ui.ShellDimens
import dev.mewdeko.mobile.core.ui.ShellField
import dev.mewdeko.mobile.core.ui.ShellHero
import dev.mewdeko.mobile.core.ui.ShellScreen
import dev.mewdeko.mobile.core.ui.ShellSectionHeader
import dev.mewdeko.mobile.core.ui.ShellStatePill
import dev.mewdeko.mobile.core.ui.ShellTone
import dev.mewdeko.mobile.core.ui.ShellType
import dev.mewdeko.mobile.core.ui.SurfaceLevel
import dev.mewdeko.mobile.core.ui.glassSurface
import dev.mewdeko.mobile.core.ui.pressFeedback
import dev.mewdeko.mobile.core.ui.rememberShellRoles
import java.net.URI

/** The hosted dashboard, used unless a selfhoster points somewhere else. */
private const val OfficialDashboard = "https://mewdeko.tech"

/** Which kind of dashboard is being added. */
private enum class DashboardChoice {
    /** The hosted dashboard at [OfficialDashboard]. */
    Official,

    /** A dashboard the user runs, entered by URL. */
    SelfHosted,
}

/**
 * First-run and switch-dashboard screen, lit by the house palette, matching
 * the iOS setup screen.
 *
 * Saved dashboards (when any exist) sit above two choice cards: the official
 * dashboard and a self-hosted one whose URL and label fields expand in
 * place. The primary action lives in a floating dock. This screen is about
 * the Mewdeko dashboard host, never a Discord server: that word is reserved
 * for guilds everywhere else in the app.
 *
 * @param currentServerId The active dashboard, marked Active in the saved list.
 */
@Composable
fun ServerSetupScreen(
    savedServers: List<ServerConfig>,
    currentServerId: String?,
    errorMessage: String?,
    isProbing: Boolean,
    onAddServer: (label: String, url: String) -> Unit,
    onSelectServer: (String) -> Unit,
    onForgetServer: (String) -> Unit,
) {
    var choice by rememberSaveable {
        mutableStateOf(if (savedServers.isEmpty()) DashboardChoice.Official else null)
    }
    var input by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var pendingRemoval by remember { mutableStateOf<ServerConfig?>(null) }

    val parsedUrl = when (choice) {
        DashboardChoice.Official -> OfficialDashboard
        DashboardChoice.SelfHosted -> parseDashboardUrl(input)
        null -> null
    }
    val actionTitle = when {
        isProbing -> "Connecting"
        savedServers.isEmpty() -> "Continue"
        else -> "Add dashboard"
    }
    val connect = {
        parsedUrl?.let { url ->
            val resolvedLabel = if (choice == DashboardChoice.SelfHosted) label.trim() else ""
            onAddServer(resolvedLabel, url)
        }
        Unit
    }

    ShellScreen(
        title = "Choose a dashboard",
        dock = {
            LuminousButton(
                text = actionTitle,
                onClick = connect,
                enabled = parsedUrl != null && !isProbing,
                loading = isProbing,
                fullWidth = true,
            )
        },
    ) {
        ShellHero(
            icon = Icons.Default.Dns,
            overline = "Setup",
            title = "Connect a dashboard",
            message = "Mewdeko Mobile talks to the dashboard that runs your bot.",
        )

        if (savedServers.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(ShellDimens.s)) {
                ShellSectionHeader(title = "Saved", icon = Icons.Default.Inventory2)
                SavedDashboards(
                    servers = savedServers,
                    currentServerId = currentServerId,
                    enabled = !isProbing,
                    onSelect = onSelectServer,
                    onForget = { pendingRemoval = it },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(ShellDimens.s)) {
            if (savedServers.isNotEmpty()) {
                ShellSectionHeader(title = "Add dashboard", icon = Icons.Default.Add)
            }
            ChoiceCard(
                title = "Official dashboard",
                subtitle = "mewdeko.tech",
                logo = painterResource(R.drawable.mewdeko_logo),
                selected = choice == DashboardChoice.Official,
                onClick = { choice = DashboardChoice.Official },
            )
            ChoiceCard(
                title = "Self-hosted",
                subtitle = "A dashboard you run yourself",
                icon = Icons.Default.Storage,
                selected = choice == DashboardChoice.SelfHosted,
                onClick = { choice = DashboardChoice.SelfHosted },
            ) {
                DashboardFields(
                    url = input,
                    onUrlChange = { input = it },
                    label = label,
                    onLabelChange = { label = it },
                    onSubmit = connect,
                )
                Text(
                    text = "The app reads the host's public mobile config from /api/mobile/config.",
                    style = ShellType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = errorMessage != null && !isProbing,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ShellCallout(
                    text = errorMessage.orEmpty(),
                    tone = ShellTone.Caution,
                    modifier = Modifier
                        .glassSurface(SurfaceLevel.Group, shape = RoundedCornerShape(ShellDimens.tileRadius))
                        .padding(ShellDimens.s),
                )
            }
        }
    }

    pendingRemoval?.let { server ->
        ShellConfirmDialog(
            title = "Forget this dashboard?",
            message = "Tokens and the cached profile for " +
                "${server.baseUrl.hostOrSelf()} will be deleted from this device.",
            confirmLabel = "Forget ${server.label}",
            onConfirm = { onForgetServer(server.id) },
            onDismiss = { pendingRemoval = null },
        )
    }
}

/**
 * The URL and optional label fields for a dashboard. [onSubmit] runs from
 * the keyboard's Go action on the label field.
 */
@Composable
private fun DashboardFields(
    url: String,
    onUrlChange: (String) -> Unit,
    label: String,
    onLabelChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ShellDimens.xs)) {
        ShellField(
            value = url,
            onValueChange = onUrlChange,
            placeholder = "https://dash.example.com",
            label = "Dashboard URL",
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Next,
            autoCorrect = false,
            onImeAction = null,
        )
        ShellField(
            value = label,
            onValueChange = onLabelChange,
            placeholder = "Label (optional)",
            label = "Label, optional",
            imeAction = ImeAction.Go,
            onImeAction = onSubmit,
        )
    }
}

/**
 * The saved dashboards as one glass card of rows split by hairlines. A row
 * switches to its dashboard; its trash button asks before forgetting it.
 */
@Composable
private fun SavedDashboards(
    servers: List<ServerConfig>,
    currentServerId: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onForget: (ServerConfig) -> Unit,
) {
    val roles = rememberShellRoles()
    val shape = RoundedCornerShape(ShellDimens.cardRadius)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .glassSurface(SurfaceLevel.Card, shape = shape),
    ) {
        servers.forEachIndexed { index, server ->
            SavedDashboardRow(
                server = server,
                isCurrent = server.id == currentServerId,
                enabled = enabled,
                onClick = { onSelect(server.id) },
                onForget = { onForget(server) },
            )
            if (index < servers.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 56.dp)
                        .height(1.dp)
                        .drawBehind { drawLine(roles.hairline, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) },
                )
            }
        }
    }
}

/**
 * A saved dashboard row: a monogram orb (or a check orb for the active
 * dashboard), the label, the host, an Active pill or chevron, and a forget
 * button.
 */
@Composable
private fun SavedDashboardRow(
    server: ServerConfig,
    isCurrent: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onForget: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val roles = rememberShellRoles()
    val interaction = remember { MutableInteractionSource() }
    val host = server.baseUrl.hostOrSelf()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "Switch to this dashboard",
                onClick = onClick,
            )
            .padding(start = ShellDimens.m, end = ShellDimens.xxs, top = ShellDimens.xs, bottom = ShellDimens.xs),
        horizontalArrangement = Arrangement.spacedBy(ShellDimens.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .pressFeedback(interaction)
                .semantics(mergeDescendants = true) {
                    contentDescription = "${server.label}, $host"
                    selected = isCurrent
                },
            horizontalArrangement = Arrangement.spacedBy(ShellDimens.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isCurrent) {
                GlyphOrb(icon = Icons.Default.Check, tint = scheme.primary)
            } else {
                MonogramOrb(label = server.label)
            }
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
            if (isCurrent) {
                ShellStatePill(text = "Active", tone = ShellTone.Brand, icon = Icons.Default.CheckCircle)
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = roles.textTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        IconButton(onClick = onForget, enabled = enabled) {
            Icon(
                Icons.Default.DeleteOutline,
                contentDescription = "Forget ${server.label}",
                tint = roles.textTertiary,
            )
        }
    }
}

/**
 * Parses user input into a dashboard URL, adding `https://` when no scheme
 * is given. Returns null for empty input or input without a host.
 */
private fun parseDashboardUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val candidate = if ("://" in trimmed) trimmed else "https://$trimmed"
    val host = runCatching { URI(candidate).host }.getOrNull()
    return if (host.isNullOrBlank()) null else candidate
}
