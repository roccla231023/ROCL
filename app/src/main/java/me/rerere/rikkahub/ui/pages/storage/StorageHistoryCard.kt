package me.rerere.rikkahub.ui.pages.storage

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.repository.OrphanEntry
import me.rerere.rikkahub.data.repository.OrphanScanResult
import me.rerere.rikkahub.data.repository.StorageCategoryUsage
import me.rerere.rikkahub.utils.UiState
import java.io.File

@Composable
fun HistoryFilesCard(
    usageState: UiState<StorageCategoryUsage>,
    scanState: UiState<OrphanScanResult>,
    onScan: () -> Unit,
    onClearAll: () -> Unit,
) {
    val context = LocalContext.current
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.storage_history_title),
                    style = MaterialTheme.typography.titleMedium,
                )

                if (scanState is UiState.Success) {
                    val result = scanState.data
                    val sizeText = runCatching { Formatter.formatShortFileSize(context, result.totalBytes) }
                        .getOrNull()
                        ?: "${result.totalBytes} B"
                    Text(
                        text = stringResource(R.string.storage_category_usage_value, sizeText, result.totalCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    when (usageState) {
                        UiState.Idle,
                        UiState.Loading,
                        -> Text(
                            text = stringResource(R.string.storage_manager_loading_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        is UiState.Error -> Text(
                            text = usageState.error.message ?: "Error",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )

                        is UiState.Success -> {
                            val bytes = usageState.data.bytes
                            val count = usageState.data.fileCount
                            val sizeText = runCatching { Formatter.formatShortFileSize(context, bytes) }.getOrNull()
                                ?: "${bytes} B"

                            Text(
                                text = stringResource(R.string.storage_category_usage_value, sizeText, count),
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
                        onClick = {                            onScan()
                        }
                    ) {
                        Icon(HugeIcons.Refresh01, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.storage_action_scan))
                    }

                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = {                            confirmClear = true
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Icon(HugeIcons.Delete02, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.storage_action_clear_orphans))
                    }
                }
            }
        }

        if (scanState != UiState.Idle) {
            Card(
                
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when (scanState) {
                        UiState.Idle -> Unit

                        UiState.Loading -> Text(
                            text = stringResource(R.string.storage_manager_loading_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        is UiState.Error -> Text(
                            text = scanState.error.message ?: "Error",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )

                        is UiState.Success -> {
                            val result = scanState.data
                            val sizeText = runCatching { Formatter.formatShortFileSize(context, result.totalBytes) }
                                .getOrNull()
                                ?: "${result.totalBytes} B"
                            Text(
                                text = stringResource(R.string.storage_history_scan_summary, sizeText, result.totalCount),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                OrphanPreviewList(entries = result.preview)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.storage_confirm_clear_history_title)) },
            text = { Text(stringResource(R.string.storage_confirm_clear_history_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {                        confirmClear = false
                        onClearAll()
                    }
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun OrphanPreviewList(entries: List<OrphanEntry>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    if (entries.isEmpty()) {
        Text(
            text = stringResource(R.string.storage_history_no_orphans),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        entries.forEach { entry ->
            val sizeText = runCatching { Formatter.formatShortFileSize(context, entry.bytes) }.getOrNull()
                ?: "${entry.bytes} B"
            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = entry.absolutePath.substringAfterLast(File.separatorChar),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = sizeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = entry.absolutePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
