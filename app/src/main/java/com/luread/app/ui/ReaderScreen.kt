package com.luread.app.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luread.app.R
import com.luread.app.core.design.LureadSpace.Sm
import com.luread.app.core.design.LoadingState
import com.luread.app.core.design.ProgressLine
import com.luread.app.core.design.StateMessage
import com.luread.app.core.util.Format
import com.luread.app.ui.reader.EpubContent
import com.luread.app.ui.reader.EpubError
import com.luread.app.ui.reader.EpubUi
import com.luread.app.ui.reader.HtmlReader
import com.luread.app.ui.reader.PdfReader
import com.luread.app.ui.reader.ReaderUi
import com.luread.app.ui.reader.ReaderViewModel
import com.luread.app.ui.reader.SavedLocation
import com.luread.app.ui.reader.TextPager
import com.luread.app.ui.reader.selectedEpubText
import kotlinx.coroutines.launch
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * Layar pembaca.
 *
 * Mendukung empat mesin baca: EPUB (Readium), PDF (PdfRenderer), teks (paginasi
 * sendiri), dan HTML (WebView lokal). Sesi membaca ditutup lewat
 * [ReaderViewModel.onLeave] saat layar ditinggalkan sehingga progres dan
 * riwayat selalu tersimpan.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalReadiumApi::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
    onTranslate: (String) -> Unit,
) {
    val vm = appViewModel { ReaderViewModel(bookId, it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val epub by vm.epub.collectAsStateWithLifecycle()
    val pages by vm.pages.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val annotations by vm.annotationList.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val translateState by vm.translate.collectAsStateWithLifecycle()
    val cbzImages by vm.cbzImages.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAnnotations by rememberSaveable { mutableStateOf(false) }
    var showTranslateSheet by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { vm.onLeave() }
    }

    DisposableEffect(settings.keepScreenOn, context) {
        val window = (context as? Activity)?.window
        if (settings.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2_200)
            vm.clearMessage()
        }
    }

    when (val state = ui) {
        is ReaderUi.Loading -> LoadingState()

        is ReaderUi.Failed -> Column(modifier = Modifier.fillMaxSize()) {
            ReaderTopBar(
                title = "",
                onBack = onBack,
                actions = {},
            )
            StateMessage(
                title = stringResource(R.string.reader_error_title),
                subtitle = state.message,
                onRetry = null,
            )
        }

        is ReaderUi.Ready -> {
            val book = state.book
            val epubReady = epub as? EpubUi.Ready

            Box(modifier = Modifier.fillMaxSize()) {
                when (state.mode) {
                    ReaderViewModel.Mode.EPUB -> when (val epubState = epub) {
                        is EpubUi.Loading -> LoadingState(modifier = Modifier.align(Alignment.Center))
                        is EpubUi.Failed -> EpubError(message = epubState.message)
                        is EpubUi.Ready -> EpubContent(state = epubState.state, viewModel = vm)
                    }

                    ReaderViewModel.Mode.PDF -> PdfReader(
                        path = book.localPath.orEmpty(),
                        startPage = vm.savedPage(),
                        onPage = vm::onProgressPage,
                    )

                    ReaderViewModel.Mode.TEXT -> TextPager(
                        pages = pages,
                        startPage = vm.savedPage(),
                        fontSp = settings.clampedFontSize,
                        onPage = vm::onProgressPage,
                        lineHeightMul = settings.clampedLineHeight,
                        animatePages = settings.pageAnimation,
                    )

                    ReaderViewModel.Mode.CBZ -> com.luread.app.ui.reader.CbzReader(
                        imagePaths = cbzImages,
                        startPage = vm.savedPage(),
                        onPage = vm::onProgressPage,
                    )

                    ReaderViewModel.Mode.HTML -> HtmlReader(
                        path = book.localPath.orEmpty(),
                        fontSp = settings.clampedFontSize,
                        onProgress = { value -> vm.onProgress(value, null) },
                    )
                }

                if (settings.clampedDim > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = settings.clampedDim)),
                    )
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                        shadowElevation = 2.dp,
                    ) {
                        ReaderTopBar(
                            title = book.title,
                            onBack = onBack,
                            actions = {
                                IconButton(onClick = {
                                    scope.launch {
                                        val ready = epubReady
                                        if (ready != null) {
                                            val location = ready.state.controller?.location
                                            if (location != null) {
                                                val saved = SavedLocation(
                                                    href = location.href.toString(),
                                                    progression = location.totalProgression.value,
                                                    text = location.position.value.toString(),
                                                )
                                                vm.addBookmark(
                                                    text = context.getString(
                                                        R.string.reader_position,
                                                        location.position.value,
                                                    ),
                                                    href = location.href.toString(),
                                                    location = ReaderViewModel.encodeLocation(saved),
                                                )
                                            } else {
                                                vm.notify(context.getString(R.string.reader_location_not_ready))
                                            }
                                        } else {
                                            val page = vm.savedPage()
                                            vm.addBookmark(
                                                text = context.getString(R.string.reader_page, page + 1),
                                                href = null,
                                                location = ReaderViewModel.encodeLocation(
                                                    SavedLocation(href = "page", page = page),
                                                ),
                                            )
                                        }
                                    }
                                }) {
                                    Icon(Icons.Outlined.Bookmark, contentDescription = null)
                                }
                                IconButton(onClick = {
                                    // Auto-translate inline: EPUB -> teks terpilih;
                                    // TEXT -> halaman saat ini.
                                    when (state.mode) {
                                        ReaderViewModel.Mode.EPUB -> {
                                            scope.launch {
                                                val selected = epubReady?.state?.let { selectedEpubText(it) }
                                                val text = selected?.text.orEmpty()
                                                if (text.isBlank()) {
                                                    vm.notify(context.getString(R.string.reader_select_text_hint))
                                                } else {
                                                    vm.translateInline(text)
                                                    showTranslateSheet = true
                                                }
                                            }
                                        }
                                        ReaderViewModel.Mode.TEXT -> {
                                            vm.translateCurrentTextPage()
                                            showTranslateSheet = true
                                        }
                                        else -> {
                                            vm.notify(context.getString(R.string.reader_select_text_hint))
                                        }
                                    }
                                }) {
                                    Icon(Icons.Outlined.FormatSize, contentDescription = null)
                                }
                                IconButton(onClick = { showAnnotations = true }) {
                                    Icon(Icons.Outlined.List, contentDescription = null)
                                }
                            },
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    message?.let { text ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Sm),
                        ) {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(Sm),
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                        shadowElevation = 4.dp,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ProgressLine(progress = progress)
                            Text(
                                text = Format.percent(progress),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            if (showAnnotations) {
                AnnotationsSheet(
                    annotations = annotations,
                    canJump = epubReady != null,
                    onClose = { showAnnotations = false },
                    onJump = { annotation ->
                        scope.launch {
                            if (!vm.jumpTo(annotation)) {
                                vm.notify(context.getString(R.string.reader_jump_epub_only))
                            } else {
                                showAnnotations = false
                            }
                        }
                    },
                    onDelete = vm::deleteAnnotation,
                )
            }

            if (showTranslateSheet) {
                TranslateSheet(
                    state = translateState,
                    onClose = {
                        showTranslateSheet = false
                        vm.clearTranslate()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
        )
        actions()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranslateSheet(
    state: ReaderViewModel.TranslateUi,
    onClose: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(Sm),
        ) {
            Text(
                text = stringResource(R.string.translator_result),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            when (state) {
                is ReaderViewModel.TranslateUi.Idle -> {}
                is ReaderViewModel.TranslateUi.Loading -> {
                    androidx.compose.material3.CircularProgressIndicator()
                }
                is ReaderViewModel.TranslateUi.Done -> {
                    if (state.lang != null) {
                        Text(
                            text = "Detected: ${state.lang} → id",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = state.original,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = state.translated,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Sm),
                    )
                    val ctx = LocalContext.current
                    OutlinedButton(onClick = {
                        val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("Luread", state.translated),
                        )
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_copy))
                    }
                }
                is ReaderViewModel.TranslateUi.Failed -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Box(modifier = Modifier.padding(bottom = 16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnnotationsSheet(
    annotations: List<com.luread.app.data.db.AnnotationEntity>,
    canJump: Boolean,
    onClose: () -> Unit,
    onJump: (com.luread.app.data.db.AnnotationEntity) -> Unit,
    onDelete: (Long) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onClose) {
        if (annotations.isEmpty()) {
            Text(
                text = stringResource(R.string.reader_annotations_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(annotations, key = { it.id }) { annotation ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = when (annotation.kind) {
                                    "BOOKMARK" -> stringResource(R.string.annotation_kind_bookmark)
                                    "HIGHLIGHT" -> stringResource(R.string.annotation_kind_highlight)
                                    else -> stringResource(R.string.annotation_kind_note)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = Format.shortDate(annotation.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = annotation.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 3,
                        )
                        Row(horizontalArrangement = Arrangement.End) {
                            if (canJump) {
                                TextButton(onClick = { onJump(annotation) }) {
                                    Text(stringResource(R.string.reader_jump))
                                }
                            }
                            TextButton(onClick = { onDelete(annotation.id) }) {
                                Text(stringResource(R.string.action_delete))
                            }
                        }
                    }
                }
            }
        }
        Box(modifier = Modifier.padding(bottom = 16.dp))
    }
}
