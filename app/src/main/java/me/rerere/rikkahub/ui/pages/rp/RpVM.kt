package me.rerere.rikkahub.ui.pages.rp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.rp.model.RpCard
import me.rerere.rikkahub.data.rp.repository.RpRepository

class RpVM(
    private val repository: RpRepository,
) : ViewModel() {
    val cards: StateFlow<List<RpCard>> = repository.observeCards()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sessions = repository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun createCard(): RpCard {
        val card = RpCard(name = "未命名 RP")
        viewModelScope.launch { repository.saveCard(card) }
        return card
    }

    fun delete(card: RpCard) {
        viewModelScope.launch { repository.deleteCard(card) }
    }
}
