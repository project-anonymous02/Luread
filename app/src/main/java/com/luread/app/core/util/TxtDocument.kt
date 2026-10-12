package com.luread.app.core.util

import java.io.File
import java.io.InputStream

/**
 * Dokumen teks sederhana untuk file .txt (dan HTML yang dibaca sebagai teks).
 *
 * Halaman dibagi pada batas karakter dengan preferensi berhenti di akhir baris
 * supaya tidak memotong kata. Pembacaan dibatasi [MAX_BYTES] agar file sangat
 * besar tidak membuat aplikasi kehabisan memori; batas ini dicatat di
 * docs/feature-matrix.md sebagai keterbatasan yang disengaja.
 */
data class TxtDocument(val pages: List<String>) {

    companion object {
        const val MAX_BYTES: Int = 4 * 1024 * 1024
        const val CHARS_PER_PAGE: Int = 3_500

        fun load(file: File): TxtDocument {
            require(file.isFile) { "File not found: ${file.name}" }
            val bytes = file.inputStream().use { input -> readCapped(input) }
            val text = String(bytes, Charsets.UTF_8)
            return TxtDocument(paginate(text))
        }

        fun paginate(text: String, pageSize: Int = CHARS_PER_PAGE): List<String> {
            val normalized = text.replace("\r\n", "\n")
            if (normalized.isBlank()) return listOf(normalized)
            if (normalized.length <= pageSize) return listOf(normalized)

            val pages = ArrayList<String>(normalized.length / pageSize + 1)
            var start = 0
            while (start < normalized.length) {
                val end = (start + pageSize).coerceAtMost(normalized.length)
                var cut = end
                if (end < normalized.length) {
                    val newline = normalized.lastIndexOf('\n', end - 1)
                    if (newline > start + pageSize / 2) cut = newline + 1
                }
                pages += normalized.substring(start, cut)
                start = cut
            }
            return pages
        }

        internal fun readCapped(input: InputStream): ByteArray {
            val buffer = ByteArray(MAX_BYTES)
            var total = 0
            while (total < MAX_BYTES) {
                val read = input.read(buffer, total, MAX_BYTES - total)
                if (read < 0) break
                total += read
            }
            return if (total == MAX_BYTES) buffer else buffer.copyOf(total)
        }
    }
}
