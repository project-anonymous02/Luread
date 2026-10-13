package com.luread.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luread.app.data.network.CatalogQuery
import com.luread.app.data.catalog.CatalogRepository
import com.luread.app.data.library.LibraryRepository
import com.luread.app.data.network.ApiResult
import com.luread.app.ui.components.BookCard
import com.luread.app.ui.components.toCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Layar beranda: kelanjutan membaca, koleksi terbaru, dan sorotan katalog.
 *
 * Semua data lokal diambil dari Room (langsung jalan offline), hanya sorotan
 * katalog yang bergantung jaringan.
 */
class HomeViewModel(
    private val library: LibraryRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {

    val continueReading: StateFlow<List<BookCard>> = library.observeLibrary()
        .map { books ->
            books
                .filter { it.progress > 0f && !it.finished && it.lastOpenedAt > 0L }
                .sortedByDescending { it.lastOpenedAt }
                .take(8)
                .map { it.toCard() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recent: StateFlow<List<BookCard>> = library.observeLibrary()
        .map { books ->
            books.sortedByDescending { it.addedAt }.take(8).map { it.toCard() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats: StateFlow<LibraryRepository.ReadingStats> = library.observeStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryRepository.ReadingStats())

    private val _featured = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
    val featured: StateFlow<CatalogUiState> = _featured

    private val _indonesian = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
    val indonesian: StateFlow<CatalogUiState> = _indonesian

    private val _children = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
    val children: StateFlow<CatalogUiState> = _children

    private val _recommendations = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
    val recommendations: StateFlow<CatalogUiState> = _recommendations

    private val _knowledge = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
    val knowledge: StateFlow<CatalogUiState> = _knowledge

    init {
        refresh()
    }

    fun refresh() {
        _featured.value = CatalogUiState.Loading
        _indonesian.value = CatalogUiState.Loading
        _children.value = CatalogUiState.Loading
        _recommendations.value = CatalogUiState.Loading
        _knowledge.value = CatalogUiState.Loading
        viewModelScope.launch {
            // Featured: Gutenberg populer (browse default).
            when (val result = catalog.firstPage(CatalogQuery())) {
                is ApiResult.Success -> _featured.value = CatalogUiState.Content(result.value)
                is ApiResult.Failure -> _featured.value = CatalogUiState.Error(result.error.toUserMessage())
            }
        }
        viewModelScope.launch {
            // Indonesia: Open Library language=ind.
            when (
                val result = catalog.firstPage(
                    CatalogQuery(languages = listOf("id")),
                    source = com.luread.app.data.catalog.BookSource.OPEN_LIBRARY,
                )
            ) {
                is ApiResult.Success -> _indonesian.value = CatalogUiState.Content(result.value)
                is ApiResult.Failure -> _indonesian.value = CatalogUiState.Error(result.error.toUserMessage())
            }
        }
        viewModelScope.launch {
            // Anak-anak: Gutenberg topic=children.
            when (
                val result = catalog.firstPage(
                    CatalogQuery(topic = "children"),
                    source = com.luread.app.data.catalog.BookSource.GUTENDEX,
                )
            ) {
                is ApiResult.Success -> _children.value = CatalogUiState.Content(result.value)
                is ApiResult.Failure -> _children.value = CatalogUiState.Error(result.error.toUserMessage())
            }
        }
        viewModelScope.launch {
            // Rekomendasi: Open Library fiksi populer (sort editions).
            when (
                val result = catalog.firstPage(
                    CatalogQuery(search = "subject:fiction"),
                    source = com.luread.app.data.catalog.BookSource.OPEN_LIBRARY,
                )
            ) {
                is ApiResult.Success -> _recommendations.value = CatalogUiState.Content(result.value)
                is ApiResult.Failure -> _recommendations.value = CatalogUiState.Error(result.error.toUserMessage())
            }
        }
        viewModelScope.launch {
            // Pengetahuan: Open Library subject:science.
            when (
                val result = catalog.firstPage(
                    CatalogQuery(search = "subject:science"),
                    source = com.luread.app.data.catalog.BookSource.OPEN_LIBRARY,
                )
            ) {
                is ApiResult.Success -> _knowledge.value = CatalogUiState.Content(result.value)
                is ApiResult.Failure -> _knowledge.value = CatalogUiState.Error(result.error.toUserMessage())
            }
        }
    }
}
