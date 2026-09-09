package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.Serializable
import me.rerere.ai.core.MessageRole

@Serializable
enum class MessageToolbarButton {
    COPY,
    FORK,
    REGENERATE,
    CONTINUE,
    TTS,
    EDIT,
    SHARE,
    SELECT_AND_COPY,
    WEB_VIEW_PREVIEW,
    DELETE,
}

@Serializable
data class MessageToolbarConfig(
    val toolbarButtons: Set<MessageToolbarButton> = emptySet(),
) {
    fun isOnToolbar(button: MessageToolbarButton): Boolean = button in toolbarButtons

    fun toggle(button: MessageToolbarButton): MessageToolbarConfig =
        if (button in toolbarButtons) copy(toolbarButtons = toolbarButtons - button)
        else copy(toolbarButtons = toolbarButtons + button)

    companion object {
        val DEFAULT_USER = MessageToolbarConfig(
            toolbarButtons = setOf(
                MessageToolbarButton.COPY,
                MessageToolbarButton.REGENERATE,
            )
        )
        val DEFAULT_ASSISTANT = MessageToolbarConfig(
            toolbarButtons = setOf(
                MessageToolbarButton.COPY,
                MessageToolbarButton.REGENERATE,
                MessageToolbarButton.TTS,
            )
        )
    }
}

fun MessageToolbarButton.isAvailableFor(role: MessageRole): Boolean = when (this) {
    MessageToolbarButton.CONTINUE, MessageToolbarButton.TTS -> role == MessageRole.ASSISTANT
    else -> true
}

fun DisplaySetting.resolvedToolbar(role: MessageRole): MessageToolbarConfig {
    val base = if (role == MessageRole.ASSISTANT) assistantMessageToolbar else userMessageToolbar
    if (role != MessageRole.ASSISTANT) return base
    return if (showContinueOnAssistantToolbar) {
        base.copy(toolbarButtons = base.toolbarButtons + MessageToolbarButton.CONTINUE)
    } else {
        base.copy(toolbarButtons = base.toolbarButtons - MessageToolbarButton.CONTINUE)
    }
}

val USER_TOOLBAR_BUTTONS = listOf(
    MessageToolbarButton.COPY,
    MessageToolbarButton.FORK,
    MessageToolbarButton.REGENERATE,
    MessageToolbarButton.EDIT,
    MessageToolbarButton.SHARE,
    MessageToolbarButton.SELECT_AND_COPY,
    MessageToolbarButton.WEB_VIEW_PREVIEW,
    MessageToolbarButton.DELETE,
)

val ASSISTANT_TOOLBAR_BUTTONS = listOf(
    MessageToolbarButton.COPY,
    MessageToolbarButton.FORK,
    MessageToolbarButton.REGENERATE,
    MessageToolbarButton.CONTINUE,
    MessageToolbarButton.TTS,
    MessageToolbarButton.EDIT,
    MessageToolbarButton.SHARE,
    MessageToolbarButton.SELECT_AND_COPY,
    MessageToolbarButton.WEB_VIEW_PREVIEW,
    MessageToolbarButton.DELETE,
)
