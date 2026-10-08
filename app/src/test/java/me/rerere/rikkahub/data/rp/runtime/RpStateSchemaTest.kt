package me.rerere.rikkahub.data.rp.runtime

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.rp.model.RpCard
import me.rerere.rikkahub.data.rp.model.RpStateChange
import me.rerere.rikkahub.data.rp.model.RpStateField
import me.rerere.rikkahub.data.rp.model.RpStateFieldType
import me.rerere.rikkahub.data.rp.model.RpStateOperation
import me.rerere.rikkahub.data.rp.model.RpStateSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RpStateSchemaTest {
    @Test
    fun schemaAllowsDeclaredPathsAndRangeBoundValues() {
        val schema = RpStateSchema(
            fields = listOf(
                RpStateField("player.health", "生命", RpStateFieldType.PROGRESS, min = 0.0, max = 100.0),
            ),
        )
        val state = buildJsonObject { put("player", buildJsonObject { put("health", 80) }) }

        val updated = RpStateReducer().apply(
            state,
            listOf(RpStateChange("player.health", RpStateOperation.SET, JsonPrimitive(65))),
            schema,
        )

        assertEquals(
            "65",
            updated["player"]!!.jsonObject["health"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun schemaRejectsUndeclaredPathAndOutOfRangeValue() {
        val schema = RpStateSchema(
            fields = listOf(
                RpStateField("player.health", "生命", RpStateFieldType.PROGRESS, min = 0.0, max = 100.0),
            ),
        )
        val state = buildJsonObject { put("player", buildJsonObject { put("health", 80) }) }
        val reducer = RpStateReducer()

        assertThrows(IllegalArgumentException::class.java) {
            reducer.apply(state, listOf(RpStateChange("player.secret", RpStateOperation.SET, JsonPrimitive("x"))), schema)
        }
        assertThrows(IllegalArgumentException::class.java) {
            reducer.apply(state, listOf(RpStateChange("player.health", RpStateOperation.SET, JsonPrimitive(120))), schema)
        }
    }
}
