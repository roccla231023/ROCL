package me.rerere.rikkahub.data.datastore

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class RpStyleRule(
    val id: String = Uuid.random().toString(),
    val pattern: String = "*",
    val colorHex: String = "#808080",
    val enabled: Boolean = true,
)

fun RpStyleRule.parseColor(): Color? {
    return runCatching { Color(android.graphics.Color.parseColor(colorHex)) }.getOrNull()
}

internal fun colorForRpPattern(rules: List<RpStyleRule>, pattern: String): Color? {
    val rule = rules.firstOrNull { it.enabled && it.pattern == pattern } ?: return null
    return rule.parseColor()
}
