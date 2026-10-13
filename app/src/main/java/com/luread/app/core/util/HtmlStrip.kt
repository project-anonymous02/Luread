package com.luread.app.core.util

/**
 * Konversi HTML menjadi teks polos untuk ringkasan dan simpanan baca.
 *
 * Fungsi murni tanpa Android sehingga bisa diuji unit test. Dipakai untuk:
 *  * merangkum artikel Wikisource pada layar detail (ringkasan singkat);
 *  * (perentalah) kebutuhan lain yang memerlukan teks dari HTML.
 */
object HtmlStrip {

    private val scriptOrStyle = Regex("(?is)<(script|style)[^>]*>.*?</\\1>")
    private val br = Regex("(?i)<br\\s*/?\\s*>")
    private val blockEnd = Regex("(?i)</\\s*(p|div|li|h[1-6]|tr|section|article|blockquote)\\s*>")
    private val anyTag = Regex("<[^>]+>")
    private val spaces = Regex("[ \\t\\x0B\\f\\r]+")
    private val lineSpaces = Regex(" *\\n *")
    private val blankLines = Regex("\\n{3,}")
    private val numericEntity = Regex("&#(x?[0-9a-fA-F]+);")

    /** HTML (fragment maupun halaman penuh) -> teks polos teratur. */
    fun toText(html: String): String {
        var s = scriptOrStyle.replace(html, " ")
        s = br.replace(s, "\n")
        s = blockEnd.replace(s, "\n\n")
        s = anyTag.replace(s, "")
        s = decodeEntities(s)
        s = spaces.replace(s, " ")
        s = lineSpaces.replace(s, "\n")
        s = blankLines.replace(s, "\n\n")
        return s.trim()
    }

    private fun decodeEntities(text: String): String {
        var s = numericEntity.replace(text) { match ->
            val body = match.groupValues[1]
            val code = if (body.startsWith("x") || body.startsWith("X")) {
                body.substring(1).toIntOrNull(16)
            } else {
                body.toIntOrNull(10)
            }
            code?.let { cp -> if (cp in 1..0x10FFFF) String(Character.toChars(cp)) else null }
                ?: match.value
        }
        s = s.replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
        s = s.replace("&amp;", "&")
        return s
    }
}
