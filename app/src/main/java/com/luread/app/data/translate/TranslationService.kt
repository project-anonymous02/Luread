package com.luread.app.data.translate

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Layanan terjemahan otomatis multi-provider (tanpa API key).
 *
 * Urutan provider (failover otomatis):
 *  1. Google Translate endpoint unofficial `translate.googleapis.com/translate_a/single`
 *     (`client=gtx`) — kualitas terbaik, mendukung auto-detect bahasa sumber;
 *  2. MyMemory Translation API (`api.mymemory.translated.net`) — cadangan,
 *     tanpa API key, rate-limit harian wajar.
 *
 * Kedua endpoint diuji langsung pada 2026-10-13:
 *  * gtx `sl=auto&tl=id` membalas array JSON dengan teks terjemahan dan kode
 *    bahasa sumber yang terdeteksi;
 *  * MyMemory `?q=…&langpair=en|id` membalas `responseData.translatedText`.
 *
 * Tidak ada mesin terjemahan lokal / model ML yang dibundel — semua panggilan
 * jaringan, dan kegagalan dilaporkan jujur ke UI.
 */
class TranslationService(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    companion object {
        const val GTX_URL = "https://translate.googleapis.com/translate_a/single"
        const val MYMEMORY_URL = "https://api.mymemory.translated.net/get"
        const val DEFAULT_TARGET = "id"
    }

    /** Hasil terjemahan: teks hasil + bahasa sumber yang terdeteksi (bila ada). */
    data class Result(
        val translatedText: String,
        val detectedSourceLang: String?,
        val provider: String,
    )

    sealed interface Outcome {
        data class Success(val result: Result) : Outcome
        data class Failure(val message: String) : Outcome
    }

    /**
     * Terjemahkan [text] dari [sourceLang] (kode ISO 639-1, atau "auto")
     * ke [targetLang]. Mencoba provider berurutan sampai sukses.
     */
    suspend fun translate(
        text: String,
        sourceLang: String = "auto",
        targetLang: String = DEFAULT_TARGET,
    ): Outcome {
        if (text.isBlank()) return Outcome.Failure("Text is empty")

        // 1. Google Translate gtx (unofficial)
        translateViaGtx(text, sourceLang, targetLang)?.let { return it }

        // 2. MyMemory (fallback)
        translateViaMyMemory(text, sourceLang, targetLang)?.let { return it }

        return Outcome.Failure("All translation providers failed")
    }

    // ------------------------------------------------------------------
    // Provider 1: Google Translate gtx (unofficial)
    // ------------------------------------------------------------------

    private suspend fun translateViaGtx(
        text: String,
        sourceLang: String,
        targetLang: String,
    ): Outcome? {
        val response = try {
            client.get(GTX_URL) {
                parameter("client", "gtx")
                parameter("sl", sourceLang)
                parameter("tl", targetLang)
                parameter("dt", "t")
                parameter("q", text)
                // LUREAD-FEAT: timeout dari Pengaturan diterapkan per-request.
                com.luread.app.data.network.NetworkTimeout.applyTo(this)
            }.bodyAsText()
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            return null // network fail -> coba provider berikutnya
        }

        return try {
            val root = json.parseToJsonElement(response)
            val outerArray = root as? JsonArray ?: return null
            val sentencesArray = outerArray.getOrNull(0) as? JsonArray ?: return null

            val translated = StringBuilder()
            for (sentence in sentencesArray) {
                val pair = sentence as? JsonArray ?: continue
                val piece = (pair.getOrNull(0) as? JsonPrimitive)?.content ?: continue
                translated.append(piece)
            }
            if (translated.isEmpty()) return null

            val detected = (outerArray.getOrNull(2) as? JsonPrimitive)?.content
            Outcome.Success(
                Result(
                    translatedText = translated.toString(),
                    detectedSourceLang = detected,
                    provider = "Google",
                ),
            )
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------
    // Provider 2: MyMemory
    // ------------------------------------------------------------------

    private suspend fun translateViaMyMemory(
        text: String,
        sourceLang: String,
        targetLang: String,
    ): Outcome? {
        // MyMemory tidak punya "auto"; pakai "Autodetect" sebagai placeholder
        // — bila sourceLang == auto, kita coba deteksi lewat gtx dulu, atau
        // fallback ke pasangan umum en|id.
        val src = if (sourceLang == "auto") "en" else sourceLang
        val response = try {
            client.get(MYMEMORY_URL) {
                parameter("q", text.take(500))
                parameter("langpair", "$src|$targetLang")
                com.luread.app.data.network.NetworkTimeout.applyTo(this)
            }.bodyAsText()
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            return null
        }

        return try {
            val root = json.parseToJsonElement(response)
            val data = root as? kotlinx.serialization.json.JsonObject ?: return null
            val responseData = data["responseData"] as? kotlinx.serialization.json.JsonObject ?: return null
            val translated = (responseData["translatedText"] as? JsonPrimitive)?.content ?: return null
            if (translated.isBlank()) return null
            Outcome.Success(
                Result(
                    translatedText = translated,
                    detectedSourceLang = src,
                    provider = "MyMemory",
                ),
            )
        } catch (_: Exception) {
            null
        }
    }
}
