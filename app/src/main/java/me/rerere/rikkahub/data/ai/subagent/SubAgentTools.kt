package me.rerere.rikkahub.data.ai.subagent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider

private val READONLY_WHITELIST = setOf(
    "workspace_read_file",
    "search_web",
    "scrape_web",
)

fun filterSubAgentTools(all: List<Tool>): List<Tool> =
    all.filter { tool ->
        tool.name in READONLY_WHITELIST &&
            tool.name != SUBAGENT_TOOL_NAME &&
            !tool.needsApproval(JsonObject(emptyMap()))
    }

fun buildSubAgentTool(
    engine: SubAgentEngine,
    model: Model,
    settings: Settings,
    tools: List<Tool>,
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
            The sub-agent is read-only. It cannot write files or run shell.
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
            )
        },
    )
}
