package me.rerere.rikkahub.data.ai.subagent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider

/**
 * 子代理允许的工具名。名字白名单是必须的: needsApproval 的 lambda 在构造期没有真实入参,
 * 所以"白名单 + 对空 JsonObject 探测"是唯一稳妥的筛法。
 *
 * 名字里为什么有 shell: 子代理要能跑命令测试。这不等于它比父级权限大 —— 还有一道
 * "父级免确认"的筛, 见 filterSubAgentTools。
 */
private val SUBAGENT_TOOL_WHITELIST = setOf(
    "workspace_read_file",
    "workspace_ls",
    "workspace_grep",
    "workspace_shell",
    "search_web",
    "scrape_web",
)

/** 子代理工具 = 允许的名字集合 ∩ 本次 createTools 的实例 ∩ 父级免确认的工具。 */
fun filterSubAgentTools(all: List<Tool>): List<Tool> =
    all.filter { tool ->
        tool.name in SUBAGENT_TOOL_WHITELIST &&
            tool.name != SUBAGENT_TOOL_NAME &&
            !tool.needsApproval(JsonObject(emptyMap()))
    }

fun buildSubAgentTool(
    engine: SubAgentEngine,
    model: Model,
    settings: Settings,
    tools: List<Tool>,
    reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    unavailableReason: String? = null,
): Tool {
    val providerSetting = model.findProvider(settings.providers)
    return Tool(
        name = SUBAGENT_TOOL_NAME,
        description = """
            Dispatch an independent sub-agent for a multi-step investigation.
            The sub-agent cannot see this conversation. The `task` argument MUST be self-contained:
            include every path, question, constraint, and fact it needs.
            Use ONLY when the work needs multiple unknown-file reads or iterative web research.
            Do NOT use for a single known-path read, a simple rewrite, or a yes/no check.
            The sub-agent cannot modify files: it has no write or edit tools. It can read files, list
            directories, search file contents, search the web, and run commands when those tools are enabled.
            The sub-agent never sees this conversation, so the task must stand alone.
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("task", buildJsonObject {
                        put("type", "string")
                        put("description", "Self-contained task. Sub-agent has no chat history.")
                    })
                },
                required = listOf("task"),
            )
        },
        needsApproval = { false },
        execute = { args ->
            val task = args.jsonObject["task"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            engine.run(
                task = task,
                model = model,
                providerSetting = providerSetting,
                tools = tools,
                reasoningLevel = reasoningLevel,
                unavailableReason = unavailableReason,
            )
        },
    )
}
