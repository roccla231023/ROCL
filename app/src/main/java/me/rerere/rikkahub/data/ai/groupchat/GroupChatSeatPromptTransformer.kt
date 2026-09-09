package me.rerere.rikkahub.data.ai.groupchat

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.TransformerContext
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.GroupChatSeat
import kotlin.uuid.Uuid

class GroupChatSeatPromptTransformer(
    private val seat: GroupChatSeat,
    private val selfAssistantId: Uuid,
    private val seatDisplayNames: Map<Uuid, String>,
    private val assistantsById: Map<Uuid, Assistant>,
    private val userName: String,
) : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return GroupChatEngine.rewritePromptMessagesForSeat(
            messages = messages,
            seat = seat,
            selfAssistantId = selfAssistantId,
            seatDisplayNames = seatDisplayNames,
            assistantsById = assistantsById,
            userName = userName,
        )
    }
}
