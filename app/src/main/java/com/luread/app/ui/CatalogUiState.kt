package com.luread.app.ui

import com.luread.app.data.catalog.CatalogPage
import com.luread.app.data.network.ApiError

/** Status layar yang bergantung pada jaringan. */
sealed interface CatalogUiState {
    data object Loading : CatalogUiState

    data class Content(
        val page: CatalogPage,
        val isLoadingMore: Boolean = false,
    ) : CatalogUiState

    data class Error(val message: String) : CatalogUiState
}

/**
 * Pemetaan galat jaringan ke pesan pengguna.
 *
 * Sengaja berupa literal Inggris (bukan resource) karena fungsi ini murni
 * fungsi pemetaan tanpa `Context`; pemanggilannya ada di ViewModel.
 */
fun ApiError.toUserMessage(): String = when (this) {
    is ApiError.Network -> "Could not reach the catalogue. Check your internet connection."
    is ApiError.Timeout -> "The catalogue server did not respond. Try again in a moment."
    is ApiError.Http -> if (code == 503) {
        "The catalogue server is busy (503). Try again shortly."
    } else {
        "The catalogue server replied with error $code."
    }
    is ApiError.NotJson -> "The server response was not valid (it may be having trouble)."
    is ApiError.InvalidUrl -> "The catalogue sent a link this app does not trust, so it was rejected."
    is ApiError.NotFound -> "That book was not found in the catalogue."
}
