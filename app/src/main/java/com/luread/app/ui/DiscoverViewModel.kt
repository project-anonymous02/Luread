package com.luread.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luread.app.data.network.CatalogQuery
import com.luread.app.data.catalog.CatalogRepository
import com.luread.app.data.library.LibraryRepository
import com.luread.app.data.network.ApiResult
import com.luread.app.data.network.GutendexPage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Layar Discover: pencarian dan penyaringan katalog lalu muat halaman berikutnya.
 *
 * Filter yang tersedia hanya yang sudah diuji terhadap API (lihat
 * docs/gutendex-api-audit.md):
 *  * `languages`: en, fr, de, es, it, nl, pt, ru, zh, fi -> 200;
 *  * `topic`: children, poetry -> 200;
 *  * `mime_type`: application/epub+zip, text/html -> 200;
 *  * `mime_type=text/plain` teramati membalas 503 dan sengaja tidak ditawarkan.
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
    )

    private val _filters = MutableStateFlow(Filters())
    val filters: StateFlow<Filters> = _filters.asStateFlow()

    private val _state = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    private var nextUrl: String? = null
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
        val url = nextUrl ?: return
        val current = _state.value
        if (current is CatalogUiState.Content && current.isLoadingMore) return
        if (_state.value is CatalogUiState.Loading) return
        _state.update { (it as? CatalogUiState.Content)?.copy(isLoadingMore = true) ?: it }

        fetchJob = viewModelScope.launch {
            when (val result = catalog.pageAt(url)) {
                is ApiResult.Success -> {
                    val previous = (current as? CatalogUiState.Content)?.page
                    nextUrl = catalog.trustedNext(result.value)
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
        nextUrl = null
        _state.value = CatalogUiState.Loading

        val f = _filters.value
        val query = CatalogQuery(
            search = f.search.trim().takeIf { it.isNotEmpty() },
            languages = listOfNotNull(f.language),
            topic = f.topic,
            mimeType = f.mimeType,
        )

        fetchJob = viewModelScope.launch {
            when (val result = catalog.firstPage(query)) {
                is ApiResult.Success -> {
                    nextUrl = catalog.trustedNext(result.value)
                    _state.value = CatalogUiState.Content(result.value)
                }
                is ApiResult.Failure -> _state.value = CatalogUiState.Error(result.error.toUserMessage())
            }
        }
    }

    private fun merge(previous: GutendexPage?, next: GutendexPage): GutendexPage {
        if (previous == null) return next
        val seen = previous.results.map { it.id }.toSet()
        return next.copy(results = previous.results + next.results.filter { it.id !in seen })
    }
}
