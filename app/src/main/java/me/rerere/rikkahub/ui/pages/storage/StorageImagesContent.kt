package me.rerere.rikkahub.ui.pages.storage

import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.Eraser
import me.rerere.hugeicons.stroke.TextSelection
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.repository.AssistantImageEntry
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog
import me.rerere.rikkahub.utils.UiState
import kotlin.uuid.Uuid

@Composable
fun StorageImagesScaffoldContent(
    innerPadding: PaddingValues,
    assistants: List<Assistant>,
    selectedAssistantId: Uuid?,
    onSelectAssistant: (Uuid?) -> Unit,
    assistantImagesState: UiState<AttachmentListState<AssistantImageEntry>>,
    onDeleteImages: (Uuid?, List<String>) -> Unit,
    onLoadMoreImages: () -> Unit,
) {
    val context = LocalContext.current

    var selectedPaths by rememberSaveable(selectedAssistantId) { mutableStateOf(emptySet<String>()) }
    var showConfirmDelete by rememberSaveable(selectedAssistantId) { mutableStateOf(false) }
    var previewIndex by rememberSaveable(selectedAssistantId) { mutableStateOf<Int?>(null) }

    val imagesState = assistantImagesState as? UiState.Success<AttachmentListState<AssistantImageEntry>>
    val images = imagesState?.data?.items.orEmpty()
    val totalCount = imagesState?.data?.totalCount ?: 0
    val totalBytes = imagesState?.data?.totalBytes ?: 0L
    val canLoadMore = imagesState?.data?.hasMore == true
    val isLoadingMore = imagesState?.data?.isLoadingMore == true

    LaunchedEffect(images) {
        if (selectedPaths.isEmpty()) return@LaunchedEffect
        val valid = images.asSequence().map { it.absolutePath }.toSet()
        selectedPaths = selectedPaths.intersect(valid)
    }

    val selectedCount = selectedPaths.size
    val selectedBytes = remember(images, selectedPaths) {
        if (selectedPaths.isEmpty()) 0L else images.asSequence()
            .filter { it.absolutePath in selectedPaths }
            .sumOf { it.bytes }
    }
    val selectedBytesText = runCatching { Formatter.formatShortFileSize(context, selectedBytes) }
        .getOrNull()
        ?: "${selectedBytes} B"

    val previewImages = remember(images, previewIndex) {
        val idx = previewIndex ?: 0
        val urls = images.map { it.url }
        if (idx <= 0 || urls.isEmpty()) urls else urls.drop(idx) + urls.take(idx)
    }
    if (previewIndex != null && images.isNotEmpty()) {
        ImagePreviewDialog(
            images = previewImages,
            onDismissRequest = { previewIndex = null },
        )
    }

    val gridState = rememberLazyGridState()
    val shouldLoadMore by remember(canLoadMore, isLoadingMore, gridState) {
        derivedStateOf {
            if (!canLoadMore || isLoadingMore) return@derivedStateOf false
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = gridState.layoutInfo.totalItemsCount
            totalItems > 0 && lastVisible >= totalItems - 4
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMoreImages()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "assistant_filter", span = { GridItemSpan(maxLineSpan) }) {
            AssistantFilterRow(
                assistants = assistants,
                selected = selectedAssistantId,
                onSelect = onSelectAssistant,
            )
        }

        item(key = "assistant_images_actions", span = { GridItemSpan(maxLineSpan) }) {
            AssistantImagesGalleryCard(
                selectedAssistantId = selectedAssistantId,
                imagesState = assistantImagesState,
                selectedCount = selectedCount,
                selectedBytesText = selectedBytesText,
                totalCount = totalCount,
                totalBytes = totalBytes,
                onSelectAll = {
                    if (assistantImagesState is UiState.Success && assistantImagesState.data.items.isNotEmpty()) {                        selectedPaths = assistantImagesState.data.items
                            .asSequence()
                            .map { it.absolutePath }
                            .toSet()
                    }
                },
                onClearSelection = {
                    if (selectedPaths.isEmpty()) return@AssistantImagesGalleryCard                    selectedPaths = emptySet()
                },
                onRequestDelete = {
                    if (selectedPaths.isEmpty()) return@AssistantImagesGalleryCard                    showConfirmDelete = true
                },
            )
        }

        if (assistantImagesState is UiState.Success) {
            itemsIndexed(
                items = images,
                key = { _, entry -> entry.absolutePath },
            ) { index, entry ->
                val selectionMode = selectedCount > 0
                val isSelected = entry.absolutePath in selectedPaths
                AssistantImageThumb(
                    entry = entry,
                    selected = isSelected,
                    selectionMode = selectionMode,
                    onClick = {
                        if (selectionMode) {                            selectedPaths = if (isSelected) selectedPaths - entry.absolutePath else selectedPaths + entry.absolutePath
                        } else {                            previewIndex = index
                        }
                    },
                    onLongClick = {                        selectedPaths = if (isSelected) selectedPaths - entry.absolutePath else selectedPaths + entry.absolutePath
                    },
                )
            }
        }

        if (isLoadingMore) {
            item(key = "images_loading_more", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.storage_manager_loading_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }

    if (showConfirmDelete) {
        val descRes = if (selectedAssistantId == null) {
            R.string.storage_confirm_delete_selected_images_desc_global
        } else {
            R.string.storage_confirm_delete_selected_images_desc
        }
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text(stringResource(R.string.storage_confirm_delete_selected_images_title)) },
            text = {
                Text(
                    text = stringResource(
                        descRes,
                        selectedCount,
                        selectedBytesText,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {                        showConfirmDelete = false
                        val targets = selectedPaths.toList()
                        selectedPaths = emptySet()
                        onDeleteImages(selectedAssistantId, targets)
                    }
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun AssistantImagesGalleryCard(
    selectedAssistantId: Uuid?,
    imagesState: UiState<AttachmentListState<AssistantImageEntry>>,
    selectedCount: Int,
    selectedBytesText: String,
    totalCount: Int,
    totalBytes: Long,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val context = LocalContext.current
    val hasSelection = selectedCount > 0
    val isReady = imagesState is UiState.Success && totalCount > 0
    Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.storage_images_preview_title),
                style = MaterialTheme.typography.titleMedium,
            )

            when (imagesState) {
                UiState.Idle,
                UiState.Loading,
                -> Text(
                    text = stringResource(R.string.storage_manager_loading_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is UiState.Error -> Text(
                    text = imagesState.error.message ?: "Error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                is UiState.Success -> {
                    if (totalCount == 0) {
                        val emptyHint = if (selectedAssistantId == null) {
                            R.string.storage_images_empty_global_hint
                        } else {
                            R.string.storage_images_empty_hint
                        }
                        Text(
                            text = stringResource(emptyHint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val totalBytesText = runCatching { Formatter.formatShortFileSize(context, totalBytes) }
                            .getOrNull()
                            ?: "${totalBytes} B"

                        Text(
                            text = if (selectedCount > 0) {
                                stringResource(R.string.storage_images_selected_summary, selectedBytesText, selectedCount)
                            } else {
                                stringResource(R.string.storage_images_total_summary, totalBytesText, totalCount)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    enabled = hasSelection || isReady,
                    onClick = if (hasSelection) onClearSelection else onSelectAll,
                ) {
                    Icon(
                        imageVector = if (hasSelection) HugeIcons.Eraser else HugeIcons.TextSelection,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (hasSelection) {
                                R.string.storage_action_clear_selection
                            } else {
                                R.string.storage_action_select_all
                            },
                        ),
                    )
                }

                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    enabled = isReady && hasSelection,
                    onClick = onRequestDelete,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(HugeIcons.Delete02, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.storage_action_delete_selected),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssistantImageThumb(
    entry: AssistantImageEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "storage_image_thumb_scale",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        AsyncImage(
            model = entry.url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
            )
            Icon(
                imageVector = HugeIcons.CheckmarkCircle02,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )
        } else if (selectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .width(18.dp)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.5f)),
            )
        }
    }
}
