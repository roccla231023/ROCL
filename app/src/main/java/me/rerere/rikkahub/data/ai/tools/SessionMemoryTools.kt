package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.SessionMemory
import me.rerere.rikkahub.data.model.SessionMemoryPlacement

object SessionMemoryStore {
    const val MAX_COUNT = 50
    const val MAX_CONTENT_CHARS = 3000

    fun create(
        current: List<SessionMemory>,
        content: String,
        placement: SessionMemoryPlacement,
        now: Long = System.currentTimeMillis(),
    ): SessionMemory {
        val normalized = content.trim()
        validateContent(normalized)
        val existing = current.firstOrNull { it.content.equals(normalized, ignoreCase = true) }
        if (existing != null) {
            return if (existing.placement == placement) {
                existing
            } else {
                existing.copy(placement = placement, updatedAt = now)
            }
        }
        if (current.size >= MAX_COUNT) {
            error("session memory limit reached; edit an existing memory instead")
        }
        return SessionMemory(
            id = nextId(current),
            content = normalized,
            createdAt = now,
            updatedAt = now,
            placement = placement,
        )
    }

    fun applyCreate(
        current: List<SessionMemory>,
        created: SessionMemory,
    ): List<SessionMemory> {
        return if (current.any { it.id == created.id }) {
            current.map { memory -> if (memory.id == created.id) created else memory }
        } else {
            current + created
        }
    }

    fun edit(
        current: List<SessionMemory>,
        id: Int,
        content: String,
        placement: SessionMemoryPlacement?,
        now: Long = System.currentTimeMillis(),
    ): SessionMemory {
        val normalized = content.trim()
        validateContent(normalized)
        val existing = current.firstOrNull { it.id == id } ?: error("session memory not found")
        return existing.copy(
            content = normalized,
            placement = placement ?: existing.placement,
            updatedAt = now,
        )
    }

    fun delete(current: List<SessionMemory>, id: Int): List<SessionMemory> {
        val updated = current.filterNot { it.id == id }
        if (updated.size == current.size) error("session memory not found")
        return updated
    }

    private fun nextId(current: List<SessionMemory>): Int =
        (current.maxOfOrNull { it.id } ?: 0) + 1

    private fun validateContent(content: String) {
        if (content.isBlank()) error("content must not be empty")
        if (content.length > MAX_CONTENT_CHARS) {
            error("content must be at most $MAX_CONTENT_CHARS characters")
        }
    }
}

fun buildSessionMemoryTools(
    json: Json,
    getMemories: () -> List<SessionMemory>,
    onChange: suspend (List<SessionMemory>) -> Unit,
): List<Tool> {
    val placementEnum = buildJsonArray {
        add(JsonPrimitive("SYSTEM_PROMPT_AFTER"))
        add(JsonPrimitive("BEFORE_LATEST_MESSAGE"))
    }
    return listOf(
        Tool(
            name = "create_session_memory",
            description = "Create a memory that stays active only in the current conversation.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("content", buildJsonObject {
                            put("type", "string")
                            put("description", "Important detail to keep active in the current conversation.")
                        })
                        put("placement", buildJsonObject {
                            put("type", "string")
                            put("enum", placementEnum)
                            put(
                                "description",
                                "Where this session memory should be injected. Use SYSTEM_PROMPT_AFTER only for stable memories that are long or rarely updated. Use BEFORE_LATEST_MESSAGE for short, changing, or uncertain memories.",
                            )
                        })
                    },
                    required = listOf("content", "placement"),
                )
            },
            systemPrompt = { _, _ -> SESSION_MEMORY_TOOL_PROMPT },
            execute = { args ->
                val params = args.jsonObject
                val content = params["content"]?.jsonPrimitive?.contentOrNull
                    ?: error("content is required")
                val placement = SessionMemoryPlacement.fromToolValue(
                    params["placement"]?.jsonPrimitive?.contentOrNull,
                )
                val current = getMemories()
                val created = SessionMemoryStore.create(current, content, placement)
                onChange(SessionMemoryStore.applyCreate(current, created))
                listOf(UIMessagePart.Text(json.encodeToJsonElement(SessionMemory.serializer(), created).toString()))
            },
        ),
        Tool(
            name = "edit_session_memory",
            description = "Update an existing memory that applies only to the current conversation.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("id", buildJsonObject {
                            put("type", "integer")
                            put("description", "ID of the session memory to update.")
                        })
                        put("content", buildJsonObject {
                            put("type", "string")
                            put("description", "New content for the session memory.")
                        })
                        put("placement", buildJsonObject {
                            put("type", "string")
                            put("enum", placementEnum)
                            put("description", "Optional new injection position. Omit this to keep the existing position.")
                        })
                    },
                    required = listOf("id", "content"),
                )
            },
            execute = { args ->
                val params = args.jsonObject
                val id = params["id"]?.jsonPrimitive?.intOrNull ?: error("id is required")
                val content = params["content"]?.jsonPrimitive?.contentOrNull
                    ?: error("content is required")
                val current = getMemories()
                val placement = params["placement"]?.jsonPrimitive?.contentOrNull
                    ?.let(SessionMemoryPlacement::fromToolValue)
                val updated = SessionMemoryStore.edit(current, id, content, placement)
                onChange(current.map { memory -> if (memory.id == id) updated else memory })
                listOf(UIMessagePart.Text(json.encodeToJsonElement(SessionMemory.serializer(), updated).toString()))
            },
        ),
        Tool(
            name = "delete_session_memory",
            description = "Delete a memory that no longer applies to the current conversation.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("id", buildJsonObject {
                            put("type", "integer")
                            put("description", "ID of the session memory to delete.")
                        })
                    },
                    required = listOf("id"),
                )
            },
            execute = { args ->
                val id = args.jsonObject["id"]?.jsonPrimitive?.intOrNull ?: error("id is required")
                val current = getMemories()
                onChange(SessionMemoryStore.delete(current, id))
                listOf(UIMessagePart.Text(JsonPrimitive(true).toString()))
            },
        ),
    )
}

private const val SESSION_MEMORY_TOOL_PROMPT = """
Session memories apply only to the current conversation.
Use create_session_memory / edit_session_memory / delete_session_memory to keep outlines, requirements, and other details that should not leak into other chats.
Place stable, long-lived notes at SYSTEM_PROMPT_AFTER and short or changing notes at BEFORE_LATEST_MESSAGE.
"""
