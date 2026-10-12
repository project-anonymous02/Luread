package com.luread.app.ui.components

import com.luread.app.data.catalog.Formats
import com.luread.app.data.db.BookEntity
import com.luread.app.data.network.GutendexBook

fun GutendexBook.toCard(): BookCard = BookCard(
    id = id,
    title = title.ifBlank { "Untitled" },
    authorsText = authorNames.joinToString(", "),
    coverUrl = Formats.coverUrl(formats),
)

fun BookEntity.toCard(): BookCard = BookCard(
    id = id,
    title = title,
    authorsText = authorsText,
    coverUrl = coverUrl,
    progress = progress,
    downloaded = localPath != null,
)
