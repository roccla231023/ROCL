package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.prepareTtsText
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.context.LocalTTSState

@Composable
fun TTSAutoPlay(vm: ChatVM, setting: Settings, conversation: Conversation) {
    // Auto-play TTS after generation completes
    val tts = LocalTTSState.current
    val currentConversation by rememberUpdatedState(conversation)
    val updatedSetting by rememberUpdatedState(setting)
    LaunchedEffect(Unit) {
        vm.generationDoneFlow.collect { conversationId ->
            if (conversationId == currentConversation.id &&
                !vm.voiceSession.state.value.isActive &&
                updatedSetting.displaySetting.autoPlayTTSAfterGeneration
            ) {
                val lastMessage = currentConversation.currentMessages.lastOrNull()
                if (lastMessage != null && lastMessage.role == MessageRole.ASSISTANT) {
                    val textToSpeak = prepareTtsText(lastMessage.toText(), updatedSetting.displaySetting)
                    if (textToSpeak.isNotBlank()) {
                        tts.speak(textToSpeak)
                    }
                }
            }
        }
    }
}
