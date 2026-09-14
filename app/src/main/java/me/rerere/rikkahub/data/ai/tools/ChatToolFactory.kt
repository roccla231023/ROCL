package me.rerere.rikkahub.data.ai.tools

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.subagent.SubAgentEngine
import me.rerere.rikkahub.data.ai.subagent.buildSubAgentTool
import me.rerere.rikkahub.data.ai.subagent.filterSubAgentTools
import me.rerere.rikkahub.data.ai.tools.local.LocalTools
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.SessionMemory
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.workspace.WorkspaceShellStatus

private const val TAG = "ChatToolFactory"

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
private fun describeEmptySubAgentToolset(assistant: Assistant, chatModel: Model): String {
    val reasons = buildList {
        add(
            if (assistant.workspaceId == null) {
                "没有绑定工作区，因此没有文件类工具"
            } else {
                "工作区不可用（未找到，或 Rootfs 不是 READY），因此没有文件类工具"
            }
        )
        if (!assistant.enableWebSearch) {
            add("该助手没有开启联网搜索")
        } else if (BuiltInTools.Search in chatModel.tools) {
            add("聊天模型自带内置搜索，因此不会挂上外部搜索工具")
        }
    }
    return reasons.joinToString("；")
}

class InvalidMcpServerNamesException(val names: List<String>) :
    IllegalStateException("Invalid MCP server names: ${names.joinToString(", ")}")

/** Creates the complete tool set for one generation run, including approval resumption. */
class ChatToolFactory(
    private val json: Json,
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
        val assembled = buildList {
            if (assistant.enableMemory) {
                val memoryAssistantId = if (assistant.useGlobalMemory) {
                    MemoryRepository.GLOBAL_MEMORY_ID
                } else {
                    assistant.id.toString()
                }
                addAll(
                    buildMemoryTools(
                        json = json,
                        onCreation = { content ->
                            memoryRepository.addMemory(
                                assistantId = memoryAssistantId,
                                content = content,
                                embeddingAssistantId = assistant.id.toString(),
                            )
                        },
                        onUpdate = { id, content ->
                            memoryRepository.updateContent(
                                id = id,
                                content = content,
                                embeddingAssistantId = assistant.id.toString(),
                            )
                        },
                        onDelete = { id -> memoryRepository.deleteMemory(id) },
                    )
                )
            }
            if (shouldUseExternalWebSearch(assistant, model)) {
                addAll(createSearchTools(settings))
            }
            addAll(localTools.getTools(assistant.localTools))
            if (assistant.enableSessionMemory && onSessionMemoriesChanged != null) {
                addAll(
                    buildSessionMemoryTools(
                        json = json,
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
            addAll(createWorkspaceToolsIfReady(assistant.workspaceId?.toString(), workspaceCwd))
            if (assistant.enabledSkills.isNotEmpty()) {
                addAll(
                    createSkillTools(
                        enabledSkills = assistant.enabledSkills,
                        allSkills = skillManager.listSkills(),
                    )
                )
            }

            val mcpTools = mcpManager.getAllAvailableTools(assistant.mcpServers)
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
        // 门控：没在设置里选子代理模型 = 完全不暴露 dispatch_subagent（schema 都不挂）。
        // 不是「暴露了再报错」，是结构上不存在。
        val subAgentModelId = settings.subAgentModelId ?: return assembled
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
            return assembled
        }
        val subTools = filterSubAgentTools(assembled)
        return assembled + buildSubAgentTool(
            engine = subAgentEngine,
            model = subAgentModel,
            settings = settings,
            tools = subTools,
            reasoningLevel = settings.subAgentReasoningLevel,
            // 筛完之后一个工具都没有时, 仍然挂上 dispatch_subagent, 让它返回一条说明原因的失败摘要。
            // 宁可让主模型看到一个可读的失败, 也不要静默什么都不发生 (设计稿 §2.3 / §10.7)。
            unavailableReason = if (subTools.isEmpty()) {
                describeEmptySubAgentToolset(assistant = assistant, chatModel = model)
            } else {
                null
            },
        )
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
