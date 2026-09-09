package me.rerere.rikkahub.data.repository

import kotlin.uuid.Uuid

interface ConversationDeletionCoordinator {
    suspend fun deleteConversationById(conversationId: Uuid, deleteFiles: Boolean = true)

    suspend fun deleteConversationsOfAssistant(assistantId: Uuid, deleteFiles: Boolean = true)
}
