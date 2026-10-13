package com.luread.app.ui.components

import com.luread.app.data.catalog.CatalogItem
import com.luread.app.data.catalog.Formats
import com.luread.app.data.db.BookEntity

fun CatalogItem.toCard(): BookCard = BookCard(
    id = key,
    title = title.ifBlank { "Untitled" },
    authorsText = authors.joinToString(", "),
    coverUrl = coverUrl ?: Formats.coverUrl(formats),
)

fun BookEntity.toCard(): BookCard = BookCard(
    id = id,
    title = title,
    authorsText = authorsText,
    coverUrl = coverUrl,
    progress = progress,
    downloaded = localPath != null,
)
