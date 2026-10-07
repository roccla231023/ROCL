package me.rerere.rikkahub.data.rp.runtime

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import me.rerere.rikkahub.data.rp.model.RpStateChange
import me.rerere.rikkahub.data.rp.model.RpStateOperation

class RpStateReducer {
    fun apply(state: JsonObject, changes: List<RpStateChange>): JsonObject {
        var result = state
        changes.forEach { change ->
            require(isValidPath(change.path)) { "Invalid state path: ${change.path}" }
            result = when (change.operation) {
                RpStateOperation.SET -> set(result, change.path, change.value)
                RpStateOperation.APPEND -> append(result, change.path, change.value)
                RpStateOperation.REMOVE -> remove(result, change.path, change.value)
            }
        }
        return result
    }

    fun visibleState(state: JsonObject, hiddenPaths: Set<String>): JsonObject =
        hiddenPaths.fold(state) { current, path -> removePath(current, path) }

    private fun set(state: JsonObject, path: String, value: JsonElement): JsonObject =
        updateObject(state, path.split('.')) { value }

    private fun append(state: JsonObject, path: String, value: JsonElement): JsonObject =
        updateObject(state, path.split('.')) { current ->
            when (current) {
                is JsonArray -> JsonArray(current + value)
                null -> JsonArray(listOf(value))
                else -> error("Cannot append to non-array state path: $path")
            }
        }

    private fun remove(state: JsonObject, path: String, value: JsonElement): JsonObject {
        val current = readPath(state, path.split('.'))
        return when (current) {
            is JsonArray -> set(state, path, JsonArray(current.filterNot { it == value }))
            else -> removePath(state, path)
        }
    }

    private fun removePath(state: JsonObject, path: String): JsonObject {
        val parts = path.split('.')
        if (parts.size == 1) return JsonObject(state - path)
        val parent = readPath(state, parts.dropLast(1)) as? JsonObject ?: return state
        val updated = JsonObject(parent - parts.last())
        return set(state, parts.dropLast(1).joinToString("."), updated)
    }

    private fun updateObject(
        state: JsonObject,
        parts: List<String>,
        transform: (JsonElement?) -> JsonElement,
    ): JsonObject {
        val key = parts.first()
        if (parts.size == 1) {
            return JsonObject(state + (key to transform(state[key])))
        }
        val child = state[key] as? JsonObject ?: buildJsonObject { }
        val updated = updateObject(child, parts.drop(1), transform)
        return JsonObject(state + (key to updated))
    }

    private fun readPath(state: JsonElement, parts: List<String>): JsonElement? {
        var current: JsonElement? = state
        parts.forEach { part -> current = (current as? JsonObject)?.get(part) }
        return current
    }

    private fun isValidPath(path: String): Boolean =
        path.isNotBlank() && path.split('.').all { it.matches(PATH_SEGMENT) }

    private companion object {
        val PATH_SEGMENT = Regex("[A-Za-z0-9_-]+")
    }
}
