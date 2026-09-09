package me.rerere.rikkahub.ui.components.message.tools

import kotlin.uuid.Uuid

private val INLINE_WORKSPACE_READ_EXTENSIONS = setOf("md", "markdown", "txt")

fun shouldInlineWorkspaceRead(
    enabled: Boolean,
    allowlist: Set<Uuid>,
    assistantId: Uuid?,
    path: String?,
): Boolean {
    if (!enabled || assistantId == null || assistantId !in allowlist) return false
    val extension = path?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase()
        .orEmpty()
    return extension in INLINE_WORKSPACE_READ_EXTENSIONS
}
