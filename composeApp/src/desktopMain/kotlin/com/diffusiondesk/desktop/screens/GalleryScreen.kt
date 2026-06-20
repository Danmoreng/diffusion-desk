package com.diffusiondesk.desktop.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diffusiondesk.desktop.core.GalleryImage
import com.diffusiondesk.desktop.core.GalleryDateFilter
import com.diffusiondesk.desktop.core.GalleryKeyword
import com.diffusiondesk.desktop.viewmodel.GalleryViewModel
import com.diffusiondesk.desktop.viewmodel.GalleryUiState
import java.awt.Cursor
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.ui.component.Text

private val GalleryTileDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun GalleryScreen(
    state: GalleryUiState,
    outputDir: String,
    isTaggingGallery: Boolean,
    onRefresh: () -> Unit,
    onTagAllPendingImages: () -> Unit,
    onQueryChange: (String) -> Unit,
    onModelFilterChange: (String) -> Unit,
    onDateFilterChange: (String) -> Unit,
    onSelectKeyword: (String) -> Unit,
    onClearKeywordFilter: () -> Unit,
    onDeleteKeyword: (String) -> Unit,
    onCleanupUnusedKeywords: () -> Unit,
    onSelectImage: (Long) -> Unit,
    onSelectImageWithModifiers: (Long, Boolean, Boolean) -> Unit,
    onSelectPreviousImage: () -> Unit,
    onSelectNextImage: () -> Unit,
    onClearImageSelection: () -> Unit,
    onDeleteSelectedImages: () -> Unit,
    onKeywordDraftChange: (String) -> Unit,
    onAddKeyword: () -> Unit,
    onRemoveKeyword: (Long, String) -> Unit,
    onTagSelectedImage: () -> Unit,
    onDeleteImage: () -> Unit,
    previewPanelWidthDp: Int,
    onPreviewPanelWidthChange: (Int) -> Unit,
    onReuseImage: (GalleryImage) -> Unit,
    onUpscaleImage: (GalleryImage) -> Unit,
    onAnalyzeComposition: (GalleryImage) -> Unit,
) {
    var imagePendingDeletion by remember { mutableStateOf<GalleryImage?>(null) }
    var keywordPendingDeletion by remember { mutableStateOf<String?>(null) }
    var cleanupKeywordsPending by remember { mutableStateOf(false) }
    var batchDeletePending by remember { mutableStateOf(false) }
    var showTagManager by remember { mutableStateOf(false) }

    LaunchedEffect(outputDir) {
        onRefresh()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(DeskScreenPadding),
    ) {
        val minPreviewWidth = 320.dp
        val maxPreviewWidth = minOf(760.dp, (maxWidth - 380.dp).coerceAtLeast(minPreviewWidth))
        val density = LocalDensity.current
        var previewWidthDp by remember { mutableStateOf(previewPanelWidthDp.toFloat()) }
        var isDraggingPreview by remember { mutableStateOf(false) }
        var dragStartWidthPx by remember { mutableStateOf(0f) }
        var draggedPx by remember { mutableStateOf(0f) }
        val previewWidth = previewWidthDp.dp.coerceIn(minPreviewWidth, maxPreviewWidth)
        val currentPreviewWidthPx by rememberUpdatedState(with(density) { previewWidth.toPx() })
        val currentPreviewWidthDp by rememberUpdatedState(previewWidthDp)

        LaunchedEffect(previewPanelWidthDp, minPreviewWidth, maxPreviewWidth) {
            if (!isDraggingPreview) {
                previewWidthDp = previewPanelWidthDp.dp.coerceIn(minPreviewWidth, maxPreviewWidth).value
            }
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(DeskLayoutGap),
            ) {
                GalleryToolbar(
                    state = state,
                    isTaggingGallery = isTaggingGallery,
                    onRefresh = onRefresh,
                    onTagAllPendingImages = onTagAllPendingImages,
                    onQueryChange = onQueryChange,
                    onModelFilterChange = onModelFilterChange,
                    onDateFilterChange = onDateFilterChange,
                    onSelectKeyword = onSelectKeyword,
                    onClearKeywordFilter = onClearKeywordFilter,
                    onManageTags = { showTagManager = true },
                    onClearImageSelection = onClearImageSelection,
                    onDeleteSelectedImages = { batchDeletePending = true },
                )
                GalleryGrid(
                    images = state.images,
                    selectedImageId = state.selectedImage?.id,
                    selectedImageIds = state.selectedImageIds,
                    onSelectImage = onSelectImage,
                    onSelectImageWithModifiers = onSelectImageWithModifiers,
                    onSelectPreviousImage = onSelectPreviousImage,
                    onSelectNextImage = onSelectNextImage,
                    onDeleteSelectedImage = {
                        if (state.selectedImageIds.isNotEmpty()) {
                            batchDeletePending = true
                        } else {
                            state.selectedImage
                                ?.takeIf { it.file.isFile && !state.isDeletingImage }
                                ?.let { imagePendingDeletion = it }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            GallerySplitter(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(DeskLayoutGap)
                    .pointerInput(minPreviewWidth, maxPreviewWidth) {
                        detectDragGestures(
                            onDragStart = {
                                isDraggingPreview = true
                                dragStartWidthPx = currentPreviewWidthPx
                                draggedPx = 0f
                            },
                            onDragEnd = {
                                isDraggingPreview = false
                                onPreviewPanelWidthChange(currentPreviewWidthDp.roundToInt())
                            },
                            onDragCancel = {
                                isDraggingPreview = false
                                onPreviewPanelWidthChange(currentPreviewWidthDp.roundToInt())
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                draggedPx += dragAmount.x
                                val nextWidthPx = (dragStartWidthPx - draggedPx)
                                    .coerceIn(
                                        with(density) { minPreviewWidth.toPx() },
                                        with(density) { maxPreviewWidth.toPx() },
                                    )
                                previewWidthDp = with(density) { nextWidthPx.toDp().value }
                            },
                        )
                    },
            )

            GalleryDetails(
                image = state.selectedImage,
                keywordDraft = state.keywordDraft,
                isTaggingSelectedImage = state.isTaggingSelectedImage,
                isDeletingImage = state.isDeletingImage,
                onKeywordDraftChange = onKeywordDraftChange,
                onAddKeyword = onAddKeyword,
                onRemoveKeyword = onRemoveKeyword,
                onTagSelectedImage = onTagSelectedImage,
                onDeleteImage = { imagePendingDeletion = it },
                onReuseImage = onReuseImage,
                onUpscaleImage = onUpscaleImage,
                onAnalyzeComposition = onAnalyzeComposition,
                modifier = Modifier
                    .width(previewWidth)
                    .fillMaxHeight(),
            )
        }
    }

    imagePendingDeletion?.let { image ->
        DeleteImageDialog(
            image = image,
            onDismiss = { imagePendingDeletion = null },
            onConfirm = {
                imagePendingDeletion = null
                onDeleteImage()
            },
        )
    }

    keywordPendingDeletion?.let { keyword ->
        DeleteKeywordDialog(
            keyword = keyword,
            onDismiss = { keywordPendingDeletion = null },
            onConfirm = {
                keywordPendingDeletion = null
                onDeleteKeyword(keyword)
            },
        )
    }

    if (showTagManager) {
        TagManagerDialog(
            keywords = state.keywordStats,
            selectedKeywords = state.selectedKeywords,
            onDismiss = { showTagManager = false },
            onSelectKeyword = onSelectKeyword,
            onDeleteKeyword = { keywordPendingDeletion = it },
            onCleanupUnusedKeywords = { cleanupKeywordsPending = true },
        )
    }

    if (cleanupKeywordsPending) {
        CleanupKeywordsDialog(
            onDismiss = { cleanupKeywordsPending = false },
            onConfirm = {
                cleanupKeywordsPending = false
                onCleanupUnusedKeywords()
            },
        )
    }

    if (batchDeletePending) {
        BatchDeleteImagesDialog(
            count = state.selectedImageIds.size,
            onDismiss = { batchDeletePending = false },
            onConfirm = {
                batchDeletePending = false
                onDeleteSelectedImages()
            },
        )
    }

}

@Composable
private fun GalleryToolbar(
    state: GalleryUiState,
    isTaggingGallery: Boolean,
    onRefresh: () -> Unit,
    onTagAllPendingImages: () -> Unit,
    onQueryChange: (String) -> Unit,
    onModelFilterChange: (String) -> Unit,
    onDateFilterChange: (String) -> Unit,
    onSelectKeyword: (String) -> Unit,
    onClearKeywordFilter: () -> Unit,
    onManageTags: () -> Unit,
    onClearImageSelection: () -> Unit,
    onDeleteSelectedImages: () -> Unit,
) {
    var keywordFilterDraft by remember(state.selectedKeywords) { mutableStateOf("") }

    DeskPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DeskControlSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "Gallery",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            DeskIconButton(
                icon = Icons.Default.Refresh,
                contentDescription = "Refresh gallery",
                onClick = onRefresh,
                enabled = !state.isIndexing,
            )
            DeskIconButton(
                icon = Icons.Default.ImageSearch,
                contentDescription = "Generate tags",
                onClick = onTagAllPendingImages,
                enabled = !state.isIndexing && !isTaggingGallery,
                loading = isTaggingGallery,
                tooltip = "Generate tags",
            )
            DeskButton(onClick = onManageTags) {
                Text("Manage tags")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DeskLayoutGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeskTextField(
                label = "",
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = "Search images",
                modifier = Modifier.weight(1f),
            )
            if (state.keywords.isNotEmpty() || state.selectedKeywords.isNotEmpty()) {
                GalleryKeywordPicker(
                    keywords = state.keywords,
                    selectedKeywords = state.selectedKeywords,
                    draft = keywordFilterDraft,
                    onDraftChange = { keywordFilterDraft = it },
                    onSelectKeyword = {
                        onSelectKeyword(it)
                        keywordFilterDraft = ""
                    },
                    onClearKeywordFilter = {
                        onClearKeywordFilter()
                        keywordFilterDraft = ""
                    },
                    modifier = Modifier.weight(0.9f),
                )
            }
            DeskCompactDropdownField(
                label = "Model",
                value = state.selectedModelId.ifBlank { GalleryViewModel.AllModelsFilterLabel },
                options = listOf(GalleryViewModel.AllModelsFilterLabel) + state.availableModels,
                onValueChange = onModelFilterChange,
                modifier = Modifier.width(190.dp),
            )
            DeskCompactDropdownField(
                label = "Date",
                value = state.selectedDateFilter.label,
                options = GalleryDateFilter.entries.map { it.label },
                onValueChange = onDateFilterChange,
                modifier = Modifier.width(150.dp),
            )
            Text(
                text = when {
                    state.isIndexing && state.images.isEmpty() -> "Indexing..."
                    state.isIndexing -> "${state.images.size} images, refreshing..."
                    else -> "${state.images.size} images"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.selectedKeywords.isNotEmpty()) {
            GalleryActiveKeywordFilters(
                selectedKeywords = state.selectedKeywords,
                onSelectKeyword = onSelectKeyword,
                onClearKeywordFilter = {
                    onClearKeywordFilter()
                    keywordFilterDraft = ""
                },
            )
        }

        if (state.selectedImageIds.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DeskControlSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${state.selectedImageIds.size} selected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                DeskOutlinedButton(onClick = onClearImageSelection, slim = true) {
                    Text("Clear")
                }
                DeskOutlinedButton(onClick = onDeleteSelectedImages, slim = true) {
                    Text("Delete selected")
                }
            }
        }

        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun GalleryKeywordPicker(
    keywords: List<String>,
    selectedKeywords: List<String>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSelectKeyword: (String) -> Unit,
    onClearKeywordFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Tags",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        DeskSearchableTextDropdownField(
            label = "",
            value = draft,
            options = keywords.filterNot { it in selectedKeywords },
            onValueChange = onDraftChange,
            onOptionSelected = onSelectKeyword,
            placeholder = "+ Add filter",
            modifier = Modifier.weight(1f).widthIn(min = 190.dp, max = 380.dp),
        )
        if (selectedKeywords.isNotEmpty()) {
            DeskIconButton(
                icon = Icons.Default.Close,
                contentDescription = "Clear tag filters",
                onClick = onClearKeywordFilter,
            )
        }
    }
}

@Composable
private fun GalleryActiveKeywordFilters(
    selectedKeywords: List<String>,
    onSelectKeyword: (String) -> Unit,
    onClearKeywordFilter: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DeskControlSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Active filters",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.width(82.dp),
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            selectedKeywords.forEach { keyword ->
                DeskChip(
                    text = keyword,
                    selected = true,
                    onRemove = { onSelectKeyword(keyword) },
                )
            }
        }
        DeskIconButton(
            icon = Icons.Default.Close,
            contentDescription = "Clear tag filters",
            onClick = onClearKeywordFilter,
        )
    }
}

@Composable
private fun GalleryGrid(
    images: List<GalleryImage>,
    selectedImageId: Long?,
    selectedImageIds: Set<Long>,
    onSelectImage: (Long) -> Unit,
    onSelectImageWithModifiers: (Long, Boolean, Boolean) -> Unit,
    onSelectPreviousImage: () -> Unit,
    onSelectNextImage: () -> Unit,
    onDeleteSelectedImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (images.isEmpty()) {
        EmptyGallery(modifier)
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val minCell = if (maxWidth < 720.dp) 150.dp else 190.dp
        val gridState = rememberLazyGridState()
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(images.isNotEmpty()) {
            if (images.isNotEmpty()) {
                focusRequester.requestFocus()
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            val scrollbarShape = RoundedCornerShape(999.dp)
            val scrollbarTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
            val scrollbarThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
            val scrollbarThumbHoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minCell),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            return@onKeyEvent false
                        }
                        when (event.key) {
                            Key.Delete -> {
                                onDeleteSelectedImage()
                                true
                            }
                            Key.DirectionLeft, Key.DirectionUp -> {
                                onSelectPreviousImage()
                                true
                            }
                            Key.DirectionRight, Key.DirectionDown -> {
                                onSelectNextImage()
                                true
                            }
                            else -> false
                        }
                    }
                    .focusable()
                    .padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(DeskLayoutGap),
                verticalArrangement = Arrangement.spacedBy(DeskLayoutGap),
            ) {
                items(images, key = { it.id }) { image ->
                    GalleryTile(
                        image = image,
                        selected = image.id == selectedImageId,
                        batchSelected = image.id in selectedImageIds,
                        onClick = {
                            focusRequester.requestFocus()
                            onSelectImage(image.id)
                        },
                        onModifiedClick = { extendRange, toggle ->
                            focusRequester.requestFocus()
                            onSelectImageWithModifiers(image.id, extendRange, toggle)
                        },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(10.dp)
                    .clip(scrollbarShape)
                    .background(scrollbarTrackColor),
            ) {
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(gridState),
                    style = ScrollbarStyle(
                        minimalHeight = 48.dp,
                        thickness = 8.dp,
                        shape = scrollbarShape,
                        hoverDurationMillis = 150,
                        unhoverColor = scrollbarThumbColor,
                        hoverColor = scrollbarThumbHoverColor,
                    ),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun EmptyGallery(modifier: Modifier) {
    DeskPanel(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DeskControlSpacing)) {
                Icon(
                    imageVector = Icons.Default.ImageSearch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(34.dp),
                )
                Text("No indexed images found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GallerySplitter(
    modifier: Modifier = Modifier,
) {
    val cursorIcon = remember {
        PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR))
    }
    Box(
        modifier = modifier.pointerHoverIcon(cursorIcon),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun GalleryTile(
    image: GalleryImage,
    selected: Boolean,
    batchSelected: Boolean,
    onClick: () -> Unit,
    onModifiedClick: (extendRange: Boolean, toggle: Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(DeskPanelCornerRadius)
    Column(
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .then(
                when {
                    batchSelected -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.82f), shape)
                    selected -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                    else -> Modifier
                },
            )
            .onPointerEvent(PointerEventType.Release) { event ->
                val keyboard = event.keyboardModifiers
                if (keyboard.isCtrlPressed || keyboard.isShiftPressed) {
                    onModifiedClick(keyboard.isShiftPressed, keyboard.isCtrlPressed)
                } else {
                    onClick()
                }
            }
            .padding(DeskControlSpacing),
        verticalArrangement = Arrangement.spacedBy(DeskCompactControlSpacing),
    ) {
        GalleryImagePreview(
            file = image.file,
            displayFile = image.previewFile,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(5.dp)),
        )
        Text(
            text = image.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DeskControlSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatGalleryTileDate(image.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            image.dimensions.takeIf { it.isNotBlank() }?.let { dimensions ->
                Text(
                    text = dimensions,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun GalleryDetails(
    image: GalleryImage?,
    keywordDraft: String,
    isTaggingSelectedImage: Boolean,
    isDeletingImage: Boolean,
    onKeywordDraftChange: (String) -> Unit,
    onAddKeyword: () -> Unit,
    onRemoveKeyword: (Long, String) -> Unit,
    onTagSelectedImage: () -> Unit,
    onDeleteImage: (GalleryImage) -> Unit,
    onReuseImage: (GalleryImage) -> Unit,
    onUpscaleImage: (GalleryImage) -> Unit,
    onAnalyzeComposition: (GalleryImage) -> Unit,
    modifier: Modifier = Modifier,
) {
    DeskPanel(modifier = modifier) {
        if (image == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select an image.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@DeskPanel
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DeskPanelSpacing),
        ) {
            GalleryImagePreview(
                file = image.file,
                displayFile = image.file,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(image.aspectRatio)
                    .clip(RoundedCornerShape(6.dp)),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DeskButton(
                    onClick = { onReuseImage(image) },
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Reuse")
                    }
                }
                DeskButton(
                    onClick = { onUpscaleImage(image) },
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Upscale")
                    }
                }
                DeskButton(
                    onClick = { onAnalyzeComposition(image) },
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.ImageSearch, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Analyze")
                    }
                }
                DeskIconButton(
                    icon = Icons.Default.FolderOpen,
                    contentDescription = "Show image in folder",
                    onClick = { showImageInExplorer(image.file) },
                )
                DeskIconButton(
                    icon = Icons.Default.ImageSearch,
                    contentDescription = "Generate tags",
                    onClick = onTagSelectedImage,
                    enabled = !isTaggingSelectedImage,
                    loading = isTaggingSelectedImage,
                    tooltip = "Generate tags",
                )
                if (image.file.isFile) {
                    DeskIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = "Delete image from disk and gallery",
                        onClick = { onDeleteImage(image) },
                        enabled = !isDeletingImage,
                        tooltip = "Delete from disk and database",
                        destructive = true,
                    )
                }
            }

            DetailBlock("Prompt", image.prompt)
            if (image.negativePrompt.isNotBlank()) {
                DetailBlock("Negative Prompt", image.negativePrompt)
            }

            DetailBlock(
                label = "Parameters",
                value = buildList {
                    image.dimensions.takeIf { it.isNotBlank() }?.let { add(it) }
                    image.generationTime?.let { add(formatGalleryDuration(it)) }
                    image.steps?.let { add("$it steps") }
                    image.cfgScale?.let { add("CFG $it") }
                    image.sampler.takeIf { it.isNotBlank() }?.let { add(it) }
                    image.seed?.let { add("Seed $it") }
                    image.modelId.takeIf { it.isNotBlank() }?.let { add(it) }
                }.joinToString("  |  "),
            )

            if (image.loras.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    DeskLabel("LoRAs")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        image.loras.forEach { lora ->
                            DeskStatusBadge(
                                text = "${lora.displayName} ${"%.2f".format(java.util.Locale.US, lora.weight)}",
                                tone = DeskStatusTone.Info,
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                DeskLabel("Tags")
                if (image.keywords.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        image.keywords.forEach { keyword ->
                            RemovableKeywordChip(
                                text = keyword,
                                onRemove = { onRemoveKeyword(image.id, keyword) },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DeskTextField(
                        label = "",
                        value = keywordDraft,
                        onValueChange = onKeywordDraftChange,
                        placeholder = "Add tag",
                        modifier = Modifier.weight(1f),
                    )
                    DeskButton(
                        onClick = onAddKeyword,
                        enabled = keywordDraft.isNotBlank(),
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryImagePreview(
    file: File,
    displayFile: File = file,
    contentScale: ContentScale = ContentScale.Crop,
    modifier: Modifier = Modifier,
) {
    val imageFile = displayFile.takeIf { it.isFile } ?: file
    val bitmap by produceState<ImageBitmap?>(initialValue = null, imageFile.absolutePath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ImageIO.read(imageFile)?.toComposeImageBitmap()
            }.getOrNull()
        }
    }

    ImageContextMenuArea(images = listOf(file.toImageContextMenuData())) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            val imageBitmap = bitmap
            if (imageBitmap == null) {
                Icon(
                    imageVector = Icons.Default.ImageSearch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = file.name,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun DetailBlock(label: String, value: String) {
    if (value.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DeskLabel(label)
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun formatGalleryTileDate(epochMillis: Long): String {
    return runCatching {
        GalleryTileDateFormatter.format(Instant.ofEpochMilli(epochMillis))
    }.getOrDefault("")
}

private fun formatGalleryDuration(seconds: Double): String {
    if (seconds < 60.0) {
        return "${"%.1f".format(java.util.Locale.US, seconds.coerceAtLeast(0.0))}s"
    }
    val roundedSeconds = seconds.roundToInt().coerceAtLeast(0)
    val minutes = roundedSeconds / 60
    val remainingSeconds = roundedSeconds % 60
    return "${minutes}m ${remainingSeconds.toString().padStart(2, '0')}s"
}

@Composable
private fun KeywordChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DeskChip(
        text = text,
        selected = selected,
        onClick = onClick,
    )
}

@Composable
private fun RemovableKeywordChip(
    text: String,
    onRemove: () -> Unit,
) {
    DeskChip(
        text = text,
        onRemove = onRemove,
    )
}

@Composable
private fun TagManagerDialog(
    keywords: List<GalleryKeyword>,
    selectedKeywords: List<String>,
    onDismiss: () -> Unit,
    onSelectKeyword: (String) -> Unit,
    onDeleteKeyword: (String) -> Unit,
    onCleanupUnusedKeywords: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filteredKeywords = remember(keywords, query) {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) {
            keywords
        } else {
            keywords.filter { it.name.lowercase().contains(normalized) }
        }
    }
    val listState = rememberLazyListState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage tags") },
        text = {
            Column(
                modifier = Modifier.width(620.dp),
                verticalArrangement = Arrangement.spacedBy(DeskPanelSpacing),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DeskControlSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DeskTextField(
                        label = "",
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search tags",
                        modifier = Modifier.weight(1f),
                    )
                    DeskOutlinedButton(onClick = onCleanupUnusedKeywords, slim = true) {
                        Text("Cleanup unused")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DeskControlSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Name",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(96.dp),
                    )
                    Text(
                        text = "Count",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(52.dp),
                    )
                    Spacer(Modifier.width(72.dp))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (filteredKeywords.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("No tags found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        lazyItems(filteredKeywords, key = { it.name }) { keyword ->
                            TagManagerRow(
                                keyword = keyword,
                                selected = keyword.name in selectedKeywords,
                                onSelectKeyword = { onSelectKeyword(keyword.name) },
                                onDeleteKeyword = { onDeleteKeyword(keyword.name) },
                            )
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(listState),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(),
                        style = ScrollbarStyle(
                            minimalHeight = 48.dp,
                            thickness = 8.dp,
                            shape = RoundedCornerShape(999.dp),
                            hoverDurationMillis = 150,
                            unhoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f),
                            hoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        )
                    )
                }

                Text(
                    text = "Total tags: ${keywords.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun TagManagerRow(
    keyword: GalleryKeyword,
    selected: Boolean,
    onSelectKeyword: () -> Unit,
    onDeleteKeyword: () -> Unit,
) {
    val shape = RoundedCornerShape(5.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
            )
            .then(if (selected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f), shape) else Modifier)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(DeskControlSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = keyword.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        DeskStatusBadge(
            text = keyword.category,
            tone = DeskStatusTone.Neutral,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = keyword.count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(52.dp),
        )
        DeskIconButton(
            icon = Icons.Default.ImageSearch,
            contentDescription = if (selected) "Remove tag filter" else "Filter by tag",
            onClick = onSelectKeyword,
            enabled = keyword.count > 0,
            tooltip = if (selected) "Remove filter" else "Add filter",
        )
        DeskIconButton(
            icon = Icons.Default.Delete,
            contentDescription = "Delete tag",
            onClick = onDeleteKeyword,
            tooltip = "Delete tag everywhere",
            destructive = true,
        )
    }
}

@Composable
private fun DeleteImageDialog(
    image: GalleryImage,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text("Delete image?") },
        text = {
            Text(
                "${image.displayName} will be permanently deleted together with its thumbnail and text file. " +
                    "The gallery database entry will also be removed.",
            )
        },
        confirmButton = {
            MaterialButton(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun DeleteKeywordDialog(
    keyword: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text("Delete tag?") },
        text = {
            Text(
                "The tag '$keyword' will be removed from every gallery image. The images themselves will not be deleted.",
            )
        },
        confirmButton = {
            MaterialButton(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun BatchDeleteImagesDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text("Delete selected images?") },
        text = {
            Text("$count selected image(s) will be permanently deleted together with thumbnails and text files.")
        },
        confirmButton = {
            MaterialButton(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun CleanupKeywordsDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text("Cleanup tags?") },
        text = {
            Text("All tags that are not assigned to any image will be deleted. This cannot be undone.")
        },
        confirmButton = {
            MaterialButton(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Cleanup")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
