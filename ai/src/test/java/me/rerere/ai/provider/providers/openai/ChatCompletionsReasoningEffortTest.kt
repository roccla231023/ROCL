package me.rerere.ai.provider.providers.openai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.util.KeyRoulette
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChatCompletionsReasoningEffortTest {
    private val api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())

    @Test
    fun `openai compatible off sends none not low`() {
        val body = buildRequest(
            reasoningLevel = ReasoningLevel.OFF,
            baseUrl = "https://api.openai.com/v1",
        )
        assertEquals("none", body["reasoning_effort"]?.jsonPrimitive?.content)
    }

    @Test
    fun `openai compatible auto omits reasoning effort`() {
        val body = buildRequest(
            reasoningLevel = ReasoningLevel.AUTO,
            baseUrl = "https://api.openai.com/v1",
        )
        assertFalse(body.containsKey("reasoning_effort"))
    }

    @Test
    fun `unknown host off also sends none`() {
        val body = buildRequest(
            reasoningLevel = ReasoningLevel.OFF,
            baseUrl = "https://proxy.example.com/v1",
        )
        assertEquals("none", body["reasoning_effort"]?.jsonPrimitive?.content)
    }

    @Test
    fun `nvidia off sends none`() {
        val body = buildRequest(
            reasoningLevel = ReasoningLevel.OFF,
            baseUrl = "https://integrate.api.nvidia.com/v1",
        )
        assertEquals("none", body["reasoning_effort"]?.jsonPrimitive?.content)
    }

    private fun buildRequest(reasoningLevel: ReasoningLevel, baseUrl: String): JsonObject {
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "buildChatCompletionRequest",
            List::class.java,
            TextGenerationParams::class.java,
            ProviderSetting.OpenAI::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        val params = TextGenerationParams(
            model = Model(
                modelId = "gpt-5",
                abilities = listOf(ModelAbility.REASONING),
            ),
            reasoningLevel = reasoningLevel,
        )
        return method.invoke(
            api,
            listOf(UIMessage.user("hi")),
            params,
            ProviderSetting.OpenAI(baseUrl = baseUrl),
            false,
        ) as JsonObject
    }
}
