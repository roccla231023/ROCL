package me.rerere.rikkahub.data.rp.runtime

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.rp.model.RpStateChange
import me.rerere.rikkahub.data.rp.model.RpStateField
import me.rerere.rikkahub.data.rp.model.RpStateFieldType
import me.rerere.rikkahub.data.rp.model.RpStateOperation
import me.rerere.rikkahub.data.rp.model.RpStateSchema
import me.rerere.rikkahub.data.rp.model.field
import me.rerere.rikkahub.data.rp.model.isDeclared

class RpStateReducer {
    fun apply(state: JsonObject, changes: List<RpStateChange>, schema: RpStateSchema = RpStateSchema()): JsonObject {
        var result = state
        changes.forEach { change ->
            require(isValidPath(change.path)) { "Invalid state path: ${change.path}" }
            val field = schema.field(change.path)
            require(schema.isDeclared(change.path)) { "State path is not declared: ${change.path}" }
            validateValue(field, change.value, change.path)
            result = when (change.operation) {
                RpStateOperation.SET -> set(result, change.path, change.value)
                RpStateOperation.APPEND -> append(result, change.path, change.value)
                RpStateOperation.REMOVE -> remove(result, change.path, change.value)
                RpStateOperation.INCREMENT -> adjustNumber(result, change.path, change.value, 1.0)
                RpStateOperation.DECREMENT -> adjustNumber(result, change.path, change.value, -1.0)
            }
            validateStoredValue(result, field, change.path)
        }
        return result
    }

    fun visibleState(
        state: JsonObject,
        hiddenPaths: Set<String>,
        schema: RpStateSchema = RpStateSchema(),
    ): JsonObject {
        val projected = if (schema.fields.isEmpty()) state else project(state, schema.fields.filter { it.visible }.map { it.path })
        return hiddenPaths.fold(projected) { current, path -> removePath(current, path) }
    }

    private fun set(state: JsonObject, path: String, value: JsonElement): JsonObject =
        updateObject(state, path.split('.')) { value }

    private fun append(state: JsonObject, path: String, value: JsonElement): JsonObject =
        updateObject(state, path.split('.')) { current ->
            when (current) {
                is JsonArray -> JsonArray(current + value)
                null, JsonNull -> JsonArray(listOf(value))
                else -> error("Cannot append to non-array state path: $path")
            }
        }

    private fun adjustNumber(state: JsonObject, path: String, value: JsonElement, direction: Double): JsonObject {
        val amount = value.jsonPrimitive.content.toDoubleOrNull()
            ?: error("Numeric state change requires a number: $path")
        val current = readPath(state, path.split('.'))?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        val updated = current + amount * direction
        return set(state, path, JsonPrimitive(if (updated % 1.0 == 0.0) updated.toInt() else updated))
    }

    private fun remove(state: JsonObject, path: String, value: JsonElement): JsonObject {
        val current = readPath(state, path.split('.'))
        return when (current) {
            is JsonArray -> set(state, path, JsonArray(current.filterNot { it == value }))
            else -> removePath(state, path)
        }
    }

    private fun project(state: JsonObject, paths: List<String>): JsonObject {
        var result = buildJsonObject { }
        paths.forEach { path ->
            readPath(state, path.split('.'))?.let { result = set(result, path, it) }
        }
        return result
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
        if (parts.size == 1) return JsonObject(state + (key to transform(state[key])))
        val child = state[key] as? JsonObject ?: buildJsonObject { }
        val updated = updateObject(child, parts.drop(1), transform)
        return JsonObject(state + (key to updated))
    }

    private fun readPath(state: JsonElement, parts: List<String>): JsonElement? {
        var current: JsonElement? = state
        parts.forEach { part -> current = (current as? JsonObject)?.get(part) }
        return current
    }

    private fun validateValue(field: RpStateField?, value: JsonElement, path: String) {
        if (field == null || value is JsonNull) return
        if (field.type == RpStateFieldType.PROGRESS || field.min != null || field.max != null) {
            require(value.jsonPrimitive.content.toDoubleOrNull() != null) { "State value must be numeric: $path" }
        }
    }

    private fun validateStoredValue(state: JsonObject, field: RpStateField?, path: String) {
        if (field == null) return
        val value = readPath(state, path.split('.'))?.jsonPrimitive?.content?.toDoubleOrNull() ?: return
        field.min?.let { require(value >= it) { "State value is below minimum: $path" } }
        field.max?.let { require(value <= it) { "State value is above maximum: $path" } }
    }

    private companion object {
        val PATH_SEGMENT = Regex("[A-Za-z0-9_-]+")
    }

    private fun isValidPath(path: String): Boolean =
        path.isNotBlank() && path.split('.').all { it.matches(PATH_SEGMENT) }
}
