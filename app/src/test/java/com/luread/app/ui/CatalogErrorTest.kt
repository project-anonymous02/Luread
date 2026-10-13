package com.luread.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogErrorTest {

    @Test
    fun `pesan galat jaringan dan timeout menjelaskan aksi pengguna`() {
        val network = ApiErrorShim.network().toUserMessage()
        val timeout = ApiErrorShim.timeout().toUserMessage()

        assertTrue(network.contains("internet connection"))
        assertTrue(timeout.contains("Try again"))
    }

    @Test
    fun `http 503 diberi pesan khusus karena teramati di audit`() {
        val message = ApiErrorShim.http503().toUserMessage()
        assertTrue(message.contains("503"))
        assertTrue(message.contains("busy"))
    }

    @Test
    fun `respons non json menyatakan gangguan server`() {
        val message = ApiErrorShim.notJson().toUserMessage()
        assertTrue(message.contains("not valid"))
    }

    @Test
    fun `buku tidak ditemukan memakai pesan khusus bukan pesan jaringan`() {
        val message = ApiErrorShim.notFound().toUserMessage()
        assertTrue(message.contains("not found"))
        assertTrue(!message.contains("internet"))
    }
}

private object ApiErrorShim {
    fun network() = com.luread.app.data.network.ApiError.Network("failed to connect")
    fun timeout() = com.luread.app.data.network.ApiError.Timeout("timeout")
    fun http503() = com.luread.app.data.network.ApiError.Http(503, "Service Unavailable")
    fun notJson() = com.luread.app.data.network.ApiError.NotJson("HTML")
    fun notFound() = com.luread.app.data.network.ApiError.NotFound("Book missing")
}
