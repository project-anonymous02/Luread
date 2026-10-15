package com.luread.app.data.manga

import com.luread.app.data.network.HttpConfig
import com.luread.app.data.network.createHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uji logika LUREAD-FEAT pengisian lubang chapter dari Komiku:
 * kecocokan judul anti-decoy, sisipan nomor hilang, tanpa ganda/0pp.
 */
class MangaFillLogicTest {

    private val client = createHttpClient(HttpConfig())
    private val repo = MangaRepository(MangaDexApi(client))

    @After
    fun tearDown() {
        client.close()
    }

    private fun ch(
        id: String,
        number: String?,
        language: String?,
        pages: Int?,
        title: String? = null,
    ) = MangaRepository.ChapterSummary(
        id = id,
        number = number,
        title = title,
        language = language,
        pages = pages,
    )

    // ---- matchSlug ----

    @Test
    fun `matchSlug judul sama ternormalisasi — SPY×FAMILY menemukan Spy X Family`() {
        val slug = repo.matchSlug(
            "SPY×FAMILY",
            listOf("spy-x-family" to "Spy X Family"),
        )
        assertEquals("spy-x-family", slug)
    }

    @Test
    fun `matchSlug decoy spin-off tidak dicocokkan`() {
        // Kasus nyata: "One Piece: Ace Story" bukan "One Piece".
        assertNull(repo.matchSlug("One Piece", listOf("one-piece-ace-story" to "One Piece: Ace Story")))
        // Kandidat asli tetap menang lewat kecocokan penuh.
        assertEquals(
            "one-piece",
            repo.matchSlug(
                "One Piece",
                listOf("one-piece-ace-story" to "One Piece: Ace Story", "one-piece" to "One Piece"),
            ),
        )
    }

    @Test
    fun `matchSlug kontainmen kecil selisih maksimal 4 karakter tetap cocok`() {
        val slug = repo.matchSlug("One Piece", listOf("one-piecex" to "One Piece X"))
        assertEquals("one-piecex", slug)
    }

    @Test
    fun `matchSlug judul kosong atau tanpa kandidat cocok menghasilkan null`() {
        assertNull(repo.matchSlug("", listOf("a" to "Spy X Family")))
        assertNull(repo.matchSlug("Judul Asing Total", emptyList()))
        assertNull(repo.matchSlug("abc", listOf("abcdef" to "ABCDEF XYZ")))
    }

    // ---- fillChapters ----

    @Test
    fun `fillChapters mengisi lubang nomor dan mengurutkan numerik`() {
        val mdex = listOf(
            ch("md2", "2", "en", 20),
            ch("md4", "4", "en", 20),
            ch("md5", "5", "en", 20),
        )
        val extras = listOf(
            ch("komiku:ch1", "1", "id", null),
            ch("komiku:ch3", "3", "id", null),
            ch("komiku:ch6", "6", "id", null),
        )
        val result = repo.fillChapters(mdex, extras)
        assertEquals(
            listOf("1", "2", "3", "4", "5", "6"),
            result.mapNotNull { it.number },
        )
        assertEquals(listOf("komiku:ch1", "md2", "komiku:ch3", "md4", "md5", "komiku:ch6"), result.map { it.id })
    }

    @Test
    fun `fillChapters nomor yang sudah ada tidak digandakan`() {
        val mdex = listOf(ch("md-3", "3", "en", 20))
        val extras = listOf(
            ch("komiku:3", "3", "id", null),
            ch("komiku:7", "7", "id", null),
        )
        val result = repo.fillChapters(mdex, extras)
        assertEquals(listOf("md-3", "komiku:7"), result.map { it.id })
    }

    @Test
    fun `fillChapters label non-numerik dan pages 0 tidak disisipkan`() {
        val mdex = listOf(ch("md1", "1", "en", 10))
        val extras = listOf(
            ch("komiku:extra", "Extra", "id", null),
            ch("komiku:9", "9", "id", 0),
        )
        val result = repo.fillChapters(mdex, extras)
        assertEquals(listOf("md1"), result.map { it.id })
    }

    @Test
    fun `fillChapters extras kosong mengembalikan daftar identik`() {
        val mdex = listOf(ch("md1", "1", "en", 10))
        assertSame(mdex, repo.fillChapters(mdex, emptyList()))
    }

    // ---- dedup: pages null (Komiku) dipertahankan ----

    @Test
    fun `dedup mempertahankan chapter pages null dengan label pp tersembunyi`() {
        // pages=null = jumlah halaman belum diketahui, BUKAN 0 pp.
        val result = repo.dedupAndSort(listOf(ch("k1", "12", "id", null)))
        assertEquals(1, result.size)
        assertEquals("k1", result.single().id)
        assertNull(result.single().pages)
    }
}
