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
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentPreset = AccentPreset.entries.first(),
    /** Warna aksen kustom (ARGB packed). Null = pakai preset. */
    val customAccentArgb: Long? = null,
    /** Ukuran teks baca dalam sp, rentang wajar 14..30. */
    val fontSizeSp: Float = 18f,
    /** Pengali line-height pembaca (1.0..2.2). */
    val lineHeightMul: Float = 1.4f,
    /** Animasi halaman di pembaca teks. */
    val pageAnimation: Boolean = true,
    /** Overlay redup gelap di pembaca (0..0.7). */
    val dimOverlay: Float = 0f,
    /** Layar tetap menyala selama membaca. */
    val keepScreenOn: Boolean = true,
    /** Statistik membaca di tab Aktivitas. */
    val trackReadingStats: Boolean = true,
    /** Jumlah kolom grid katalog/manga. */
    val gridColumns: Int = 3,
    /** Rasio tinggi/lebar cover. */
    val coverAspectRatio: Float = 0.68f,
    /** Radius sudut kartu. */
    val cardRadius: RadiusPreset = RadiusPreset.Rounded,
    /** Metadata ringkas di kartu. */
    val compactMetadata: Boolean = false,
    /** Kurangi animasi (accessibility). */
    val reduceMotion: Boolean = false,
    /** Tampilkan baris Manga di Home. */
    val showMangaHome: Boolean = true,
    /** Bahasa bacaan default untuk terjemahan (kode ISO). */
    val defaultTranslateLanguage: String = "id",
) {
    val clampedFontSize: Float get() = fontSizeSp.coerceIn(14f, 30f)
    val clampedLineHeight: Float get() = lineHeightMul.coerceIn(1.0f, 2.2f)
    val clampedDim: Float get() = dimOverlay.coerceIn(0f, 0.7f)
    val clampedGridColumns: Int get() = gridColumns.coerceIn(2, 5)
    val clampedCoverRatio: Float get() = coverAspectRatio.coerceIn(0.5f, 1.2f)

    fun extras(): com.luread.app.core.design.LureadExtras =
        com.luread.app.core.design.LureadExtras(
            radius = cardRadius,
            reduceMotion = reduceMotion,
            hapticsEnabled = !reduceMotion,
            coverAspectRatio = clampedCoverRatio,
            gridColumns = clampedGridColumns,
            compactMetadata = compactMetadata,
        )
}

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val accent = stringPreferencesKey("accent")
        val customAccent = longPreferencesKey("custom_accent_argb")
        val fontSize = floatPreferencesKey("font_size_sp")
        val lineHeight = floatPreferencesKey("line_height_mul")
        val pageAnimation = booleanPreferencesKey("page_animation")
        val dimOverlay = floatPreferencesKey("dim_overlay")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val trackReadingStats = booleanPreferencesKey("track_reading_stats")
        val gridColumns = intPreferencesKey("grid_columns")
        val coverRatio = floatPreferencesKey("cover_aspect_ratio")
        val cardRadius = stringPreferencesKey("card_radius")
        val compactMetadata = booleanPreferencesKey("compact_metadata")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val showMangaHome = booleanPreferencesKey("show_manga_home")
        val translateLang = stringPreferencesKey("default_translate_lang")
    }

    val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            themeMode = prefs[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            accent = prefs[Keys.accent]?.let { name ->
                AccentPreset.entries.firstOrNull { it.name == name }
            } ?: AccentPreset.entries.first(),
            customAccentArgb = prefs[Keys.customAccent],
            fontSizeSp = prefs[Keys.fontSize] ?: 18f,
            lineHeightMul = prefs[Keys.lineHeight] ?: 1.4f,
            pageAnimation = prefs[Keys.pageAnimation] ?: true,
            dimOverlay = prefs[Keys.dimOverlay] ?: 0f,
            keepScreenOn = prefs[Keys.keepScreenOn] ?: true,
            trackReadingStats = prefs[Keys.trackReadingStats] ?: true,
            gridColumns = prefs[Keys.gridColumns] ?: 3,
            coverAspectRatio = prefs[Keys.coverRatio] ?: 0.68f,
            cardRadius = prefs[Keys.cardRadius]?.let { runCatching { RadiusPreset.valueOf(it) }.getOrNull() }
                ?: RadiusPreset.Rounded,
            compactMetadata = prefs[Keys.compactMetadata] ?: false,
            reduceMotion = prefs[Keys.reduceMotion] ?: false,
            showMangaHome = prefs[Keys.showMangaHome] ?: true,
            defaultTranslateLanguage = prefs[Keys.translateLang] ?: "id",
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.themeMode] = mode.name }

    suspend fun setAccent(accent: AccentPreset) = edit {
        it[Keys.accent] = accent.name
        it.remove(Keys.customAccent)
    }

    suspend fun setCustomAccent(argb: Long?) = edit { prefs ->
        if (argb == null) prefs.remove(Keys.customAccent)
        else prefs[Keys.customAccent] = argb
    }

    suspend fun setFontSize(sp: Float) = edit { it[Keys.fontSize] = sp.coerceIn(14f, 30f) }

    suspend fun setLineHeight(mul: Float) = edit { it[Keys.lineHeight] = mul.coerceIn(1.0f, 2.2f) }

    suspend fun setPageAnimation(enabled: Boolean) = edit { it[Keys.pageAnimation] = enabled }

    suspend fun setDimOverlay(value: Float) = edit { it[Keys.dimOverlay] = value.coerceIn(0f, 0.7f) }

    suspend fun setKeepScreenOn(enabled: Boolean) = edit { it[Keys.keepScreenOn] = enabled }

    suspend fun setTrackReadingStats(enabled: Boolean) = edit { it[Keys.trackReadingStats] = enabled }

    suspend fun setGridColumns(columns: Int) = edit { it[Keys.gridColumns] = columns.coerceIn(2, 5) }

    suspend fun setCoverAspectRatio(ratio: Float) = edit { it[Keys.coverRatio] = ratio.coerceIn(0.5f, 1.2f) }

    suspend fun setCardRadius(radius: RadiusPreset) = edit { it[Keys.cardRadius] = radius.name }

    suspend fun setCompactMetadata(enabled: Boolean) = edit { it[Keys.compactMetadata] = enabled }

    suspend fun setReduceMotion(enabled: Boolean) = edit { it[Keys.reduceMotion] = enabled }

    suspend fun setShowMangaHome(enabled: Boolean) = edit { it[Keys.showMangaHome] = enabled }

    suspend fun setDefaultTranslateLanguage(code: String) = edit {
        it[Keys.translateLang] = code.take(5).ifBlank { "id" }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }
}
