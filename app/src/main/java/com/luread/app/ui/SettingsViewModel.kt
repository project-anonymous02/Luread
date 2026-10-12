package com.luread.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luread.app.core.design.AccentPreset
import com.luread.app.core.design.ThemeMode
import com.luread.app.data.library.LibraryRepository
import com.luread.app.data.settings.SettingsRepository
import com.luread.app.data.settings.UserSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val library: LibraryRepository,
) : ViewModel() {

    val state: StateFlow<UserSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    fun setThemeMode(mode: ThemeMode) = launch { settings.setThemeMode(mode) }

    fun setAccent(accent: AccentPreset) = launch { settings.setAccent(accent) }

    fun setFontSize(sp: Float) = launch { settings.setFontSize(sp) }

    fun setKeepScreenOn(enabled: Boolean) = launch { settings.setKeepScreenOn(enabled) }

    fun setTrackReadingStats(enabled: Boolean) = launch { settings.setTrackReadingStats(enabled) }

    fun clearActivity() = launch { library.clearActivity() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
