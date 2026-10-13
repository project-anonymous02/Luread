package com.luread.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luread.app.core.design.AccentPreset
import com.luread.app.core.design.RadiusPreset
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

    // ---- Tampilan ----
    fun setThemeMode(mode: ThemeMode) = launch { settings.setThemeMode(mode) }
    fun setAccent(accent: AccentPreset) = launch { settings.setAccent(accent) }
    fun setCustomAccent(argb: Long?) = launch { settings.setCustomAccent(argb) }
    fun setGridColumns(columns: Int) = launch { settings.setGridColumns(columns) }
    fun setCoverAspectRatio(ratio: Float) = launch { settings.setCoverAspectRatio(ratio) }
    fun setCardRadius(radius: RadiusPreset) = launch { settings.setCardRadius(radius) }
    fun setCompactMetadata(enabled: Boolean) = launch { settings.setCompactMetadata(enabled) }
    fun setReduceMotion(enabled: Boolean) = launch { settings.setReduceMotion(enabled) }
    fun setShowMangaHome(enabled: Boolean) = launch { settings.setShowMangaHome(enabled) }
    fun setShowComicsHome(enabled: Boolean) = launch { settings.setShowComicsHome(enabled) }
    fun setShowKnowledgeHome(enabled: Boolean) = launch { settings.setShowKnowledgeHome(enabled) }
    fun setShowContinueReading(enabled: Boolean) = launch { settings.setShowContinueReading(enabled) }
    fun setShowStatsHome(enabled: Boolean) = launch { settings.setShowStatsHome(enabled) }
    fun setHomeSectionOrder(order: String) = launch { settings.setHomeSectionOrder(order) }

    // ---- Membaca ----
    fun setFontSize(sp: Float) = launch { settings.setFontSize(sp) }
    fun setLineHeight(mul: Float) = launch { settings.setLineHeight(mul) }
    fun setPageAnimation(enabled: Boolean) = launch { settings.setPageAnimation(enabled) }
    fun setDimOverlay(value: Float) = launch { settings.setDimOverlay(value) }
    fun setKeepScreenOn(enabled: Boolean) = launch { settings.setKeepScreenOn(enabled) }
    fun setTrackReadingStats(enabled: Boolean) = launch { settings.setTrackReadingStats(enabled) }
    fun setFontFamily(family: String) = launch { settings.setFontFamily(family) }
    fun setJustifyText(enabled: Boolean) = launch { settings.setJustifyText(enabled) }
    fun setBrightnessOverride(value: Float) = launch { settings.setBrightnessOverride(value) }
    fun setVolumeKeyNavigation(enabled: Boolean) = launch { settings.setVolumeKeyNavigation(enabled) }
    fun setTapLeftForNext(enabled: Boolean) = launch { settings.setTapLeftForNext(enabled) }
    fun setShowPageNumbers(enabled: Boolean) = launch { settings.setShowPageNumbers(enabled) }
    fun setAutoScrollManga(enabled: Boolean) = launch { settings.setAutoScrollManga(enabled) }
    fun setAutoScrollSpeed(speed: Float) = launch { settings.setAutoScrollSpeed(speed) }
    fun setMangaReadingDirection(dir: String) = launch { settings.setMangaReadingDirection(dir) }
    fun setDefaultReadingMode(mode: String) = launch { settings.setDefaultReadingMode(mode) }

    // ---- Perpustakaan ----
    fun setDefaultSort(sort: String) = launch { settings.setDefaultSort(sort) }
    fun setShowFinishedBooks(enabled: Boolean) = launch { settings.setShowFinishedBooks(enabled) }
    fun setAutoDownloadNext(enabled: Boolean) = launch { settings.setAutoDownloadNext(enabled) }
    fun setImportDuplicatePolicy(policy: String) = launch { settings.setImportDuplicatePolicy(policy) }

    // ---- Jaringan & Data ----
    fun setMangaImageQuality(quality: String) = launch { settings.setMangaImageQuality(quality) }
    fun setWifiOnlyDownload(enabled: Boolean) = launch { settings.setWifiOnlyDownload(enabled) }
    fun setCacheSizeMb(mb: Int) = launch { settings.setCacheSizeMb(mb) }
    fun setTimeoutSeconds(seconds: Int) = launch { settings.setTimeoutSeconds(seconds) }

    // ---- Terjemahan ----
    fun setDefaultTranslateLanguage(code: String) = launch { settings.setDefaultTranslateLanguage(code) }
    fun setTranslateChunkSize(size: Int) = launch { settings.setTranslateChunkSize(size) }

    // ---- Aksesibilitas ----
    fun setHighContrast(enabled: Boolean) = launch { settings.setHighContrast(enabled) }
    fun setLargerTouchTargets(enabled: Boolean) = launch { settings.setLargerTouchTargets(enabled) }
    fun setBoldText(enabled: Boolean) = launch { settings.setBoldText(enabled) }

    fun clearActivity() = launch { library.clearActivity() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
