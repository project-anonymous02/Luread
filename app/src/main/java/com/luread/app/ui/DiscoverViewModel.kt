package com.luread.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luread.app.data.catalog.BookSource
import com.luread.app.data.catalog.CatalogRepository
import com.luread.app.data.library.LibraryRepository
import com.luread.app.data.network.ApiResult
import com.luread.app.data.network.CatalogQuery
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Layar Discover: pencarian, pemilihan sumber, dan penyaringan katalog lalu
 * muat halaman berikutnya.
 *
 * Filter per sumber (semua diverifikasi lewat curl 2026-10-13, lihat
 * docs/book-apis-audit.md):
 *  * Gutenberg: languages en/fr/de/es/it/nl/pt/ru/zh/fi, topic
 *    children/poetry, mime epub/html (mime text/plain sengaja tidak ditawarkan
 *    karena teramati membalas 503);
 *  * Open Library & Internet Archive: languages (kode ISO 639-2) — topic dan
 *    format tidak didukung pencarian keduanya sehingga barisnya disembunyikan.
 */
class DiscoverViewModel(
    @Suppress("unused") private val library: LibraryRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {

    data class Filters(
        val search: String = "",
        val language: String? = null,
        val topic: String? = null,
        val mimeType: String? = null,
        val source: BookSource = BookSource.GUTENDEX,
    )

    private val _filters = MutableStateFlow(Filters())
    val filters: StateFlow<Filters> = _filters.asStateFlow()

    private val _state = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    private var nextCursor: String? = null
    private var searchJob: Job? = null
    private var fetchJob: Job? = null

    init {
        reload()
    }

    /** Pencarian debounce 400ms agar tidak membanjiri API. */
    fun onSearchChange(text: String) {
        _filters.update { it.copy(search = text) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            reload()
        }
    }

    fun setSource(source: BookSource) {
        if (_filters.value.source == source) return
        _filters.update { it.copy(source = source) }
        reload()
    }

    fun setLanguage(code: String?) {
        _filters.update { it.copy(language = code) }
        reload()
    }

    fun setTopic(topic: String?) {
        _filters.update { it.copy(topic = topic) }
        reload()
    }

    fun setMimeType(mime: String?) {
        _filters.update { it.copy(mimeType = mime) }
        reload()
    }

    fun retry() = reload()

    fun loadMore() {
        val cursor = nextCursor ?: return
        val current = _state.value
        if (current is CatalogUiState.Content && current.isLoadingMore) return
        if (_state.value is CatalogUiState.Loading) return
        _state.update { (it as? CatalogUiState.Content)?.copy(isLoadingMore = true) ?: it }

        fetchJob = viewModelScope.launch {
            when (val result = catalog.nextPage(cursor, queryOf(_filters.value), _filters.value.source)) {
                is ApiResult.Success -> {
                    val previous = (current as? CatalogUiState.Content)?.page
                    nextCursor = result.value.next
                    _state.value = CatalogUiState.Content(
                        page = merge(previous, result.value),
                        isLoadingMore = false,
                    )
                }
                is ApiResult.Failure -> {
                    // Galat saat muat lanjutan tidak menghapus hasil yang sudah ada.
                    _state.update {
                        (it as? CatalogUiState.Content)?.copy(isLoadingMore = false) ?: it
                    }
                }
            }
        }
    }

    private fun reload() {
        fetchJob?.cancel()
        nextCursor = null
        _state.value = CatalogUiState.Loading

        val f = _filters.value
        fetchJob = viewModelScope.launch {
            when (val result = catalog.firstPage(queryOf(f), f.source)) {
                is ApiResult.Success -> {
                    nextCursor = result.value.next
                    _state.value = CatalogUiState.Content(result.value)
                }
                is ApiResult.Failure -> _state.value = CatalogUiState.Error(result.error.toUserMessage())
            }
        }
    }

    private fun queryOf(f: Filters) = CatalogQuery(
        search = f.search.trim().takeIf { it.isNotEmpty() },
        languages = listOfNotNull(f.language),
        topic = f.topic,
        mimeType = f.mimeType,
    )

    private fun merge(
        previous: com.luread.app.data.catalog.CatalogPage?,
        next: com.luread.app.data.catalog.CatalogPage,
    ): com.luread.app.data.catalog.CatalogPage {
        if (previous == null) return next
        val seen = previous.results.map { it.key }.toSet()
        return next.copy(results = previous.results + next.results.filter { it.key !in seen })
    }
}
