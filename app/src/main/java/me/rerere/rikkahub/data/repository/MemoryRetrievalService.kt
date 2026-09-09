package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getEmbeddingRetrievalTimeoutMillis
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.MemoryRetrievalMode
import me.rerere.rikkahub.data.model.effectiveMemoryRetrievalMode

class MemoryRetrievalService(
    private val memoryRepository: MemoryRepository,
    private val embeddingService: EmbeddingService,
) {
    suspend fun memoriesForGeneration(
        assistant: Assistant,
        query: String,
        settings: Settings,
    ): List<AssistantMemory> {
        val memoryOwnerId = if (assistant.useGlobalMemory) {
            MemoryRepository.GLOBAL_MEMORY_ID
        } else {
            assistant.id.toString()
        }
        val embeddingOwnerId = assistant.id.toString()
        val all = MemoryRetrieval.filterByType(
            memories = memoryRepository.getMemoriesOfAssistant(memoryOwnerId),
            includeCore = assistant.ragIncludeCore,
            includeEpisodes = assistant.ragIncludeEpisodes,
        )
        if (all.isEmpty()) return emptyList()

        val hasEmbeddingModel = embeddingService.getEmbeddingModelId(embeddingOwnerId) != null
        val mode = MemoryRetrieval.resolveMode(
            assistant.effectiveMemoryRetrievalMode(),
            hasEmbeddingModel,
        )
        val limit = assistant.ragLimit.coerceIn(1, 50)
        return when (mode) {
            MemoryRetrievalMode.OFF -> all
            MemoryRetrievalMode.KEYWORD -> MemoryRetrieval.retrieveKeyword(all, query, limit)
            MemoryRetrievalMode.VECTOR, MemoryRetrievalMode.HYBRID -> {
                val timeout = settings.getEmbeddingRetrievalTimeoutMillis()
                val vectorHits = withTimeoutOrNull(timeout) {
                    retrieveVector(all, query, assistant, embeddingOwnerId)
                }
                when {
                    vectorHits == null && mode == MemoryRetrievalMode.HYBRID ->
                        MemoryRetrieval.retrieveKeyword(all, query, limit)
                    vectorHits == null ->
                        if (settings.displaySetting.useLastTurnMemoryOnSkip) all.take(limit) else emptyList()
                    else -> vectorHits
                }
            }
        }
    }

    private suspend fun retrieveVector(
        memories: List<AssistantMemory>,
        query: String,
        assistant: Assistant,
        assistantId: String,
    ): List<AssistantMemory> {
        val limit = assistant.ragLimit.coerceIn(1, 50)
        val fallback = MemoryRetrieval.retrieveKeyword(memories, query, limit)
        val queryEmbedding = embeddingService.embed(query, assistantId)
        return MemoryRetrieval.retrieveVector(
            memories = memories,
            queryEmbedding = queryEmbedding,
            threshold = assistant.ragSimilarityThreshold,
            limit = limit,
            keywordFallback = fallback,
        )
    }
}
