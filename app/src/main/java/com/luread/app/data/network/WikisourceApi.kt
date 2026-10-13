package com.luread.app.data.network

import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder
import kotlinx.serialization.json.Json

/**
 * Klien MediaWiki Wikisource Indonesia (tanpa API key).
 *
 * Diverifikasi langsung 2026-10-13 (docs/book-apis-audit.md):
 *  * endpoint tunggal `https://id.wikisource.org/w/api.php` dengan
 *    `format=json&formatversion=2`;
 *  * `list=search&srlimit=20` membalas 20 hasil dan `continue.sroffset`;
 *  * `list=allpages&aplimit=20` untuk browse tanpa kata kunci, dilanjutkan
 *    lewat `continue.apcontinue` (judul halaman terakhir);
 *  * `action=parse&prop=text&pageid=…` membalas HTML isi artikel
 *    (prop=extracts tidak berfungsi di wiki ini).
 */
class WikisourceApi(
    private val client: HttpClient,
    private val json: Json = GutendexApi.defaultJson,
) {
    companion object {
        const val BASE_URL = "https://id.wikisource.org"
        const val API_URL = "$BASE_URL/w/api.php"
        const val PAGE_SIZE = 20

        private fun base(): URLBuilder = URLBuilder(API_URL).apply {
            parameters.append("format", "json")
            parameters.append("formatversion", "2")
        }

        /** Pencarian artikel (offset 0-based, kelipatan halaman). */
        fun searchUrl(term: String, offset: Int): String = base().apply {
            parameters.append("action", "query")
            parameters.append("list", "search")
            parameters.append("srsearch", term)
            parameters.append("srnamespace", "0")
            parameters.append("srlimit", PAGE_SIZE.toString())
            if (offset > 0) parameters.append("sroffset", offset.toString())
        }.buildString()

        /** Browse seluruh halaman utama; `continueTitle` = hasil terakhir. */
        fun browseUrl(continueTitle: String?): String = base().apply {
            parameters.append("action", "query")
            parameters.append("list", "allpages")
            parameters.append("aplimit", PAGE_SIZE.toString())
            if (!continueTitle.isNullOrEmpty()) parameters.append("apcontinue", continueTitle)
        }.buildString()

        /** HTML isi halaman (untuk ringkasan sekaligus unduhan baca). */
        fun parseUrl(pageid: Long): String = base().apply {
            parameters.append("action", "parse")
            parameters.append("pageid", pageid.toString())
            parameters.append("prop", "text")
        }.buildString()

        /** Bungkus fragment HTML parser menjadi dokumen yang layak dibaca WebView. */
        fun wrapDocument(title: String, fragment: String): String = buildString {
            append("<!doctype html>\n<html><head><meta charset=\"utf-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            append("<title>").append(escapeHtml(title)).append("</title>")
            append("<style>body{font-family:sans-serif;line-height:1.6;margin:16px;")
            append("font-size:16px;word-wrap:break-word}img{max-width:100%;height:auto}</style>")
            append("</head><body>")
            append(fragment)
            append("</body></html>")
        }

        fun escapeHtml(text: String): String = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    suspend fun search(term: String, offset: Int): ApiResult<WikisourceSearchResponse> =
        fetchJson(client, searchUrl(term, offset)) {
            json.decodeFromString(WikisourceSearchResponse.serializer(), it)
        }

    suspend fun browse(continueTitle: String?): ApiResult<WikisourceSearchResponse> =
        fetchJson(client, browseUrl(continueTitle)) {
            json.decodeFromString(WikisourceSearchResponse.serializer(), it)
        }

    suspend fun parse(pageid: Long): ApiResult<WikisourceParse> =
        fetchJson(client, parseUrl(pageid)) {
            json.decodeFromString(WikisourceParseResponse.serializer(), it).parse
                ?: throw IllegalArgumentException("parse missing")
        }
}
