package me.rerere.rikkahub.data.ai.subagent

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationResult
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SubAgentEngineTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val provider = ProviderSetting.OpenAI(name = "test")
    private val model = Model(modelId = "test-model", displayName = "test")

    @Test
    fun occupyIsExclusiveAndAbortResetsOnClear() {
        val registry = SubAgentRunRegistry()
        assertTrue(registry.tryOccupy())
        assertFalse(registry.tryOccupy())
        registry.requestAbort()
        assertTrue(registry.isAbortRequested())
        registry.clear()
        assertFalse(registry.isAbortRequested())
        assertNull(registry.active.value)
        assertTrue(registry.tryOccupy())
        assertFalse(registry.isAbortRequested())
        registry.clear()
    }

    @Test
    fun occupyFailureResetsNothingAndDoesNotLeaveSlot() {
        val registry = SubAgentRunRegistry()
        registry.requestAbort()
        assertTrue(registry.tryOccupy())
        assertFalse(registry.isAbortRequested())
        registry.clear()
    }

    @Test
    fun filterKeepsReadonlyNamesAndDropsApprovalAndSelf() {
        val keep = dummyTool("search_web", needsApproval = false)
        val scrape = dummyTool("scrape_web", needsApproval = false)
        val read = dummyTool("workspace_read_file", needsApproval = false)
        val write = dummyTool("workspace_write_file", needsApproval = false)
        val shell = dummyTool("workspace_shell", needsApproval = true)
        val approvedRead = dummyTool("workspace_read_file", needsApproval = true)
        val self = dummyTool(SUBAGENT_TOOL_NAME, needsApproval = false)
        val filtered = filterSubAgentTools(
            listOf(keep, scrape, read, write, shell, approvedRead, self),
        )
        assertEquals(listOf("search_web", "scrape_web", "workspace_read_file"), filtered.map { it.name })
    }

    @Test
    fun emptyTaskFailsWithoutOccupying() = runBlocking {
        val registry = SubAgentRunRegistry()
        val engine = SubAgentEngine(json, registry) { _, _, _ -> error("should not generate") }
        val parts = engine.run(task = "  ", model = model, providerSetting = provider, tools = emptyList())
        val run = parts.decodeRun()
        assertEquals("failed", run.status)
        assertNull(registry.active.value)
    }

    @Test
    fun missingProviderFailsWithoutOccupying() = runBlocking {
        val registry = SubAgentRunRegistry()
        val engine = SubAgentEngine(json, registry) { _, _, _ -> error("should not generate") }
        val parts = engine.run(task = "look around", model = model, providerSetting = null, tools = emptyList())
        assertEquals("failed", parts.decodeRun().status)
        assertNull(registry.active.value)
    }

    @Test
    fun occupiedEngineRejectsSecondDispatch() = runBlocking {
        val registry = SubAgentRunRegistry()
        assertTrue(registry.tryOccupy())
        val engine = SubAgentEngine(json, registry) { _, _, _ -> error("should not generate") }
        val run = engine.run("task", model, provider, emptyList()).decodeRun()
        assertEquals("failed", run.status)
        assertTrue(run.summary.contains("已有一个"))
        assertNotNull(registry.active.value)
        registry.clear()
    }

    @Test
    fun completesWhenModelWritesTextWithoutTools() = runBlocking {
        val registry = SubAgentRunRegistry()
        val engine = SubAgentEngine(json, registry) { _, _, _ ->
            TextGenerationResult(
                id = "1",
                model = "test-model",
                message = UIMessage.assistant("Found nothing relevant."),
            )
        }
        val run = engine.run("inspect /workspace/a.md", model, provider, emptyList()).decodeRun()
        assertEquals("completed", run.status)
        assertEquals("Found nothing relevant.", run.summary)
        assertTrue(run.steps.isEmpty())
        assertNull(registry.active.value)
    }

    @Test
    fun executesToolThenStopsOnFinalText() = runBlocking {
        val registry = SubAgentRunRegistry()
        val generates = AtomicInteger(0)
        val search = dummyTool("search_web") { listOf(UIMessagePart.Text("title: docs")) }
        val engine = SubAgentEngine(json, registry) { _, messages, _ ->
            val round = generates.getAndIncrement()
            if (round == 0) {
                TextGenerationResult(
                    id = "1",
                    model = "test-model",
                    message = UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(
                            UIMessagePart.Tool(
                                toolCallId = "c1",
                                toolName = "search_web",
                                input = """{"query":"rocl"}""",
                            )
                        ),
                    ),
                )
            } else {
                assertTrue(messages.last().getTools().any { it.isExecuted })
                TextGenerationResult(
                    id = "2",
                    model = "test-model",
                    message = UIMessage.assistant("ROCL is the fork."),
                )
            }
        }
        val parts = engine.run("research ROCL", model, provider, listOf(search))
        val run = parts.decodeRun()
        assertEquals("completed", run.status)
        assertEquals(1, run.steps.size)
        assertEquals("search_web", run.steps[0].toolName)
        assertEquals("ROCL is the fork.", run.summary)
        assertEquals(2, generates.get())
        val text = parts.filterIsInstance<UIMessagePart.Text>().single()
        assertNotNull(text.metadata?.get(SUBAGENT_METADATA_KEY))
        assertEquals("ROCL is the fork.", run.summary)
        assertTrue(text.text.startsWith("[引擎]"))
        assertTrue(text.text.contains("ROCL is the fork."))
    }

    @Test
    fun lastStepDoesNotExecutePendingTools() = runBlocking {
        val registry = SubAgentRunRegistry()
        val executes = AtomicInteger(0)
        val generates = AtomicInteger(0)
        val search = dummyTool("search_web") {
            executes.incrementAndGet()
            listOf(UIMessagePart.Text("should not run"))
        }
        val engine = SubAgentEngine(json, registry) { _, _, _ ->
            val callId = "c${generates.getAndIncrement()}"
            TextGenerationResult(
                id = callId,
                model = "test-model",
                message = UIMessage(
                    role = MessageRole.ASSISTANT,
                    parts = listOf(
                        UIMessagePart.Text("partial"),
                        UIMessagePart.Tool(
                            toolCallId = callId,
                            toolName = "search_web",
                            input = "{}",
                        ),
                    ),
                ),
            )
        }
        val run = engine.run(
            task = "loop forever",
            model = model,
            providerSetting = provider,
            tools = listOf(search),
        ).decodeRun()
        assertEquals("completed", run.status)
        assertEquals(SUBAGENT_MAX_STEPS - 1, executes.get())
        assertEquals(SUBAGENT_MAX_STEPS - 1, run.steps.size)
        assertTrue(run.summary.contains("partial"))
    }

    @Test
    fun generateFailureBecomesFailedOutputNotThrown() = runBlocking {
        val registry = SubAgentRunRegistry()
        val engine = SubAgentEngine(json, registry) { _, _, _ -> error("network down") }
        val run = engine.run("go", model, provider, emptyList()).decodeRun()
        assertEquals("failed", run.status)
        assertTrue(run.summary.contains("network down") || run.summary.contains("失败"))
        assertNull(registry.active.value)
    }

    @Test
    fun abortDuringLoopReturnsPartialSummary() = runBlocking {
        val registry = SubAgentRunRegistry()
        val search = dummyTool("search_web") { listOf(UIMessagePart.Text("hit")) }
        val engine = SubAgentEngine(json, registry) { _, _, _ ->
            registry.requestAbort()
            TextGenerationResult(
                id = "1",
                model = "test-model",
                message = UIMessage(
                    role = MessageRole.ASSISTANT,
                    parts = listOf(
                        UIMessagePart.Tool(
                            toolCallId = "c1",
                            toolName = "search_web",
                            input = "{}",
                        )
                    ),
                ),
            )
        }
        val run = engine.run("abort me", model, provider, listOf(search)).decodeRun()
        assertEquals("aborted", run.status)
        assertNull(registry.active.value)
    }

    private fun dummyTool(
        name: String,
        needsApproval: Boolean = false,
        execute: suspend (kotlinx.serialization.json.JsonElement) -> List<UIMessagePart> = {
            listOf(UIMessagePart.Text("ok"))
        },
    ) = Tool(
        name = name,
        description = name,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject { put("x", buildJsonObject { put("type", "string") }) })
        },
        needsApproval = { needsApproval },
        execute = execute,
    )

    private fun List<UIMessagePart>.decodeRun(): SubAgentRun {
        val text = filterIsInstance<UIMessagePart.Text>().first()
        val payload = text.metadata?.get(SUBAGENT_METADATA_KEY)
        assertNotNull(payload)
        return json.decodeFromJsonElement(SubAgentRun.serializer(), payload!!)
    }
}
