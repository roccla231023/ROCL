package me.rerere.ai.provider.providers.claude

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClaudeCodeSpoofingRequestTest {
    private lateinit var provider: ClaudeProvider

    @Before
    fun setUp() {
        provider = ClaudeProvider(OkHttpClient())
    }

    private fun buildRequest(
        providerSetting: ProviderSetting.Claude,
        messages: List<UIMessage>,
        params: TextGenerationParams = TextGenerationParams(model = Model(modelId = "claude-haiku-4-5")),
    ): JsonObject {
        val method = ClaudeProvider::class.java.getDeclaredMethod(
            "buildMessageRequest",
            ProviderSetting.Claude::class.java,
            List::class.java,
            TextGenerationParams::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        return method.invoke(provider, providerSetting, messages, params, false) as JsonObject
    }

    @Test
    fun `spoofing off should keep a plain Claude request`() {
        val request = buildRequest(
            ProviderSetting.Claude(claudeCodeSpoofing = false),
            listOf(
                UIMessage.system("custom system"),
                UIMessage.user("hello"),
            ),
        )

        val system = request["system"]!!.jsonArray
        assertEquals(1, system.size)
        assertEquals("custom system", system[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertNull(request["tools"])

        val firstUser = request["messages"]!!.jsonArray.first().jsonObject
        assertEquals("user", firstUser["role"]!!.jsonPrimitive.content)
        assertEquals(1, firstUser["content"]!!.jsonArray.size)
        assertEquals(
            "hello",
            firstUser["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `spoofing on should prepend CLI system blocks and first-user reminder`() {
        val request = buildRequest(
            ProviderSetting.Claude(claudeCodeSpoofing = true),
            listOf(
                UIMessage.system("custom system"),
                UIMessage.user("hello"),
                UIMessage.assistant("hi"),
                UIMessage.user("second turn"),
            ),
        )

        val system = request["system"]!!.jsonArray
        assertEquals(4, system.size)
        assertEquals(
            "x-anthropic-billing-header: cc_version=2.1.34.712; cc_entrypoint=sdk-cli;",
            system[0].jsonObject["text"]!!.jsonPrimitive.content,
        )
        assertEquals(
            "You are a Claude agent, built on Anthropic's Claude Agent SDK.",
            system[1].jsonObject["text"]!!.jsonPrimitive.content,
        )
        assertEquals("ephemeral", system[1].jsonObject["cache_control"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertTrue(system[2].jsonObject["text"]!!.jsonPrimitive.content.contains("Do NOT output CLI greetings"))
        assertEquals("custom system", system[3].jsonObject["text"]!!.jsonPrimitive.content)

        val messages = request["messages"]!!.jsonArray
        val firstUser = messages[0].jsonObject
        val firstContent = firstUser["content"]!!.jsonArray
        assertEquals("user", firstUser["role"]!!.jsonPrimitive.content)
        assertEquals(2, firstContent.size)
        val reminder = firstContent[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(reminder.startsWith("<system-reminder>"))
        assertEquals('t', reminder[4])
        assertEquals('-', reminder[7])
        assertEquals('e', reminder[20])
        assertEquals("hello", firstContent[1].jsonObject["text"]!!.jsonPrimitive.content)

        val secondUser = messages[2].jsonObject
        assertEquals("user", secondUser["role"]!!.jsonPrimitive.content)
        assertEquals(1, secondUser["content"]!!.jsonArray.size)
        assertEquals(
            "second turn",
            secondUser["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content,
        )
        assertEquals(0, request["tools"]!!.jsonArray.size)
    }

    @Test
    fun `spoofing on without user system still injects CLI system blocks`() {
        val request = buildRequest(
            ProviderSetting.Claude(claudeCodeSpoofing = true),
            listOf(UIMessage.user("hello")),
        )

        val system = request["system"]!!.jsonArray
        assertEquals(3, system.size)
        assertTrue(
            system[0].jsonObject["text"]!!.jsonPrimitive.content.startsWith("x-anthropic-billing-header:"),
        )
        assertFalse(
            request["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray[0]
                .jsonObject["text"]!!.jsonPrimitive.content == "hello",
        )
    }
}
