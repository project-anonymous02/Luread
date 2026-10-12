package com.luread.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.luread.app.core.design.AccentPreset
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
    /** Ukuran teks baca dalam sp, rentang wajar 14..30. */
    val fontSizeSp: Float = 18f,
    /** Layar tetap menyala selama membaca. */
    val keepScreenOn: Boolean = true,
    /** Statistik membaca di tab Aktivitas. */
    val trackReadingStats: Boolean = true,
) {
    val clampedFontSize: Float get() = fontSizeSp.coerceIn(14f, 30f)
}

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val accent = stringPreferencesKey("accent")
        val fontSize = floatPreferencesKey("font_size_sp")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val trackReadingStats = booleanPreferencesKey("track_reading_stats")
    }

    val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            themeMode = prefs[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            accent = prefs[Keys.accent]?.let { name ->
                AccentPreset.entries.firstOrNull { it.name == name }
            } ?: AccentPreset.entries.first(),
            fontSizeSp = prefs[Keys.fontSize] ?: 18f,
            keepScreenOn = prefs[Keys.keepScreenOn] ?: true,
            trackReadingStats = prefs[Keys.trackReadingStats] ?: true,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.themeMode] = mode.name }

    suspend fun setAccent(accent: AccentPreset) = edit { it[Keys.accent] = accent.name }

    suspend fun setFontSize(sp: Float) = edit { it[Keys.fontSize] = sp.coerceIn(14f, 30f) }

    suspend fun setKeepScreenOn(enabled: Boolean) = edit { it[Keys.keepScreenOn] = enabled }

    suspend fun setTrackReadingStats(enabled: Boolean) = edit { it[Keys.trackReadingStats] = enabled }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }
}
