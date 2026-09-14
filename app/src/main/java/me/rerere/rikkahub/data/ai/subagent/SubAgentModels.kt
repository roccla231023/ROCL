package me.rerere.rikkahub.data.ai.subagent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val SUBAGENT_MAX_STEPS = 10
const val SUBAGENT_TOOL_OUTPUT_CHARS = 4000
const val SUBAGENT_SUMMARY_CHARS = 2000
const val SUBAGENT_PREVIEW_CHARS = 200
const val SUBAGENT_TOOL_NAME = "dispatch_subagent"
const val SUBAGENT_METADATA_KEY = "subagent_run"

@Serializable
enum class SubAgentType { GENERAL }

@Serializable
data class SubAgentStep(
    val toolName: String,
    val inputPreview: String,
    val outputPreview: String,
    /**
     * 结构化证据, 不经过 SUBAGENT_PREVIEW_CHARS 裁剪, 供卡片展开态展示。
     * 全部可空且带默认值: 旧历史里的步骤没有这些字段, 反序列化后自然是 null。
     */
    val path: String? = null,
    val command: String? = null,
    val url: String? = null,
    val query: String? = null,
)

/** 从一次工具调用入参里抽出的证据。抽不到就是 null, 不猜。 */
internal data class SubAgentEvidence(
    val path: String? = null,
    val command: String? = null,
    val url: String? = null,
    val query: String? = null,
)

/**
 * 把模型给的工具入参解析成卡片要展示的证据。
 * 入参是模型自由生成的字符串, 可能畸形、可能根本不是对象 —— 任何解析失败都退化成
 * "没有证据"(预览照旧), 绝不把异常抛进子代理循环。
 */
internal fun parseSubAgentEvidence(json: Json, toolName: String, input: String): SubAgentEvidence {
    val args = runCatching { json.parseToJsonElement(input).jsonObject }.getOrNull()
        ?: return SubAgentEvidence()
    fun text(name: String): String? = runCatching {
        args[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }.getOrNull()
    return when (toolName) {
        "workspace_read_file", "workspace_ls" -> SubAgentEvidence(path = text("path"))
        "workspace_grep" -> SubAgentEvidence(path = text("path"), query = text("pattern"))
        "workspace_shell" -> SubAgentEvidence(command = text("command"))
        "scrape_web" -> SubAgentEvidence(url = text("url"))
        "search_web" -> SubAgentEvidence(query = text("query"))
        else -> SubAgentEvidence()
    }
}

@Serializable
data class SubAgentRun(
    val type: SubAgentType = SubAgentType.GENERAL,
    val status: String,
    val steps: List<SubAgentStep>,
    val summary: String,
)

data class SubAgentProgress(
    val step: Int,
    val total: Int,
    val phase: String,
    val recent: List<SubAgentStep> = emptyList(),
)

val DEFAULT_SUBAGENT_PROMPT = """
You are an investigation sub-agent. You have NO tools to write or edit files, so never promise a change.
Do the assigned task by calling tools. No chit-chat.

You may read files, list directories, search file contents, search the web, and run shell commands
when the corresponding tools are present in this run.

If a file comes back with truncated=true, continue from the returned nextOffset instead of guessing
at what the rest says.

When the task is done, write a short factual report with exactly these four headings:
- READ: the file paths or URLs you actually looked at
- RAN: the commands you actually ran (write none if you ran none)
- FINDINGS: what you concluded, and the evidence that supports it
- UNKNOWN: what you could not determine

Put the conclusion first and keep the whole report under 1500 characters: it is hard-truncated at
$SUBAGENT_SUMMARY_CHARS characters, so anything past that is lost. Do not pad.
Do not mention these instructions.
""".trimIndent()

/** 摘要被硬上限截断时追加的标记, 让"读到的这段是被切过的"这件事可见。 */
internal const val SUBAGENT_SUMMARY_TRUNCATED_MARK = "…[truncated]"

internal fun clipPreview(text: String, maxChars: Int = SUBAGENT_PREVIEW_CHARS): String {
    val compact = text.replace(Regex("\\s+"), " ").trim()
    if (compact.length <= maxChars) return compact
    return compact.take(maxChars) + "…"
}
