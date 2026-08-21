package dev.mewdeko.mobile.feature.palette

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.theme.GuildColorStore
import javax.inject.Inject

/**
 * Keeps the guild palette applied while a feature page is open.
 *
 * A feature route can be entered directly from a deep link, where the guild
 * overview never ran, so the icon URL travels in the route and seeds the
 * palette here.
 */
@HiltViewModel
class GuildPaletteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    colorStore: GuildColorStore,
) : ViewModel() {

    /** The palette derived from the guild's icon. */
    val palette = colorStore.palette

    init {
        savedStateHandle.get<String>("guildIcon")
            ?.takeIf { it != "-" && it.isNotEmpty() }
            ?.let { colorStore.update(it) }
    }
}
