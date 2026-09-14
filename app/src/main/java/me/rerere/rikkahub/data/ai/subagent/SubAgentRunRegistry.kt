package me.rerere.rikkahub.data.ai.subagent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SubAgentRunRegistry {
    private val _active = MutableStateFlow<SubAgentProgress?>(null)
    val active: StateFlow<SubAgentProgress?> = _active.asStateFlow()

    @Volatile
    private var abortRequested = false

    @Synchronized
    fun tryOccupy(): Boolean {
        if (_active.value != null) return false
        abortRequested = false
        _active.value = SubAgentProgress(
            step = 0,
            total = SUBAGENT_MAX_STEPS,
            phase = "准备中",
        )
        return true
    }

    @Synchronized
    fun update(transform: (SubAgentProgress) -> SubAgentProgress) {
        val current = _active.value ?: return
        _active.value = transform(current)
    }

    fun requestAbort() {
        abortRequested = true
    }

    fun isAbortRequested(): Boolean = abortRequested

    @Synchronized
    fun clear() {
        abortRequested = false
        _active.value = null
    }
}
