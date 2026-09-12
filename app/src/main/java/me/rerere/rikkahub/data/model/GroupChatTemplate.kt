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
    val quickMessageIds: Set<Uuid> = emptySet(),
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
    val enableSessionMemory: Boolean? = null,
    val mcpServers: Set<Uuid>? = null,
    val enabledSkills: Set<String> = emptySet(),
    val lorebookIds: Set<Uuid> = emptySet(),
    val modeInjectionIds: Set<Uuid> = emptySet(),
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

fun resolveGroupChatDisplaySeat(
    template: GroupChatTemplate,
    stickySpeakerSeatId: Uuid?,
): GroupChatSeat? {
    val enabledSeats = template.seats.filter { it.defaultEnabled }
    return stickySpeakerSeatId?.let { sticky -> enabledSeats.find { it.id == sticky } }
        ?: enabledSeats.firstOrNull()
}

fun resolveGroupChatModelId(
    template: GroupChatTemplate,
    stickySpeakerSeatId: Uuid?,
    assistantsById: Map<Uuid, Assistant>,
    globalChatModelId: Uuid,
): Uuid {
    val seat = resolveGroupChatDisplaySeat(template, stickySpeakerSeatId) ?: return globalChatModelId
    val assistant = assistantsById[seat.assistantId] ?: return globalChatModelId
    return assistant.applyGroupSeat(template, seat).chatModelId ?: globalChatModelId
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
        enableWebSearch = overrides.enableWebSearch ?: false,
        enableMemory = overrides.enableMemory ?: false,
        enableSessionMemory = overrides.enableSessionMemory ?: false,
        useGlobalMemory = false,
        enableRecentChatsReference = false,
        enableTimeReminder = false,
        mcpServers = overrides.mcpServers ?: emptySet(),
        workspaceId = template.workspaceId ?: workspaceId,
        enabledSkills = overrides.enabledSkills,
        lorebookIds = overrides.lorebookIds,
        modeInjectionIds = overrides.modeInjectionIds,
        allowConversationPromptInjection = false,
        allowConversationSystemPrompt = false,
        quickMessageIds = emptySet(),
    )
}
