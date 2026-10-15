package com.luread.app.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Uji deteksi magic byte (LUREAD-FIX: sniff MOBI butuh 68 byte, penambahan
 * deteksi RAR dan HTML — lihat Formats.sniff).
 */
class SniffFormatsTest {

    private fun head(vararg bytes: Int): ByteArray = ByteArray(bytes.size) { bytes[it].toByte() }

    @Test
    fun `sniff mengenali zip dan pdf`() {
        assertEquals(
            Formats.EPUB_OR_ZIP,
            Formats.sniff(head('P'.code, 'K'.code, 3, 4)),
        )
        assertEquals(
            Formats.MIME_PDF,
            Formats.sniff(head('%'.code, 'P'.code, 'D'.code, 'F'.code)),
        )
    }

    @Test
    fun `sniff mengenali MOBI dari tag BOOKMOBI di offset 60`() {
        val mobi = ByteArray(68)
        mobi[60] = 'B'.code.toByte(); mobi[61] = 'O'.code.toByte()
        mobi[62] = 'O'.code.toByte(); mobi[63] = 'K'.code.toByte()
        mobi[64] = 'M'.code.toByte(); mobi[65] = 'O'.code.toByte()
        mobi[66] = 'B'.code.toByte(); mobi[67] = 'I'.code.toByte()
        assertEquals(Formats.MIME_MOBI, Formats.sniff(mobi))
        // Kepala 8 byte saja (kasus lama) tidak cukup → null, bukan salah tebak.
        assertNull(Formats.sniff(mobi.copyOf(8)))
    }

    @Test
    fun `sniff mengenali RAR meski ekstensi mengaku cbz`() {
        // RAR4: "Rar!\x1A\x07"
        val rar4 = head('R'.code, 'a'.code, 'r'.code, '!'.code, 0x1A, 0x07, 0, 0)
        assertEquals(Formats.MIME_RAR, Formats.sniff(rar4))
        // RAR5: "Rar!\x1A\x07\x01"
        val rar5 = head('R'.code, 'a'.code, 'r'.code, '!'.code, 0x1A, 0x07, 0x01, 0)
        assertEquals(Formats.MIME_RAR, Formats.sniff(rar5))
    }

    @Test
    fun `sniff mengenali HTML dan FB2 dari awal tag`() {
        val html = "<!DOCTYPE html><html>".toByteArray()
        assertEquals(Formats.MIME_HTML, Formats.sniff(html))
        val html2 = "<html><body>".toByteArray()
        assertEquals(Formats.MIME_HTML, Formats.sniff(html2))
        val fb2 = "<?xml version='1.0'?><FictionBook xmlns='http://www.gribuser.ru/xml/fb2'>".toByteArray()
        assertEquals(Formats.MIME_FB2, Formats.sniff(fb2))
    }

    @Test
    fun `sniff mengenali RTF dan menolak teks polos`() {
        val rtf = "{\\rtf1\\ansi hello}".toByteArray()
        assertEquals(Formats.MIME_RTF, Formats.sniff(rtf))
        assertNull("teks polos tidak boleh ditebak".toByteArray().let { Formats.sniff(it) })
        assertNull(Formats.sniff(ByteArray(0)))
        assertNull(Formats.sniff(head('P'.code))) // terlalu pendek
    }
}
