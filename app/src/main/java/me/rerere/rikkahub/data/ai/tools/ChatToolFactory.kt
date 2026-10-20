package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.util.Log
import java.io.File
import kotlin.uuid.Uuid
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.subagent.SubAgentEngine
import me.rerere.rikkahub.data.ai.subagent.buildSubAgentTool
import me.rerere.rikkahub.data.ai.subagent.filterSubAgentTools
import me.rerere.rikkahub.data.ai.tools.local.LocalTools
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.SessionMemory
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.repository.memoryId
import me.rerere.rikkahub.data.skills.SkillManager
import me.rerere.workspace.WorkspaceShellStatus

private const val TAG = "ChatToolFactory"
private const val MAX_TOOL_OUTPUT_CHARS = 32 * 1024
private const val TOOL_OUTPUT_PREVIEW_CHARS = 4 * 1024

// 搜索结果的体积由用户设置的结果数决定，且结果列表 UI 与引用跳转都依赖完整的 JSON 结构，不参与截断
private val TOOLS_WITHOUT_OUTPUT_TRUNCATION = setOf("search_web")

internal fun shouldUseExternalWebSearch(assistant: Assistant, model: Model): Boolean {
    return assistant.enableWebSearch && BuiltInTools.Search !in model.tools
}

/**
 * 子代理被启用了、但这一轮一个工具都筛不出来时, 给出人能读懂的原因。
 *
 * 搜索工具是按「聊天模型 + 助手联网开关」挂的 (shouldUseExternalWebSearch), 所以这里看的是
 * chatModel 而不是子代理模型 —— 内置搜索是挂在模型上的 (BuiltInTools.Search), 模型自带内置搜索时
 * 外部 search_web 根本不会挂上。
 */
private fun describeEmptySubAgentToolset(assistant: Assistant, chatModel: Model, assembled: List<Tool>): String {
    val reasons = buildList {
        val workspaceTools = assembled.filter { it.name.startsWith("workspace_") }
        add(when {
            workspaceTools.isNotEmpty() -> "工作区工具需要审批，子代理无法请求审批"
            assistant.workspaceId == null -> "该助手或群聊座位没有绑定工作区"
            else -> "工作区未找到或 Rootfs 尚未就绪"
        })
        add(when {
            !assistant.enableWebSearch -> "该助手或群聊座位未开启联网搜索"
            BuiltInTools.Search in chatModel.tools -> "聊天模型使用内置搜索，子代理不能借用该内置工具"
            assembled.any { it.name == "search_web" } -> "搜索工具需要审批，子代理无法请求审批"
            else -> "所选搜索服务不可用"
        })
    }
    return reasons.joinToString("；")
}

class InvalidMcpServerNamesException(val names: List<String>) :
    IllegalStateException("Invalid MCP server names: ${names.joinToString(", ")}")

/** Creates the complete tool set for one generation run, including approval resumption. */
class ChatToolFactory(
    private val context: Context,
    private val memoryRepository: MemoryRepository,
    private val conversationRepository: ConversationRepository,
    private val localTools: LocalTools,
    private val mcpManager: McpManager,
    private val skillManager: SkillManager,
    private val workspaceRepository: WorkspaceRepository,
    private val subAgentEngine: SubAgentEngine,
) {
    suspend fun createTools(
        settings: Settings,
        assistant: Assistant,
        model: Model,
        workspaceCwd: String? = null,
        sessionMemories: List<SessionMemory> = emptyList(),
        onSessionMemoriesChanged: (suspend (List<SessionMemory>) -> Unit)? = null,
    ): List<Tool> {
        var currentSessionMemories = sessionMemories
        val workspaceTools = createWorkspaceToolsIfReady(assistant.workspaceId?.toString(), workspaceCwd)
        val assembled = buildList {
            if (assistant.enableMemory) {
                addAll(buildMemoryTools(memoryRepository, assistant.memoryId))
            }
            if (shouldUseExternalWebSearch(assistant, model)) {
                addAll(createSearchTools(settings, assistant.searchServiceId))
            }
            addAll(localTools.getTools(assistant.localTools))
            if (assistant.enableSessionMemory && onSessionMemoriesChanged != null) {
                addAll(
                    buildSessionMemoryTools(
                        json = me.rerere.rikkahub.utils.JsonInstant,
                        getMemories = { currentSessionMemories },
                        onChange = { updated ->
                            currentSessionMemories = updated
                            onSessionMemoriesChanged(updated)
                        },
                    )
                )
            }
            if (assistant.enableRecentChatsReference) {
                addAll(createConversationTools(conversationRepository, assistant.id))
            }
            addAll(workspaceTools)
            if (assistant.enabledSkills.isNotEmpty()) {
                addAll(
                    createSkillTools(
                        enabledSkills = assistant.enabledSkills,
                        allSkills = skillManager.listSkills(),
                    )
                )
            }

            val mcpTools = mcpManager.getAllAvailableTools(assistant)
            val invalidNames = mcpTools
                .map { it.second }
                .distinct()
                .filter { name -> name.isEmpty() || !name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' } }
            if (invalidNames.isNotEmpty()) {
                throw InvalidMcpServerNamesException(invalidNames)
            }
            mcpTools.forEach { (serverId, serverName, tool) ->
                add(
                    Tool(
                        name = "mcp__${serverName}__${tool.name}",
                        description = tool.description ?: "",
                        parameters = { tool.inputSchema },
                        needsApproval = { tool.needsApproval },
                        execute = { mcpManager.callTool(serverId, tool.name, it.jsonObject) },
                    )
                )
            }
        }
        val finalTools = if (workspaceTools.isEmpty()) assembled else assembled.map(::withOutputTruncation)
        // 门控：没在设置里选子代理模型 = 完全不暴露 dispatch_subagent（schema 都不挂）。
        // 不是「暴露了再报错」，是结构上不存在。
        val subAgentModelId = settings.subAgentModelId ?: return finalTools
        val subAgentModel = settings.providers.findModelById(subAgentModelId)
        // findModelById 不看 provider.enabled，只看 id。provider 被禁用时也要按未配置处理，
        // 否则会挂上一个调用时才失败的 dispatch_subagent。
        // checkOverwrite=false：带 providerOverwrite 的模型会返回一份 models 被清空的副本，
        // 那份副本上的 enabled 不能用来判断真实 provider。
        val subAgentProvider = subAgentModel?.findProvider(settings.providers, checkOverwrite = false)
            ?.takeIf { it.enabled }
        if (subAgentModel == null || subAgentProvider == null) {
            Log.w(
                TAG,
                "createTools: sub-agent model $subAgentModelId not usable, sub-agent disabled"
            )
            return finalTools
        }
        val subTools = filterSubAgentTools(assembled)
        return finalTools + buildSubAgentTool(
            engine = subAgentEngine,
            model = subAgentModel,
            settings = settings,
            tools = subTools,
            reasoningLevel = settings.subAgentReasoningLevel,
            // 筛完之后一个工具都没有时, 仍然挂上 dispatch_subagent, 让它返回一条说明原因的失败摘要。
            // 宁可让主模型看到一个可读的失败, 也不要静默什么都不发生 (设计稿 §2.3 / §10.7)。
            unavailableReason = if (subTools.isEmpty()) {
                describeEmptySubAgentToolset(assistant = assistant, chatModel = model, assembled = assembled)
            } else {
                null
            },
        )
    }

    private fun withOutputTruncation(tool: Tool): Tool {
        if (tool.name in TOOLS_WITHOUT_OUTPUT_TRUNCATION) return tool
        val execute = tool.execute
        return tool.copy(execute = { truncateOutput(execute(it)) })
    }

    private fun truncateOutput(output: List<UIMessagePart>): List<UIMessagePart> {
        val textParts = output.filterIsInstance<UIMessagePart.Text>()
        val totalChars = textParts.sumOf { it.text.length }
        if (totalChars <= MAX_TOOL_OUTPUT_CHARS) return output

        val fileName = "${Uuid.random()}.txt"
        Log.i(TAG, "truncateOutput: saving $totalChars chars to $fileName")
        val fullText = textParts.joinToString("\n") { it.text }
        val outputDir = File(context.filesDir, FileFolders.TOOL_OUTPUTS).apply { mkdirs() }
        File(outputDir, fileName).writeText(fullText)

        return listOf(
            UIMessagePart.Text(
                buildString {
                    appendLine("[Tool output truncated: $totalChars characters total]")
                    appendLine("Full output saved to: /tool_outputs/$fileName")
                    appendLine("Use shell to read: `cat /tool_outputs/$fileName`")
                    appendLine("Use shell to search: `grep \"pattern\" /tool_outputs/$fileName`")
                    appendLine()
                    append(fullText.take(TOOL_OUTPUT_PREVIEW_CHARS))
                }
            )
        ) + output.filter { it !is UIMessagePart.Text }
    }

    private suspend fun createWorkspaceToolsIfReady(workspaceId: String?, cwd: String?): List<Tool> {
        if (workspaceId.isNullOrBlank()) return emptyList()
        val workspace = workspaceRepository.getById(workspaceId) ?: return emptyList()
        if (workspace.shellStatus != WorkspaceShellStatus.READY.name) {
            Log.d(
                TAG,
                "createWorkspaceToolsIfReady: skip workspace tools, workspace=$workspaceId, status=${workspace.shellStatus}"
            )
            return emptyList()
        }
        return createWorkspaceTools(workspaceId, workspaceRepository, cwd)
    }
}
