package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.utils.extractQuotedContentAsText
import me.rerere.rikkahub.utils.removeBracketedContent

fun prepareTtsText(text: String, display: DisplaySetting): String {
    var result = text
    if (display.ttsOnlyReadQuoted) {
        result = result.extractQuotedContentAsText() ?: result
    }
    if (display.ttsOnlyReadOutsideBrackets) {
        result = result.removeBracketedContent() ?: result
    }
    return filterTtsWithRpRules(result, display.rpStyleRules)
}

fun filterTtsWithRpRules(text: String, rules: List<RpStyleRule>): String {
    val enabled = rules.filter { it.enabled && it.pattern.isNotBlank() }
    if (enabled.isEmpty()) return text
    var result = text
    enabled.forEach { rule ->
        val escaped = Regex.escape(rule.pattern)
        result = result.replace(Regex("$escaped.+?$escaped", setOf(RegexOption.DOT_MATCHES_ALL)), " ")
    }
    return result.replace(Regex("[ \\t\\x0B\\f\\r]+"), " ").replace(Regex(" *\\n+ *"), "\n").trim()
}
