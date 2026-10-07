package me.rerere.rikkahub.ui.pages.rp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.rp.model.RpSessionSnapshot
import me.rerere.rikkahub.data.rp.repository.RpRepository
import me.rerere.rikkahub.data.rp.runtime.RpRuntime
import kotlin.uuid.Uuid

class RpSessionVM(
    id: String,
    repository: RpRepository,
    private val runtime: RpRuntime,
) : ViewModel() {
    private val sessionId = Uuid.parse(id)
    val snapshot: StateFlow<RpSessionSnapshot?> = repository.observeSnapshot(sessionId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun submit(input: String): Job = viewModelScope.launch {
        runtime.submitInput(sessionId, input)
    }

    fun retry(turn: me.rerere.rikkahub.data.rp.model.RpTurn): Job = viewModelScope.launch {
        val phase = when {
            turn.outcome == null -> me.rerere.rikkahub.data.rp.model.RpTurnPhase.ADJUDICATION
            turn.review == null || turn.review.accepted.not() -> me.rerere.rikkahub.data.rp.model.RpTurnPhase.REVIEW
            else -> me.rerere.rikkahub.data.rp.model.RpTurnPhase.NARRATION
        }
        runtime.retryPhase(turn.id, phase)
    }

    fun rollback(turnId: Uuid): Job = viewModelScope.launch {
        runtime.rollbackToTurn(turnId)
    }

    fun fork(turnId: Uuid): Job = viewModelScope.launch {
        runtime.forkFromTurn(turnId)
    }
}
