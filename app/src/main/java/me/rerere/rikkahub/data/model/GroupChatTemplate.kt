package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import me.rerere.ai.core.ReasoningLevel
import kotlin.uuid.Uuid

@Serializable
data class GroupChatTemplate(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val intro: String = "",
    val workspaceId: Uuid? = null,
    val seats: List<GroupChatSeat> = emptyList(),
)

@Serializable
data class GroupChatSeat(
    val id: Uuid = Uuid.random(),
    val assistantId: Uuid,
    val instanceNumber: Int = 1,
    val overrides: GroupChatSeatOverrides = GroupChatSeatOverrides(),
    val defaultEnabled: Boolean = true,
)

@Serializable
data class GroupChatSeatOverrides(
    val chatModelId: Uuid? = null,
    val systemPrompt: String? = null,
    val reasoningLevel: ReasoningLevel? = null,
    val maxTokens: Int? = null,
    val enableWebSearch: Boolean? = null,
    val enableMemory: Boolean? = null,
    val mcpServers: Set<Uuid>? = null,
)

fun GroupChatTemplate.ensureSeatInstanceNumbers(): GroupChatTemplate {
    if (seats.isEmpty()) return this
    val usedNumbersByAssistantId = mutableMapOf<Uuid, MutableSet<Int>>()
    val maxNumberByAssistantId = mutableMapOf<Uuid, Int>()

    fun nextNumber(assistantId: Uuid): Int {
        val next = (maxNumberByAssistantId[assistantId] ?: 0) + 1
        maxNumberByAssistantId[assistantId] = next
        usedNumbersByAssistantId.getOrPut(assistantId) { mutableSetOf() }.add(next)
        return next
    }

    val updatedSeats = seats.map { seat ->
        val usedNumbers = usedNumbersByAssistantId.getOrPut(seat.assistantId) { mutableSetOf() }
        val currentMax = maxNumberByAssistantId[seat.assistantId] ?: 0
        if (currentMax < seat.instanceNumber) {
            maxNumberByAssistantId[seat.assistantId] = seat.instanceNumber
        }
        val number = seat.instanceNumber
        val resolvedNumber = if (number >= 1 && number !in usedNumbers) {
            usedNumbers.add(number)
            number
        } else {
            nextNumber(seat.assistantId)
        }
        if (resolvedNumber == seat.instanceNumber) seat else seat.copy(instanceNumber = resolvedNumber)
    }
    return if (updatedSeats == seats) this else copy(seats = updatedSeats)
}

fun GroupChatTemplate.buildSeatDisplayNames(
    assistantsById: Map<Uuid, Assistant>,
    defaultName: String = "Assistant",
): Map<Uuid, String> {
    if (seats.isEmpty()) return emptyMap()
    val safeDefaultName = defaultName.trim().ifBlank { "Assistant" }
    return seats.associate { seat ->
        val baseName = assistantsById[seat.assistantId]
            ?.name
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: safeDefaultName
        val number = seat.instanceNumber.coerceAtLeast(1)
        val displayName = if (number == 1) baseName else "$baseName#$number"
        seat.id to displayName
    }
}

fun Assistant.applyGroupSeat(
    template: GroupChatTemplate,
    seat: GroupChatSeat,
): Assistant {
    val overrides = seat.overrides
    return copy(
        chatModelId = overrides.chatModelId ?: chatModelId,
        systemPrompt = overrides.systemPrompt ?: systemPrompt,
        reasoningLevel = overrides.reasoningLevel ?: reasoningLevel,
        maxTokens = overrides.maxTokens ?: maxTokens,
        enableWebSearch = overrides.enableWebSearch ?: enableWebSearch,
        enableMemory = overrides.enableMemory ?: enableMemory,
        mcpServers = overrides.mcpServers ?: mcpServers,
        workspaceId = template.workspaceId ?: workspaceId,
    )
}
