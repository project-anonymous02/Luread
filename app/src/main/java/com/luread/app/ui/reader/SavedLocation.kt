package com.luread.app.ui.reader

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Posisi baca milik Luread, sengaja dibuat sederhana dan milik aplikasi ini
 * sendiri sehingga penyimpanan tidak terikat pada format lokasi Readium.
 *
 * File ini bebas dependensi Android/Readium agar bisa diuji langsung sebagai
 * unit test JVM murni.
 */
@Serializable
data class SavedLocation(
    val href: String,
    val progression: Double? = null,
    val text: String? = null,
    /** Halaman (PDF/teks) atau offset karakter. */
    val page: Int? = null,
)

object LocationCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun decode(raw: String?): SavedLocation? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(SavedLocation.serializer(), raw) }.getOrNull()
    }

    fun encode(location: SavedLocation): String =
        runCatching { json.encodeToString(SavedLocation.serializer(), location) }.getOrDefault("")
}
