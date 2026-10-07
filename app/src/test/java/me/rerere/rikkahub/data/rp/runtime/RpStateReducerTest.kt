package me.rerere.rikkahub.data.rp.runtime

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.rp.model.RpStateChange
import me.rerere.rikkahub.data.rp.model.RpStateOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RpStateReducerTest {
    private val reducer = RpStateReducer()
    private val initial = buildJsonObject {
        put("player", buildJsonObject {
            put("location", "home")
            put("items", buildJsonObject { put("food", kotlinx.serialization.json.buildJsonArray { }) })
        })
        put("secret", "unknown")
    }

    @Test
    fun appliesNestedSetAndPreservesUnchangedState() {
        val updated = reducer.apply(
            initial,
            listOf(RpStateChange("player.location", RpStateOperation.SET, JsonPrimitive("kitchen"))),
        )

        assertEquals("kitchen", updated["player"]!!.jsonObject["location"]!!.jsonPrimitive.content)
        assertEquals("unknown", updated["secret"]!!.jsonPrimitive.content)
    }

    @Test
    fun appendsAndRemovesArrayValues() {
        val withItem = reducer.apply(
            initial,
            listOf(RpStateChange("player.items.food", RpStateOperation.APPEND, JsonPrimitive("bread"))),
        )
        assertEquals("bread", withItem["player"]!!.jsonObject["items"]!!.jsonObject["food"]!!.jsonArray.single().jsonPrimitive.content)

        val removed = reducer.apply(
            withItem,
            listOf(RpStateChange("player.items.food", RpStateOperation.REMOVE, JsonPrimitive("bread"))),
        )
        assertTrue(removed["player"]!!.jsonObject["items"]!!.jsonObject["food"]!!.jsonArray.isEmpty())
    }

    @Test
    fun hiddenPathsAreExcludedWithoutChangingCanonicalState() {
        val visible = reducer.visibleState(initial, setOf("secret"))

        assertFalse("secret" in visible)
        assertTrue("secret" in initial)
    }

    @Test
    fun rejectsPathTraversalAndInvalidSegments() {
        assertThrows(IllegalArgumentException::class.java) {
            reducer.apply(initial, listOf(RpStateChange("player..location", RpStateOperation.SET, JsonPrimitive("x"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            reducer.apply(initial, listOf(RpStateChange("player.secret/value", RpStateOperation.SET, JsonPrimitive("x"))))
        }
    }
}
