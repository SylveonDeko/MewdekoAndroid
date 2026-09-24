package dev.mewdeko.mobile.feature.rolemenus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.theme.LocalGuildPalette
import dev.mewdeko.mobile.core.theme.Rgb
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.GuildCard
import dev.mewdeko.mobile.core.ui.NewItemFab
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.StatePill
import dev.mewdeko.mobile.core.ui.readableInk
import dev.mewdeko.mobile.navigation.GuildRouteArgs

/** Subtitle shared with the dashboard and iOS. */
private const val FeatureSubtitle = "Dropdowns and buttons that let members pick their own roles"

/** Solid green of a live menu. */
internal val LiveGreen = Color(0xFF10B981)

/** Solid red of a missing channel, a role problem, and destructive actions. */
internal val ProblemRed = Color(0xFFEF4444)

/** How many role chips a menu card shows before summing up the rest. */
private const val MaxChips = 6

private val Tabs = listOf(
    SectionTab("menus", "Menus", Icons.AutoMirrored.Filled.PlaylistAddCheck),
    SectionTab("move", "Move older setups", Icons.Default.SwapHoriz),
)

/**
 * Role Menus: messages with a dropdown or buttons that members use to pick
 * their own roles, plus moving older emoji role setups over.
 */
@Composable
fun RoleMenusScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: RoleMenusViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<RoleMenu?>(null) }

    FeatureScaffold(
        title = "Role Menus",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        floatingActionButton = {
            if (state.section == "menus" && !state.editorOpen) {
                NewItemFab(label = "New menu", onClick = viewModel::startNew)
            }
        },
    ) {
        Text(
            text = FeatureSubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        if (state.lookups?.botCanManageRoles == false) {
            RoleMenuCallout(
                text = "The bot doesn't have Manage Roles, so menus can't give out roles until it does.",
                tone = LocalGuildPalette.current.accent.color,
            )
        }

        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        when (state.section) {
            "menus" -> MenusSection(
                state = state,
                onNew = viewModel::startNew,
                onMove = { viewModel.setSection("move") },
                onEdit = viewModel::startEdit,
                onSetEnabled = viewModel::setEnabled,
                onRepost = viewModel::repost,
                onDelete = { pendingDelete = it },
            )
            "move" -> RoleMenuImport(state = state, viewModel = viewModel)
        }
    }

    if (state.editorOpen) {
        RoleMenuEditor(state = state, viewModel = viewModel)
    }

    pendingDelete?.let { menu ->
        DeleteMenuDialog(
            menu = menu,
            onConfirm = {
                pendingDelete = null
                viewModel.delete(menu)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** The shared delete confirmation for a menu and its message. */
@Composable
internal fun DeleteMenuDialog(menu: RoleMenu, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = "Delete this menu?",
        message = "Its message in ${menu.channelLabel} is deleted too. Members keep the roles they already picked.",
        confirmLabel = "Delete menu",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** The menu list, its count line, and the empty state. */
@Composable
private fun MenusSection(
    state: RoleMenusState,
    onNew: () -> Unit,
    onMove: () -> Unit,
    onEdit: (RoleMenu) -> Unit,
    onSetEnabled: (RoleMenu, Boolean) -> Unit,
    onRepost: (RoleMenu) -> Unit,
    onDelete: (RoleMenu) -> Unit,
) {
    if (state.menus.isEmpty()) {
        MenusEmptyState(sourceCount = state.importSources.size, onNew = onNew, onMove = onMove)
        return
    }

    Text(
        text = "${state.menus.size} of ${RoleMenuLimits.MaxMenus} menus",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )

    state.menus.forEach { menu ->
        MenuCard(
            menu = menu,
            busy = menu.id in state.busyMenuIds,
            state = state,
            onEdit = { onEdit(menu) },
            onSetEnabled = { onSetEnabled(menu, it) },
            onRepost = { onRepost(menu) },
            onDelete = { onDelete(menu) },
        )
    }
}

/** Shown when the server has no menus yet, with a nudge toward older setups when there are any. */
@Composable
private fun MenusEmptyState(sourceCount: Int, onNew: () -> Unit, onMove: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    SectionCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.PlaylistAddCheck,
                contentDescription = null,
                tint = primary,
                modifier = Modifier
                    .size(48.dp)
                    .alpha(0.5f),
            )
            Text(
                text = "No role menus yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Post a message with a dropdown or buttons, and members pick their own roles. " +
                    "Pronouns, colors, pings, regions: one menu each.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            RoleMenuActionButton(text = "Create your first menu", icon = null, onClick = onNew, solid = true)
            if (sourceCount > 0) {
                Text(
                    text = "You have $sourceCount older emoji role setups you can move over.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                RoleMenuActionButton(text = "Move older setups", icon = Icons.Default.SwapHoriz, onClick = onMove)
            }
        }
    }
}

/** One menu: name and status, the meta line, role chips, and its actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MenuCard(
    menu: RoleMenu,
    busy: Boolean,
    state: RoleMenusState,
    onEdit: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onRepost: () -> Unit,
    onDelete: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val status = menu.statusKind
    var menuOpen by remember { mutableStateOf(false) }

    GuildCard(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = menu.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatePill(text = status.label, tone = status.tone())
                Box {
                    if (busy) {
                        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Actions for ${menu.name}")
                        }
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (menu.enabled) "Pause" else "Resume") },
                            leadingIcon = {
                                Icon(
                                    if (menu.enabled) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onSetEnabled(!menu.enabled)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Post again") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onRepost()
                            },
                        )
                        menu.jumpUrl?.takeIf { it.isNotBlank() }?.let { url ->
                            DropdownMenuItem(
                                text = { Text("Open in Discord") },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    runCatching { uriHandler.openUri(url) }
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete", color = ProblemRed) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ProblemRed) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }

            Text(
                text = listOf(
                    menu.channelLabel,
                    RoleMenuStyle.from(menu.style).label,
                    RoleMenuMode.from(menu.mode).label,
                    if (menu.options.size == 1) "1 option" else "${menu.options.size} options",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (menu.options.isNotEmpty()) {
                val sorted = menu.options.sortedBy { it.position }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    sorted.take(MaxChips).forEach { option ->
                        RoleChip(
                            emoji = option.emoji.orEmpty(),
                            label = option.label.ifBlank { option.roleName ?: state.role(option.roleId)?.name.orEmpty() },
                            roleColor = option.roleColor,
                            problem = option.problem,
                        )
                    }
                    if (sorted.size > MaxChips) {
                        RoleChip(emoji = "", label = "+${sorted.size - MaxChips}", roleColor = null, problem = null)
                    }
                }
            }

            if (status == RoleMenuStatus.CHANNEL_MISSING) {
                RoleMenuCallout(
                    text = "Its channel was deleted. Edit the menu, pick a new channel, and save to post it again.",
                    tone = ProblemRed,
                )
            }

            if (status == RoleMenuStatus.NOT_POSTED) {
                RoleMenuActionButton(
                    text = "Post again",
                    icon = Icons.AutoMirrored.Filled.Send,
                    onClick = onRepost,
                    enabled = !busy,
                    solid = true,
                )
            }
        }
    }
}

/**
 * A role chip: a dot in the role color, the emoji, and the name. Options
 * with a problem are tinted red and say what is wrong.
 */
@Composable
private fun RoleChip(emoji: String, label: String, roleColor: Long?, problem: String?) {
    val tone = if (problem != null) ProblemRed else MaterialTheme.colorScheme.primary
    val dot = roleColor?.takeIf { it != 0L }?.let { Rgb.fromArgb(it.toInt()).color }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tone.copy(alpha = DashAlpha.Hex20),
        border = BorderStroke(1.dp, tone.copy(alpha = DashAlpha.Hex30)),
        modifier = Modifier.heightIn(min = 28.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (roleColor != null) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(dot ?: MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                )
            }
            if (problem != null) {
                Icon(Icons.Default.Warning, contentDescription = problem, tint = ProblemRed, modifier = Modifier.size(12.dp))
            }
            RoleMenuEmoji(emoji, size = 14.dp)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = readableInk(tone),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The solid color of a menu status: green, the muted ink, the accent, or red. */
@Composable
@ReadOnlyComposable
internal fun RoleMenuStatus.tone(): Color = when (this) {
    RoleMenuStatus.LIVE -> LiveGreen
    RoleMenuStatus.PAUSED -> LocalGuildPalette.current.muted.color
    RoleMenuStatus.NOT_POSTED -> LocalGuildPalette.current.accent.color
    RoleMenuStatus.CHANNEL_MISSING -> ProblemRed
}

/** An inline notice at the `20` wash of [tone], with a glyph and text in the solid tone. */
@Composable
internal fun RoleMenuCallout(text: String, tone: Color, modifier: Modifier = Modifier, icon: ImageVector = Icons.Default.Info) {
    val shape = MaterialTheme.shapes.medium
    Surface(
        shape = shape,
        color = tone.copy(alpha = DashAlpha.Hex20),
        border = BorderStroke(1.dp, tone.copy(alpha = DashAlpha.Hex30)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(18.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * A 44dp button in the dashboard recipe: [tone] at the `20` wash with a `30`
 * border and solid text, or a solid [tone] fill when [solid] is set. A
 * [loading] button shows a spinner in place of its icon.
 */
@Composable
internal fun RoleMenuActionButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    solid: Boolean = false,
    loading: Boolean = false,
) {
    val content = if (solid) MaterialTheme.colorScheme.onPrimary else readableInk(tone)
    Surface(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(12.dp),
        color = if (solid) tone else tone.copy(alpha = DashAlpha.Hex20),
        border = if (solid) null else BorderStroke(1.dp, tone.copy(alpha = DashAlpha.Hex30)),
        modifier = modifier
            .heightIn(min = 44.dp)
            .alpha(if (enabled) 1f else 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = content)
            } else if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
        }
    }
}
