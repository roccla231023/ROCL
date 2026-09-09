package me.rerere.rikkahub.ui.pages.storage

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.repository.AssistantAttachmentStats
import me.rerere.rikkahub.data.repository.AssistantFileEntry
import me.rerere.rikkahub.data.repository.AssistantImageEntry
import me.rerere.rikkahub.data.repository.CacheTopLevelUsage
import me.rerere.rikkahub.data.repository.ChatRecordsMonthEntry
import me.rerere.rikkahub.data.repository.LightConversationEntity
import me.rerere.rikkahub.data.repository.OrphanScanResult
import me.rerere.rikkahub.data.repository.StorageCategoryKey
import me.rerere.rikkahub.data.repository.StorageCategoryUsage
import me.rerere.rikkahub.utils.UiState
import kotlin.uuid.Uuid

@Composable
fun StorageCategoryScaffoldContent(
    category: StorageCategoryKey,
    innerPadding: PaddingValues,
    usageState: UiState<StorageCategoryUsage>,
    assistants: List<Assistant>,
    selectedAssistantId: Uuid?,
    onSelectAssistant: (Uuid?) -> Unit,
    attachmentStatsState: UiState<AssistantAttachmentStats>,
    conversationCountState: UiState<Int>,
    assistantImagesState: UiState<AttachmentListState<AssistantImageEntry>>,
    assistantFilesState: UiState<AttachmentListState<AssistantFileEntry>>,
    chatRecordMonthsState: UiState<List<ChatRecordsMonthEntry>>,
    cacheTopLevelUsageState: UiState<List<CacheTopLevelUsage>>,
    onDeleteImages: (Uuid?, List<String>) -> Unit,
    onDeleteFiles: (Uuid?, List<String>) -> Unit,
    onLoadChatRecordConversationsByYearMonth: suspend (Uuid?, String) -> List<LightConversationEntity>,
    onClearChatRecordSelection: (Uuid?, Set<String>, Set<String>) -> Unit,
    orphanScanState: UiState<OrphanScanResult>,
    onScanOrphans: () -> Unit,
    onClearAllOrphans: () -> Unit,
    onClearCache: () -> Unit,
    onLoadMoreImages: () -> Unit,
    onLoadMoreFiles: () -> Unit,
) {
    when (category) {
        StorageCategoryKey.IMAGES -> {
            StorageImagesScaffoldContent(
                innerPadding = innerPadding,
                assistants = assistants,
                selectedAssistantId = selectedAssistantId,
                onSelectAssistant = onSelectAssistant,
                assistantImagesState = assistantImagesState,
                onDeleteImages = onDeleteImages,
                onLoadMoreImages = onLoadMoreImages,
            )
        }
        StorageCategoryKey.FILES -> {
            StorageFilesScaffoldContent(
                innerPadding = innerPadding,
                assistants = assistants,
                selectedAssistantId = selectedAssistantId,
                onSelectAssistant = onSelectAssistant,
                assistantFilesState = assistantFilesState,
                onDeleteFiles = onDeleteFiles,
                onLoadMoreFiles = onLoadMoreFiles,
            )
        }
        StorageCategoryKey.CHAT_RECORDS -> {
            StorageChatRecordsScaffoldContent(
                innerPadding = innerPadding,
                assistants = assistants,
                selectedAssistantId = selectedAssistantId,
                onSelectAssistant = onSelectAssistant,
                monthEntriesState = chatRecordMonthsState,
                conversationCountState = conversationCountState,
                attachmentStatsState = attachmentStatsState,
                onLoadChatRecordConversationsByYearMonth = onLoadChatRecordConversationsByYearMonth,
                onClearChatRecordSelection = onClearChatRecordSelection,
            )
        }
        else -> {
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (category) {
                    StorageCategoryKey.CACHE -> {
                        item(key = "cache") {
                            StorageCacheCard(
                                usageState = usageState,
                                topLevelUsageState = cacheTopLevelUsageState,
                                onClearCache = onClearCache,
                            )
                        }
                    }
                    StorageCategoryKey.HISTORY_FILES -> {
                        item(key = "history") {
                            HistoryFilesCard(
                                usageState = usageState,
                                scanState = orphanScanState,
                                onScan = onScanOrphans,
                                onClearAll = onClearAllOrphans,
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
internal fun CategoryUsageCard(
    usageState: UiState<StorageCategoryUsage>,
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.storage_category_usage_title),
                style = MaterialTheme.typography.titleMedium,
            )
            when (usageState) {
                UiState.Idle, UiState.Loading -> Text(
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
                        ?: "$bytes B"
                    Text(
                        text = stringResource(R.string.storage_category_usage_value, sizeText, count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AssistantFilterRow(
    assistants: List<Assistant>,
    selected: Uuid?,
    onSelect: (Uuid?) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.storage_filter_all_assistants)) },
        )
        assistants.forEach { assistant ->
            val name = assistant.name.trim().ifBlank { stringResource(R.string.storage_assistant_unnamed) }
            FilterChip(
                selected = selected == assistant.id,
                onClick = { onSelect(assistant.id) },
                label = { Text(name) },
            )
        }
    }
}
