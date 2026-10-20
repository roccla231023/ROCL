package me.rerere.rikkahub.ui.pages.assistant.groupchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.uuid.Uuid
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
            val normalized = template.ensureSeatInstanceNumbers().copy(id = templateId)
            settingsStore.update { current ->
                val templates = current.groupChatTemplates
                current.copy(groupChatTemplates = if (templates.any { it.id == templateId }) {
                    templates.map { if (it.id == templateId) normalized else it }
                } else templates + normalized)
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            settingsStore.update({ current ->
                current.copy(
                    groupChatTemplates = current.groupChatTemplates.filterNot { it.id == templateId },
                    assistantId = if (current.assistantId == templateId) {
                        current.assistants.firstOrNull()?.id ?: current.assistantId
                    } else {
                        current.assistantId
                    },
                )
             })
        }
    }
}
