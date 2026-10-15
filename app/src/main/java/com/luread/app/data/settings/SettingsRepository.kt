package com.luread.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.luread.app.core.design.AccentPreset
import com.luread.app.core.design.RadiusPreset
import com.luread.app.core.design.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Satu instance DataStore per proses. Pola ini wajib berupa properti
 * top-level: membuat instance ganda akan membuang exception "Multiple
 * DataStore instances" saat runtime.
 */
private val Context.lureadSettingsStore by preferencesDataStore(name = "luread_settings")

/** Akses instance DataStore pengaturan dari Context mana pun. */
fun settingsDataStore(context: Context): DataStore<Preferences> =
    context.applicationContext.lureadSettingsStore

/**
 * Pengaturan pengguna, disimpan di Preferences DataStore.
 *
 * Semua nilai punya default aman sehingga pengguna baru tidak perlu
 * melakukan konfigurasi apa pun untuk mulai membaca.
 */
data class UserSettings(
    // ---- Tampilan ----
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentPreset = AccentPreset.entries.first(),
    val customAccentArgb: Long? = null,
    val gridColumns: Int = 3,
    val coverAspectRatio: Float = 0.68f,
    val cardRadius: RadiusPreset = RadiusPreset.Rounded,
    val compactMetadata: Boolean = false,
    val reduceMotion: Boolean = false,
    val showMangaHome: Boolean = true,
    val showComicsHome: Boolean = true,
    val showKnowledgeHome: Boolean = true,
    val showContinueReading: Boolean = true,
    val showStatsHome: Boolean = true,
    val homeSectionOrder: String = "featured,indonesian,children,recommendations,knowledge,manga,comics",
    // ---- Membaca ----
    val fontSizeSp: Float = 18f,
    val lineHeightMul: Float = 1.4f,
    val pageAnimation: Boolean = true,
    val pageAnimationStyle: String = "slide",
    val dimOverlay: Float = 0f,
    val keepScreenOn: Boolean = true,
    val trackReadingStats: Boolean = true,
    val fontFamily: String = "default",
    val justifyText: Boolean = false,
    val brightnessOverride: Float = -1f,
    val volumeKeyNavigation: Boolean = false,
    val tapLeftForNext: Boolean = false,
    val showPageNumbers: Boolean = true,
    // LUREAD-FEAT: arah baca per mode — umum (Teks/EPUB/PDF), Manga, Komik.
    val readingDirection: String = "ltr",
    val mangaReadingDirection: String = "rtl",
    val komikReadingDirection: String = "ltr",
    // ---- Perpustakaan ----
    val defaultSort: String = "recent",
    val showFinishedBooks: Boolean = true,
    val autoDownloadNext: Boolean = false,
    val importDuplicatePolicy: String = "skip",
    // ---- Jaringan & Data ----
    val mangaImageQuality: String = "data_saver",
    val wifiOnlyDownload: Boolean = false,
    val cacheSizeMb: Int = 256,
    val timeoutSeconds: Int = 30,
    // ---- Terjemahan ----
    val defaultTranslateLanguage: String = "id",
    val translateChunkSize: Int = 4000,
    // ---- Aksesibilitas ----
    val highContrast: Boolean = false,
    val largerTouchTargets: Boolean = false,
    val boldText: Boolean = false,
) {
    val clampedFontSize: Float get() = fontSizeSp.coerceIn(10f, 40f)
    val clampedLineHeight: Float get() = lineHeightMul.coerceIn(1.0f, 2.5f)
    val clampedDim: Float get() = dimOverlay.coerceIn(0f, 0.8f)
    val clampedGridColumns: Int get() = gridColumns.coerceIn(1, 6)
    val clampedCoverRatio: Float get() = coverAspectRatio.coerceIn(0.4f, 1.5f)
    val clampedBrightness: Float get() = brightnessOverride.coerceIn(-1f, 1f)
    val clampedCacheSize: Int get() = cacheSizeMb.coerceIn(64, 2048)
    val clampedTimeout: Int get() = timeoutSeconds.coerceIn(10, 120)
    val clampedTranslateChunk: Int get() = translateChunkSize.coerceIn(1000, 8000)

    fun extras(): com.luread.app.core.design.LureadExtras =
        com.luread.app.core.design.LureadExtras(
            radius = cardRadius,
            reduceMotion = reduceMotion,
            hapticsEnabled = !reduceMotion,
            coverAspectRatio = clampedCoverRatio,
            gridColumns = clampedGridColumns,
            compactMetadata = compactMetadata,
            highContrast = highContrast,
            largerTouchTargets = largerTouchTargets,
            boldText = boldText,
        )

    /** Arah baca umum: `ltr` (Kiri→Kanan, default) atau `rtl` (Kanan→Kiri). */
    val isRtlReading: Boolean get() = readingDirection == "rtl"
}

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        // Tampilan
        val themeMode = stringPreferencesKey("theme_mode")
        val accent = stringPreferencesKey("accent")
        val customAccent = longPreferencesKey("custom_accent_argb")
        val gridColumns = intPreferencesKey("grid_columns")
        val coverRatio = floatPreferencesKey("cover_aspect_ratio")
        val cardRadius = stringPreferencesKey("card_radius")
        val compactMetadata = booleanPreferencesKey("compact_metadata")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val showMangaHome = booleanPreferencesKey("show_manga_home")
        val showComicsHome = booleanPreferencesKey("show_comics_home")
        val showKnowledgeHome = booleanPreferencesKey("show_knowledge_home")
        val showContinueReading = booleanPreferencesKey("show_continue_reading")
        val showStatsHome = booleanPreferencesKey("show_stats_home")
        val homeSectionOrder = stringPreferencesKey("home_section_order")
        // Membaca
        val fontSize = floatPreferencesKey("font_size_sp")
        val lineHeight = floatPreferencesKey("line_height_mul")
        val pageAnimation = booleanPreferencesKey("page_animation")
        val pageAnimationStyle = stringPreferencesKey("page_animation_style")
        val dimOverlay = floatPreferencesKey("dim_overlay")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val trackReadingStats = booleanPreferencesKey("track_reading_stats")
        val fontFamily = stringPreferencesKey("font_family")
        val justifyText = booleanPreferencesKey("justify_text")
        val brightnessOverride = floatPreferencesKey("brightness_override")
        val volumeKeyNavigation = booleanPreferencesKey("volume_key_navigation")
        val tapLeftForNext = booleanPreferencesKey("tap_left_for_next")
        val showPageNumbers = booleanPreferencesKey("show_page_numbers")
        val readingDirection = stringPreferencesKey("reading_direction")
        val mangaReadingDirection = stringPreferencesKey("manga_reading_direction")
        val komikReadingDirection = stringPreferencesKey("komik_reading_direction")
        // Perpustakaan
        val defaultSort = stringPreferencesKey("default_sort")
        val showFinishedBooks = booleanPreferencesKey("show_finished_books")
        val autoDownloadNext = booleanPreferencesKey("auto_download_next")
        val importDuplicatePolicy = stringPreferencesKey("import_duplicate_policy")
        // Jaringan & Data
        val mangaImageQuality = stringPreferencesKey("manga_image_quality")
        val wifiOnlyDownload = booleanPreferencesKey("wifi_only_download")
        val cacheSizeMb = intPreferencesKey("cache_size_mb")
        val timeoutSeconds = intPreferencesKey("timeout_seconds")
        // Terjemahan
        val translateLang = stringPreferencesKey("default_translate_lang")
        val translateChunkSize = intPreferencesKey("translate_chunk_size")
        // Aksesibilitas
        val highContrast = booleanPreferencesKey("high_contrast")
        val largerTouchTargets = booleanPreferencesKey("larger_touch_targets")
        val boldText = booleanPreferencesKey("bold_text")
    }

    val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            // Tampilan
            themeMode = prefs[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            accent = prefs[Keys.accent]?.let { name ->
                AccentPreset.entries.firstOrNull { it.name == name }
            } ?: AccentPreset.entries.first(),
            customAccentArgb = prefs[Keys.customAccent],
            gridColumns = prefs[Keys.gridColumns] ?: 3,
            coverAspectRatio = prefs[Keys.coverRatio] ?: 0.68f,
            cardRadius = prefs[Keys.cardRadius]?.let { runCatching { RadiusPreset.valueOf(it) }.getOrNull() }
                ?: RadiusPreset.Rounded,
            compactMetadata = prefs[Keys.compactMetadata] ?: false,
            reduceMotion = prefs[Keys.reduceMotion] ?: false,
            showMangaHome = prefs[Keys.showMangaHome] ?: true,
            showComicsHome = prefs[Keys.showComicsHome] ?: true,
            showKnowledgeHome = prefs[Keys.showKnowledgeHome] ?: true,
            showContinueReading = prefs[Keys.showContinueReading] ?: true,
            showStatsHome = prefs[Keys.showStatsHome] ?: true,
            homeSectionOrder = prefs[Keys.homeSectionOrder]
                ?: "featured,indonesian,children,recommendations,knowledge,manga,comics",
            // Membaca
            fontSizeSp = prefs[Keys.fontSize] ?: 18f,
            lineHeightMul = prefs[Keys.lineHeight] ?: 1.4f,
            pageAnimation = prefs[Keys.pageAnimation] ?: true,
            pageAnimationStyle = prefs[Keys.pageAnimationStyle] ?: "slide",
            dimOverlay = prefs[Keys.dimOverlay] ?: 0f,
            keepScreenOn = prefs[Keys.keepScreenOn] ?: true,
            trackReadingStats = prefs[Keys.trackReadingStats] ?: true,
            fontFamily = prefs[Keys.fontFamily] ?: "default",
            justifyText = prefs[Keys.justifyText] ?: false,
            brightnessOverride = prefs[Keys.brightnessOverride] ?: -1f,
            volumeKeyNavigation = prefs[Keys.volumeKeyNavigation] ?: false,
            tapLeftForNext = prefs[Keys.tapLeftForNext] ?: false,
            showPageNumbers = prefs[Keys.showPageNumbers] ?: true,
            readingDirection = prefs[Keys.readingDirection] ?: "ltr",
            mangaReadingDirection = prefs[Keys.mangaReadingDirection] ?: "rtl",
            komikReadingDirection = prefs[Keys.komikReadingDirection] ?: "ltr",
            // Perpustakaan
            defaultSort = prefs[Keys.defaultSort] ?: "recent",
            showFinishedBooks = prefs[Keys.showFinishedBooks] ?: true,
            autoDownloadNext = prefs[Keys.autoDownloadNext] ?: false,
            importDuplicatePolicy = prefs[Keys.importDuplicatePolicy] ?: "skip",
            // Jaringan & Data
            mangaImageQuality = prefs[Keys.mangaImageQuality] ?: "data_saver",
            wifiOnlyDownload = prefs[Keys.wifiOnlyDownload] ?: false,
            cacheSizeMb = prefs[Keys.cacheSizeMb] ?: 256,
            timeoutSeconds = prefs[Keys.timeoutSeconds] ?: 30,
            // Terjemahan
            defaultTranslateLanguage = prefs[Keys.translateLang] ?: "id",
            translateChunkSize = prefs[Keys.translateChunkSize] ?: 4000,
            // Aksesibilitas
            highContrast = prefs[Keys.highContrast] ?: false,
            largerTouchTargets = prefs[Keys.largerTouchTargets] ?: false,
            boldText = prefs[Keys.boldText] ?: false,
        )
    }

    // ---- Tampilan ----
    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.themeMode] = mode.name }
    suspend fun setAccent(accent: AccentPreset) = edit {
        it[Keys.accent] = accent.name
        it.remove(Keys.customAccent)
    }
    suspend fun setCustomAccent(argb: Long?) = edit { prefs ->
        if (argb == null) prefs.remove(Keys.customAccent)
        else prefs[Keys.customAccent] = argb
    }
    suspend fun setGridColumns(columns: Int) = edit { it[Keys.gridColumns] = columns.coerceIn(1, 6) }
    suspend fun setCoverAspectRatio(ratio: Float) = edit { it[Keys.coverRatio] = ratio.coerceIn(0.4f, 1.5f) }
    suspend fun setCardRadius(radius: RadiusPreset) = edit { it[Keys.cardRadius] = radius.name }
    suspend fun setCompactMetadata(enabled: Boolean) = edit { it[Keys.compactMetadata] = enabled }
    suspend fun setReduceMotion(enabled: Boolean) = edit { it[Keys.reduceMotion] = enabled }
    suspend fun setShowMangaHome(enabled: Boolean) = edit { it[Keys.showMangaHome] = enabled }
    suspend fun setShowComicsHome(enabled: Boolean) = edit { it[Keys.showComicsHome] = enabled }
    suspend fun setShowKnowledgeHome(enabled: Boolean) = edit { it[Keys.showKnowledgeHome] = enabled }
    suspend fun setShowContinueReading(enabled: Boolean) = edit { it[Keys.showContinueReading] = enabled }
    suspend fun setShowStatsHome(enabled: Boolean) = edit { it[Keys.showStatsHome] = enabled }
    suspend fun setHomeSectionOrder(order: String) = edit { it[Keys.homeSectionOrder] = order.take(200) }

    // ---- Membaca ----
    suspend fun setFontSize(sp: Float) = edit { it[Keys.fontSize] = sp.coerceIn(10f, 40f) }
    suspend fun setLineHeight(mul: Float) = edit { it[Keys.lineHeight] = mul.coerceIn(1.0f, 2.5f) }
    suspend fun setPageAnimation(enabled: Boolean) = edit { it[Keys.pageAnimation] = enabled }
    suspend fun setPageAnimationStyle(style: String) = edit {
        it[Keys.pageAnimationStyle] = when (style) {
            "fade", "none" -> style
            else -> "slide"
        }
    }
    suspend fun setDimOverlay(value: Float) = edit { it[Keys.dimOverlay] = value.coerceIn(0f, 0.8f) }
    suspend fun setKeepScreenOn(enabled: Boolean) = edit { it[Keys.keepScreenOn] = enabled }
    suspend fun setTrackReadingStats(enabled: Boolean) = edit { it[Keys.trackReadingStats] = enabled }
    suspend fun setFontFamily(family: String) = edit { it[Keys.fontFamily] = family.take(40) }
    suspend fun setJustifyText(enabled: Boolean) = edit { it[Keys.justifyText] = enabled }
    suspend fun setBrightnessOverride(value: Float) = edit { it[Keys.brightnessOverride] = value.coerceIn(-1f, 1f) }
    suspend fun setVolumeKeyNavigation(enabled: Boolean) = edit { it[Keys.volumeKeyNavigation] = enabled }
    suspend fun setTapLeftForNext(enabled: Boolean) = edit { it[Keys.tapLeftForNext] = enabled }
    suspend fun setShowPageNumbers(enabled: Boolean) = edit { it[Keys.showPageNumbers] = enabled }
    suspend fun setReadingDirection(dir: String) = edit {
        it[Keys.readingDirection] = if (dir == "rtl") "rtl" else "ltr"
    }
    suspend fun setMangaReadingDirection(dir: String) = edit {
        it[Keys.mangaReadingDirection] = if (dir == "ltr") "ltr" else "rtl"
    }
    suspend fun setKomikReadingDirection(dir: String) = edit {
        it[Keys.komikReadingDirection] = if (dir == "rtl") "rtl" else "ltr"
    }

    // ---- Perpustakaan ----
    suspend fun setDefaultSort(sort: String) = edit { it[Keys.defaultSort] = sort.take(20) }
    suspend fun setShowFinishedBooks(enabled: Boolean) = edit { it[Keys.showFinishedBooks] = enabled }
    suspend fun setAutoDownloadNext(enabled: Boolean) = edit { it[Keys.autoDownloadNext] = enabled }
    suspend fun setImportDuplicatePolicy(policy: String) = edit { it[Keys.importDuplicatePolicy] = policy.take(16) }

    // ---- Jaringan & Data ----
    suspend fun setMangaImageQuality(quality: String) = edit { it[Keys.mangaImageQuality] = quality.take(16) }
    suspend fun setWifiOnlyDownload(enabled: Boolean) = edit { it[Keys.wifiOnlyDownload] = enabled }
    suspend fun setCacheSizeMb(mb: Int) = edit { it[Keys.cacheSizeMb] = mb.coerceIn(64, 2048) }
    suspend fun setTimeoutSeconds(seconds: Int) = edit { it[Keys.timeoutSeconds] = seconds.coerceIn(10, 120) }

    // ---- Terjemahan ----
    suspend fun setDefaultTranslateLanguage(code: String) = edit {
        it[Keys.translateLang] = code.take(5).ifBlank { "id" }
    }
    suspend fun setTranslateChunkSize(size: Int) = edit {
        it[Keys.translateChunkSize] = size.coerceIn(1000, 8000)
    }

    // ---- Aksesibilitas ----
    suspend fun setHighContrast(enabled: Boolean) = edit { it[Keys.highContrast] = enabled }
    suspend fun setLargerTouchTargets(enabled: Boolean) = edit { it[Keys.largerTouchTargets] = enabled }
    suspend fun setBoldText(enabled: Boolean) = edit { it[Keys.boldText] = enabled }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }
}
