package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class SessionMemoryPlacement {
    SYSTEM_PROMPT_AFTER,
    BEFORE_LATEST_MESSAGE;

    companion object {
        fun fromToolValue(value: String?): SessionMemoryPlacement {
            return when (value?.trim()?.lowercase()) {
                "system_prompt_after", "after_system_prompt", "system-prompt-after",
                "after-system-prompt", "system", "stable" -> SYSTEM_PROMPT_AFTER
                else -> BEFORE_LATEST_MESSAGE
            }
        }
    }
}

@Serializable
data class SessionMemory(
    val id: Int,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val placement: SessionMemoryPlacement = SessionMemoryPlacement.BEFORE_LATEST_MESSAGE,
)
