package com.luread.app.data.network

import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder
import kotlinx.serialization.json.Json

/**
 * Klien MediaWiki generik (tanpa API key) untuk wiki berbasis MediaWiki yang
 * memakai Action API standar: Wikisource Indonesia dan Wikibooks Indonesia.
 *
 * Diverifikasi langsung 2026-10-13 (docs/book-apis-audit.md):
 *  * endpoint tunggal `…/w/api.php` dengan `format=json&formatversion=2`;
 *  * `list=search&srlimit=20` membalas 20 hasil dan `continue.sroffset`;
 *  * `list=allpages&aplimit=20` untuk browse tanpa kata kunci, dilanjutkan
 *    lewat `continue.apcontinue` (judul halaman terakhir);
 *  * `action=parse&prop=text&pageid=…` membalas HTML isi artikel
 *    (prop=extracts tidak berfungsi di wiki ini).
 *
 * [baseUrl] bisa diganti ke `https://id.wikibooks.org` untuk Wikibooks.
 */
class WikisourceApi(
    private val client: HttpClient,
    private val json: Json = GutendexApi.defaultJson,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://id.wikisource.org"
        const val WIKIBOOKS_BASE_URL = "https://id.wikibooks.org"
        const val PAGE_SIZE = 20

        fun searchUrl(baseUrl: String, term: String, offset: Int): String =
            URLBuilder("$baseUrl/w/api.php").apply {
                parameters.append("format", "json")
                parameters.append("formatversion", "2")
                parameters.append("action", "query")
                parameters.append("list", "search")
                parameters.append("srsearch", term)
                parameters.append("srnamespace", "0")
                parameters.append("srlimit", PAGE_SIZE.toString())
                if (offset > 0) parameters.append("sroffset", offset.toString())
            }.buildString()

        fun browseUrl(baseUrl: String, continueTitle: String?): String =
            URLBuilder("$baseUrl/w/api.php").apply {
                parameters.append("format", "json")
                parameters.append("formatversion", "2")
                parameters.append("action", "query")
                parameters.append("list", "allpages")
                parameters.append("aplimit", PAGE_SIZE.toString())
                if (!continueTitle.isNullOrEmpty()) parameters.append("apcontinue", continueTitle)
            }.buildString()

        fun parseUrl(baseUrl: String, pageid: Long): String =
            URLBuilder("$baseUrl/w/api.php").apply {
                parameters.append("format", "json")
                parameters.append("formatversion", "2")
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
        fetchJson(client, searchUrl(baseUrl, term, offset)) {
            json.decodeFromString(WikisourceSearchResponse.serializer(), it)
        }

    suspend fun browse(continueTitle: String?): ApiResult<WikisourceSearchResponse> =
        fetchJson(client, browseUrl(baseUrl, continueTitle)) {
            json.decodeFromString(WikisourceSearchResponse.serializer(), it)
        }

    suspend fun parse(pageid: Long): ApiResult<WikisourceParse> =
        fetchJson(client, parseUrl(baseUrl, pageid)) {
            json.decodeFromString(WikisourceParseResponse.serializer(), it).parse
                ?: throw IllegalArgumentException("parse missing")
        }
}
