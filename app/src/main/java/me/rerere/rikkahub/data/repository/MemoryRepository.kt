package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.db.dao.MemoryDAO
import me.rerere.rikkahub.data.db.entity.MemoryEntity
import me.rerere.rikkahub.data.db.entity.MemoryType
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.utils.JsonInstant

class MemoryRepository(
    private val memoryDAO: MemoryDAO,
    private val embeddingService: EmbeddingService,
) {
    companion object {
        const val GLOBAL_MEMORY_ID = "__global__"
    }

    fun getMemoriesOfAssistantFlow(assistantId: String): Flow<List<AssistantMemory>> =
        memoryDAO.getMemoriesOfAssistantFlow(assistantId)
            .map { entities -> entities.map { it.toAssistantMemory() } }

    suspend fun getMemoriesOfAssistant(assistantId: String): List<AssistantMemory> {
        return memoryDAO.getMemoriesOfAssistant(assistantId).map { it.toAssistantMemory() }
    }

    fun getGlobalMemoriesFlow(): Flow<List<AssistantMemory>> =
        memoryDAO.getMemoriesOfAssistantFlow(GLOBAL_MEMORY_ID)
            .map { entities -> entities.map { it.toAssistantMemory() } }

    suspend fun getGlobalMemories(): List<AssistantMemory> {
        return memoryDAO.getMemoriesOfAssistant(GLOBAL_MEMORY_ID).map { it.toAssistantMemory() }
    }

    suspend fun deleteMemoriesOfAssistant(assistantId: String) {
        memoryDAO.deleteMemoriesOfAssistant(assistantId)
    }

    suspend fun updateContent(
        id: Int,
        content: String,
        embeddingAssistantId: String? = null,
    ): AssistantMemory {
        val old = memoryDAO.getMemoryById(id) ?: error("Memory record #$id not found")
        val now = System.currentTimeMillis()
        val embedId = embeddingAssistantId ?: old.assistantId
        val embedding = embeddingService.embed(content, embedId)
        val newMemory = old.copy(
            content = content,
            embedding = embedding?.let { JsonInstant.encodeToString(it) },
            embeddingModelId = embeddingService.getEmbeddingModelId(embedId)?.toString(),
            updatedAt = now,
        )
        memoryDAO.updateMemory(newMemory)
        return newMemory.toAssistantMemory()
    }

    suspend fun updateMetadata(
        id: Int,
        type: Int = MemoryType.CORE,
        pinned: Boolean = false,
    ): AssistantMemory {
        val old = memoryDAO.getMemoryById(id) ?: error("Memory record #$id not found")
        val now = System.currentTimeMillis()
        val newMemory = old.copy(
            type = if (type == MemoryType.EPISODIC) MemoryType.EPISODIC else MemoryType.CORE,
            pinned = pinned,
            updatedAt = now,
        )
        memoryDAO.updateMemory(newMemory)
        return newMemory.toAssistantMemory()
    }

    suspend fun addMemory(
        assistantId: String,
        content: String,
        type: Int = MemoryType.CORE,
        pinned: Boolean = false,
        embeddingAssistantId: String? = null,
    ): AssistantMemory {
        val now = System.currentTimeMillis()
        val embedId = embeddingAssistantId ?: assistantId
        val embedding = embeddingService.embed(content, embedId)
        val storedType = if (type == MemoryType.EPISODIC) MemoryType.EPISODIC else MemoryType.CORE
        val id = memoryDAO.insertMemory(
            MemoryEntity(
                assistantId = assistantId,
                content = content,
                embedding = embedding?.let { JsonInstant.encodeToString(it) },
                embeddingModelId = embeddingService.getEmbeddingModelId(embedId)?.toString(),
                type = storedType,
                pinned = pinned,
                createdAt = now,
                lastAccessedAt = now,
                updatedAt = now,
            )
        ).toInt()
        return (memoryDAO.getMemoryById(id) ?: MemoryEntity(
            id = id,
            assistantId = assistantId,
            content = content,
            type = storedType,
            pinned = pinned,
            createdAt = now,
            lastAccessedAt = now,
            updatedAt = now,
        )).toAssistantMemory()
    }

    suspend fun deleteMemory(id: Int) {
        memoryDAO.deleteMemory(id)
    }

    private fun MemoryEntity.toAssistantMemory(): AssistantMemory = AssistantMemory(
        id = id,
        content = content,
        embedding = embedding.parseEmbedding(),
        embeddingModelId = embeddingModelId,
        type = type,
        pinned = pinned,
        lastAccessedAt = lastAccessedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun String?.parseEmbedding(): List<Float>? {
        if (this.isNullOrBlank()) return null
        return runCatching { JsonInstant.decodeFromString<List<Float>>(this) }.getOrNull()
    }
}
