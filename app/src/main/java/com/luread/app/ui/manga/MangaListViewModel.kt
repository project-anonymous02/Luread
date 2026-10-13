package com.luread.app.ui.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luread.app.data.manga.MangaRepository
import com.luread.app.data.network.ApiResult
import com.luread.app.ui.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface MangaListUi {
    data object Loading : MangaListUi
    data class Content(
        val items: List<MangaRepository.MangaSummary>,
        val isLoadingMore: Boolean = false,
    ) : MangaListUi

    data class Error(val message: String) : MangaListUi
}

/** ViewModel untuk daftar manga (Discover + Home). */
class MangaListViewModel(
    private val manga: MangaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<MangaListUi>(MangaListUi.Loading)
    val state: StateFlow<MangaListUi> = _state.asStateFlow()

    private var nextOffset: Int? = null
    private var searchJob: Job? = null
    private var fetchJob: Job? = null
    private var currentQuery: String? = null

    init {
        reload(null)
    }

    fun onSearchChange(text: String) {
        currentQuery = text.trim().takeIf { it.isNotEmpty() }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            reload(currentQuery)
        }
    }

    fun retry() = reload(currentQuery)

    fun loadMore() {
        val offset = nextOffset ?: return
        val current = _state.value
        if (current is MangaListUi.Content && current.isLoadingMore) return
        if (_state.value is MangaListUi.Loading) return
        _state.update { (it as? MangaListUi.Content)?.copy(isLoadingMore = true) ?: it }

        fetchJob = viewModelScope.launch {
            val result = if (currentQuery == null) manga.popular(offset = offset)
                else manga.search(currentQuery, offset = offset)
            when (result) {
                is ApiResult.Success -> {
                    val previous = (current as? MangaListUi.Content)?.items.orEmpty()
                    nextOffset = result.value.nextOffset
                    _state.value = MangaListUi.Content(
                        items = previous + result.value.results,
                        isLoadingMore = false,
                    )
                }
                is ApiResult.Failure -> {
                    _state.update {
                        (it as? MangaListUi.Content)?.copy(isLoadingMore = false) ?: it
                    }
                }
            }
        }
    }

    private fun reload(query: String?) {
        fetchJob?.cancel()
        nextOffset = null
        _state.value = MangaListUi.Loading
        fetchJob = viewModelScope.launch {
            val result = if (query == null) manga.popular() else manga.search(query)
            when (result) {
                is ApiResult.Success -> {
                    nextOffset = result.value.nextOffset
                    _state.value = MangaListUi.Content(result.value.results)
                }
                is ApiResult.Failure -> _state.value = MangaListUi.Error(result.error.toUserMessage())
            }
        }
    }
}
