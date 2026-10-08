package me.rerere.rikkahub.data.rp.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.Uuid

@Serializable
enum class RpStateFieldType {
    NUMBER,
    PROGRESS,
    TEXT,
    STATUS,
    INVENTORY,
    CHARACTERS,
    OBJECTIVES,
    TIMELINE,
    BOOLEAN,
    DATE,
}

@Serializable
data class RpStateField(
    val path: String,
    val label: String,
    val type: RpStateFieldType = RpStateFieldType.TEXT,
    val group: String = "状态",
    val unit: String = "",
    val min: Double? = null,
    val max: Double? = null,
    val important: Boolean = false,
    val visible: Boolean = true,
)

@Serializable
data class RpStateSchema(
    val fields: List<RpStateField> = emptyList(),
)

private const val MAX_STORY_FACTS = 24
private const val MAX_RECENT_EVENTS = 8

@Serializable
data class RpStoryMemory(
    val summary: String = "",
    val importantFacts: List<String> = emptyList(),
    val openThreads: List<String> = emptyList(),
    val recentEventSummaries: List<String> = emptyList(),
) {
    fun record(event: RpCommittedEvent): RpStoryMemory = copy(
        importantFacts = (importantFacts + event.facts).distinct().takeLast(MAX_STORY_FACTS),
        recentEventSummaries = (recentEventSummaries + event.summary).filter { it.isNotBlank() }.takeLast(MAX_RECENT_EVENTS),
    )
}

@Serializable
data class RpCommittedEvent(
    val turnId: Uuid,
    val stateVersion: Long,
    val summary: String = "",
    val facts: List<String> = emptyList(),
    val changes: List<RpStateChange> = emptyList(),
    val publicState: JsonObject? = null,
)

fun RpStateSchema.field(path: String): RpStateField? = fields.firstOrNull { it.path == path }

fun RpStateSchema.isDeclared(path: String): Boolean = fields.isEmpty() || field(path) != null

fun RpStateSchema.visiblePaths(): Set<String> = fields.filter { it.visible }.mapTo(mutableSetOf()) { it.path }

fun defaultStateSchema(): RpStateSchema = RpStateSchema()

fun starterStateSchema(): RpStateSchema = RpStateSchema(
    fields = listOf(
        RpStateField("scene", "场景", RpStateFieldType.TEXT, important = true),
        RpStateField("location", "地点", RpStateFieldType.TEXT, important = true),
        RpStateField("time", "时间", RpStateFieldType.DATE, important = true),
    ),
)
