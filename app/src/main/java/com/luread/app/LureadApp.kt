package com.luread.app

import android.app.Application
import android.content.Context
import com.luread.app.data.catalog.CatalogRepository
import com.luread.app.data.db.LureadDatabase
import com.luread.app.data.downloads.DownloadManager
import com.luread.app.data.library.LibraryRepository
import com.luread.app.data.manga.MangaDexApi
import com.luread.app.data.manga.MangaRepository
import com.luread.app.data.network.ArchiveApi
import com.luread.app.data.network.GutendexApi
import com.luread.app.data.network.HttpConfig
import com.luread.app.data.network.OpenLibraryApi
import com.luread.app.data.network.createHttpClient
import com.luread.app.data.settings.SettingsRepository
import com.luread.app.data.settings.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.luread.app.data.network.NetworkTimeout

/**
 * Wadah dependensi (service locator) untuk seluruh aplikasi.
 *
 * Keputusan: tanpa Hilt/DI framework. Alasannya terdokumentasi di
 * docs/architecture.md — jumlah object terbatas, siklus hidupnya jelas, dan
 * menghindari plugin tambahan yang belum terverifikasi pada AGP 9.
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val httpClient by lazy { createHttpClient(HttpConfig()) }

    val catalog by lazy {
        CatalogRepository(
            gutendex = GutendexApi(httpClient),
            openLibrary = OpenLibraryApi(httpClient),
            archive = ArchiveApi(httpClient),
        )
    }

    val translator by lazy { com.luread.app.data.translate.TranslationService(httpClient) }

    // LUREAD-FEAT: sumber kedua Komiku (scraping) menyusul MangaDex.
    val manga by lazy { MangaRepository(MangaDexApi(httpClient), com.luread.app.data.komiku.KomikuApi(httpClient)) }

    val database by lazy { LureadDatabase.get(appContext) }

    val library by lazy {
        LibraryRepository(
            books = database.bookDao(),
            sessions = database.sessionDao(),
            annotations = database.annotationDao(),
        )
    }

    val downloads by lazy {
        DownloadManager(
            context = appContext,
            books = database.bookDao(),
            scope = appScope,
        )
    }

    val settings by lazy { SettingsRepository(settingsDataStore(appContext)) }

    init {
        // LUREAD-FEAT: Pengaturan → timeout HTTP (per-request) & unduhan, realtime.
        appScope.launch {
            settings.settings.collect { s ->
                val timeoutMs = s.clampedTimeout * 1000L
                NetworkTimeout.requestTimeoutMs = timeoutMs
                downloads.connectTimeoutMs = (timeoutMs / 2).coerceIn(5_000L, 60_000L).toInt()
                downloads.readTimeoutMs = timeoutMs.toInt()
            }
        }
    }

    fun close() {
        downloads.cancelAll()
        httpClient.close()
        appScope.cancel()
    }
}

class LureadApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun onTerminate() {
        container.close()
        super.onTerminate()
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as LureadApp).container
