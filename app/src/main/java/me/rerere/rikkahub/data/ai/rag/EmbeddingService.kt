package me.rerere.rikkahub.data.ai.rag

import me.rerere.ai.provider.EmbeddingGenerationParams
import me.rerere.ai.provider.ProviderManager
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getEmbeddingRetrievalTimeoutMillis
import kotlin.uuid.Uuid

class EmbeddingService(
    private val providerManager: ProviderManager,
    private val settingsStore: SettingsStore,
) {
    fun getRetrievalTimeoutMillis(): Long {
        return settingsStore.settingsFlow.value.getEmbeddingRetrievalTimeoutMillis()
    }

    fun getEmbeddingModelId(assistantId: String? = null): Uuid? {
        val settings = settingsStore.settingsFlow.value
        if (assistantId != null) {
            val assistant = settings.assistants.find { it.id.toString() == assistantId }
            assistant?.embeddingModelId?.let { return it }
        }
        val global = settings.embeddingModelId
        return settings.findModelById(global)?.id
    }

    suspend fun embed(
        text: String,
        assistantId: String? = null,
    ): List<Float>? {
        val settings = settingsStore.settingsFlow.value
        val modelId = getEmbeddingModelId(assistantId) ?: return null
        val model = settings.findModelById(modelId) ?: return null
        val providerSetting = model.findProvider(settings.providers) ?: return null
        return runCatching {
            val provider = providerManager.getProviderByType(providerSetting)
            val result = provider.generateEmbedding(
                providerSetting,
                EmbeddingGenerationParams(
                    model = model,
                    input = listOf(text),
                ),
            )
            result.embeddings.firstOrNull()
        }.getOrNull()
    }
}
