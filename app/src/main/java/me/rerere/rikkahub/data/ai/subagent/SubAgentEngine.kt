package me.rerere.rikkahub.data.ai.subagent

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
                // 单独的状态: UI 要把它跟"跑了但失败了"分开显示
                status = "unavailable",
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
                    phase = "thinking",
                    steps = steps.toList(),
                    pendingToolName = null,
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
                        phase = "calling",
                        steps = steps.toList(),
                        pendingToolName = call.toolName,
                    )
                }
                var toolFailure: String? = null
                val rawOutput = try {
                    val def = tools.find { it.name == call.toolName }
                        ?: error("Tool ${call.toolName} not found")
                    def.execute(call.inputAsJson())
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    toolFailure = error.message ?: error::class.java.simpleName
                    listOf(
                        UIMessagePart.Text(
                            "工具执行失败：$toolFailure",
                        )
                    )
                }
                // 结果路径从**未裁剪**的输出里抽, 免得路径落在 clip 之外
                val rawText = rawOutput.filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n") { it.text }
                val clipped = clip(rawOutput, SUBAGENT_TOOL_OUTPUT_CHARS, json)
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
                    // 请求证据来自入参; 执行事实来自返回。两者分开记, 卡片也分开显示。
                    success = toolFailure == null,
                    error = toolFailure?.take(SUBAGENT_ERROR_CHARS),
                    resultPaths = if (toolFailure == null) {
                        parseSubAgentResultPaths(json, call.toolName, rawText)
                    } else {
                        emptyList()
                    },
                )
                registry.update {
                    it.copy(
                        steps = steps.toList(),
                        pendingToolName = null,
                    )
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
        // metadata.summary 只留模型写的, 给卡片当「报告」.
        // 进主上下文的 text 在 completed/aborted 前面加一行引擎摘录, 免得父模型只信那篇可能编的报告.
        val parentText = if (status == "completed" || status == "aborted") {
            formatEngineLedger(steps) + "\n\n" + clippedSummary
        } else {
            clippedSummary
        }
        val run = SubAgentRun(
            status = status,
            steps = steps,
            summary = clippedSummary,
        )
        return listOf(
            UIMessagePart.Text(
                text = parentText,
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

private const val CLIP_NOTE_KEY = "clipNote"

/** 给 clipNote 预留的字符数; 控制字段和这条说明都不占载荷预算 */
private const val CLIP_NOTE_MAX_CHARS = 260

/** 截断说明末尾的续读引导: 模型该换个更窄的请求, 而不是猜剩下的内容 */
private const val CLIP_NOTE_GUIDANCE =
    ". Narrow the request (grep / head / tail -n) to read the rest."

private const val CLIP_MIN_PAYLOAD_CHARS = 200

private const val CLIP_TRUNCATED_SUFFIX = "\n…[truncated]"

/** 只有这些字段允许被裁。其余一切 (truncated / nextOffset / hint / count / exitCode / timedOut / ...) 整体保留。 */
private val CLIP_PAYLOAD_STRING_KEYS = listOf("text", "stdout", "stderr")

private val CLIP_PAYLOAD_ARRAY_KEYS = listOf("matches", "entries")

/**
 * 内层保险丝。
 *
 * 工具返回的 Text 通常是 JSON, 直接按字符砍会砍出**非法 JSON** —— 更糟的是可能正好砍掉
 * truncated / nextOffset / hint, 而那正是"还能往后读"的协议本身, 等于毁掉续读能力。
 *
 * 所以: 能解析成 JSON 对象时, 控制字段整体保留、只按预算裁载荷, 再重新序列化成合法 JSON;
 * 解析不出来 (散文 / 纯文本) 才退回原样截断。
 */
internal fun clip(
    parts: List<UIMessagePart>,
    maxChars: Int = SUBAGENT_TOOL_OUTPUT_CHARS,
    json: Json = Json,
): List<UIMessagePart> {
    val texts = parts.filterIsInstance<UIMessagePart.Text>()
    val rest = parts.filter { it !is UIMessagePart.Text }
    val joined = texts.joinToString("\n") { it.text }
    if (joined.length <= maxChars) return parts
    val clippedJoined = texts.joinToString("\n") { it.text.clipToolText(json, maxChars) }
    return listOf(UIMessagePart.Text(clippedJoined)) + rest
}

private fun String.clipToolText(json: Json, budget: Int): String {
    if (length <= budget) return this
    val obj = runCatching { json.parseToJsonElement(this) as? JsonObject }.getOrNull()
        ?: return take(budget) + CLIP_TRUNCATED_SUFFIX
    val clipped = runCatching { obj.clipJsonObject(json, budget) }.getOrNull()
        ?: return take(budget) + CLIP_TRUNCATED_SUFFIX
    return runCatching { json.encodeToString(JsonObject.serializer(), clipped) }
        .getOrElse { take(budget) + CLIP_TRUNCATED_SUFFIX }
}

private fun JsonObject.clipJsonObject(json: Json, budget: Int): JsonObject {
    val values = this.toMutableMap()
    // 空载荷不占 slot。shell 结果永远带 stdout 与 stderr 两个字段, 失败时 stderr 是空串,
    // 照样给它一份预算的话, 真有内容的 stdout 只能拿到一半 (4000 预算 → 约 1916)。
    val stringKeys = CLIP_PAYLOAD_STRING_KEYS.filter { key ->
        val value = values[key] as? JsonPrimitive
        value?.isString == true && value.content.isNotEmpty()
    }
    val arrayKeys = CLIP_PAYLOAD_ARRAY_KEYS.filter { key ->
        (values[key] as? JsonArray)?.isNotEmpty() == true
    }
    val slots = stringKeys.size + arrayKeys.size
    if (slots == 0) return this

    // 骨架 = 去掉全部载荷后的序列化长度。控制字段不占预算, 这是这次修复的重点。
    val skeleton = JsonObject(values.filterKeys { it !in stringKeys && it !in arrayKeys })
    val skeletonSize = json.encodeToString(JsonObject.serializer(), skeleton).length
    val available = (budget - skeletonSize - CLIP_NOTE_MAX_CHARS)
        .coerceAtLeast(slots * CLIP_MIN_PAYLOAD_CHARS)
    val share = (available / slots).coerceAtLeast(CLIP_MIN_PAYLOAD_CHARS)

    val notes = mutableListOf<String>()

    stringKeys.forEach { key ->
        val value = (values[key] as? JsonPrimitive)?.content ?: return@forEach
        if (value.length > share) {
            values[key] = JsonPrimitive(value.take(share) + "…")
            notes += "$key trimmed to $share chars"
        }
    }

    arrayKeys.forEach { key ->
        val array = values[key] as? JsonArray ?: return@forEach
        val kept = mutableListOf<JsonElement>()
        var used = 0
        for (element in array) {
            val size = json.encodeToString(JsonElement.serializer(), element).length + 1
            if (used + size > share) break
            kept += element
            used += size
        }
        if (kept.size < array.size) {
            values[key] = JsonArray(kept)
            if (values.containsKey("count")) values["count"] = JsonPrimitive(kept.size)
            notes += "$key trimmed to ${kept.size} of ${array.size} items"
        }
    }

    if (notes.isEmpty()) return this

    values["truncated"] = JsonPrimitive(true)
    values[CLIP_NOTE_KEY] = JsonPrimitive(
        "sub-agent engine clipped this result to fit its budget: ${notes.joinToString("; ")}$CLIP_NOTE_GUIDANCE"
    )
    return JsonObject(values)
}
