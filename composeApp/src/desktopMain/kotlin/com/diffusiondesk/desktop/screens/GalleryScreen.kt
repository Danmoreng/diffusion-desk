package com.diffusiondesk.desktop.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.diffusiondesk.desktop.core.GalleryImage
import com.diffusiondesk.desktop.viewmodel.GalleryUiState
import java.awt.Cursor
import java.awt.FileDialog
import java.awt.Desktop
import java.awt.EventQueue
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.ui.component.DefaultButton as Button
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
    onSelectKeyword: (String) -> Unit,
    onClearKeywordFilter: () -> Unit,
    onSelectImage: (Long) -> Unit,
    onKeywordDraftChange: (String) -> Unit,
    onAddKeyword: () -> Unit,
    onRemoveKeyword: (Long, String) -> Unit,
    onTagSelectedImage: () -> Unit,
    onDeleteImage: () -> Unit,
    previewPanelWidthDp: Int,
    onPreviewPanelWidthChange: (Int) -> Unit,
    onReuseImage: (GalleryImage) -> Unit,
) {
    var imagePendingDeletion by remember { mutableStateOf<GalleryImage?>(null) }

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
                    onSelectKeyword = onSelectKeyword,
                    onClearKeywordFilter = onClearKeywordFilter,
                )
                GalleryGrid(
                    images = state.images,
                    selectedImageId = state.selectedImage?.id,
                    onSelectImage = onSelectImage,
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
}

@Composable
private fun GalleryToolbar(
    state: GalleryUiState,
    isTaggingGallery: Boolean,
    onRefresh: () -> Unit,
    onTagAllPendingImages: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectKeyword: (String) -> Unit,
    onClearKeywordFilter: () -> Unit,
) {
    var keywordFilterDraft by remember(state.selectedKeyword) { mutableStateOf("") }

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
            if (state.keywords.isNotEmpty() || state.selectedKeyword.isNotBlank()) {
                GalleryKeywordFilter(
                    keywords = state.keywords,
                    selectedKeyword = state.selectedKeyword,
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
                    modifier = Modifier.weight(1f),
                )
            }
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
private fun GalleryKeywordFilter(
    keywords: List<String>,
    selectedKeyword: String,
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
        if (selectedKeyword.isNotBlank()) {
            KeywordChip(
                text = selectedKeyword,
                selected = true,
                onClick = onClearKeywordFilter,
            )
        }
        DeskSearchableTextDropdownField(
            label = "",
            value = draft,
            options = keywords.filterNot { it == selectedKeyword },
            onValueChange = onDraftChange,
            onOptionSelected = onSelectKeyword,
            placeholder = "+ Add filter",
            modifier = Modifier.weight(1f).widthIn(min = 190.dp, max = 340.dp),
        )
        if (selectedKeyword.isNotBlank()) {
            DeskIconButton(
                icon = Icons.Default.Close,
                contentDescription = "Clear tag filter",
                onClick = onClearKeywordFilter,
            )
        }
    }
}

@Composable
private fun GalleryGrid(
    images: List<GalleryImage>,
    selectedImageId: Long?,
    onSelectImage: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (images.isEmpty()) {
        EmptyGallery(modifier)
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val minCell = if (maxWidth < 720.dp) 150.dp else 190.dp
        val gridState = rememberLazyGridState()
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
                    .padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(DeskLayoutGap),
                verticalArrangement = Arrangement.spacedBy(DeskLayoutGap),
            ) {
                items(images, key = { it.id }) { image ->
                    GalleryTile(
                        image = image,
                        selected = image.id == selectedImageId,
                        onClick = { onSelectImage(image.id) },
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

@Composable
private fun GalleryTile(
    image: GalleryImage,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(DeskPanelCornerRadius)
    Column(
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick)
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
                Button(
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
                DeskIconButton(
                    icon = Icons.Default.FolderOpen,
                    contentDescription = "Show image in folder",
                    onClick = { showInFolder(image.file) },
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
                    image.steps?.let { add("$it steps") }
                    image.cfgScale?.let { add("CFG $it") }
                    image.sampler.takeIf { it.isNotBlank() }?.let { add(it) }
                    image.seed?.let { add("Seed $it") }
                    image.modelId.takeIf { it.isNotBlank() }?.let { add(it) }
                }.joinToString("  |  "),
            )

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
                    Button(
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

    ContextMenuArea(
        items = {
            buildList {
                if (file.exists()) {
                    add(ContextMenuItem("Copy Image") { runAfterPopupClick { file.copyImageToClipboard() } })
                    add(ContextMenuItem("Save Image As...") { runAfterPopupClick { file.saveImageAs() } })
                    add(ContextMenuItem("Open Image") { runAfterPopupClick { openFile(file) } })
                    add(ContextMenuItem("Show in Explorer") { runAfterPopupClick { showInFolder(file) } })
                }
            }
        },
    ) {
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

private fun runAfterPopupClick(action: () -> Unit) {
    EventQueue.invokeLater(action)
}

@Composable
private fun KeywordChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RemovableKeywordChip(
    text: String,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Remove $text",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(15.dp)
                .clickable(onClick = onRemove),
        )
    }
}

private fun showInFolder(file: File) {
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
    } else if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().open(file.parentFile)
    }
}

private fun openFile(file: File) {
    if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().open(file)
    }
}

private fun File.copyImageToClipboard() {
    val image = ImageIO.read(this) ?: return
    Toolkit.getDefaultToolkit().systemClipboard.setContents(GalleryImageTransferable(image), null)
}

private fun File.saveImageAs() {
    val extension = extension.ifBlank { "png" }
    val dialog = FileDialog(activeFrame(), "Save Image", FileDialog.SAVE).apply {
        file = name
        isVisible = true
    }
    val selectedFile = dialog.file ?: return
    val directory = dialog.directory ?: return
    val target = File(directory, selectedFile).withImageExtension(extension)
    copyTo(target, overwrite = true)
}

private fun File.withImageExtension(extension: String): File {
    return if (name.substringAfterLast('.', missingDelimiterValue = "").isBlank()) {
        File(parentFile, "$name.$extension")
    } else {
        this
    }
}

private fun activeFrame(): Frame? {
    return Frame.getFrames().firstOrNull { it.isActive }
        ?: Frame.getFrames().firstOrNull { it.isVisible }
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

private class GalleryImageTransferable(
    private val image: java.awt.Image,
) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor

    override fun getTransferData(flavor: DataFlavor): Any {
        if (!isDataFlavorSupported(flavor)) {
            throw UnsupportedOperationException("Unsupported clipboard flavor: $flavor")
        }
        return image
    }
}
