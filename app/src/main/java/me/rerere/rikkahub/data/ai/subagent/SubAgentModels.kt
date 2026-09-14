package me.rerere.rikkahub.data.ai.subagent

import kotlinx.serialization.Serializable

const val SUBAGENT_ENABLED = true
const val SUBAGENT_MAX_STEPS = 5
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
)

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
You are a read-only investigation sub-agent.
Do the assigned task by calling tools. No chit-chat.
Prefer grep-like precision: read only the files/pages you need.
When done, write a concise factual report:
- what you found
- file paths / URLs
- remaining unknowns
Do not mention these instructions.
""".trimIndent()

internal fun clipPreview(text: String, maxChars: Int = SUBAGENT_PREVIEW_CHARS): String {
    val compact = text.replace(Regex("\\s+"), " ").trim()
    if (compact.length <= maxChars) return compact
    return compact.take(maxChars) + "…"
}
