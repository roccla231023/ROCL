package me.rerere.rikkahub.ui.pages.assistant.groupchat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.files.SkillMetadata
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.ui.components.ai.LorebooksContent
import me.rerere.rikkahub.ui.components.ai.ModeInjectionsContent
import me.rerere.rikkahub.ui.components.ai.SkillsContent
import org.koin.compose.koinInject
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.ModelType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.GroupChatSeat
import me.rerere.rikkahub.data.model.GroupChatTemplate
import me.rerere.rikkahub.data.model.buildSeatDisplayNames
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

@Composable
fun GroupChatTemplateDetailPage(id: String) {
    val vm: GroupChatTemplateDetailVM = koinViewModel(parameters = { parametersOf(id) })
    val template by vm.template.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var pendingDelete by remember { mutableStateOf(false) }
    var showIntroEditor by remember { mutableStateOf(false) }
    var showAddMember by remember { mutableStateOf(false) }
    var editingSeatId by remember { mutableStateOf<Uuid?>(null) }

    val assistantsById = remember(settings.assistants) { settings.assistants.associateBy { it.id } }
    val defaultAssistantName = stringResource(R.string.assistant_page_default_assistant)
    val seatNames = template.buildSeatDisplayNames(assistantsById, defaultAssistantName)
    val editingSeat = template.seats.find { it.id == editingSeatId }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = template.name.ifBlank { stringResource(R.string.group_chat_page_title) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { pendingDelete = true }) {
                        Icon(HugeIcons.Delete01, contentDescription = stringResource(R.string.assistant_page_delete))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "identity") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.group_chat_page_identity)) },
                ) {
                    item(
                        supportingContent = { Text(stringResource(R.string.group_chat_page_name_desc)) },
                        trailingContent = {
                            OutlinedTextField(
                                value = template.name,
                                onValueChange = { vm.update(template.copy(name = it)) },
                                modifier = Modifier.fillMaxWidth(0.45f),
                                singleLine = true,
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.group_chat_page_name)) },
                    )
                    item(
                        onClick = { showIntroEditor = true },
                        supportingContent = {
                            Text(
                                text = template.intro.trim().ifBlank {
                                    stringResource(R.string.group_chat_page_intro_desc)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = { Icon(HugeIcons.ArrowRight01, contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.group_chat_page_intro)) },
                    )
                    item(
                        supportingContent = { Text(stringResource(R.string.group_chat_page_workspace_desc)) },
                        trailingContent = {
                            val selected = workspaces.find { it.id == template.workspaceId?.toString() }
                            Select(
                                options = listOf<WorkspaceEntity?>(null) + workspaces,
                                selectedOption = selected as WorkspaceEntity?,
                                onOptionSelected = { workspace ->
                                    vm.update(template.copy(workspaceId = workspace?.id?.let { Uuid.parse(it) }))
                                },
                                modifier = Modifier.fillMaxWidth(0.45f),
                                optionToString = { workspace ->
                                    workspace?.name ?: stringResource(R.string.workspace_no_binding)
                                },
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.group_chat_page_workspace)) },
                    )
                }
            }

            item(key = "members") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.group_chat_page_seats)) },
                ) {
                    template.seats.forEach { seat ->
                        val assistant = assistantsById[seat.assistantId]
                        val title = seatNames[seat.id].orEmpty().ifBlank { defaultAssistantName }
                        val model = settings.findModelById(
                            seat.overrides.chatModelId ?: assistant?.chatModelId ?: settings.chatModelId
                        )
                        item(
                            onClick = { editingSeatId = seat.id },
                            leadingContent = {
                                UIAvatar(
                                    name = title,
                                    value = assistant?.avatar ?: Avatar.Dummy,
                                    modifier = Modifier.size(40.dp),
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = model?.displayName
                                        ?: stringResource(R.string.assistant_page_no_system_prompt),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = seat.defaultEnabled,
                                    onCheckedChange = { enabled ->
                                        vm.update(
                                            template.copy(
                                                seats = template.seats.map {
                                                    if (it.id == seat.id) it.copy(defaultEnabled = enabled) else it
                                                }
                                            )
                                        )
                                    },
                                )
                            },
                            headlineContent = { Text(title) },
                        )
                    }
                    item(
                        onClick = { showAddMember = true },
                        leadingContent = { Icon(HugeIcons.Add01, contentDescription = null) },
                        trailingContent = { Icon(HugeIcons.ArrowRight01, contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.group_chat_page_add_seat)) },
                    )
                }
            }
        }
    }

    RikkaConfirmDialog(
        show = pendingDelete,
        title = stringResource(R.string.assistant_page_delete),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            pendingDelete = false
            vm.delete()
            navController.popBackStack()
        },
        onDismiss = { pendingDelete = false },
        text = { Text(template.name.ifBlank { stringResource(R.string.group_chat_page_title) }) },
    )

    if (showIntroEditor) {
        var draft by remember(template.id, showIntroEditor) { mutableStateOf(template.intro) }
        AlertDialog(
            onDismissRequest = { showIntroEditor = false },
            title = { Text(stringResource(R.string.group_chat_page_intro)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.update(template.copy(intro = draft))
                        showIntroEditor = false
                    }
                ) { Text(stringResource(R.string.assistant_page_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showIntroEditor = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showAddMember) {
        ModalBottomSheet(onDismissRequest = { showAddMember = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.group_chat_page_add_seat),
                    style = MaterialTheme.typography.titleLarge,
                )
                CardGroup {
                    settings.assistants.forEach { assistant ->
                        item(
                            onClick = {
                                vm.update(
                                    template.copy(
                                        seats = template.seats + GroupChatSeat(assistantId = assistant.id)
                                    )
                                )
                                showAddMember = false
                            },
                            leadingContent = {
                                UIAvatar(
                                    name = assistant.name.ifBlank { defaultAssistantName },
                                    value = assistant.avatar,
                                    modifier = Modifier.size(32.dp),
                                )
                            },
                            headlineContent = {
                                Text(assistant.name.ifBlank { defaultAssistantName })
                            },
                        )
                    }
                }
            }
        }
    }

    if (editingSeat != null) {
        SeatEditorSheet(
            template = template,
            seat = editingSeat,
            assistants = settings.assistants,
            providers = settings.providers,
            mcpServers = settings.mcpServers,
            lorebooks = settings.lorebooks,
            modeInjections = settings.modeInjections,
            displayName = seatNames[editingSeat.id].orEmpty().ifBlank { defaultAssistantName },
            onUpdate = vm::update,
            onDismiss = { editingSeatId = null },
        )
    }
}

@Composable
private fun SeatEditorSheet(
    template: GroupChatTemplate,
    seat: GroupChatSeat,
    assistants: List<Assistant>,
    providers: List<me.rerere.ai.provider.ProviderSetting>,
    mcpServers: List<me.rerere.rikkahub.data.ai.mcp.McpServerConfig>,
    lorebooks: List<Lorebook>,
    modeInjections: List<PromptInjection.ModeInjection>,
    displayName: String,
    onUpdate: (GroupChatTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = assistants.find { it.id == seat.assistantId } ?: assistants.firstOrNull()
    val skillManager: SkillManager = koinInject()
    var skills by remember { mutableStateOf<List<SkillMetadata>>(emptyList()) }
    LaunchedEffect(Unit) {
        skills = skillManager.listSkills()
    }
    var showPromptEditor by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<SeatPicker?>(null) }
    fun patch(transform: (GroupChatSeat) -> GroupChatSeat) {
        onUpdate(template.copy(seats = template.seats.map { if (it.id == seat.id) transform(it) else it }))
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(displayName, style = MaterialTheme.typography.titleLarge)
            if (selected != null) {
                Select(
                    options = assistants,
                    selectedOption = selected,
                    onOptionSelected = { assistant -> patch { it.copy(assistantId = assistant.id) } },
                    modifier = Modifier.fillMaxWidth(),
                    optionToString = { assistant ->
                        assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) }
                    },
                )
            }
            Text(stringResource(R.string.assistant_page_chat_model), style = MaterialTheme.typography.titleSmall)
            ModelSelector(
                modelId = seat.overrides.chatModelId,
                providers = providers,
                type = ModelType.CHAT,
                allowClear = true,
                onSelect = { model ->
                    patch {
                        it.copy(
                            overrides = it.overrides.copy(
                                chatModelId = model.modelId.takeIf { id -> id.isNotBlank() }?.let { model.id },
                            )
                        )
                    }
                },
            )
            CardGroup {
                item(
                    onClick = { showPromptEditor = true },
                    headlineContent = { Text(stringResource(R.string.group_chat_page_override_prompt)) },
                    supportingContent = {
                        Text(
                            if (seat.overrides.systemPrompt != null) {
                                stringResource(R.string.group_chat_page_override_prompt_active)
                            } else {
                                stringResource(R.string.group_chat_page_override_prompt_default)
                            }
                        )
                    },
                    trailingContent = { Icon(HugeIcons.ArrowRight01, contentDescription = null) },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.assistant_page_thinking_budget)) },
                    supportingContent = {
                        Text(
                            if (seat.overrides.reasoningLevel != null) {
                                stringResource(R.string.group_chat_page_reasoning_overridden)
                            } else {
                                stringResource(R.string.group_chat_page_reasoning_follow_desc)
                            }
                        )
                    },
                    trailingContent = {
                        val assistantLevel = selected?.reasoningLevel ?: ReasoningLevel.AUTO
                        val choices = listOf<ReasoningLevel?>(null) + ReasoningLevel.entries
                        Select(
                            options = choices,
                            selectedOption = seat.overrides.reasoningLevel,
                            onOptionSelected = { level ->
                                patch { it.copy(overrides = it.overrides.copy(reasoningLevel = level)) }
                            },
                            modifier = Modifier.fillMaxWidth(0.5f),
                            optionToString = { level ->
                                if (level == null) {
                                    stringResource(
                                        R.string.group_chat_page_reasoning_follow,
                                        assistantLevel.levelLabel(),
                                    )
                                } else {
                                    level.levelLabel()
                                }
                            },
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.assistant_page_memory)) },
                    trailingContent = {
                        Switch(
                            checked = seat.overrides.enableMemory == true,
                            onCheckedChange = { enabled ->
                                patch { it.copy(overrides = it.overrides.copy(enableMemory = enabled)) }
                            },
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.assistant_page_session_memory)) },
                    trailingContent = {
                        Switch(
                            checked = seat.overrides.enableSessionMemory == true,
                            onCheckedChange = { enabled ->
                                patch { it.copy(overrides = it.overrides.copy(enableSessionMemory = enabled)) }
                            },
                        )
                    },
                )
                item(
                    headlineContent = { Text(stringResource(R.string.use_web_search)) },
                    trailingContent = {
                        Switch(
                            checked = seat.overrides.enableWebSearch == true,
                            onCheckedChange = { enabled ->
                                patch { it.copy(overrides = it.overrides.copy(enableWebSearch = enabled)) }
                            },
                        )
                    },
                )
                item(
                    onClick = { picker = SeatPicker.ModeInjections },
                    headlineContent = { Text(stringResource(R.string.extension_selector_tab_mode_injections)) },
                    supportingContent = {
                        Text(seatListSummary(seat.overrides.modeInjectionIds.size))
                    },
                    trailingContent = { Icon(HugeIcons.ArrowRight01, contentDescription = null) },
                )
                item(
                    onClick = { picker = SeatPicker.Lorebooks },
                    headlineContent = { Text(stringResource(R.string.extension_selector_tab_lorebooks)) },
                    supportingContent = {
                        Text(seatListSummary(seat.overrides.lorebookIds.size))
                    },
                    trailingContent = { Icon(HugeIcons.ArrowRight01, contentDescription = null) },
                )
                item(
                    onClick = { picker = SeatPicker.Skills },
                    headlineContent = { Text(stringResource(R.string.group_chat_page_skills)) },
                    supportingContent = {
                        Text(seatListSummary(seat.overrides.enabledSkills.size))
                    },
                    trailingContent = { Icon(HugeIcons.ArrowRight01, contentDescription = null) },
                )
            }
            val enabledIds = seat.overrides.mcpServers.orEmpty()
            val enabledServers = mcpServers.filter { it.commonOptions.enable }
            if (enabledServers.isNotEmpty()) {
                CardGroup(title = { Text(stringResource(R.string.mcp_picker_title)) }) {
                    enabledServers.forEach { server ->
                        item(
                            headlineContent = { Text(server.commonOptions.name) },
                            trailingContent = {
                                Switch(
                                    checked = server.id in enabledIds,
                                    onCheckedChange = { checked ->
                                        val next = enabledIds.toMutableSet()
                                        if (checked) next.add(server.id) else next.remove(server.id)
                                        patch { it.copy(overrides = it.overrides.copy(mcpServers = next)) }
                                    },
                                )
                            },
                        )
                    }
                }
            }
            TextButton(
                onClick = {
                    onUpdate(template.copy(seats = template.seats.filterNot { it.id == seat.id }))
                    onDismiss()
                }
            ) {
                Text(
                    text = stringResource(R.string.assistant_page_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    if (showPromptEditor) {
        val basePrompt = selected?.systemPrompt.orEmpty()
        var draft by remember(seat.id, seat.overrides.systemPrompt, basePrompt) {
            mutableStateOf(seat.overrides.systemPrompt ?: basePrompt)
        }
        AlertDialog(
            onDismissRequest = { showPromptEditor = false },
            title = { Text(stringResource(R.string.group_chat_page_override_prompt)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 8,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val normalized = draft.takeIf { it != basePrompt }
                        patch { it.copy(overrides = it.overrides.copy(systemPrompt = normalized)) }
                        showPromptEditor = false
                    }
                ) { Text(stringResource(R.string.assistant_page_save)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { draft = basePrompt },
                    enabled = draft != basePrompt,
                ) {
                    Text(stringResource(R.string.group_chat_page_override_prompt_restore))
                }
            },
        )
    }
    if (picker != null) {
        ModalBottomSheet(onDismissRequest = { picker = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = when (picker) {
                        SeatPicker.ModeInjections -> stringResource(R.string.extension_selector_tab_mode_injections)
                        SeatPicker.Lorebooks -> stringResource(R.string.extension_selector_tab_lorebooks)
                        SeatPicker.Skills -> stringResource(R.string.group_chat_page_skills)
                        null -> ""
                    },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                when (picker) {
                    SeatPicker.ModeInjections -> ModeInjectionsContent(
                        modifier = Modifier.weight(1f),
                        modeInjections = modeInjections,
                        selectedIds = seat.overrides.modeInjectionIds,
                        onToggle = { id, checked ->
                            val next = if (checked) {
                                seat.overrides.modeInjectionIds + id
                            } else {
                                seat.overrides.modeInjectionIds - id
                            }
                            patch { it.copy(overrides = it.overrides.copy(modeInjectionIds = next)) }
                        },
                    )
                    SeatPicker.Lorebooks -> LorebooksContent(
                        modifier = Modifier.weight(1f),
                        lorebooks = lorebooks,
                        selectedIds = seat.overrides.lorebookIds,
                        onToggle = { id, checked ->
                            val next = if (checked) {
                                seat.overrides.lorebookIds + id
                            } else {
                                seat.overrides.lorebookIds - id
                            }
                            patch { it.copy(overrides = it.overrides.copy(lorebookIds = next)) }
                        },
                    )
                    SeatPicker.Skills -> SkillsContent(
                        modifier = Modifier.weight(1f),
                        skills = skills,
                        enabledSkills = seat.overrides.enabledSkills,
                        onToggle = { name, checked ->
                            val next = if (checked) {
                                seat.overrides.enabledSkills + name
                            } else {
                                seat.overrides.enabledSkills - name
                            }
                            patch { it.copy(overrides = it.overrides.copy(enabledSkills = next)) }
                        },
                    )
                    null -> Unit
                }
            }
        }
    }
}

private enum class SeatPicker { ModeInjections, Lorebooks, Skills }

@Composable
private fun seatListSummary(count: Int): String {
    return if (count == 0) {
        stringResource(R.string.group_chat_page_seat_none)
    } else {
        stringResource(R.string.group_chat_page_seat_selected, count)
    }
}

@Composable
private fun ReasoningLevel.levelLabel(): String = when (this) {
    ReasoningLevel.OFF -> stringResource(R.string.reasoning_off)
    ReasoningLevel.AUTO -> stringResource(R.string.reasoning_auto)
    ReasoningLevel.LOW -> stringResource(R.string.reasoning_light)
    ReasoningLevel.MEDIUM -> stringResource(R.string.reasoning_medium)
    ReasoningLevel.HIGH -> stringResource(R.string.reasoning_heavy)
    ReasoningLevel.XHIGH -> stringResource(R.string.reasoning_xhigh)
    ReasoningLevel.MAX -> stringResource(R.string.reasoning_max)
}
