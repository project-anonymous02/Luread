package com.luread.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luread.app.data.db.SessionEntity
import com.luread.app.data.library.LibraryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Riwayat aktivitas membaca. */
class ActivityViewModel(private val library: LibraryRepository) : ViewModel() {

    val sessions: StateFlow<List<SessionEntity>> = library.observeActivity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats: StateFlow<LibraryRepository.ReadingStats> = library.observeStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryRepository.ReadingStats())

    fun clearHistory() {
        viewModelScope.launch { library.clearActivity() }
    }
}
