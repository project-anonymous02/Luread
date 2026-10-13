package com.luread.app.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Konfigurasi jaringan yang bisa diubah dari Settings. */
data class HttpConfig(
    val requestTimeoutMs: Long = 20_000L,
    val connectTimeoutMs: Long = 10_000L,
)

/**
 * Membangun HttpClient Ktor dengan engine OkHttp.
 *
 * `expectSuccess = false` disengaja: Gutendex mengembalikan body HTML berstatus
 * 503 untuk sebagian kondisi galat, sehingga status harus ditangani manual
 * daripada dilempar sebagai exception yang tidak bisa dibedakan pengguna.
 *
 * Konfigurasi engine OkHttp tidak diperlukan: follow-redirect dan
 * retry-on-connection-failure sudah default aktif di OkHttp.
 */
fun createHttpClient(config: HttpConfig): HttpClient = HttpClient(OkHttp) {
    expectSuccess = false

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                isLenient = true
                encodeDefaults = false
            }
        )
    }

    install(HttpTimeout) {
        requestTimeoutMillis = config.requestTimeoutMs
        connectTimeoutMillis = config.connectTimeoutMs
        socketTimeoutMillis = config.requestTimeoutMs
    }
}
