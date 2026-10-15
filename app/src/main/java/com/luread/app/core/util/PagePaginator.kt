package com.luread.app.core.util

import kotlin.math.floor

/**
 * Pembagian teks menjadi halaman visual untuk pembaca horizontal.
 *
 * LUREAD-FIX: pembaca tidak boleh menggulir vertikal, sehingga teks harus
 * dipotong persis sebanyak yang muat di satu layar. Semua fungsi di sini
 * murni (tanpa Android/Compose) agar bisa diuji unit: pengukuran baris
 * layout asli disuntikkan lewat lambda [lineEnd] (indeks karakter setelah
 * baris terakhir), biasanya dari `TextLayoutResult.getLineEnd`.
 */
object PagePaginator {

    /** Jumlah baris yang muat pada tinggi area [areaHeightPx] dengan tinggi baris [lineHeightPx]. */
    fun linesPerPage(areaHeightPx: Float, lineHeightPx: Float): Int {
        if (areaHeightPx.isFinite().not() || lineHeightPx.isFinite().not()) return 1
        if (areaHeightPx <= 0f || lineHeightPx <= 0f) return 1
        return floor(areaHeightPx / lineHeightPx).toInt().coerceAtLeast(1)
    }

    /**
     * Bagi [totalLines] baris menjadi rentang baris per halaman
     * berisi paling banyak [linesPerPage] baris.
     */
    fun lineRanges(totalLines: Int, linesPerPage: Int): List<IntRange> {
        if (totalLines <= 0) return emptyList()
        val per = linesPerPage.coerceAtLeast(1)
        val ranges = ArrayList<IntRange>(totalLines / per + 1)
        var start = 0
        while (start < totalLines) {
            val end = (start + per - 1).coerceAtMost(totalLines - 1)
            ranges += start..end
            start = end + 1
        }
        return ranges
    }

    /**
     * Potong [text] menjadi halaman: setiap halaman berisi maksimal
     * [linesPerPage] baris, diukur lewat [lineEnd] (inklusif-eksklusif,
     * monoton tidak menurun, dipotong pada rentang teks yang valid).
     *
     * @param totalLines jumlah baris hasil layout teks penuh.
     * @param lineEnd lambda: nomor baris (0-based) -> offset karakter setelah baris itu.
     */
    fun paginate(
        text: String,
        totalLines: Int,
        linesPerPage: Int,
        lineEnd: (Int) -> Int,
    ): List<String> {
        if (text.isEmpty()) return listOf("")
        if (totalLines <= 0 || linesPerPage <= 1) return listOf(text)

        val pages = ArrayList<String>(totalLines / linesPerPage + 1)
        var cursor = 0
        for (range in lineRanges(totalLines, linesPerPage)) {
            val rawEnd = lineEnd(range.last)
            val end = rawEnd.coerceIn(cursor, text.length)
            if (end > cursor) {
                pages += text.substring(cursor, end)
                cursor = end
            }
        }
        if (cursor < text.length) pages += text.substring(cursor)
        if (pages.isEmpty()) return listOf(text)
        return pages
    }

    /**
     * Pembagian cadangan tanpa layout: bagi teks per [charsPerPage] pada
     * batas spasi bila mungkin (tidak memotong kata) — dipakai sebelum
     * hasil pengukuran tersedia dan untuk teks yang sangat besar.
     */
    fun paginateByChars(text: String, charsPerPage: Int): List<String> {
        if (text.isEmpty()) return listOf("")
        val size = charsPerPage.coerceAtLeast(1)
        if (text.length <= size) return listOf(text)

        val pages = ArrayList<String>(text.length / size + 1)
        var start = 0
        while (start < text.length) {
            var end = (start + size).coerceAtMost(text.length)
            if (end < text.length) {
                val window = text.substring(start, end)
                val lastBreak = window.lastIndexOf(' ').coerceAtLeast(window.lastIndexOf('\n'))
                if (lastBreak > size / 2) end = start + lastBreak + 1
            }
            pages += text.substring(start, end)
            start = end
        }
        return pages
    }
}
