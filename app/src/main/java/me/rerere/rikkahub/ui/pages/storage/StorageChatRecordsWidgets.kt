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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.Eraser
import me.rerere.hugeicons.stroke.TextSelection
import me.rerere.hugeicons.stroke.TransactionHistory
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.repository.AssistantAttachmentStats
import me.rerere.rikkahub.data.repository.ChatRecordsMonthEntry
import me.rerere.rikkahub.utils.UiState
import java.time.YearMonth
import kotlin.uuid.Uuid

@Composable
internal fun ChatRecordsActionCard(
    selectedAssistantId: Uuid?,
    conversationCountState: UiState<Int>,
    attachmentStatsState: UiState<AssistantAttachmentStats>,
    monthEntriesState: UiState<List<ChatRecordsMonthEntry>>,
    selectedMonthCount: Int,
    totalConversationCount: Int,
    selectedConversationCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRequestClear: () -> Unit,
) {
    val context = LocalContext.current

    Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.storage_chat_records_assistant_title),
                style = MaterialTheme.typography.titleMedium,
            )

            if (selectedAssistantId != null) {
                val convCountText = when (conversationCountState) {
                    UiState.Idle,
                    UiState.Loading,
                    -> stringResource(R.string.storage_manager_loading_placeholder)

                    is UiState.Error -> conversationCountState.error.message ?: "Error"
                    is UiState.Success -> stringResource(R.string.storage_chat_records_count, conversationCountState.data)
                }

                Text(
                    text = convCountText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (attachmentStatsState is UiState.Success) {
                    val stats = attachmentStatsState.data
                    val totalAttachmentBytes = stats.imageBytes + stats.fileBytes
                    val totalAttachmentCount = stats.imageCount + stats.fileCount
                    val sizeText = runCatching { Formatter.formatShortFileSize(context, totalAttachmentBytes) }.getOrNull()
                        ?: "${totalAttachmentBytes} B"
                    Text(
                        text = stringResource(
                            R.string.storage_chat_records_attachments_hint,
                            sizeText,
                            totalAttachmentCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when (monthEntriesState) {
                UiState.Idle,
                UiState.Loading,
                -> Text(
                    text = stringResource(R.string.storage_manager_loading_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is UiState.Error -> Text(
                    text = monthEntriesState.error.message ?: "Error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                is UiState.Success -> {
                    if (monthEntriesState.data.isNotEmpty()) {
                        Text(
                            text = if (selectedMonthCount > 0) {
                                stringResource(
                                    R.string.storage_chat_records_months_selected_summary,
                                    selectedMonthCount,
                                    selectedConversationCount,
                                )
                            } else {
                                stringResource(
                                    R.string.storage_chat_records_months_total_summary,
                                    monthEntriesState.data.size,
                                    totalConversationCount,
                                )
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
                val canSelectAll = monthEntriesState is UiState.Success && monthEntriesState.data.isNotEmpty()
                val hasSelection = selectedMonthCount > 0
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    enabled = if (hasSelection) true else canSelectAll,
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
                    enabled = hasSelection,
                    onClick = onRequestClear,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(HugeIcons.TransactionHistory, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.storage_action_clear_records_only))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatRecordsMonthRow(
    entry: ChatRecordsMonthEntry,
    selectedCount: Int,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "storage_chat_records_month_row_scale",
    )

    val yearMonth = remember(entry.yearMonth) { runCatching { YearMonth.parse(entry.yearMonth) }.getOrNull() }
    val titleText = if (yearMonth == null) {
        entry.yearMonth
    } else {
        stringResource(R.string.storage_chat_records_month_label, yearMonth.year, yearMonth.monthValue)
    }

    val selected = selectedCount > 0

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
                    imageVector = HugeIcons.TransactionHistory,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                    )
                    Text(
                        text = stringResource(R.string.storage_chat_records_count, entry.conversationCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                if (selected) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = HugeIcons.CheckmarkCircle02,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = selectedCount.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
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
