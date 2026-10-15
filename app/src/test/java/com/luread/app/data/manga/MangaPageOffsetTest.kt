package com.luread.app.data.manga

import org.junit.Assert.assertEquals
import org.junit.Test

/** Uji offset MangaPage (LUREAD-FEAT: sumber ganda tak boleh menggeser halaman). */
class MangaPageOffsetTest {

    private fun items(n: Int) = List(n) {
        MangaRepository.MangaSummary(
            id = "id$it",
            title = "T$it",
            coverUrl = null,
            authorNames = emptyList(),
            tags = emptyList(),
            status = null,
            description = null,
        )
    }

    @Test
    fun `nextOffset otomatis mengikuti konsumsi halaman`() {
        val page = MangaRepository.MangaPage(results = items(20), offset = 0, total = 100)
        assertEquals(20, page.nextOffset)
        val last = MangaRepository.MangaPage(results = items(20), offset = 80, total = 100)
        assertEquals(null, last.nextOffset)
    }

    @Test
    fun `nextOffset manual menang saat results memuat sumber tambahan`() {
        // 20 hasil MangaDex + 5 hasil Komiku; halaman MangaDex berikutnya tetap 20.
        val page = MangaRepository.MangaPage(
            results = items(25),
            offset = 0,
            total = 105,
            nextOffset = 20,
        )
        assertEquals(20, page.nextOffset)
    }
}
