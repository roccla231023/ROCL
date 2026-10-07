package me.rerere.rikkahub.data.rp.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import kotlin.uuid.Uuid

class RpModelGateway(
    private val providerManager: ProviderManager,
    private val json: Json,
) {
    suspend fun complete(
        settings: Settings,
        model: Model,
        systemPrompt: String,
        userPrompt: String,
        sessionId: Uuid,
    ): String {
        val provider = model.findProvider(settings.providers)
            ?: error("Provider not found for RP model ${model.displayName}")
        val providerImpl = providerManager.getProviderByType(provider)
        val result = providerImpl.generateText(
            providerSetting = provider,
            messages = listOf(UIMessage.system(systemPrompt), UIMessage.user(userPrompt)),
            params = TextGenerationParams(
                model = model,
                reasoningLevel = ReasoningLevel.OFF,
                customHeaders = model.customHeaders,
                customBody = model.customBodies,
                sessionId = sessionId.toString(),
            ),
        )
        return result.message.toText().trim()
    }

    fun parseJsonObject(text: String): kotlinx.serialization.json.JsonObject {
        val normalized = text
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return json.parseToJsonElement(normalized).jsonObject
    }
}
