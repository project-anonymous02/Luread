package com.luread.app.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uji bus input pembaca (LUREAD-FEAT: tombol volume & tap zone berbagi
 * satu jalur ke pager aktif).
 */
class ReaderNavTest {

    @Test
    fun `ReaderNav meneruskan delta ke handler yang terdaftar`() {
        val nav = ReaderNav()
        assertFalse(nav.isRegistered)
        assertFalse(nav.move(1)) // tidak ada pembaca → false

        val received = mutableListOf<Int>()
        nav.register { received += it }
        assertTrue(nav.isRegistered)
        assertTrue(nav.move(1))
        assertTrue(nav.move(-1))
        assertEquals(listOf(1, -1), received)
    }

    @Test
    fun `ReaderNav berhenti menangani setelah di-unregister`() {
        val nav = ReaderNav()
        var count = 0
        nav.register { count++ }
        nav.move(1)
        nav.register(null)
        assertFalse(nav.move(1))
        assertEquals(1, count)
    }

    @Test
    fun `ReaderInputBus meneruskan gerakan dan membersihkan handler`() {
        ReaderInputBus.clear()
        assertNull(ReaderInputBus.handler)

        val received = mutableListOf<Int>()
        ReaderInputBus.handler = { received += it }
        ReaderInputBus.move(2)
        assertEquals(listOf(2), received)

        ReaderInputBus.clear()
        assertNull(ReaderInputBus.handler)
        ReaderInputBus.move(3) // tanpa handler → tidak crash
        assertEquals(listOf(2), received)
    }

    @Test
    fun `ReaderInputBus enabled default false dan bisa diubah`() {
        ReaderInputBus.clear()
        ReaderInputBus.enabled = false
        assertFalse(ReaderInputBus.enabled)
        ReaderInputBus.enabled = true
        assertTrue(ReaderInputBus.enabled)
        ReaderInputBus.enabled = false
    }
}
