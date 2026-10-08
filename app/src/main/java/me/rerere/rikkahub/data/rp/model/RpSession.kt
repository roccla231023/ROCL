package me.rerere.rikkahub.data.rp.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlin.uuid.Uuid

@Serializable
enum class RpSessionStatus {
    ACTIVE,
    PAUSED,
    ARCHIVED,
}

@Serializable
enum class RpTurnStatus {
    PENDING,
    ADJUDICATING,
    REVIEWING,
    NARRATING,
    COMPLETED,
    FAILED,
}

@Serializable
enum class RpTurnPhase {
    ADJUDICATION,
    REVIEW,
    NARRATION,
}

@Serializable
data class RpSession(
    val id: Uuid = Uuid.random(),
    val card: RpCard,
    val conversationId: Uuid,
    val activeBranchId: Uuid = Uuid.random(),
    val revision: Long = 0,
    val state: JsonObject = card.initialState,
    val storyMemory: RpStoryMemory = RpStoryMemory(),
    val status: RpSessionStatus = RpSessionStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class RpTurn(
    val id: Uuid = Uuid.random(),
    val sessionId: Uuid,
    val branchId: Uuid,
    val input: String,
    val status: RpTurnStatus = RpTurnStatus.PENDING,
    val outcome: RpOutcome? = null,
    val review: RpReview? = null,
    val narrative: String = "",
    val stateBefore: JsonObject? = null,
    val stateAfter: JsonObject? = null,
    val event: RpCommittedEvent? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class RpOutcome(
    val summary: String = "",
    val facts: List<String> = emptyList(),
    val nextPrompt: String = "",
    val state: JsonObject = buildJsonObject { },
    val changes: List<RpStateChange> = emptyList(),
    val needsChoice: Boolean = false,
)

@Serializable
enum class RpStateOperation {
    SET,
    APPEND,
    REMOVE,
    INCREMENT,
    DECREMENT,
}

@Serializable
data class RpStateChange(
    val path: String,
    val operation: RpStateOperation = RpStateOperation.SET,
    val value: JsonElement = kotlinx.serialization.json.JsonNull,
)

@Serializable
data class RpAdjudicationProposal(
    val summary: String = "",
    val facts: List<String> = emptyList(),
    val changes: List<RpStateChange> = emptyList(),
    val nextPrompt: String = "",
    val needsChoice: Boolean = false,
)

@Serializable
data class RpReview(
    val accepted: Boolean,
    val confidence: Float? = null,
    val reason: String = "",
    val retryable: Boolean = false,
)

@Serializable
data class RpSessionSnapshot(
    val session: RpSession,
    val turns: List<RpTurn>,
)
