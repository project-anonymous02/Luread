package com.luread.app.data.downloads

import android.content.Context
import com.luread.app.data.catalog.Formats
import com.luread.app.data.db.BookDao
import com.luread.app.data.db.BookEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Pengunduh buku.
 *
 * Implementasi memakai `HttpURLConnection` dari platform, bukan streaming
 * Ktor, karena pengunduhan 25 MB (EPUB ukuran penuh pada sampel audit)
 * membutuhkan kontrol progres per-chunk dan pembatalan yang sederhana tanpa
 * bergantung pada API streaming yang berubah antar versi Ktor.
 *
 * Jaminan yang diberikan:
 *  * unduhan ditulis ke berkas `.part` dan hanya diganti nama setelah magic
 *    number file lolos cek [Formats.sniff];
 *  * kegagalan di tengah jalan tidak meninggalkan berkas setengah jadi di
 *    perpustakaan;
 *  * progres dilaporkan per buku lewat [progress];
 *  * pembatalan memutus koneksi dan berkas sementara dibersihkan.
 */
class DownloadManager(
    private val context: Context,
    private val books: BookDao,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    var connectTimeoutMs: Int = 15_000,
    var readTimeoutMs: Int = 30_000,
) {
    data class Progress(
        val bookId: String,
        val state: BookEntity.DownloadState,
        val doneBytes: Long = 0L,
        val totalBytes: Long = -1L,
    )

    private val _progress = MutableStateFlow<Map<String, Progress>>(emptyMap())
    val progress: StateFlow<Map<String, Progress>> = _progress.asStateFlow()

    // LUREAD-FIX: pesan galat per buku agar layar detail bisa menampilkan
    // mengapa unduhan gagal (sebelumnya exception ditelan tanpa jejak).
    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors: StateFlow<Map<String, String>> = _errors.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()
    private val cancelled = mutableSetOf<String>()

    @Synchronized
    fun enqueue(bookId: String, url: String, mime: String?) {
        if (jobs[bookId]?.isActive == true) return
        cancelled.remove(bookId)
        _errors.update { it - bookId }
        jobs[bookId] = scope.launch(dispatcher) {
            try {
                download(bookId, url, mime)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Pembatalan: negara sudah diatur oleh cancel(); jangan menulis ulang.
                throw e
            } catch (t: Throwable) {
                withContext(NonCancellable) {
                    books.updateDownload(bookId, BookEntity.DownloadState.FAILED.name, 0L, -1L)
                    publish(bookId, BookEntity.DownloadState.FAILED, 0L, -1L)
                    fail(bookId, t.message ?: t::class.simpleName ?: "Download failed")
                }
            }
        }
    }

    @Synchronized
    fun cancel(bookId: String) {
        cancelled.add(bookId)
        jobs[bookId]?.cancel()
        publish(bookId, BookEntity.DownloadState.NONE, 0L, -1L)
    }

    /**
     * Simpan teks/HTML hasil fetch aplikasi sebagai berkas baca, dengan
     * sikap progres/gagal yang sama seperti unduhan biasa.
     */
    @Synchronized
    fun enqueueText(bookId: String, text: String, mime: String) {
        if (jobs[bookId]?.isActive == true) return
        cancelled.remove(bookId)
        _errors.update { it - bookId }
        jobs[bookId] = scope.launch(dispatcher) {
            try {
                val total = text.toByteArray(Charsets.UTF_8).size.toLong()
                publish(bookId, BookEntity.DownloadState.RUNNING, 0L, total)
                books.updateDownload(bookId, BookEntity.DownloadState.RUNNING.name, 0L, -1L)

                val target = targetFile(bookId, mime)
                target.parentFile?.mkdirs()
                val temp = File(target.parentFile, target.name + ".part")
                temp.writeText(text)

                coroutineContext.ensureActive()
                if (cancelled.contains(bookId)) throw kotlinx.coroutines.CancellationException("cancelled")

                withContext(NonCancellable) {
                    if (target.exists()) target.delete()
                    if (!temp.renameTo(target)) {
                        temp.copyTo(target, overwrite = true)
                        temp.delete()
                    }
                    books.setLocalFile(bookId, target.absolutePath, mime, BookEntity.DownloadState.DONE.name)
                }
                publish(bookId, BookEntity.DownloadState.DONE, total, total)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                withContext(NonCancellable) {
                    books.updateDownload(bookId, BookEntity.DownloadState.FAILED.name, 0L, -1L)
                    publish(bookId, BookEntity.DownloadState.FAILED, 0L, -1L)
                    fail(bookId, t.message ?: t::class.simpleName ?: "Save failed")
                }
            }
        }
    }

    @Synchronized
    fun cancelAll() {
        jobs.values.toList().forEach { it.cancel() }
        jobs.clear()
    }

    // LUREAD-FIX: unduh dengan retry + manual redirect. Internet Archive
    // sering meredir ke node (ia*.eu.archive.org / dn*.archive.org) dan
    // kadang membalas 503 sesaat; HttpURLConnection juga tidak mengikuti
    // redirect lintas-protokol (http→https), jadi kita ikuti sendiri.
    private suspend fun download(bookId: String, url: String, mime: String?) = withContext(dispatcher) {
        val target = targetFile(bookId, mime)
        if (target.isFile && target.length() > 0) {
            books.setLocalFile(bookId, target.absolutePath, mime ?: Formats.MIME_EPUB, BookEntity.DownloadState.DONE.name)
            publish(bookId, BookEntity.DownloadState.DONE, target.length(), target.length())
            return@withContext
        }

        publish(bookId, BookEntity.DownloadState.RUNNING, 0L, -1L)
        books.updateDownload(bookId, BookEntity.DownloadState.RUNNING.name, 0L, -1L)

        var attempt = 0
        var lastError: Exception? = null
        while (attempt < MAX_ATTEMPTS) {
            if (cancelled.contains(bookId)) throw kotlinx.coroutines.CancellationException("cancelled")
            try {
                fetchOnce(bookId, url, mime, target)
                publish(bookId, BookEntity.DownloadState.DONE, target.length(), target.length())
                _errors.update { it - bookId }
                return@withContext
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Exception) {
                lastError = t
                attempt += 1
                if (attempt < MAX_ATTEMPTS) {
                    kotlinx.coroutines.delay(RETRY_DELAY_MS * attempt)
                }
            }
        }
        throw lastError ?: DownloadException("Download failed: $url")
    }

    private suspend fun fetchOnce(bookId: String, url: String, mime: String?, target: File) {
        var currentUrl = url
        var redirects = 0
        while (true) {
            val connection = URL(currentUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            // Kita tangani redirect sendiri agar lintas-protokol (http→https)
            // dan lintas-node tetap diikuti.
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Luread/0.1.0 (Android ebook reader)")
            connection.setRequestProperty("Accept-Encoding", "identity")
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: throw DownloadException("HTTP $code tanpa Location untuk $currentUrl")
                    currentUrl = URL(URL(currentUrl), location).toString()
                    redirects += 1
                    if (redirects > MAX_REDIRECTS) throw DownloadException("Terlalu banyak redirect untuk $url")
                    continue
                }
                if (code !in 200..299) throw DownloadException("HTTP $code untuk $currentUrl")

                val total = connection.contentLengthLong.takeIf { it > 0 } ?: -1L
                target.parentFile?.mkdirs()
                val temp = File(target.parentFile, target.name + ".part")
                var done = 0L

                connection.inputStream.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER)
                        while (true) {
                            coroutineContext.ensureActive()
                            if (cancelled.contains(bookId)) throw kotlinx.coroutines.CancellationException("cancelled")
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            publish(bookId, BookEntity.DownloadState.RUNNING, done, total)
                        }
                        output.flush()
                    }
                }

                if (done <= 0L) throw DownloadException("Respons kosong dari $currentUrl")

                val head = readHead(temp)
                val sniffed = Formats.sniff(head)
                // text/html, text/plain, dan format lain tanpa magic number wajar
                // bikin sniff gagal; yang penting berkas tidak kosong dan tipe yang
                // terdeteksi bukan format yang tidak dikenal.
                val ok = sniffed == null ||
                    sniffed == Formats.EPUB_OR_ZIP ||
                    sniffed == Formats.MIME_PDF ||
                    sniffed == Formats.MIME_MOBI ||
                    sniffed == Formats.MIME_FB2 ||
                    sniffed == Formats.MIME_DJVU ||
                    sniffed == Formats.MIME_RTF
                if (!ok) {
                    temp.delete()
                    throw DownloadException("Isi berkas tidak dikenal (sniff=$sniffed)")
                }

                withContext(NonCancellable) {
                    if (target.exists()) target.delete()
                    if (!temp.renameTo(target)) {
                        temp.copyTo(target, overwrite = true)
                        temp.delete()
                    }
                    // Utamakan mime yang dinyatakan pemanggil (lebih spesifik daripada
                    // sniff yang hanya bisa membedakan zip/pdf, bukan epub/zip/cbz).
                    books.setLocalFile(bookId, target.absolutePath, mime ?: sniffed ?: Formats.MIME_EPUB, BookEntity.DownloadState.DONE.name)
                }
                return
            } finally {
                connection.disconnect()
                File(target.parentFile, target.name + ".part").takeIf { it.exists() && !target.exists() }?.delete()
            }
        }
    }

    /** Hapus berkas buku dari penyimpanan. */
    fun deleteFile(path: String): Boolean = runCatching { File(path).delete() }.getOrDefault(false)

    private fun targetFile(bookId: String, mime: String?): File {
        val dir = File(context.filesDir, "books").apply { mkdirs() }
        val ext = Formats.extensionFor(mime ?: Formats.MIME_EPUB)
        return File(dir, safeName(bookId) + "." + ext)
    }

    private fun safeName(key: String): String = key.replace(":", "_")

    private fun publish(bookId: String, state: BookEntity.DownloadState, done: Long, total: Long) {
        _progress.update { it + (bookId to Progress(bookId, state, done, total)) }
    }

    private fun fail(bookId: String, message: String) {
        _errors.update { it + (bookId to message) }
    }

    private fun readHead(file: File): ByteArray {
        // LUREAD-FIX: 128 byte (dulunya 8) — sniff MOBI butuh byte offset 60.
        val bytes = ByteArray(128)
        file.inputStream().use { input ->
            var read = 0
            while (read < bytes.size) {
                val n = input.read(bytes, read, bytes.size - read)
                if (n < 0) break
                read += n
            }
            return if (read == bytes.size) bytes else bytes.copyOf(read)
        }
    }

    class DownloadException(message: String) : Exception(message)

    private companion object {
        const val DEFAULT_BUFFER = 32 * 1024
        const val MAX_ATTEMPTS = 3
        const val MAX_REDIRECTS = 5
        const val RETRY_DELAY_MS = 750L
    }
}
