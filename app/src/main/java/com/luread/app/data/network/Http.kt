package com.luread.app.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Konfigurasi jaringan yang bisa diubah dari Settings. */
data class HttpConfig(
    val requestTimeoutMs: Long = 30_000L,
    val connectTimeoutMs: Long = 15_000L,
)

/**
 * LUREAD-FEAT: timeout dari Settings (disetel realtime oleh AppContainer).
 *
 * Ktor 3 tidak mengekspos config plugin HttpTimeout yang sudah terpasang,
 * jadi nilai global ini dipakai lewat `timeout { … }` per-request di titik
 * pusat fetch (fetchJson, Gutendex, MangaDex, terjemahan).
 */
object NetworkTimeout {
    @Volatile
    var requestTimeoutMs: Long = 30_000L

    /** Terapkan nilai global pada builder request (dipanggil per request). */
    fun applyTo(builder: io.ktor.client.request.HttpRequestBuilder) {
        builder.timeout {
            requestTimeoutMillis = requestTimeoutMs
            socketTimeoutMillis = requestTimeoutMs
        }
    }
}

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
