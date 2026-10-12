package com.luread.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {

    @Test
    fun `percent membulatkan pecahan menjadi persen`() {
        assertEquals("0%", Format.percent(0f))
        assertEquals("50%", Format.percent(0.5f))
        assertEquals("100%", Format.percent(1f))
        assertEquals("100%", Format.percent(2f))
        assertEquals("0%", Format.percent(-1f))
    }

    @Test
    fun `bytes memakai satuan yang masuk akal`() {
        assertEquals("—", Format.bytes(0))
        assertEquals("—", Format.bytes(-5))
        assertEquals("512 B", Format.bytes(512))
        assertEquals("1 KB", Format.bytes(1024))
        assertEquals("1.5 MB", Format.bytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("24 MB", Format.bytes(24_836_548L))
    }

    @Test
    fun `duration merender jam dan menit`() {
        assertEquals("0m", Format.duration(0))
        assertEquals("5m", Format.duration(5))
        assertEquals("1h 0m", Format.duration(60))
        assertEquals("2h 30m", Format.duration(150))
    }

    @Test
    fun `listJoin membatasi jumlah item`() {
        assertEquals("—", Format.listJoin(emptyList()))
        assertEquals("a", Format.listJoin(listOf("a")))
        assertEquals("a, b, c", Format.listJoin(listOf("a", "b", "c")))
        assertEquals("a, b, c +2", Format.listJoin(listOf("a", "b", "c", "d", "e")))
    }

    @Test
    fun `isSameDay membandingkan tanggal kalender`() {
        val now = 1_700_000_000_000L
        assertTrue(Format.isSameDay(now, now + 60_000L))
        assertTrue(!Format.isSameDay(now, now + 26L * 60 * 60 * 1000))
    }

    @Test
    fun `durationSeconds format jam dan menit detik`() {
        assertEquals("00:05", Format.durationSeconds(5))
        assertEquals("01:05", Format.durationSeconds(65))
        assertEquals("1:01:01", Format.durationSeconds(3661))
    }
}
