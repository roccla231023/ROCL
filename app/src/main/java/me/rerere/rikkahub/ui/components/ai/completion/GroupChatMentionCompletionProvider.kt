package me.rerere.rikkahub.ui.components.ai.completion

import androidx.compose.ui.text.TextRange
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.GroupChatTemplate
import me.rerere.rikkahub.data.model.buildSeatDisplayNames
import kotlin.uuid.Uuid

class GroupChatMentionCompletionProvider(
    private val template: GroupChatTemplate,
    private val assistantsById: Map<Uuid, Assistant>,
) : ChatCompletionProvider {
    override val id: String = "group_chat_mention"

    override suspend fun complete(context: ChatCompletionContext): ChatCompletionList? {
        if (context.hasSelection) return null
        val mention = findMention(context.text, context.cursor) ?: return null
        val names = template.buildSeatDisplayNames(assistantsById)
        val query = mention.query.lowercase()
        val items = template.seats.mapNotNull { seat ->
            if (!seat.defaultEnabled) return@mapNotNull null
            val name = names[seat.id] ?: return@mapNotNull null
            if (query.isNotBlank() && !name.lowercase().contains(query)) return@mapNotNull null
            ChatCompletionItem(
                label = "@$name",
                insertText = "@$name ",
                detail = assistantsById[seat.assistantId]?.name,
                sortScore = if (name.lowercase().startsWith(query)) 10_000 else 9_000,
            )
        }
        if (items.isEmpty()) return null
        return ChatCompletionList(
            providerId = id,
            replacementRange = mention.range,
            items = items,
        )
    }

    private fun findMention(text: String, cursor: Int): Mention? {
        if (cursor < 0 || cursor > text.length) return null
        val prefix = text.substring(0, cursor)
        val start = prefix.lastIndexOf('@')
        if (start < 0) return null
        if (start > 0 && !prefix[start - 1].isWhitespace()) return null
        val query = prefix.substring(start + 1)
        if (query.any { it.isWhitespace() }) return null
        return Mention(query = query, range = TextRange(start, cursor))
    }

    private data class Mention(val query: String, val range: TextRange)
}
