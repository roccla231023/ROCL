package me.rerere.rikkahub.data.repository

import me.rerere.rikkahub.data.ai.rag.VectorEngine
import me.rerere.rikkahub.data.db.entity.MemoryType
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.MemoryRetrievalMode
import me.rerere.rikkahub.data.model.requiresEmbedding

object MemoryRetrieval {
    fun resolveMode(mode: MemoryRetrievalMode, hasEmbeddingModel: Boolean): MemoryRetrievalMode {
        return if (mode.requiresEmbedding && !hasEmbeddingModel) {
            MemoryRetrievalMode.KEYWORD
        } else {
            mode
        }
    }

    fun filterByType(
        memories: List<AssistantMemory>,
        includeCore: Boolean,
        includeEpisodes: Boolean,
    ): List<AssistantMemory> {
        return memories.filter { memory ->
            if (memory.pinned) return@filter true
            if (isEpisodic(memory.type)) includeEpisodes else includeCore
        }
    }

    fun retrieveKeyword(
        memories: List<AssistantMemory>,
        query: String,
        limit: Int,
    ): List<AssistantMemory> {
        val capped = limit.coerceIn(1, 50)
        val tokens = tokenize(query)
        if (tokens.isEmpty()) {
            return memories.filter { it.pinned }.ifEmpty { memories.take(capped) }
        }
        return memories
            .map { memory ->
                val contentTokens = tokenize(memory.content)
                val overlap = tokens.count { it in contentTokens }
                memory to overlap.toFloat()
            }
            .filter { (memory, score) -> memory.pinned || score > 0f }
            .sortedWith(
                compareByDescending<Pair<AssistantMemory, Float>> { it.first.pinned }
                    .thenByDescending { it.second }
            )
            .map { it.first }
            .distinctBy { it.id }
            .take(capped)
    }

    fun retrieveVector(
        memories: List<AssistantMemory>,
        queryEmbedding: List<Float>?,
        threshold: Float,
        limit: Int,
        keywordFallback: List<AssistantMemory>,
    ): List<AssistantMemory> {
        if (queryEmbedding == null) return keywordFallback
        val capped = limit.coerceIn(1, 50)
        val scored = memories.mapNotNull { memory ->
            val embedding = memory.embedding ?: return@mapNotNull null
            val score = VectorEngine.cosineSimilarity(queryEmbedding, embedding)
            if (memory.pinned || score >= threshold) memory to score else null
        }.sortedWith(
            compareByDescending<Pair<AssistantMemory, Float>> { it.first.pinned }
                .thenByDescending { it.second }
        )
        val hits = scored.map { it.first }.distinctBy { it.id }.take(capped)
        return hits.ifEmpty { keywordFallback }
    }

    fun tokenize(value: String): Set<String> {
        return value.lowercase()
            .split(TOKEN_SPLIT)
            .map { it.trim() }
            .filter { it.length >= 2 && it !in STOP_WORDS }
            .toSet()
    }

    private fun isEpisodic(type: Int): Boolean {
        return type == MemoryType.EPISODIC ||
            type == MemoryType.TOOL_RESULT ||
            type == MemoryType.TOOL_RESULT_CHUNK
    }

    private val TOKEN_SPLIT = Regex("[\\s\\p{Punct}]+")
    private val STOP_WORDS = setOf(
        "the", "and", "for", "are", "was", "were", "this", "that",
        "的", "了", "吗", "呢", "我", "你", "他", "她", "是", "在", "和", "什么",
    )
}
