package me.rerere.rikkahub.data.ai.subagent

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.TextGenerationResult
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.handleTextGenerationResult

class SubAgentEngine(
    private val json: Json,
    private val registry: SubAgentRunRegistry,
    private val generate: suspend (
        providerSetting: ProviderSetting,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ) -> TextGenerationResult,
) {
    constructor(
        json: Json,
        registry: SubAgentRunRegistry,
        providerManager: ProviderManager,
    ) : this(
        json = json,
        registry = registry,
        generate = { providerSetting, messages, params ->
            providerManager.getProviderByType(providerSetting).generateText(
                providerSetting = providerSetting,
                messages = messages,
                params = params,
            )
        },
    )

    suspend fun run(
        task: String,
        model: Model,
        providerSetting: ProviderSetting?,
        tools: List<Tool>,
        reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
        unavailableReason: String? = null,
    ): List<UIMessagePart> {
        // 配置了子代理模型但这一轮没有任何可用工具: 明确失败并说明原因, 而不是静默什么都不发生。
        // 放在 tryOccupy 之前, 不占用单槽。
        if (unavailableReason != null) {
            return output(
                status = "failed",
                steps = emptyList(),
                summary = "子代理本轮没有可用工具，未执行。原因：$unavailableReason",
            )
        }
        val trimmed = task.trim()
        if (trimmed.isEmpty()) {
            return output(
                status = "failed",
                steps = emptyList(),
                summary = "任务为空。子代理需要一条自包含的 task。",
            )
        }
        if (providerSetting == null) {
            return output(
                status = "failed",
                steps = emptyList(),
                summary = "找不到当前模型对应的提供商，无法派生子代理。",
            )
        }
        if (!registry.tryOccupy()) {
            return output(
                status = "failed",
                steps = emptyList(),
                summary = "已有一个子代理在运行。",
            )
        }

        try {
            return executeOccupied(
                task = trimmed,
                model = model,
                providerSetting = providerSetting,
                tools = tools,
                reasoningLevel = reasoningLevel,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            return output(
                status = "failed",
                steps = emptyList(),
                summary = "子代理失败：${error.message ?: error::class.java.simpleName}",
            )
        } finally {
            registry.clear()
        }
    }

    private suspend fun executeOccupied(
        task: String,
        model: Model,
        providerSetting: ProviderSetting,
        tools: List<Tool>,
        reasoningLevel: ReasoningLevel,
    ): List<UIMessagePart> {
        var messages = listOf(
            UIMessage.system(DEFAULT_SUBAGENT_PROMPT),
            UIMessage.user(task),
        )
        val steps = mutableListOf<SubAgentStep>()
        val params = TextGenerationParams(
            model = model,
            tools = tools,
            reasoningLevel = reasoningLevel,
        )

        for (stepIndex in 0 until SUBAGENT_MAX_STEPS) {
            if (registry.isAbortRequested()) {
                return output("aborted", steps, summarize(steps, fallback = "已中止。"))
            }

            val isLast = stepIndex == SUBAGENT_MAX_STEPS - 1
            if (isLast) {
                messages = messages + UIMessage.user(FINAL_STEP_REMINDER)
            }

            registry.update {
                it.copy(
                    step = stepIndex + 1,
                    total = SUBAGENT_MAX_STEPS,
                    phase = "思考中",
                    recent = steps.takeLast(3),
                )
            }

            val result = try {
                generate(providerSetting, messages, params)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                return output(
                    status = "failed",
                    steps = steps,
                    summary = summarize(
                        steps,
                        fallback = "子代理调用模型失败：${error.message ?: error::class.java.simpleName}",
                    ),
                )
            }

            messages = messages.handleTextGenerationResult(result, model)
            val assistant = messages.last()
            val pending = assistant.getTools().filter { !it.isExecuted }
            if (pending.isEmpty() || isLast) {
                val summary = assistant.toText().ifBlank { summarize(steps) }
                return output("completed", steps, summary)
            }

            val executed = mutableListOf<UIMessagePart.Tool>()
            for (call in pending) {
                if (registry.isAbortRequested()) {
                    return output("aborted", steps, summarize(steps, fallback = "已中止。"))
                }
                registry.update {
                    it.copy(
                        step = stepIndex + 1,
                        total = SUBAGENT_MAX_STEPS,
                        phase = "调用 ${call.toolName}",
                        recent = steps.takeLast(3),
                    )
                }
                val rawOutput = try {
                    val def = tools.find { it.name == call.toolName }
                        ?: error("Tool ${call.toolName} not found")
                    def.execute(call.inputAsJson())
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    listOf(
                        UIMessagePart.Text(
                            "工具执行失败：${error.message ?: error::class.java.simpleName}",
                        )
                    )
                }
                val clipped = clip(rawOutput, SUBAGENT_TOOL_OUTPUT_CHARS)
                val preview = clipped.filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n") { it.text }
                val evidence = parseSubAgentEvidence(json, call.toolName, call.input)
                steps += SubAgentStep(
                    toolName = call.toolName,
                    inputPreview = clipPreview(call.input),
                    outputPreview = clipPreview(preview),
                    path = evidence.path,
                    command = evidence.command,
                    url = evidence.url,
                    query = evidence.query,
                )
                registry.update {
                    it.copy(recent = steps.takeLast(3))
                }
                executed += call.copy(output = clipped)
            }

            val updatedParts = assistant.parts.map { part ->
                if (part is UIMessagePart.Tool) {
                    executed.find { it.toolCallId == part.toolCallId } ?: part
                } else {
                    part
                }
            }
            messages = messages.dropLast(1) + assistant.copy(parts = updatedParts)
        }

        return output("completed", steps, summarize(steps))
    }

    private fun output(
        status: String,
        steps: List<SubAgentStep>,
        summary: String,
    ): List<UIMessagePart> {
        val trimmed = summary.trim().ifBlank { "子代理没有产生摘要。" }
        val clippedSummary = if (trimmed.length > SUBAGENT_SUMMARY_CHARS) {
            val room = (SUBAGENT_SUMMARY_CHARS - SUBAGENT_SUMMARY_TRUNCATED_MARK.length).coerceAtLeast(1)
            trimmed.take(room) + SUBAGENT_SUMMARY_TRUNCATED_MARK
        } else {
            trimmed
        }
        val run = SubAgentRun(
            status = status,
            steps = steps,
            summary = clippedSummary,
        )
        return listOf(
            UIMessagePart.Text(
                text = clippedSummary,
                metadata = buildJsonObject {
                    put(SUBAGENT_METADATA_KEY, json.encodeToJsonElement(run))
                },
            )
        )
    }
}

private const val FINAL_STEP_REMINDER =
    "这是最后一步，禁止再调用工具，立即根据已有信息输出最终报告。"

private fun summarize(
    steps: List<SubAgentStep>,
    fallback: String = "子代理没有产生摘要。",
): String {
    if (steps.isEmpty()) return fallback
    return buildString {
        append("已完成 ${steps.size} 步：")
        steps.forEach { step ->
            append("\n- ${step.toolName}: ${step.outputPreview.ifBlank { step.inputPreview }}")
        }
    }.take(SUBAGENT_SUMMARY_CHARS)
}

internal fun clip(parts: List<UIMessagePart>, maxChars: Int = SUBAGENT_TOOL_OUTPUT_CHARS): List<UIMessagePart> {
    val texts = parts.filterIsInstance<UIMessagePart.Text>()
    val rest = parts.filter { it !is UIMessagePart.Text }
    val joined = texts.joinToString("\n") { it.text }
    if (joined.length <= maxChars) return parts
    return listOf(UIMessagePart.Text(joined.take(maxChars) + "\n…[truncated]")) + rest
}
