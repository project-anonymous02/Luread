package com.luread.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entitas Room untuk data lokal Luread.
 *
 * Keputusan desain:
 *  * kunci buku adalah string komposit sumber-id (mis. gutenberg:1342) sehingga
 *  * beberapa sumber katalog hidup berdampingan tanpa tabrakan id; layar
 *    perpustakaan dan aktivitas tetap terbaca tanpa jaringan;
 *  * daftar penulis/simpan sebagai teks gabungan (bukan relasi) karena model
 *    sumber hanya dipakai untuk tampilan dan tidak perlu diquery per-orang;
 *  * lokasi baca disimpan sebagai JSON string hasil serialisasi Readium,
 *    bukan kolom terstruktur, agar perubahan skema navigator tidak memaksa
 *    migrasi database;
 *  * tidak ada kolom ukuran file dari katalog karena API Gutendex tidak
 *    menyediakan ukuran -> nilai diisi saat unduhan benar-benar berjalan.
 */
@Entity(
    tableName = "books",
    indices = [Index(value = ["addedAt"]), Index(value = ["lastOpenedAt"])],
)
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val authorsText: String,
    val summary: String?,
    val languagesText: String,
    val subjectsText: String,
    val coverUrl: String?,
    val sourceUrl: String?,
    val contentType: String?,
    val localPath: String?,
    val downloadState: String = DownloadState.NONE.name,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    /** 0f..1f, pembacaan progres kumulatif. */
    val progress: Float = 0f,
    val locationJson: String? = null,
    val finished: Boolean = false,
    val favorite: Boolean = false,
    val addedAt: Long = 0L,
    val lastOpenedAt: Long = 0L,
    /** Nama rak/koleksi kustom (null = tanpa koleksi). */
    val collectionName: String? = null,
) {
    enum class DownloadState { NONE, QUEUED, RUNNING, DONE, FAILED }
}

/** Riwayat membaca untuk tab Aktivitas. */
@Entity(
    tableName = "sessions",
    indices = [Index(value = ["bookId"]), Index(value = ["startedAt"])],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val bookId: String,
    val bookTitle: String,
    val startedAt: Long,
    val endedAt: Long,
    val progressBefore: Float,
    val progressAfter: Float,
) {
    val durationMillis: Long get() = (endedAt - startedAt).coerceAtLeast(0L)
}

/** Penanda baca dan sorotan. */
@Entity(
    tableName = "annotations",
    indices = [Index(value = ["bookId"]), Index(value = ["createdAt"])],
)
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val bookId: String,
    val href: String?,
    val text: String,
    val note: String = "",
    val kind: String = Kind.BOOKMARK.name,
    /**
     * Lokasi baca tersimpan sebagai JSON kecil milik Luread
     * (bukan objek Readium) supaya skema database tidak bergantung pada
     * versi navigator.
     */
    val locationJson: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    enum class Kind { BOOKMARK, HIGHLIGHT, NOTE }
}
