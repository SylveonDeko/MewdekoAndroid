package dev.mewdeko.mobile.core.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One choice in a [SectionTabs] row. */
data class SectionTab(
    val id: String,
    val title: String,
    val icon: ImageVector? = null,
)

/**
 * The most segments that stay legible on a phone.
 *
 * Material sizes segmented buttons to share the full width evenly, so past
 * this count an icon plus a label no longer fits and titles start truncating.
 */
private const val MaxSegments = 4

/**
 * Top-of-screen section switcher.
 *
 * Up to [MaxSegments] sections render as a segmented button row, which shows
 * every choice at once. Beyond that the row would truncate its own labels, so
 * it becomes a scrollable tab row instead: labels stay readable and the
 * overflow stays discoverable by swiping.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionTabs(
    tabs: List<SectionTab>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.size > MaxSegments) {
        val selectedIndex = tabs.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedIndex,
            modifier = modifier.fillMaxWidth(),
            edgePadding = 0.dp,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = index == selectedIndex,
                    onClick = { onSelect(tab.id) },
                    text = {
                        Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    icon = tab.icon?.let {
                        { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    },
                )
            }
        }
        return
    }

    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, tab ->
            SegmentedButton(
                selected = tab.id == selectedId,
                onClick = { onSelect(tab.id) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                icon = {
                    if (tab.icon != null) {
                        Icon(tab.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    } else {
                        SegmentedButtonDefaults.Icon(active = tab.id == selectedId)
                    }
                },
            ) {
                Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A search field styled to match the feature cards around it. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                androidx.compose.material3.IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear",
                    )
                }
            }
        },
        singleLine = true,
        shape = androidx.compose.material3.MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    )
}
