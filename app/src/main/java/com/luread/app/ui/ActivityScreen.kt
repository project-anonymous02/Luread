package com.luread.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luread.app.R
import com.luread.app.core.design.LureadSpace.Lg
import com.luread.app.core.design.LureadSpace.Md
import com.luread.app.core.design.LureadSpace.SectionGap
import com.luread.app.core.design.LureadSpace.Sm
import com.luread.app.core.design.SectionHeader
import com.luread.app.core.design.StateMessage
import com.luread.app.core.util.Format

@Composable
fun ActivityScreen(onOpenBook: (Int) -> Unit) {
    val vm = appViewModel { ActivityViewModel(it.library) }
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Lg, end = Lg, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(Sm),
    ) {
        item {
            Text(
                text = stringResource(R.string.nav_activity),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = Lg),
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Sm)) {
                StatCard(
                    value = Format.duration(stats.todayMillis / 60_000L),
                    label = stringResource(R.string.activity_today),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value = Format.duration(stats.totalMillis / 60_000L),
                    label = stringResource(R.string.activity_total_sessions),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value = stats.finishedCount.toString(),
                    label = stringResource(R.string.activity_finished),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            SectionHeader(title = stringResource(R.string.activity_history))
        }

        if (sessions.isEmpty()) {
            item {
                StateMessage(
                    title = stringResource(R.string.activity_empty_title),
                    subtitle = stringResource(R.string.activity_empty_subtitle),
                    onRetry = null,
                )
            }
        } else {
            items(sessions, key = { it.id }) { session ->
                SessionRow(session = session, onClick = { onOpenBook(session.bookId) })
            }

            item {
                TextButton(onClick = vm::clearHistory, modifier = Modifier.padding(top = Md)) {
                    Text(
                        text = stringResource(R.string.action_clear_history),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.shapes.medium,
            )
            .padding(Md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionRow(session: com.luread.app.data.db.SessionEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = session.bookTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = Format.durationSeconds(session.durationMillis / 1000L),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = "${Format.date(session.startedAt)} · " +
                "${Format.percent(session.progressBefore)} → ${Format.percent(session.progressAfter)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
