package me.rerere.rikkahub.ui.pages.rp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.rp.model.RpCard
import me.rerere.rikkahub.data.rp.model.RpModelBindings
import me.rerere.rikkahub.data.rp.repository.RpRepository
import kotlin.uuid.Uuid

class RpCardDetailVM(
    id: String,
    private val settingsStore: SettingsStore,
    private val rpRepository: RpRepository,
    private val conversationRepository: ConversationRepository,
) : ViewModel() {
    private val cardId = Uuid.parse(id)

    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings.dummy())

    val card: StateFlow<RpCard> = rpRepository.observeCards()
        .map { cards -> cards.firstOrNull { it.id == cardId } ?: RpCard(id = cardId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RpCard(id = cardId))

    fun update(card: RpCard) {
        viewModelScope.launch { rpRepository.saveCard(card.copy(updatedAt = System.currentTimeMillis())) }
    }

    fun setModels(bindings: RpModelBindings) {
        update(card.value.copy(modelBindings = bindings))
    }

    fun startSession(onCreated: (Uuid) -> Unit) {
        viewModelScope.launch {
            val current = card.value
            val conversationId = Uuid.random()
            val sessionId = Uuid.random()
            val session = me.rerere.rikkahub.data.rp.model.RpSession(
                id = sessionId,
                card = current,
                conversationId = conversationId,
            )
            rpRepository.createSession(session)
            conversationRepository.insertConversation(
                Conversation.ofId(
                    id = conversationId,
                    assistantId = settings.value.assistants.firstOrNull()?.id ?: settings.value.assistantId,
                ).copy(
                    title = current.name,
                    rpSessionId = sessionId,
                )
            )
            onCreated(sessionId)
        }
    }
}
