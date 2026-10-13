package com.luread.app.data.catalog

/**
 * Sumber katalog buku gratis yang didukung Luread.
 *
 * Setiap sumber memakai kunci komposit `id:remote-id`:
 *  * `gutenberg:1342`            -> Project Gutenberg lewat Gutendex;
 *  * `openlibrary:OL66554W`      -> Open Library (ID karya tanpa `/works/`);
 *  * `archive:prideprejudice..`  -> Internet Archive (identifier item);
 *  * `wikisource:33610`          -> Wikisource berbahasa Indonesia (pageid).
 *
 * Kunci pernah mengandung `/` tidak diizinkan karena nama berkas unduhan
 * dibentuk dari kunci (lihat DownloadManager.safeName).
 */
enum class BookSource(val id: String) {
    GUTENDEX("gutenberg"),
    OPEN_LIBRARY("openlibrary"),
    INTERNET_ARCHIVE("archive"),
    WIKISOURCE("wikisource");

    val prefix: String
        get() = "$id:"

    /** ID remote dari kunci, atau null bila kunci bukan milik sumber ini. */
    fun remoteIdOf(key: String): String? =
        if (key.startsWith(prefix)) key.substring(prefix.length).takeIf { it.isNotEmpty() } else null

    companion object {
        /**
         * Baca sumber dari kunci. Kunci yang tidak dikenal (mis. impor lokal
         * `local:…`) jatuh kembali ke [GUTENDEX] sehingga pemanggil lama tetap
         * aman; [remoteIdOf] akan mengembalikan null untuk detail.
         */
        fun fromKey(key: String): BookSource =
            entries.firstOrNull { key.startsWith(it.prefix) } ?: GUTENDEX
    }
}
