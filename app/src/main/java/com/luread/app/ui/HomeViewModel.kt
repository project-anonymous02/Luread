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

    init {
        refresh()
    }

    fun refresh() {
        _featured.value = CatalogUiState.Loading
        _indonesian.value = CatalogUiState.Loading
        viewModelScope.launch {
            when (val result = catalog.firstPage(CatalogQuery())) {
                is ApiResult.Success -> _featured.value = CatalogUiState.Content(result.value)
                is ApiResult.Failure -> _featured.value = CatalogUiState.Error(result.error.toUserMessage())
            }
            // Section Indonesia: Wikisource (semua konten berbahasa Indonesia)
            when (val result = catalog.firstPage(CatalogQuery(), source = com.luread.app.data.catalog.BookSource.WIKISOURCE)) {
                is ApiResult.Success -> _indonesian.value = CatalogUiState.Content(result.value)
                is ApiResult.Failure -> _indonesian.value = CatalogUiState.Error(result.error.toUserMessage())
            }
        }
    }
}
