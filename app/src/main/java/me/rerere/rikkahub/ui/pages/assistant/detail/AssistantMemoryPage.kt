package me.rerere.rikkahub.ui.pages.assistant.detail

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Pin
import me.rerere.hugeicons.stroke.PinOff
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById

import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.MemoryRetrievalMode
import me.rerere.rikkahub.data.model.effectiveMemoryRetrievalMode
import me.rerere.rikkahub.data.model.requiresEmbedding
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt

@Composable
fun AssistantMemoryPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val memories by vm.memories.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_page_tab_memory))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        AssistantMemoryContent(
            innerPadding = innerPadding,
            assistant = assistant,
            memories = memories,
            settings = settings,
            onUpdateAssistant = { vm.update(it) },
            onDeleteMemory = { vm.deleteMemory(it) },
            onAddMemory = { vm.addMemory(it) },
            onUpdateMemory = { vm.updateMemory(it) }
        )
    }
}

@Composable
private fun AssistantMemoryContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    memories: List<AssistantMemory>,
    settings: Settings,
    onUpdateAssistant: (Assistant) -> Unit,
    onAddMemory: (AssistantMemory) -> Unit,
    onUpdateMemory: (AssistantMemory) -> Unit,
    onDeleteMemory: (AssistantMemory) -> Unit,
) {
    val memoryDialogState = useEditState<AssistantMemory> {
        if (it.id == 0) {
            onAddMemory(it)
        } else {
            onUpdateMemory(it)
        }
    }
    var pendingDeleteMemory by remember { mutableStateOf<AssistantMemory?>(null) }

    var showTimeReminderIntervalDialog by remember(assistant.id) { mutableStateOf(false) }
    var timeReminderIntervalInput by remember(assistant.id) { mutableStateOf("") }

    if (showTimeReminderIntervalDialog) {
        val interval = timeReminderIntervalInput.toIntOrNull()?.takeIf { it > 0 }
        AlertDialog(
            onDismissRequest = { showTimeReminderIntervalDialog = false },
            title = { Text(stringResource(R.string.assistant_page_time_reminder_interval)) },
            text = {
                TextField(
                    value = timeReminderIntervalInput,
                    onValueChange = { timeReminderIntervalInput = it },
                    label = { Text(stringResource(R.string.assistant_page_time_reminder_interval_label)) },
                    supportingText = { Text(stringResource(R.string.assistant_page_time_reminder_interval_hint)) },
                    isError = interval == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = interval != null,
                    onClick = {
                        interval?.let {
                            onUpdateAssistant(assistant.copy(timeReminderIntervalMinutes = it))
                        }
                        showTimeReminderIntervalDialog = false
                    },
                ) {
                    Text(stringResource(R.string.assistant_page_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimeReminderIntervalDialog = false }) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
            },
        )
    }

    memoryDialogState.EditStateContent { memory, update ->
        AlertDialog(
            onDismissRequest = {
                memoryDialogState.dismiss()
            },
            title = {
                Text(stringResource(R.string.assistant_page_manage_memory_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        value = memory.content,
                        onValueChange = {
                            update(memory.copy(content = it))
                        },
                        label = {
                            Text(stringResource(R.string.assistant_page_manage_memory_title))
                        },
                        minLines = 2,
                        maxLines = 8
                    )
                    FilterChip(
                        selected = memory.pinned,
                        onClick = { update(memory.copy(pinned = !memory.pinned)) },
                        label = { Text(stringResource(R.string.assistant_page_memory_pinned_badge)) },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        memoryDialogState.confirm()
                    }
                ) {
                    Text(stringResource(R.string.assistant_page_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        memoryDialogState.dismiss()
                    }
                ) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(innerPadding)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CardGroup {
            item(
                headlineContent = { Text(memoryModeTitle(assistant)) },
                supportingContent = { Text(memoryModeDescription(assistant)) },
            )
        }

        CardGroup {
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_memory)) },
                supportingContent = {
                    Text(text = stringResource(R.string.assistant_page_memory_desc))
                },
                trailingContent = {
                    Switch(
                        checked = assistant.enableMemory,
                        onCheckedChange = {
                            onUpdateAssistant(assistant.copy(enableMemory = it))
                        }
                    )
                }
            )
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_session_memory)) },
                supportingContent = {
                    Text(text = stringResource(R.string.assistant_page_session_memory_desc))
                },
                trailingContent = {
                    Switch(
                        checked = assistant.enableSessionMemory,
                        onCheckedChange = {
                            onUpdateAssistant(assistant.copy(enableSessionMemory = it))
                        }
                    )
                }
            )
        }
        AnimatedVisibility(
            visible = assistant.enableMemory,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            CardGroup {
                item(
                    headlineContent = { Text(stringResource(R.string.assistant_page_global_memory)) },
                    supportingContent = {
                        Text(text = stringResource(R.string.assistant_page_global_memory_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = assistant.useGlobalMemory,
                            onCheckedChange = {
                                onUpdateAssistant(assistant.copy(useGlobalMemory = it))
                            },
                        )
                    }
                )
                item(
                    headlineContent = { Text(stringResource(R.string.assistant_page_recent_chats)) },
                    supportingContent = {
                        Text(text = stringResource(R.string.assistant_page_recent_chats_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = assistant.enableRecentChatsReference,
                            onCheckedChange = {
                                onUpdateAssistant(assistant.copy(enableRecentChatsReference = it))
                            }
                        )
                    }
                )
            }
        }

        CardGroup {
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_time_reminder)) },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.assistant_page_time_reminder_desc),
                    )
                },
                trailingContent = {
                    Switch(
                        checked = assistant.enableTimeReminder,
                        onCheckedChange = {
                            onUpdateAssistant(
                                assistant.copy(
                                    enableTimeReminder = it
                                )
                            )
                        }
                    )
                }
            )
        }
        AnimatedVisibility(
            visible = assistant.enableTimeReminder,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            CardGroup {
                item(
                    headlineContent = { Text(stringResource(R.string.assistant_page_time_reminder_interval)) },
                    supportingContent = { Text(stringResource(R.string.assistant_page_time_reminder_interval_desc)) },
                    trailingContent = { Text(stringResource(R.string.assistant_page_time_reminder_interval_value, assistant.timeReminderIntervalMinutes)) },
                    onClick = {
                        timeReminderIntervalInput = assistant.timeReminderIntervalMinutes.toString()
                        showTimeReminderIntervalDialog = true
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = assistant.enableMemory,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MemoryRetrievalSettings(
                assistant = assistant,
                settings = settings,
                onUpdateAssistant = onUpdateAssistant,
            )
            Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.assistant_page_manage_memory_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .align(Alignment.CenterStart)
            )

            IconButton(
                onClick = {
                    memoryDialogState.open(AssistantMemory(id = 0, content = ""))
                },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = HugeIcons.Add01,
                    contentDescription = null
                )
            }
        }

        memories.fastForEach { memory ->
            key(memory.id) {
                MemoryItem(
                    memory = memory,
                    onEditMemory = {
                        memoryDialogState.open(it)
                    },
                    onTogglePin = {
                        onUpdateMemory(it.copy(pinned = !it.pinned))
                    },
                    onDeleteMemory = {
                        pendingDeleteMemory = it
                    }
                )
            }
        }
        }
        }
    }

    RikkaConfirmDialog(
        show = pendingDeleteMemory != null,
        title = stringResource(R.string.confirm_delete),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            pendingDeleteMemory?.let(onDeleteMemory)
            pendingDeleteMemory = null
        },
        onDismiss = { pendingDeleteMemory = null },
        text = {
            Text(
                text = pendingDeleteMemory?.content.orEmpty(),
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

@Composable
private fun MemoryRetrievalSettings(
    assistant: Assistant,
    settings: Settings,
    onUpdateAssistant: (Assistant) -> Unit,
) {
    val mode = assistant.effectiveMemoryRetrievalMode()
    val selectedMode = if (mode == MemoryRetrievalMode.HYBRID) MemoryRetrievalMode.VECTOR else mode
    val hasEmbeddingModel = settings.findModelById(
        uuid = assistant.embeddingModelId,
        fallback = settings.embeddingModelId,
    ) != null

    Card(
        colors = CustomColors.cardColorsOnSurfaceContainer
    ) {
        FormItem(
            modifier = Modifier.padding(8.dp),
            label = { Text(stringResource(R.string.assistant_page_memory_retrieval_mode_title)) },
            description = { Text(memoryRetrievalModeDescription(selectedMode)) },
        ) {
            Select(
                options = MemoryRetrievalMode.entries.filter { it != MemoryRetrievalMode.HYBRID },
                selectedOption = selectedMode,
                onOptionSelected = { selected ->
                    onUpdateAssistant(
                        assistant.copy(
                            memoryRetrievalMode = selected,
                            useRagMemoryRetrieval = selected == MemoryRetrievalMode.VECTOR,
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                optionToString = { memoryRetrievalModeLabel(it) },
            )
            if (selectedMode.requiresEmbedding && !hasEmbeddingModel) {
                Text(
                    text = stringResource(R.string.assistant_page_memory_retrieval_fallback_keyword),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        HorizontalDivider()

        FormItem(
            modifier = Modifier.padding(8.dp),
            label = { Text(stringResource(R.string.assistant_page_embedding_model_override)) },
            description = { Text(stringResource(R.string.assistant_page_embedding_model_override_desc)) },
        ) {
            ModelSelector(
                modelId = assistant.embeddingModelId,
                providers = settings.providers,
                type = ModelType.EMBEDDING,
                allowClear = true,
                onSelect = { model ->
                    onUpdateAssistant(
                        assistant.copy(
                            embeddingModelId = model.modelId.takeIf { it.isNotBlank() }?.let { model.id }
                        )
                    )
                },
            )
        }

        if (selectedMode.requiresEmbedding) {
            HorizontalDivider()
            val threshold = assistant.ragSimilarityThreshold.coerceIn(0f, 1f)
            var thresholdSlider by remember(assistant.id, threshold) {
                mutableFloatStateOf(threshold)
            }
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.assistant_page_rag_similarity_threshold)) },
                description = {
                    Text(
                        stringResource(
                            R.string.assistant_page_rag_similarity_threshold_desc,
                            "%.2f".format(thresholdSlider),
                        )
                    )
                },
            ) {
                Slider(
                    value = thresholdSlider,
                    onValueChange = { thresholdSlider = it },
                    onValueChangeFinished = {
                        onUpdateAssistant(assistant.copy(ragSimilarityThreshold = thresholdSlider))
                    },
                    valueRange = 0f..1f,
                    steps = 19,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.assistant_page_rag_similarity_all),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        stringResource(R.string.assistant_page_rag_similarity_exact),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        if (selectedMode != MemoryRetrievalMode.OFF) {
            HorizontalDivider()
            val limit = assistant.ragLimit.coerceIn(1, 50)
            var topKSlider by remember(assistant.id, limit) {
                mutableFloatStateOf(limit.toFloat())
            }
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text(stringResource(R.string.assistant_page_rag_topk)) },
                description = {
                    Text(
                        stringResource(
                            R.string.assistant_page_rag_topk_desc,
                            topKSlider.roundToInt(),
                        )
                    )
                },
            ) {
                Slider(
                    value = topKSlider,
                    onValueChange = { topKSlider = it },
                    onValueChangeFinished = {
                        onUpdateAssistant(
                            assistant.copy(ragLimit = topKSlider.roundToInt().coerceIn(1, 50))
                        )
                    },
                    valueRange = 1f..50f,
                    steps = 48,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun MemoryItem(
    memory: AssistantMemory,
    onEditMemory: (AssistantMemory) -> Unit,
    onTogglePin: (AssistantMemory) -> Unit,
    onDeleteMemory: (AssistantMemory) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (memory.pinned) {
                        Tag(type = TagType.WARNING) {
                            Text(stringResource(R.string.assistant_page_memory_pinned_badge))
                        }
                    }
                }
                Text(
                    text = memory.content,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(
                onClick = { onTogglePin(memory) }
            ) {
                Icon(
                    imageVector = if (memory.pinned) HugeIcons.PinOff else HugeIcons.Pin,
                    contentDescription = stringResource(R.string.assistant_page_memory_pinned_badge),
                )
            }
            IconButton(
                onClick = { onEditMemory(memory) }
            ) {
                Icon(HugeIcons.PencilEdit01, null)
            }
            IconButton(
                onClick = { onDeleteMemory(memory) }
            ) {
                Icon(
                    HugeIcons.Delete01,
                    stringResource(R.string.assistant_page_delete)
                )
            }
        }
    }
}

@Composable
private fun memoryRetrievalModeLabel(mode: MemoryRetrievalMode): String {
    return stringResource(
        when (mode) {
            MemoryRetrievalMode.OFF -> R.string.assistant_page_memory_retrieval_mode_off
            MemoryRetrievalMode.KEYWORD -> R.string.assistant_page_memory_retrieval_mode_keyword
            MemoryRetrievalMode.VECTOR -> R.string.assistant_page_memory_retrieval_mode_vector
            MemoryRetrievalMode.HYBRID -> R.string.assistant_page_memory_retrieval_mode_hybrid
        }
    )
}

@Composable
private fun memoryRetrievalModeDescription(mode: MemoryRetrievalMode): String {
    return stringResource(
        when (mode) {
            MemoryRetrievalMode.OFF -> R.string.assistant_page_memory_retrieval_mode_desc
            MemoryRetrievalMode.KEYWORD -> R.string.assistant_page_memory_retrieval_mode_desc_keyword
            MemoryRetrievalMode.VECTOR -> R.string.assistant_page_memory_retrieval_mode_desc_vector
            MemoryRetrievalMode.HYBRID -> R.string.assistant_page_memory_retrieval_mode_desc_hybrid
        }
    )
}

@Composable
private fun memoryModeTitle(assistant: Assistant): String {
    return stringResource(
        R.string.assistant_page_memory_mode_format,
        stringResource(
            when {
                !assistant.enableMemory -> R.string.assistant_page_memory_mode_off_name
                assistant.effectiveMemoryRetrievalMode() == MemoryRetrievalMode.KEYWORD ->
                    R.string.assistant_page_memory_mode_keyword_name
                assistant.effectiveMemoryRetrievalMode() == MemoryRetrievalMode.OFF ->
                    R.string.assistant_page_memory_mode_basic_name
                else -> R.string.assistant_page_memory_mode_rag_name
            }
        ),
    )
}

@Composable
private fun memoryModeDescription(assistant: Assistant): String {
    return stringResource(
        when {
            !assistant.enableMemory -> R.string.assistant_page_memory_mode_off_desc
            assistant.effectiveMemoryRetrievalMode() == MemoryRetrievalMode.KEYWORD ->
                R.string.assistant_page_memory_mode_keyword_desc
            assistant.effectiveMemoryRetrievalMode() == MemoryRetrievalMode.OFF ->
                R.string.assistant_page_memory_mode_basic_desc
            else -> R.string.assistant_page_memory_mode_rag_desc
        }
    )
}
