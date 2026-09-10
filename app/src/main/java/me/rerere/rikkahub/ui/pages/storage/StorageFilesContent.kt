package me.rerere.rikkahub.ui.pages.storage

import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.Eraser
import me.rerere.hugeicons.stroke.File01
import me.rerere.hugeicons.stroke.TextSelection
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.repository.AssistantFileEntry
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.UiState
import java.io.File
import kotlin.uuid.Uuid

@Composable
fun StorageFilesScaffoldContent(
    innerPadding: PaddingValues,
    assistants: List<Assistant>,
    selectedAssistantId: Uuid?,
    onSelectAssistant: (Uuid?) -> Unit,
    assistantFilesState: UiState<AttachmentListState<AssistantFileEntry>>,
    onDeleteFiles: (Uuid?, List<String>) -> Unit,
    onLoadMoreFiles: () -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current

    var selectedPaths by rememberSaveable(selectedAssistantId) { mutableStateOf(emptySet<String>()) }
    var showConfirmDelete by rememberSaveable(selectedAssistantId) { mutableStateOf(false) }

    val filesState = assistantFilesState as? UiState.Success<AttachmentListState<AssistantFileEntry>>
    val files = filesState?.data?.items.orEmpty()
    val totalCount = filesState?.data?.totalCount ?: 0
    val totalBytes = filesState?.data?.totalBytes ?: 0L
    val canLoadMore = filesState?.data?.hasMore == true
    val isLoadingMore = filesState?.data?.isLoadingMore == true

    LaunchedEffect(files) {
        if (selectedPaths.isEmpty()) return@LaunchedEffect
        val valid = files.asSequence().map { it.absolutePath }.toSet()
        selectedPaths = selectedPaths.intersect(valid)
    }

    val selectedCount = selectedPaths.size
    val selectedBytes = remember(files, selectedPaths) {
        if (selectedPaths.isEmpty()) 0L else files.asSequence()
            .filter { it.absolutePath in selectedPaths }
            .sumOf { it.bytes }
    }
    val selectedBytesText = runCatching { Formatter.formatShortFileSize(context, selectedBytes) }
        .getOrNull()
        ?: "${selectedBytes} B"

    val listState = rememberLazyListState()
    val shouldLoadMore by remember(canLoadMore, isLoadingMore, listState) {
        derivedStateOf {
            if (!canLoadMore || isLoadingMore) return@derivedStateOf false
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            totalItems > 0 && lastVisible >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMoreFiles()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "assistant_filter") {
            AssistantFilterRow(
                assistants = assistants,
                selected = selectedAssistantId,
                onSelect = onSelectAssistant,
            )
        }

        item(key = "files_card") {
            AssistantFilesCard(
                selectedAssistantId = selectedAssistantId,
                filesState = assistantFilesState,
                selectedCount = selectedCount,
                selectedBytesText = selectedBytesText,
                totalCount = totalCount,
                totalBytes = totalBytes,
                onSelectAll = {
                    if (assistantFilesState !is UiState.Success) return@AssistantFilesCard
                    if (assistantFilesState.data.items.isEmpty()) return@AssistantFilesCard
                    selectedPaths = assistantFilesState.data.items
                        .asSequence()
                        .map { it.absolutePath }
                        .toSet()
                },
                onClearSelection = {
                    if (selectedPaths.isEmpty()) return@AssistantFilesCard
                    selectedPaths = emptySet()
                },
                onRequestDelete = {
                    if (selectedPaths.isEmpty()) return@AssistantFilesCard
                    showConfirmDelete = true
                },
            )
        }

        if (assistantFilesState is UiState.Success) {
            items(
                items = files,
                key = { it.absolutePath },
            ) { entry ->
                val selectionMode = selectedCount > 0
                val isSelected = entry.absolutePath in selectedPaths
                AssistantFileRow(
                    entry = entry,
                    selected = isSelected,
                    selectionMode = selectionMode,
                    onClick = {
                        if (selectionMode) {
                        selectedPaths =
                                if (isSelected) selectedPaths - entry.absolutePath else selectedPaths + entry.absolutePath
                            return@AssistantFileRow
                        }
                        runCatching {
                            val file = File(entry.absolutePath)
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file,
                            )
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, entry.mime.trim().ifBlank { "*/*" })
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                        }.onFailure {
                        toaster.show(
                                message = context.getString(
                                    R.string.storage_files_open_failed,
                                    entry.fileName.trim().ifBlank { File(entry.absolutePath).name },
                                ),
                                type = com.dokar.sonner.ToastType.Error,
                            )
                        }
                    },
                    onLongClick = {
                        selectedPaths =
                            if (isSelected) selectedPaths - entry.absolutePath else selectedPaths + entry.absolutePath
                    },
                )
            }
        }

        if (isLoadingMore) {
            item(key = "files_loading_more") {
                Text(
                    text = stringResource(R.string.storage_manager_loading_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showConfirmDelete) {
        val descRes = if (selectedAssistantId == null) {
            R.string.storage_confirm_delete_selected_files_desc_global
        } else {
            R.string.storage_confirm_delete_selected_files_desc
        }
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text(stringResource(R.string.storage_confirm_delete_selected_files_title)) },
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
                    onClick = {
                        showConfirmDelete = false
                        val targets = selectedPaths.toList()
                        selectedPaths = emptySet()
                        onDeleteFiles(selectedAssistantId, targets)
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
private fun AssistantFilesCard(
    selectedAssistantId: Uuid?,
    filesState: UiState<AttachmentListState<AssistantFileEntry>>,
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
    val isReady = filesState is UiState.Success && totalCount > 0
    Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.storage_files_preview_title),
                style = MaterialTheme.typography.titleMedium,
            )

            when (filesState) {
                UiState.Idle,
                UiState.Loading,
                -> Text(
                    text = stringResource(R.string.storage_manager_loading_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is UiState.Error -> Text(
                    text = filesState.error.message ?: "Error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                is UiState.Success -> {
                    if (totalCount == 0) {
                        val emptyHint = if (selectedAssistantId == null) {
                            R.string.storage_files_empty_global_hint
                        } else {
                            R.string.storage_files_empty_hint
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
                                stringResource(R.string.storage_files_selected_summary, selectedBytesText, selectedCount)
                            } else {
                                stringResource(R.string.storage_files_total_summary, totalBytesText, totalCount)
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssistantFileRow(
    entry: AssistantFileEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "storage_file_row_scale",
    )

    val sizeText = remember(entry.bytes) {
        runCatching { Formatter.formatShortFileSize(context, entry.bytes) }.getOrNull()
            ?: "${entry.bytes} B"
    }

    Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                )
            }

            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = HugeIcons.File01,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = entry.fileName.trim().ifBlank { File(entry.absolutePath).name },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = sizeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (selected) {
                    Icon(
                        imageVector = HugeIcons.CheckmarkCircle02,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                } else if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)),
                    )
                }
            }
        }
    }
}
