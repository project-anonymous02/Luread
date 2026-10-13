package com.luread.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luread.app.core.design.BookCover
import com.luread.app.core.design.LocalLureadExtras
import com.luread.app.core.design.LureadSpace.Sm
import com.luread.app.core.design.ProgressLine
import com.luread.app.core.util.Format

/** Model tampilan yang dipakai kartu dan baris buku di semua layar. */
data class BookCard(
    val id: String,
    val title: String,
    val authorsText: String,
    val coverUrl: String?,
    val progress: Float = 0f,
    val downloaded: Boolean = false,
)

@Composable
fun BookGridItem(card: BookCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val aspect = LocalLureadExtras.current.coverAspectRatio
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(bottom = Sm),
    ) {
        BookCover(
            title = card.title,
            coverUrl = card.coverUrl,
            aspectRatio = aspect,
            modifier = Modifier.fillMaxWidth(),
        )
        if (card.progress > 0f) {
            ProgressLine(progress = card.progress)
        }
        Text(
            text = card.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (card.authorsText.isNotBlank()) {
            Text(
                text = card.authorsText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun BookRowItem(
    card: BookCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(
            title = card.title,
            coverUrl = card.coverUrl,
            aspectRatio = 0.68f,
            modifier = Modifier.width(52.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Sm),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = card.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (card.authorsText.isNotBlank()) {
                Text(
                    text = card.authorsText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (card.progress > 0f) {
                Text(
                    text = Format.percent(card.progress),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        trailing?.invoke()
    }
}
