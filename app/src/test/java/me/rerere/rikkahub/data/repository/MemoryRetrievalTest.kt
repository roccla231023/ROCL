package me.rerere.rikkahub.data.repository

import me.rerere.rikkahub.data.db.entity.MemoryType
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.MemoryRetrievalMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRetrievalTest {
    @Test
    fun vectorAndHybridDegradeToKeywordWithoutEmbeddingModel() {
        assertEquals(
            MemoryRetrievalMode.KEYWORD,
            MemoryRetrieval.resolveMode(MemoryRetrievalMode.VECTOR, hasEmbeddingModel = false),
        )
        assertEquals(
            MemoryRetrievalMode.KEYWORD,
            MemoryRetrieval.resolveMode(MemoryRetrievalMode.HYBRID, hasEmbeddingModel = false),
        )
    }

    @Test
    fun offAndKeywordStayWithoutEmbeddingModel() {
        assertEquals(
            MemoryRetrievalMode.OFF,
            MemoryRetrieval.resolveMode(MemoryRetrievalMode.OFF, hasEmbeddingModel = false),
        )
        assertEquals(
            MemoryRetrievalMode.KEYWORD,
            MemoryRetrieval.resolveMode(MemoryRetrievalMode.KEYWORD, hasEmbeddingModel = false),
        )
        assertEquals(
            MemoryRetrievalMode.VECTOR,
            MemoryRetrieval.resolveMode(MemoryRetrievalMode.VECTOR, hasEmbeddingModel = true),
        )
    }

    @Test
    fun typeFilterKeepsPinnedEvenWhenThatTypeIsExcluded() {
        val core = memory(id = 1, content = "core fact", type = MemoryType.CORE)
        val pinnedEpisodic = memory(
            id = 2,
            content = "pinned episode",
            type = MemoryType.EPISODIC,
            pinned = true,
        )
        val episodic = memory(id = 3, content = "loose episode", type = MemoryType.EPISODIC)
        val filtered = MemoryRetrieval.filterByType(
            memories = listOf(core, pinnedEpisodic, episodic),
            includeCore = true,
            includeEpisodes = false,
        )
        assertEquals(listOf(1, 2), filtered.map { it.id })
    }

    @Test
    fun typeFilterDropsCoreWhenDisabled() {
        val core = memory(id = 1, content = "core fact", type = MemoryType.CORE)
        val episodic = memory(id = 2, content = "an episode", type = MemoryType.EPISODIC)
        val filtered = MemoryRetrieval.filterByType(
            memories = listOf(core, episodic),
            includeCore = false,
            includeEpisodes = true,
        )
        assertEquals(listOf(2), filtered.map { it.id })
    }

    @Test
    fun keywordPrefersPinnedAndMatchingTokens() {
        val pinned = memory(id = 1, content = "unrelated pin", pinned = true)
        val match = memory(id = 2, content = "user likes coffee in the morning")
        val miss = memory(id = 3, content = "unrelated note")
        val hits = MemoryRetrieval.retrieveKeyword(
            memories = listOf(miss, match, pinned),
            query = "coffee morning",
            limit = 5,
        )
        assertEquals(listOf(1, 2), hits.map { it.id })
    }

    @Test
    fun emptyKeywordQueryReturnsPinnedOrFirstLimit() {
        val memories = listOf(
            memory(id = 1, content = "one"),
            memory(id = 2, content = "two"),
            memory(id = 3, content = "three"),
        )
        val withoutPin = MemoryRetrieval.retrieveKeyword(memories, query = "??", limit = 2)
        assertEquals(listOf(1, 2), withoutPin.map { it.id })

        val pinned = memories.map { if (it.id == 3) it.copy(pinned = true) else it }
        val withPin = MemoryRetrieval.retrieveKeyword(pinned, query = "  ", limit = 2)
        assertEquals(listOf(3), withPin.map { it.id })
    }

    @Test
    fun vectorDropsBelowThresholdUnlessPinnedAndFallsBackWhenEmpty() {
        val query = listOf(1f, 0f)
        val high = memory(id = 1, content = "high", embedding = listOf(1f, 0f))
        val low = memory(id = 2, content = "low", embedding = listOf(0f, 1f))
        val pinnedLow = memory(
            id = 3,
            content = "pinned-low",
            embedding = listOf(0f, 1f),
            pinned = true,
        )
        val fallback = listOf(memory(id = 99, content = "keyword"))

        val hits = MemoryRetrieval.retrieveVector(
            memories = listOf(high, low, pinnedLow),
            queryEmbedding = query,
            threshold = 0.9f,
            limit = 5,
            keywordFallback = fallback,
        )
        assertEquals(listOf(3, 1), hits.map { it.id })
        assertFalse(hits.any { it.id == 2 })

        val noEmbeddingHits = MemoryRetrieval.retrieveVector(
            memories = listOf(low),
            queryEmbedding = query,
            threshold = 0.9f,
            limit = 5,
            keywordFallback = fallback,
        )
        assertEquals(listOf(99), noEmbeddingHits.map { it.id })

        val nullQuery = MemoryRetrieval.retrieveVector(
            memories = listOf(high),
            queryEmbedding = null,
            threshold = 0.1f,
            limit = 5,
            keywordFallback = fallback,
        )
        assertEquals(listOf(99), nullQuery.map { it.id })
    }

    @Test
    fun tokenizeDropsStopWordsAndShortTokens() {
        val tokens = MemoryRetrieval.tokenize("The coffee 的是 A")
        assertTrue(tokens.contains("coffee"))
        assertFalse(tokens.contains("the"))
        assertFalse(tokens.contains("的"))
        assertFalse(tokens.contains("a"))
    }

    private fun memory(
        id: Int,
        content: String,
        type: Int = MemoryType.CORE,
        pinned: Boolean = false,
        embedding: List<Float>? = null,
    ) = AssistantMemory(
        id = id,
        content = content,
        embedding = embedding,
        type = type,
        pinned = pinned,
    )
}
