package me.rerere.rikkahub.ui.pages.assistant.groupchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.GroupChatTemplate
import me.rerere.rikkahub.data.model.ensureSeatInstanceNumbers
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import kotlin.uuid.Uuid

class GroupChatTemplateDetailVM(
    private val id: String,
    private val settingsStore: SettingsStore,
    workspaceRepository: WorkspaceRepository,
) : ViewModel() {
    private val templateId = Uuid.parse(id)

    val settings: StateFlow<Settings> =
        settingsStore.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings.dummy())

    val template: StateFlow<GroupChatTemplate> = settings
        .map { current ->
            current.groupChatTemplates.find { it.id == templateId } ?: GroupChatTemplate(id = templateId)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, GroupChatTemplate(id = templateId))

    val workspaces: StateFlow<List<WorkspaceEntity>> = workspaceRepository
        .listFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun update(template: GroupChatTemplate) {
        viewModelScope.launch {
            val current = settings.value
            val normalized = template.ensureSeatInstanceNumbers()
            val exists = current.groupChatTemplates.any { it.id == templateId }
            val templates = if (exists) {
                current.groupChatTemplates.map { if (it.id == templateId) normalized else it }
            } else {
                current.groupChatTemplates + normalized
            }
            settingsStore.update(current.copy(groupChatTemplates = templates))
        }
    }

    fun delete() {
        viewModelScope.launch {
            val current = settings.value
            settingsStore.update(
                current.copy(
                    groupChatTemplates = current.groupChatTemplates.filterNot { it.id == templateId },
                    assistantId = if (current.assistantId == templateId) {
                        current.assistants.firstOrNull()?.id ?: current.assistantId
                    } else {
                        current.assistantId
                    },
                )
            )
        }
    }
}
