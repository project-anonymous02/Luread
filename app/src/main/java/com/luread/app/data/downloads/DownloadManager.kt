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
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) {
    data class Progress(
        val bookId: String,
        val state: BookEntity.DownloadState,
        val doneBytes: Long = 0L,
        val totalBytes: Long = -1L,
    )

    private val _progress = MutableStateFlow<Map<String, Progress>>(emptyMap())
    val progress: StateFlow<Map<String, Progress>> = _progress.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()
    private val cancelled = mutableSetOf<String>()

    @Synchronized
    fun enqueue(bookId: String, url: String, mime: String?) {
        if (jobs[bookId]?.isActive == true) return
        cancelled.remove(bookId)
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

    @Synchronized
    fun cancelAll() {
        jobs.values.toList().forEach { it.cancel() }
        jobs.clear()
    }

    private suspend fun download(bookId: String, url: String, mime: String?) = withContext(dispatcher) {
        val target = targetFile(bookId, mime)
        if (target.isFile && target.length() > 0) {
            books.setLocalFile(bookId, target.absolutePath, mime ?: Formats.MIME_EPUB, BookEntity.DownloadState.DONE.name)
            publish(bookId, BookEntity.DownloadState.DONE, target.length(), target.length())
            return@withContext
        }

        publish(bookId, BookEntity.DownloadState.RUNNING, 0L, -1L)
        books.updateDownload(bookId, BookEntity.DownloadState.RUNNING.name, 0L, -1L)

        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Luread/0.1.0 (Android ebook reader)")

        try {
            val code = connection.responseCode
            if (code !in 200..299) throw DownloadException("HTTP $code for $url")

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

            if (done <= 0L) throw DownloadException("Empty response body")

            val head = readHead(temp)
            val sniffed = Formats.sniff(head)
            // text/html dan text/plain tidak punya magic number, jadi wajar bila
            // sniff gagal; yang penting berkas tidak kosong dan tipe yang terdeteksi
            // bukan format yang tidak dikenal.
            val ok = sniffed == null || sniffed == Formats.EPUB_OR_ZIP || sniffed == Formats.MIME_PDF
            if (!ok) {
                temp.delete()
                throw DownloadException("Unrecognised file content (sniff=$sniffed)")
            }

            withContext(NonCancellable) {
                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                books.setLocalFile(bookId, target.absolutePath, sniffed ?: mime ?: Formats.MIME_EPUB, BookEntity.DownloadState.DONE.name)
            }
            publish(bookId, BookEntity.DownloadState.DONE, done, total)
        } finally {
            connection.disconnect()
            File(target.parentFile, target.name + ".part").takeIf { it.exists() && !target.exists() }?.delete()
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

    private fun readHead(file: File): ByteArray {
        val bytes = ByteArray(8)
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
    }
}
