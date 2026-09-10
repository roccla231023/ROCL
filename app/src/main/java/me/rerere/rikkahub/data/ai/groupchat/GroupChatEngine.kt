package me.rerere.rikkahub.data.ai.groupchat

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.GroupChatSeat
import me.rerere.rikkahub.data.model.GroupChatTemplate
import me.rerere.rikkahub.data.model.buildSeatDisplayNames
import java.util.Locale
import kotlin.uuid.Uuid

object GroupChatEngine {
    fun resolveMentionedSeatIds(
        text: String,
        template: GroupChatTemplate,
        assistantsById: Map<Uuid, Assistant>,
        defaultName: String = "Assistant",
    ): List<Uuid> {
        if (text.isBlank() || !text.contains('@')) return emptyList()
        val seatDisplayNames = template.buildSeatDisplayNames(assistantsById, defaultName)
        val keyToSeatIds = mutableMapOf<String, MutableList<Uuid>>()
        template.seats.forEach { seat ->
            val key = seatDisplayNames[seat.id]?.trim()?.takeIf { it.isNotBlank() } ?: return@forEach
            keyToSeatIds.getOrPut(key.lowercase(Locale.ROOT)) { mutableListOf() }.add(seat.id)
        }
        if (keyToSeatIds.isEmpty()) return emptyList()
        val sortedKeys = keyToSeatIds.keys.sortedByDescending { it.length }
        val result = mutableListOf<Uuid>()
        val lowerText = text.lowercase(Locale.ROOT)
        var cursor = 0
        while (true) {
            val atIndex = lowerText.indexOf('@', startIndex = cursor)
            if (atIndex < 0) break
            val after = lowerText.substring(atIndex + 1)
            val matchedKey = sortedKeys.firstOrNull { after.startsWith(it) }
            if (matchedKey != null) {
                keyToSeatIds[matchedKey]?.forEach { seatId ->
                    if (seatId !in result) result.add(seatId)
                }
                cursor = atIndex + 1 + matchedKey.length
            } else {
                cursor = atIndex + 1
            }
        }
        return result
    }

    fun resolveSpeakerSeatIds(
        userText: String,
        template: GroupChatTemplate,
        assistantsById: Map<Uuid, Assistant>,
        stickySeatId: Uuid?,
        defaultName: String = "Assistant",
    ): List<Uuid> {
        val enabledSeats = template.seats.filter { it.defaultEnabled }
        val seatsById = enabledSeats.associateBy { it.id }
        val mentioned = resolveMentionedSeatIds(userText, template, assistantsById, defaultName)
            .filter { it in seatsById }
        if (mentioned.isNotEmpty()) return mentioned.distinct()
        stickySeatId?.let { sticky ->
            if (sticky in seatsById) return listOf(sticky)
        }
        return enabledSeats.firstOrNull()?.id?.let { listOf(it) }.orEmpty()
    }

    fun nextStickySeatId(speakerSeatIds: List<Uuid>, previousSticky: Uuid?): Uuid? {
        return speakerSeatIds.lastOrNull() ?: previousSticky
    }

    fun rewritePromptMessagesForSeat(
        messages: List<UIMessage>,
        seat: GroupChatSeat,
        selfAssistantId: Uuid,
        seatDisplayNames: Map<Uuid, String>,
        assistantsById: Map<Uuid, Assistant>,
        userName: String,
    ): List<UIMessage> {
        if (messages.isEmpty()) {
            return listOf(UIMessage.user("Please reply."))
        }
        val transformed = messages.mapNotNull { message ->
            when (message.role) {
                MessageRole.ASSISTANT -> rewriteAssistantMessage(
                    message = message,
                    seat = seat,
                    selfAssistantId = selfAssistantId,
                    seatDisplayNames = seatDisplayNames,
                    assistantsById = assistantsById,
                )
                MessageRole.USER -> prefixUserMessage(message, userName)
                MessageRole.SYSTEM -> message
                else -> null
            }
        }
        if (transformed.isEmpty()) {
            return listOf(UIMessage.user("Please reply."))
        }
        if (transformed.last().role != MessageRole.USER) {
            return transformed + UIMessage.user("Please reply.")
        }
        return transformed
    }

    fun patchGeneratedMessages(
        messages: List<UIMessage>,
        seat: GroupChatSeat,
        assistant: Assistant,
    ): List<UIMessage> {
        return messages.map { message ->
            if (message.role != MessageRole.ASSISTANT) return@map message
            if (message.speakerSeatId != null && message.speakerSeatId != seat.id) return@map message
            message.copy(
                speakerSeatId = seat.id,
                speakerAssistantId = assistant.id,
            )
        }
    }

    fun contextSystemPromptSuffix(
        template: GroupChatTemplate,
        seat: GroupChatSeat,
        seatDisplayNames: Map<Uuid, String>,
    ): String {
        val selfName = seatDisplayNames[seat.id] ?: "Assistant"
        val others = template.seats
            .filter { it.id != seat.id && it.defaultEnabled }
            .mapNotNull { seatDisplayNames[it.id] }
        return buildString {
            val intro = template.intro.trim()
            if (intro.isNotBlank()) {
                appendLine(intro)
            }
            appendLine("You are $selfName in a group chat.")
            if (others.isNotEmpty()) {
                appendLine("Other seats: ${others.joinToString(", ")}.")
            }
            appendLine("Reply as $selfName only. Do not speak for other seats.")
            appendLine("User messages and other seats are labelled. Tool output from other seats is included as labelled notes.")
        }.trim()
    }

    internal fun extractToolOutputSummary(message: UIMessage, maxChars: Int = 2000): String {
        val tools = message.parts.filterIsInstance<UIMessagePart.Tool>().filter { it.isExecuted }
        if (tools.isEmpty()) return ""
        return tools.joinToString("\n") { tool ->
            val body = tool.output
                .filterIsInstance<UIMessagePart.Text>()
                .joinToString("\n") { it.text }
                .trim()
            val clipped = if (body.length > maxChars) body.take(maxChars) + "…" else body
            val name = tool.toolName.ifBlank { "tool" }
            "[$name]\n$clipped"
        }.trim()
    }

    private fun isSelfMessage(
        message: UIMessage,
        seat: GroupChatSeat,
        selfAssistantId: Uuid,
    ): Boolean {
        val speakerSeatId = message.speakerSeatId
        val speakerAssistantId = message.speakerAssistantId
        return when {
            speakerSeatId != null -> speakerSeatId == seat.id
            speakerAssistantId != null -> speakerAssistantId == selfAssistantId
            else -> false
        }
    }

    private fun rewriteAssistantMessage(
        message: UIMessage,
        seat: GroupChatSeat,
        selfAssistantId: Uuid,
        seatDisplayNames: Map<Uuid, String>,
        assistantsById: Map<Uuid, Assistant>,
    ): UIMessage? {
        if (isSelfMessage(message, seat, selfAssistantId)) return message
        val speakerName = resolveSpeakerName(message, seatDisplayNames, assistantsById)
        val content = message.toText().trim()
        val toolSummary = extractToolOutputSummary(message)
        if (content.isBlank() && toolSummary.isBlank()) return null
        val label = if (speakerName.isNullOrBlank()) {
            "another assistant"
        } else {
            speakerName
        }
        val text = buildString {
            appendLine("[Message from $label (assistant)]")
            if (content.isNotBlank()) {
                append(content.take(4000))
            }
            if (toolSummary.isNotBlank()) {
                if (content.isNotBlank()) appendLine()
                appendLine("[Tool output from $label]")
                append(toolSummary)
            }
        }.trim()
        return message.copy(
            role = MessageRole.USER,
            parts = listOf(UIMessagePart.Text(text)),
        )
    }

    private fun prefixUserMessage(message: UIMessage, userName: String): UIMessage {
        val name = userName.trim().ifBlank { "User" }
        val prefix = "[Message from $name (user)]"
        val parts = message.parts
        val firstTextIndex = parts.indexOfFirst { it is UIMessagePart.Text }
        val updatedParts = if (firstTextIndex >= 0) {
            parts.mapIndexed { index, part ->
                if (index != firstTextIndex) return@mapIndexed part
                val textPart = part as UIMessagePart.Text
                UIMessagePart.Text(
                    buildString {
                        appendLine(prefix)
                        append(textPart.text.trim())
                    }
                )
            }
        } else {
            listOf(UIMessagePart.Text(prefix)) + parts
        }
        return message.copy(parts = updatedParts)
    }

    private fun resolveSpeakerName(
        message: UIMessage,
        seatDisplayNames: Map<Uuid, String>,
        assistantsById: Map<Uuid, Assistant>,
    ): String? {
        message.speakerSeatId?.let { seatId ->
            return seatDisplayNames[seatId]?.trim()?.takeIf { it.isNotBlank() }
        }
        message.speakerAssistantId?.let { assistantId ->
            return assistantsById[assistantId]?.name?.trim()?.takeIf { it.isNotBlank() }
        }
        return null
    }
}
