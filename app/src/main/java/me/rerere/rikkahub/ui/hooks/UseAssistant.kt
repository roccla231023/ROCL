package me.rerere.rikkahub.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getGroupChatTemplate
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.GroupChatTemplate
import kotlin.uuid.Uuid

@Composable
fun rememberAssistantState(
    settings: Settings,
    onUpdateSettings: (Settings) -> Unit
): AssistantState {
    return remember(settings, onUpdateSettings) {
        AssistantState(settings, onUpdateSettings)
    }
}

class AssistantState(
    private val settings: Settings,
    private val onUpdateSettings: (Settings) -> Unit
) {
    private var _currentAssistant by mutableStateOf(
        settings.getCurrentAssistant()
    )
    val currentAssistant get() = _currentAssistant

    val currentGroup: GroupChatTemplate? = settings.getGroupChatTemplate(settings.assistantId)

    fun setSelectAssistant(assistant: Assistant) {
        onUpdateSettings(
            settings.copy(
                assistantId = assistant.id
            )
        )
    }

    fun setSelectTarget(id: Uuid) {
        onUpdateSettings(settings.copy(assistantId = id))
    }
}
