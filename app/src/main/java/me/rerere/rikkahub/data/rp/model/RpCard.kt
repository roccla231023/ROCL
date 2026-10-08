package me.rerere.rikkahub.data.rp.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.uuid.Uuid

@Serializable
enum class RpPlayMode {
    CHARACTER,
    SIMULATION,
    COLLABORATIVE,
    MYSTERY,
    TABLETOP,
    CUSTOM,
}

@Serializable
enum class RpModelRole {
    NARRATOR,
    ADJUDICATOR,
    REVIEWER,
    STATE_KEEPER,
    MEMORY_CURATOR,
}

@Serializable
data class RpModelBindings(
    val narratorModelId: Uuid? = null,
    val adjudicatorModelId: Uuid? = null,
    val reviewerModelId: Uuid? = null,
    val stateKeeperModelId: Uuid? = null,
    val memoryCuratorModelId: Uuid? = null,
)

@Serializable
data class RpViewField(
    val path: String,
    val label: String,
)

@Serializable
data class RpParticipant(
    val id: Uuid = Uuid.random(),
    val name: String,
    val role: String = "",
    val prompt: String = "",
    val visible: Boolean = true,
)

@Serializable
data class RpCard(
    val id: Uuid = Uuid.random(),
    val version: Int = 1,
    val name: String = "",
    val description: String = "",
    val genre: String = "",
    val mode: RpPlayMode = RpPlayMode.SIMULATION,
    val worldPrompt: String = "",
    val rulesPrompt: String = "",
    val narrativeStyle: String = "",
    val initialState: JsonObject = defaultInitialState(),
    val viewFields: List<RpViewField> = defaultViewFields(),
    val hiddenStatePaths: Set<String> = emptySet(),
    val participants: List<RpParticipant> = emptyList(),
    val modelBindings: RpModelBindings = RpModelBindings(),
    val stateSchema: RpStateSchema = defaultStateSchema(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

fun defaultInitialState(): JsonObject = buildJsonObject {
    put("scene", "")
    put("location", "")
    put("time", "")
}

fun defaultViewFields(): List<RpViewField> = listOf(
    RpViewField("scene", "场景"),
    RpViewField("location", "地点"),
    RpViewField("time", "时间"),
)

fun RpCard.displayModeName(): String = when (mode) {
    RpPlayMode.CHARACTER -> "角色互动"
    RpPlayMode.SIMULATION -> "沙盒模拟"
    RpPlayMode.COLLABORATIVE -> "共同创作"
    RpPlayMode.MYSTERY -> "悬疑推理"
    RpPlayMode.TABLETOP -> "规则跑团"
    RpPlayMode.CUSTOM -> "自定义"
}

fun RpCard.hasAdjudication(): Boolean = mode != RpPlayMode.CHARACTER && mode != RpPlayMode.COLLABORATIVE

fun JsonElement.asDisplayText(): String = when (this) {
    is JsonPrimitive -> content
    else -> toString()
}
