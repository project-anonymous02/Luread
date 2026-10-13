package com.luread.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlStripTest {

    @Test
    fun `tag dibuang dan blok dipisah baris kosong`() {
        val text = HtmlStrip.toText("<p>Paragraf satu.</p><p>Paragraf dua.</p>")
        assertEquals("Paragraf satu.\n\nParagraf dua.", text)
    }

    @Test
    fun `script dan style ikut hilang beserta isinya`() {
        val text = HtmlStrip.toText("<style>p{color:red}</style><script>alert(1)</script><p>Aman</p>")
        assertEquals("Aman", text)
    }

    @Test
    fun `br dan heading menjadi pemisah baris`() {
        val text = HtmlStrip.toText("Satu<br>Dua<h3>Judul</h3>Tiga")
        assertTrue(text.startsWith("Satu\nDua"))
        assertTrue(text.endsWith("Tiga"))
        assertTrue(text.contains("Judul"))
    }

    @Test
    fun `entitas html dinormalisasi termasuk numerik`() {
        val text = HtmlStrip.toText("<p>a &amp; b &lt;c&gt; &nbsp;x &#65;&#x42;</p>")
        assertEquals("a & b <c> x AB", text)
    }

    @Test
    fun `spasi berlebih dirapikan tanpa menghilangkan struktur paragraf`() {
        val text = HtmlStrip.toText("<p>  Kata   1  </p>\n\n\n<p>Kata\n2</p>")
        assertEquals("Kata 1\n\nKata\n2", text)
    }

    @Test
    fun `html kosong dan tag saja menghasilkan string kosong`() {
        assertEquals("", HtmlStrip.toText(""))
        assertEquals("", HtmlStrip.toText("<div></div>"))
        assertFalse(HtmlStrip.toText("<img src=x>").contains("<"))
    }
}
