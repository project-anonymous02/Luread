package com.luread.app.data.manga

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Uji DTO MangaDex — LUREAD-FEAT `externalUrl` (fallback manga tanpa chapter)
 * dan field lain yang dipakai UI.
 */
class MangaDexModelTest {

    private val json = MangaDexApi.defaultJson

    @Test
    fun `externalUrl diambil dari attributes dan di-trim`() {
        val manga = json.decodeFromString(
            MangaDexManga.serializer(),
            """
            {"id":"abc","type":"manga","relationships":[],"attributes":{
              "title":{"en":"Solo Leveling"},
              "externalUrl":" https://example.com/solo "
            }}
            """.trimIndent(),
        )
        assertEquals("https://example.com/solo", manga.externalUrl)
    }

    @Test
    fun `externalUrl kosong atau hilang menjadi null`() {
        val missing = json.decodeFromString(
            MangaDexManga.serializer(),
            """{"id":"a","type":"manga","attributes":{"title":{"en":"X"}}}""",
        )
        assertNull(missing.externalUrl)
        val blank = json.decodeFromString(
            MangaDexManga.serializer(),
            """{"id":"b","type":"manga","attributes":{"title":{"en":"Y"},"externalUrl":"   "}}""",
        )
        assertNull(blank.externalUrl)
    }

    @Test
    fun `title jatuh ke Manga dan description kosong menjadi null`() {
        val manga = json.decodeFromString(
            MangaDexManga.serializer(),
            """{"id":"c","type":"manga","attributes":{"description":{"en":"  "}}}""",
        )
        assertEquals("Manga", manga.title)
        assertNull(manga.description)
    }

    @Test
    fun `coverUrl dibangun dari hubungan cover_art`() {
        val manga = json.decodeFromString(
            MangaDexManga.serializer(),
            """
            {"id":"123","type":"manga","attributes":{},"relationships":[
              {"id":"x","type":"cover_art","attributes":{"fileName":"cover.jpg"}}
            ]}
            """.trimIndent(),
        )
        assertEquals("https://uploads.mangadex.org/covers/123/cover.jpg.256.jpg", manga.coverUrl)
    }

    @Test
    fun `pageUrl memilih kualitas data atau data-saver dan menolak index di luar jangkauan`() {
        val chapter = MangaDexAtHomeChapter(
            hash = "h",
            data = listOf("1.png", "2.png"),
            dataSaver = listOf("s1.jpg"),
        )
        assertEquals(
            "https://base/data/h/1.png",
            chapter.pageUrl("https://base", 0),
        )
        assertEquals(
            "https://base/data-saver/h/s1.jpg",
            chapter.pageUrl("https://base", 0, saver = true),
        )
        assertNull(chapter.pageUrl("https://base", 5))
        assertNull(chapter.pageUrl("https://base", 1, saver = true))
    }
}
