package dev.mewdeko.mobile.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Makes a row tappable while holding Material's minimum touch target.
 *
 * Several rows here are a single line of text, which would otherwise present a
 * target well under the 48dp floor.
 */
fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this
    .heightIn(min = 48.dp)
    .clickable(role = Role.Button, onClick = onClick)
