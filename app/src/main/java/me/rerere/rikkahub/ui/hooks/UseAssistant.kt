package me.rerere.rikkahub.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.uuid.Uuid
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getGroupChatTemplate
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.GroupChatTemplate

@Composable
fun rememberAssistantState(
    settings: Settings,
    onSelectAssistant: (Assistant) -> Unit
): AssistantState {
    return remember(settings, onSelectAssistant) {
        AssistantState(settings, onSelectAssistant)
    }
}

class AssistantState(
    private val settings: Settings,
    private val onSelectAssistant: (Assistant) -> Unit
) {
    private var _currentAssistant by mutableStateOf(
        settings.getCurrentAssistant()
    )
    val currentAssistant get() = _currentAssistant

    val currentGroup: GroupChatTemplate? = settings.getGroupChatTemplate(settings.assistantId)

    fun setSelectAssistant(assistant: Assistant) {
        onSelectAssistant(assistant)
    }

    fun setSelectTarget(id: Uuid) {
        onSelectAssistant(settings.assistants.find { it.id == id } ?: Assistant(id = id))
    }
}
