package me.rerere.rikkahub.ui.components.ai.completion

import androidx.compose.ui.text.TextRange
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiBrain01
import me.rerere.hugeicons.stroke.File02

class GroupChatMentionCoordinator(
    private val members: GroupChatMentionCompletionProvider,
    private val files: WorkspaceCompletionProvider?,
    private val membersLabel: String = MEMBER_CATEGORY,
    private val filesLabel: String = FILE_CATEGORY,
    private val membersDetail: String = MEMBER_CATEGORY_DETAIL,
    private val filesDetail: String = FILE_CATEGORY_DETAIL,
) : ChatCompletionProvider {
    override val id: String = "group_chat_mention_coordinator"

    private var lastRangeStart: Int? = null
    private var selected: Pair<Int, Category>? = null

    override suspend fun complete(context: ChatCompletionContext): ChatCompletionList? {
        if (context.hasSelection) return null
        val mention = findMention(context.text, context.cursor) ?: run {
            selected = null
            lastRangeStart = null
            return null
        }
        lastRangeStart = mention.range.min
        if (selected?.first != mention.range.min) selected = null
        if (files == null) return members.complete(context)

        val query = mention.query
        return when (selected?.second) {
            Category.Members -> members.complete(context)
            Category.Files -> files.complete(context)
            null -> if (query.isNotBlank()) {
                merge(context, mention.range)
            } else ChatCompletionList(
                providerId = id,
                replacementRange = mention.range,
                items = listOf(
                    ChatCompletionItem(
                        label = membersLabel,
                        insertText = "",
                        detail = membersDetail,
                        icon = HugeIcons.AiBrain01,
                        sortScore = 2,
                        action = ACTION_MEMBERS,
                    ),
                    ChatCompletionItem(
                        label = filesLabel,
                        insertText = "",
                        detail = filesDetail,
                        icon = HugeIcons.File02,
                        sortScore = 1,
                        action = ACTION_FILES,
                    ),
                ),
            )
        }
    }

    override fun handleCompletionItem(item: ChatCompletionItem): Boolean {
        val category = when (item.action) {
            ACTION_MEMBERS -> Category.Members
            ACTION_FILES -> Category.Files
            else -> return false
        }
        val start = lastRangeStart ?: return false
        selected = start to category
        return true
    }

    private suspend fun merge(
        context: ChatCompletionContext,
        range: TextRange,
    ): ChatCompletionList? {
        val items = mergeItems(
            members.complete(context)?.items.orEmpty(),
            files?.complete(context)?.items.orEmpty(),
        )
        if (items.isEmpty()) return null
        return ChatCompletionList(id, range, items)
    }

    private enum class Category { Members, Files }

    companion object {
        const val ACTION_MEMBERS = "group_mention_members"
        const val ACTION_FILES = "group_mention_files"
        const val MEMBER_CATEGORY = "Members"
        const val FILE_CATEGORY = "Files"
        const val MEMBER_CATEGORY_DETAIL = "Mention a seat"
        const val FILE_CATEGORY_DETAIL = "Mention a workspace file"

        fun mergeLists(lists: List<ChatCompletionList>): ChatCompletionList? {
            val primary = lists.firstOrNull() ?: return null
            val merged = lists
                .filter { it.replacementRange == primary.replacementRange }
                .flatMap { it.items }
            return primary.copy(items = mergeItems(merged, emptyList()))
        }

        private fun mergeItems(
            first: List<ChatCompletionItem>,
            second: List<ChatCompletionItem>,
        ): List<ChatCompletionItem> {
            return (first + second)
                .distinctBy { it.label to it.insertText }
                .sortedWith(
                    compareByDescending<ChatCompletionItem> { it.sortScore }
                        .thenBy { it.label.length }
                        .thenBy { it.label.lowercase() },
                )
                .take(8)
        }
    }
}

internal fun findMention(text: String, cursor: Int): Mention? {
    if (cursor < 0 || cursor > text.length) return null
    val prefix = text.substring(0, cursor)
    val start = prefix.lastIndexOf('@')
    if (start < 0) return null
    if (start > 0 && !prefix[start - 1].isWhitespace()) return null
    val query = prefix.substring(start + 1)
    if (query.any { it.isWhitespace() }) return null
    return Mention(query = query, range = TextRange(start, cursor))
}

internal data class Mention(val query: String, val range: TextRange)
