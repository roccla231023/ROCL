package me.rerere.rikkahub.ui.pages.assistant.groupchat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.ModelType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.GroupChatSeat
import me.rerere.rikkahub.data.model.GroupChatTemplate
import me.rerere.rikkahub.data.model.buildSeatDisplayNames
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
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

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.group_chat_page_title)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(innerPadding)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FormItem(label = { Text(stringResource(R.string.group_chat_page_name)) }) {
                OutlinedTextField(
                    value = template.name,
                    onValueChange = { vm.update(template.copy(name = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            FormItem(
                label = { Text(stringResource(R.string.group_chat_page_workspace)) },
                description = { Text(stringResource(R.string.group_chat_page_workspace_desc)) },
            ) {
                val selected = workspaces.find { it.id == template.workspaceId?.toString() }
                Select(
                    options = listOf<WorkspaceEntity?>(null) + workspaces,
                    selectedOption = selected,
                    onOptionSelected = { workspace ->
                        vm.update(template.copy(workspaceId = workspace?.id?.let { Uuid.parse(it) }))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    optionToString = { workspace ->
                        workspace?.name ?: stringResource(R.string.workspace_no_binding)
                    },
                )
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.group_chat_page_seats),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        val firstAssistant = settings.assistants.firstOrNull() ?: return@IconButton
                        vm.update(
                            template.copy(
                                seats = template.seats + GroupChatSeat(assistantId = firstAssistant.id)
                            )
                        )
                    }
                ) {
                    Icon(HugeIcons.Add01, contentDescription = stringResource(R.string.group_chat_page_add_seat))
                }
            }
            val names = template.buildSeatDisplayNames(settings.assistants.associateBy { it.id })
            template.seats.forEach { seat ->
                SeatRow(
                    template = template,
                    seat = seat,
                    displayName = names[seat.id].orEmpty(),
                    assistants = settings.assistants,
                    providers = settings.providers,
                    mcpServers = settings.mcpServers,
                    onUpdate = vm::update,
                )
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
}

@Composable
private fun SeatRow(
    template: GroupChatTemplate,
    seat: GroupChatSeat,
    displayName: String,
    assistants: List<Assistant>,
    providers: List<me.rerere.ai.provider.ProviderSetting>,
    mcpServers: List<me.rerere.rikkahub.data.ai.mcp.McpServerConfig>,
    onUpdate: (GroupChatTemplate) -> Unit,
) {
    val selected = assistants.find { it.id == seat.assistantId } ?: assistants.firstOrNull()
    fun patch(transform: (GroupChatSeat) -> GroupChatSeat) {
        onUpdate(template.copy(seats = template.seats.map { if (it.id == seat.id) transform(it) else it }))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(displayName.ifBlank { stringResource(R.string.assistant_page_default_assistant) })
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Select(
                options = assistants,
                selectedOption = selected,
                onOptionSelected = { assistant -> patch { it.copy(assistantId = assistant.id) } },
                modifier = Modifier.weight(1f),
                optionToString = { assistant ->
                    assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) }
                },
            )
            IconButton(onClick = { onUpdate(template.copy(seats = template.seats.filterNot { it.id == seat.id })) }) {
                Icon(HugeIcons.Delete01, contentDescription = stringResource(R.string.assistant_page_delete))
            }
        }
        FormItem(label = { Text(stringResource(R.string.assistant_page_chat_model)) }) {
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
        }
        FormItem(
            label = { Text(stringResource(R.string.assistant_page_memory)) },
            tail = {
                Switch(
                    checked = seat.overrides.enableMemory ?: (selected?.enableMemory == true),
                    onCheckedChange = { enabled ->
                        patch { it.copy(overrides = it.overrides.copy(enableMemory = enabled)) }
                    },
                )
            },
        )
        FormItem(
            label = { Text(stringResource(R.string.use_web_search)) },
            tail = {
                Switch(
                    checked = seat.overrides.enableWebSearch ?: (selected?.enableWebSearch == true),
                    onCheckedChange = { enabled ->
                        patch { it.copy(overrides = it.overrides.copy(enableWebSearch = enabled)) }
                    },
                )
            },
        )
        FormItem(
            label = { Text(stringResource(R.string.mcp_picker_title)) },
        ) {
            val enabledIds = seat.overrides.mcpServers ?: selected?.mcpServers.orEmpty()
            mcpServers.filter { it.commonOptions.enable }.forEach { server ->
                FormItem(
                    label = { Text(server.commonOptions.name) },
                    tail = {
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
        HorizontalDivider()
    }
}
