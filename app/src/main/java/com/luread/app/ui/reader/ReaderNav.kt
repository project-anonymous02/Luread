package com.luread.app.ui.reader

/**
 * Bus input pembaca.
 *
 * LUREAD-FEAT: tombol volume (dihandle [com.luread.app.MainActivity]) dan
 * tap zone di layar pembaca berbagi satu jalur menuju pager yang sedang
 * aktif. Satu pembaca mendaftarkan dirinya; aktivitas memanggil [move].
 */
object ReaderInputBus {
    /** Ikuti pengaturan "volume key navigasi" di Settings. */
    @Volatile
    var enabled: Boolean = false

    @Volatile
    var handler: ((Int) -> Unit)? = null

    /** +1 = halaman berikutnya, -1 = halaman sebelumnya. */
    fun move(delta: Int) {
        handler?.invoke(delta)
    }

    fun clear() {
        handler = null
    }
}

/**
 * Navigasi halaman dalam satu layar pembaca: tap zone kiri/tengah/kanan,
 * tombol volume, dan kontrol chrome memakai instance ini.
 *
 * Pembaca (EPUB/teks/PDF/CBZ/HTML/manga) mendaftarkan callback gerak;
 * [ReaderScreen] memakainya untuk tap zone dan volume key.
 */
class ReaderNav {
    @Volatile
    private var moveHandler: ((Int) -> Unit)? = null

    val isRegistered: Boolean get() = moveHandler != null

    fun register(handler: ((Int) -> Unit)?) {
        moveHandler = handler
    }

    /** @return true bila ada pembaca yang menangani gerakan ini. */
    fun move(delta: Int): Boolean {
        val handler = moveHandler ?: return false
        handler(delta)
        return true
    }
}
