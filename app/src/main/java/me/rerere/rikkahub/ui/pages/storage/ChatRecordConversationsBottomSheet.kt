package me.rerere.rikkahub.ui.pages.storage

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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.repository.LightConversationEntity
import me.rerere.rikkahub.utils.UiState
import java.time.YearMonth

@Composable
internal fun ChatRecordConversationsBottomSheet(
    yearMonth: String,
    totalCount: Int,
    conversationsState: UiState<List<LightConversationEntity>>,
    initialSelection: ChatRecordMonthSelection?,
    onDismissRequest: () -> Unit,
    onApplySelection: (Set<String>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var initializedSelection by remember(yearMonth) { mutableStateOf(false) }
    var selectedIds by remember(yearMonth) { mutableStateOf(emptySet<String>()) }

    val conversations = (conversationsState as? UiState.Success)?.data
    LaunchedEffect(yearMonth, conversations, initialSelection) {
        if (conversations == null) return@LaunchedEffect
        if (initializedSelection) return@LaunchedEffect

        val allIds = conversations.asSequence().map { it.id }.toSet()
        selectedIds = when (initialSelection) {
            is ChatRecordMonthSelection.All -> allIds
            is ChatRecordMonthSelection.Some -> initialSelection.conversationIds.intersect(allIds)
            null -> emptySet()
        }
        initializedSelection = true
    }

    val onHide: () -> Unit = {
        scope.launch {
            sheetState.hide()
            onDismissRequest()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onHide,
        sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            IconButton(
                onClick = {
                        onHide()
                },
            ) {
                Icon(HugeIcons.ArrowDown01, contentDescription = null)
            }
        },
    ) {
        val parsedYearMonth = remember(yearMonth) { runCatching { YearMonth.parse(yearMonth) }.getOrNull() }
        val titleText = if (parsedYearMonth == null) {
            yearMonth
        } else {
            stringResource(
                R.string.storage_chat_records_month_label,
                parsedYearMonth.year,
                parsedYearMonth.monthValue,
            )
        }

        val total = (conversationsState as? UiState.Success)?.data?.size ?: totalCount
        val selectedCount = selectedIds.size

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = stringResource(
                        R.string.storage_chat_records_sheet_selected_summary,
                        selectedCount,
                        total,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    enabled = conversationsState is UiState.Success && conversationsState.data.isNotEmpty(),
                    onClick = {
                        val allIds = (conversationsState as? UiState.Success)
                            ?.data
                            ?.asSequence()
                            ?.map { it.id }
                            ?.toSet()
                            .orEmpty()
                        if (allIds.isEmpty()) return@FilledTonalButton
                    selectedIds = allIds
                    },
                ) {
                    Text(stringResource(R.string.storage_action_select_all))
                }

                FilledTonalButton(
                    enabled = selectedIds.isNotEmpty(),
                    onClick = {
                        selectedIds = emptySet()
                    },
                ) {
                    Text(stringResource(R.string.storage_action_clear_selection))
                }

                TextButton(
                    onClick = {
                        onApplySelection(selectedIds)
                        onHide()
                    },
                ) {
                    Text(stringResource(R.string.done))
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            when (conversationsState) {
                UiState.Idle,
                UiState.Loading,
                -> Text(
                    text = stringResource(R.string.storage_manager_loading_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is UiState.Error -> Text(
                    text = conversationsState.error.message ?: "Error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                is UiState.Success -> {
                    val list = conversationsState.data
                    if (list.isEmpty()) {
                        Text(
                            text = stringResource(R.string.storage_chat_records_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(
                                items = list,
                                key = { it.id },
                            ) { conversation ->
                                val selected = conversation.id in selectedIds
                                ChatRecordConversationRow(
                                    title = conversation.title,
                                    selected = selected,
                                    onClick = {
                        selectedIds = if (selected) {
                                            selectedIds - conversation.id
                                        } else {
                                            selectedIds + conversation.id
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRecordConversationRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "storage_chat_records_conversation_row_scale",
    )

    val titleText = title.trim().ifBlank { stringResource(R.string.chat_page_new_chat) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surfaceContainerLow,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = HugeIcons.CheckmarkCircle02,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Text(
            text = titleText,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Spacer(Modifier.width(2.dp))
    }
}

