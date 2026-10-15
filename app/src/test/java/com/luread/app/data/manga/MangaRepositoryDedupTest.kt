package com.luread.app.data.manga

import com.luread.app.data.network.HttpConfig
import com.luread.app.data.network.createHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uji logika dedup & urutan chapter MangaDex (LUREAD-FIX: satu nomor satu
 * entri lintas bahasa, chapter eksternal pages=0 dibuang, urutan numerik).
 */
class MangaRepositoryDedupTest {

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

    @Test
    fun `dedup menghapus chapter ganda dengan nomor dan bahasa sama`() {
        val result = repo.dedupAndSort(
            listOf(
                ch("a", "1", "id", 20),
                ch("b", "1", "id", 20),
                ch("c", "2", "id", 18),
            ),
        )
        assertEquals(listOf("a", "c"), result.map { it.id })
    }

    @Test
    fun `dedup memilih chapter yang punya halaman daripada 0 pp`() {
        val result = repo.dedupAndSort(
            listOf(
                ch("zero-pages", "5", "en", 0),
                ch("has-pages", "5", "en", 22),
            ),
        )
        assertEquals(1, result.size)
        assertEquals("has-pages", result.single().id)
        assertTrue((result.single().pages ?: 0) > 0)
    }

    @Test
    fun `nomor sama antar bahasa digabung jadi satu, id menang`() {
        // LUREAD-FIX: satu nomor satu entri — id dipilih, en tidak dobel.
        val result = repo.dedupAndSort(
            listOf(
                ch("en-ch", "3", "en", 10, title = "English title"),
                ch("id-ch", "3", "id", 10, title = "Judul Indonesia"),
            ),
        )
        assertEquals(listOf("id-ch"), result.map { it.id })
    }

    @Test
    fun `urutan chapter numerik bukan leksikograf`() {
        val result = repo.dedupAndSort(
            listOf(
                ch("c10", "10", "en", 5),
                ch("c2", "2", "en", 5),
                ch("c1", "1", "en", 5),
                ch("cx", null, "en", 5),
            ),
        )
        assertEquals(listOf("c1", "c2", "c10", "cx"), result.map { it.id })
    }

    @Test
    fun `chapter eksternal tanpa gambar dibuang dari daftar`() {
        // Kasus nyata: en 1,2,3,138-140 Tensei Slime = tautan KManga, pages=0.
        val result = repo.dedupAndSort(
            listOf(
                ch("ext-1", "1", "en", 0),
                ch("ext-2", "2", "en", 0),
                ch("readable", "4", "en", 20),
            ),
        )
        assertEquals(listOf("readable"), result.map { it.id })
    }

    @Test
    fun `nomor hanya ada di bahasa lain tetap ikut tampil`() {
        // Kasus nyata: ch. 131-136 hanya ada di es-la/pt-br/ar.
        val result = repo.dedupAndSort(
            listOf(
                ch("id-130", "130", "id", 18),
                ch("es-131", "131", "es-la", 16),
                ch("ar-132", "132", "ar", 14),
                ch("id-133", "133", "id", 17),
            ),
        )
        assertEquals(listOf("id-130", "es-131", "ar-132", "id-133"), result.map { it.id })
    }

    @Test
    fun `id 0pp kalah dari en yang punya halaman`() {
        val result = repo.dedupAndSort(
            listOf(
                ch("id-zero", "7", "id", 0),
                ch("en-full", "7", "en", 12),
            ),
        )
        assertEquals(listOf("en-full"), result.map { it.id })
    }

    @Test
    fun `nomor sama beda judul scanlation tetap digabung satu`() {
        val result = repo.dedupAndSort(
            listOf(
                ch("grp-a", "9", "id", 12, title = "Title A"),
                ch("grp-b", "9", "id", 20, title = "Title B"),
            ),
        )
        assertEquals(listOf("grp-b"), result.map { it.id })
    }

    @Test
    fun `nomor kosong tidak digabung satu sama lain`() {
        val result = repo.dedupAndSort(
            listOf(
                ch("oneshot-a", null, "id", 30),
                ch("oneshot-b", null, "en", 25),
            ),
        )
        assertEquals(2, result.size)
    }
}
