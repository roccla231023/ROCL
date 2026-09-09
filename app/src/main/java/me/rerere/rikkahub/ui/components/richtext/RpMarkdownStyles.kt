package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import me.rerere.rikkahub.data.datastore.RpStyleRule
import me.rerere.rikkahub.data.datastore.parseColor

private val CUSTOM_SKIP = setOf("*", "**", "***", "~~", "`", "#", "##", "###", "####", "#####", "######", ">")

private data class PatternSpan(
    val start: Int,
    val end: Int,
    val innerStart: Int,
    val innerEnd: Int,
    val color: Color,
)

internal fun AnnotatedString.Builder.appendTextWithRpRules(text: String, rules: List<RpStyleRule>) {
    val enabled = rules.filter { it.enabled && it.pattern.isNotBlank() && it.pattern !in CUSTOM_SKIP }
    if (enabled.isEmpty()) {
        append(text)
        return
    }
    val spans = mutableListOf<PatternSpan>()
    enabled.forEach { rule ->
        val color = rule.parseColor() ?: return@forEach
        val regex = wrappingRegex(rule.pattern)
        regex.findAll(text).forEach { match ->
            val inner = match.groups[1] ?: return@forEach
            spans += PatternSpan(
                start = match.range.first,
                end = match.range.last + 1,
                innerStart = inner.range.first,
                innerEnd = inner.range.last + 1,
                color = color,
            )
        }
    }
    if (spans.isEmpty()) {
        append(text)
        return
    }
    spans.sortBy { it.start }
    val used = BooleanArray(text.length)
    val accepted = mutableListOf<PatternSpan>()
    for (span in spans) {
        if ((span.start until span.end).any { used[it] }) continue
        for (i in span.start until span.end) used[i] = true
        accepted += span
    }
    var cursor = 0
    accepted.sortedBy { it.start }.forEach { span ->
        if (cursor < span.start) append(text.substring(cursor, span.start))
        append(text.substring(span.start, span.innerStart))
        pushStyle(SpanStyle(color = span.color))
        append(text.substring(span.innerStart, span.innerEnd))
        pop()
        append(text.substring(span.innerEnd, span.end))
        cursor = span.end
    }
    if (cursor < text.length) append(text.substring(cursor))
}

private fun wrappingRegex(pattern: String): Regex {
    val escaped = Regex.escape(pattern)
    val flags = setOf(RegexOption.DOT_MATCHES_ALL)
    return if (pattern.length >= 2 && pattern.all { it == pattern.first() }) {
        val ch = Regex.escape(pattern.first().toString())
        Regex("(?<!$ch)$escaped(.+?)$escaped(?!$ch)", flags)
    } else {
        Regex("$escaped(.+?)$escaped", flags)
    }
}
