package dev.mewdeko.mobile.core.theme

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Exposes the shared [GuildColorStore] palette to the app shell. */
@HiltViewModel
class GuildColorViewModel @Inject constructor(
    colorStore: GuildColorStore,
) : ViewModel() {

    /** The palette currently applied to guild-scoped UI. */
    val palette = colorStore.palette
}
