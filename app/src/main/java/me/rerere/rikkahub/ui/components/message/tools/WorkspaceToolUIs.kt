package me.rerere.rikkahub.ui.components.message.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import me.rerere.ai.ui.DiffMetadata
import me.rerere.ai.ui.metadataAs
import me.rerere.common.http.jsonObjectOrNull
import me.rerere.highlight.CodeHighlightText
import androidx.compose.ui.res.stringResource
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.hugeicons.stroke.FileAdd
import me.rerere.hugeicons.stroke.FileEdit
import me.rerere.hugeicons.stroke.FileView
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Folder02
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.richtext.DiffAddedColor
import me.rerere.rikkahub.ui.components.richtext.DiffRemovedColor
import me.rerere.rikkahub.ui.components.richtext.DiffView
import me.rerere.rikkahub.ui.components.richtext.HighlightCodeBlock
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.richtext.parseDiffStats
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.modifier.shimmer
import me.rerere.rikkahub.utils.generateUnifiedDiff
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull

/**
 * 工作空间编辑文件: 摘要显示增删统计与精简 diff, 详情为完整 diff view
 */
object EditFileToolUI : ToolUIRenderer {
    private const val SUMMARY_MAX_LINES = 10

    override val toolName: String = "workspace_edit_file"

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.FileEdit

    @Composable
    override fun title(context: ToolUIContext): String {
        val path = context.arguments.getStringContent("path")
        return if (path != null) stringResource(R.string.tool_ui_edit_file, path) else stringResource(R.string.tool_ui_edit_file_default)
    }

    /**
     * 执行后读取输出部件 metadata 中的全文件 diff;
     * 未执行 (如等待审批) 时基于入参的 old_text/new_text 片段生成预览 diff
     */
    private fun diffOf(context: ToolUIContext): String? {
        if (context.tool.isExecuted) {
            return context.tool.output.firstOrNull()?.metadataAs<DiffMetadata>()?.diff
        }
        val path = context.arguments.getStringContent("path") ?: return null
        val oldText = context.arguments.getStringContent("old_text") ?: return null
        val newText = context.arguments.getStringContent("new_text") ?: return null
        return generateUnifiedDiff(oldText, newText, path)
    }

    override fun hasSummary(context: ToolUIContext): Boolean = diffOf(context) != null

    @Composable
    override fun Summary(context: ToolUIContext) {
        val diff = remember(context) { diffOf(context) } ?: return
        val stats = remember(diff) { parseDiffStats(diff) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "+${stats.additions}",
                style = MaterialTheme.typography.labelSmall,
                color = DiffAddedColor,
            )
            Text(
                text = "-${stats.deletions}",
                style = MaterialTheme.typography.labelSmall,
                color = DiffRemovedColor,
            )
        }
        DiffView(
            diff = diff,
            modifier = Modifier.fillMaxWidth(),
            maxLines = SUMMARY_MAX_LINES,
            showFileHeader = false,
        )
    }

    @Composable
    override fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        val diff = remember(context) { diffOf(context) }
        if (diff == null) {
            DefaultToolPreview(context = context)
            return
        }
        val stats = remember(diff) { parseDiffStats(diff) }
        Column(
            modifier = Modifier
                .fillMaxHeight(0.8f)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = context.arguments.getStringContent("path") ?: toolName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "+${stats.additions}",
                    style = MaterialTheme.typography.labelMedium,
                    color = DiffAddedColor,
                )
                Text(
                    text = "-${stats.deletions}",
                    style = MaterialTheme.typography.labelMedium,
                    color = DiffRemovedColor,
                )
            }
            DiffView(
                diff = diff,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 工作空间读取文件: 默认 10 行代码预览; 开关+白名单命中的 md/txt 用 Markdown 内联
 */
object ReadFileToolUI : ToolUIRenderer {
    override val toolName: String = "workspace_read_file"

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.FileView

    @Composable
    override fun title(context: ToolUIContext): String {
        val path = context.arguments.getStringContent("path")
        return if (path != null) stringResource(R.string.tool_ui_read_file, path) else stringResource(R.string.tool_ui_read_file_default)
    }

    /** 已执行时从输出 JSON 读取文件内容 */
    private fun textOf(context: ToolUIContext): String? =
        context.content.getStringContent("text")

    override fun hasSummary(context: ToolUIContext): Boolean = textOf(context) != null

    @Composable
    override fun Summary(context: ToolUIContext) {
        val text = remember(context) { textOf(context) } ?: return
        val path = context.arguments.getStringContent("path")
        if (context.shouldInlineMarkdown(path)) {
            MarkdownBlock(content = text, modifier = Modifier.fillMaxWidth(), compact = true)
        } else {
            FileContentSummary(
                text = text,
                path = path,
                loading = context.loading,
            )
        }
    }

    @Composable
    override fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        val text = remember(context) { textOf(context) }
        if (text == null) {
            DefaultToolPreview(context = context)
            return
        }
        val path = context.arguments.getStringContent("path")
        if (context.shouldInlineMarkdown(path)) {
            FileMarkdownPreview(path = path, content = text)
        } else {
            FileContentPreview(path = path, code = text)
        }
    }
}

/**
 * 列目录: 标题是路径, 气泡是条数, 点进去目录在上文件在下.
 * truncated 收人话, 不铺 JSON.
 */
object ListFilesToolUI : ToolUIRenderer {
    override val toolName: String = "workspace_ls"

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.Folder02

    @Composable
    override fun title(context: ToolUIContext): String {
        return stringResource(R.string.tool_ui_list_files, context.dirLabel())
    }

    override fun hasSummary(context: ToolUIContext): Boolean = context.content != null

    @Composable
    override fun Summary(context: ToolUIContext) {
        val content = context.content ?: return
        val count = content.int("count") ?: 0
        val truncated = content.boolean("truncated") == true
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (count == 0) {
                    stringResource(R.string.tool_ui_list_files_empty)
                } else {
                    stringResource(R.string.tool_ui_list_files_count, count)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (truncated) {
                Text(
                    text = stringResource(R.string.tool_ui_list_files_truncated),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }

    @Composable
    override fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        val content = context.content
        if (content == null) {
            DefaultToolPreview(context = context)
            return
        }
        val entries = content.entriesOf()
        val dirs = entries.filter { it.type == "dir" }
        val files = entries.filter { it.type != "dir" }
        val truncated = content.boolean("truncated") == true
        val hint = content.getStringContent("hint")
        Column(
            modifier = Modifier
                .fillMaxHeight(0.8f)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.tool_ui_list_files, context.dirLabel()),
                style = MaterialTheme.typography.titleMedium,
            )
            if (truncated) {
                Text(
                    text = hint ?: stringResource(R.string.tool_ui_list_files_truncated),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            dirs.forEach { entry ->
                PathLine(path = entry.path, suffix = "/")
            }
            files.forEach { entry ->
                PathLine(path = entry.path)
            }
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.tool_ui_list_files_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 按内容检索: 标题是搜索词, 气泡是命中数 + 扫文件被砍时的提示.
 * 点进去每条 文件:行号 + 命中行, 不嵌套进子代理卡片.
 */
object GrepToolUI : ToolUIRenderer {
    override val toolName: String = "workspace_grep"

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.Search01

    @Composable
    override fun title(context: ToolUIContext): String {
        val pattern = context.arguments.getStringContent("pattern")
        return if (pattern.isNullOrBlank()) {
            stringResource(R.string.tool_ui_grep_default)
        } else {
            stringResource(R.string.tool_ui_grep, pattern.shortLabel())
        }
    }

    override fun hasSummary(context: ToolUIContext): Boolean = context.content != null

    @Composable
    override fun Summary(context: ToolUIContext) {
        val content = context.content ?: return
        val count = content.int("count") ?: 0
        val truncated = content.boolean("truncated") == true
        val scanCapped = content.boolean("scanCapped") == true
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.tool_ui_grep_count, count),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (truncated) {
                Text(
                    text = if (scanCapped) {
                        stringResource(R.string.tool_ui_grep_scan_capped)
                    } else {
                        stringResource(R.string.tool_ui_grep_truncated)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }

    @Composable
    override fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        val content = context.content
        if (content == null) {
            DefaultToolPreview(context = context)
            return
        }
        val matches = content.matchesOf()
        val truncated = content.boolean("truncated") == true
        val hint = content.getStringContent("hint")
        Column(
            modifier = Modifier
                .fillMaxHeight(0.8f)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title(context),
                style = MaterialTheme.typography.titleMedium,
            )
            if (truncated && !hint.isNullOrBlank()) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            matches.forEach { hit ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${hit.path.fileLabel()}:${hit.line}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = hit.text,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
            if (matches.isEmpty()) {
                Text(
                    text = stringResource(R.string.tool_ui_grep_count, 0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 工作空间写入文件: 内容取自入参 (未执行也可预览), 摘要为内容首部, 详情为完整内容
 */
object WriteFileToolUI : ToolUIRenderer {
    override val toolName: String = "workspace_write_file"

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.FileAdd

    @Composable
    override fun title(context: ToolUIContext): String {
        val path = context.arguments.getStringContent("path")
        return if (path != null) stringResource(R.string.tool_ui_write_file, path) else stringResource(R.string.tool_ui_write_file_default)
    }

    private fun textOf(context: ToolUIContext): String? =
        context.arguments.getStringContent("text")

    override fun hasSummary(context: ToolUIContext): Boolean = textOf(context) != null

    @Composable
    override fun Summary(context: ToolUIContext) {
        val text = remember(context) { textOf(context) } ?: return
        FileContentSummary(
            text = text,
            path = context.arguments.getStringContent("path"),
            loading = context.loading,
        )
    }

    @Composable
    override fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        val text = remember(context) { textOf(context) }
        if (text == null) {
            DefaultToolPreview(context = context)
            return
        }
        FileContentPreview(path = context.arguments.getStringContent("path"), code = text)
    }
}

/** 内联摘要: 按扩展名语法高亮展示文件内容首部若干行 */
@Composable
private fun FileContentSummary(text: String, path: String?, loading: Boolean) {
    val preview = remember(text) {
        text.lineSequence().take(FILE_SUMMARY_MAX_LINES).joinToString("\n")
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .shimmer(isLoading = loading),
    ) {
        CodeHighlightText(
            code = preview,
            language = languageOf(path),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = FILE_SUMMARY_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ToolUIContext.shouldInlineMarkdown(path: String?): Boolean {
    val display = LocalSettings.current.displaySetting
    return shouldInlineWorkspaceRead(
        enabled = display.inlineWorkspaceReadEnabled,
        allowlist = display.inlineWorkspaceReadAssistantIds,
        assistantId = assistantId,
        path = path,
    )
}

@Composable
private fun FileMarkdownPreview(path: String?, content: String) {
    Column(
        modifier = Modifier
            .fillMaxHeight(0.8f)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = path ?: stringResource(R.string.tool_ui_file),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        MarkdownBlock(
            content = content,
            modifier = Modifier.fillMaxWidth(),
            compact = true,
        )
    }
}

/** BottomSheet 详情: 文件路径 + 按扩展名语法高亮的完整内容 */
@Composable
private fun FileContentPreview(path: String?, code: String) {
    Column(
        modifier = Modifier
            .fillMaxHeight(0.8f)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = path ?: stringResource(R.string.tool_ui_file),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        HighlightCodeBlock(
            code = code,
            language = languageOf(path),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 工作空间执行 Shell: 摘要显示退出状态与输出首部, 详情为命令 + stdout/stderr
 */
object ShellToolUI : ToolUIRenderer {
    private const val TITLE_MAX_CHARS = 40
    private const val SUMMARY_MAX_LINES = 8

    override val toolName: String = "workspace_shell"

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.ComputerTerminal01

    @Composable
    override fun title(context: ToolUIContext): String {
        val command = context.arguments.getStringContent("command") ?: return stringResource(R.string.tool_ui_shell_default)
        val preview = command.replace("\n", " ").trim()
        val truncated = if (preview.length > TITLE_MAX_CHARS) preview.take(TITLE_MAX_CHARS) + "…" else preview
        return stringResource(R.string.tool_ui_shell, truncated)
    }

    override fun hasSummary(context: ToolUIContext): Boolean = context.content != null

    @Composable
    override fun Summary(context: ToolUIContext) {
        val content = context.content ?: return
        val combined = remember(content) {
            listOf(content.getStringContent("stdout"), content.getStringContent("stderr"))
                .filterNot { it.isNullOrBlank() }
                .joinToString("\n")
                .trim()
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ShellExitStatus(content, MaterialTheme.typography.labelSmall)
            if (combined.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .shimmer(isLoading = context.loading),
                ) {
                    Text(
                        text = combined.lineSequence().take(SUMMARY_MAX_LINES).joinToString("\n"),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = SUMMARY_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    @Composable
    override fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        val content = context.content
        if (content == null) {
            DefaultToolPreview(context = context)
            return
        }
        val command = context.arguments.getStringContent("command").orEmpty()
        val cwd = context.arguments.getStringContent("cwd")
        val stdout = content.getStringContent("stdout").orEmpty()
        val stderr = content.getStringContent("stderr").orEmpty()
        Column(
            modifier = Modifier
                .fillMaxHeight(0.8f)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.tool_ui_shell_default),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                ShellExitStatus(content, MaterialTheme.typography.labelMedium)
            }
            HighlightCodeBlock(
                code = if (cwd.isNullOrBlank()) command else "# cwd: $cwd\n$command",
                language = "bash",
                modifier = Modifier.fillMaxWidth(),
            )
            if (stdout.isNotEmpty()) {
                Text(text = "stdout", style = MaterialTheme.typography.labelMedium)
                HighlightCodeBlock(
                    code = stdout,
                    language = "plaintext",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (stderr.isNotEmpty()) {
                Text(
                    text = "stderr",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                HighlightCodeBlock(
                    code = stderr,
                    language = "plaintext",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Shell 退出状态文本: exit code 为 0 显示绿色, 超时或非零显示错误色 */
@Composable
private fun ShellExitStatus(content: JsonElement, style: androidx.compose.ui.text.TextStyle) {
    val exitCode = content.int("exitCode")
    val timedOut = content.boolean("timedOut") ?: false
    val ok = !timedOut && exitCode == 0
    Text(
        text = when {
            timedOut -> stringResource(R.string.tool_ui_shell_timeout)
            else -> stringResource(R.string.tool_ui_shell_exit, exitCode?.toString() ?: "?")
        },
        style = style,
        color = if (ok) DiffAddedColor else MaterialTheme.colorScheme.error,
    )
}

/** 从工具输出 JSON 读取布尔字段 */
private fun JsonElement?.boolean(key: String): Boolean? =
    this?.jsonObjectOrNull?.get(key)?.jsonPrimitiveOrNull?.booleanOrNull

/** 从工具输出 JSON 读取整型字段 */
private fun JsonElement?.int(key: String): Int? =
    this?.jsonObjectOrNull?.get(key)?.jsonPrimitiveOrNull?.intOrNull

/** 从工具输出 JSON 读取长整型字段 */
private fun JsonElement?.long(key: String): Long? =
    this?.jsonObjectOrNull?.get(key)?.jsonPrimitiveOrNull?.longOrNull

private const val FILE_SUMMARY_MAX_LINES = 10

private data class ListEntry(val path: String, val type: String)

private data class GrepHit(val path: String, val line: Int, val text: String)

private fun JsonElement?.entriesOf(): List<ListEntry> {
    val array = this?.jsonObjectOrNull?.get("entries") as? JsonArray ?: return emptyList()
    return array.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val path = obj.getStringContent("path") ?: return@mapNotNull null
        ListEntry(path = path, type = obj.getStringContent("type") ?: "file")
    }
}

private fun JsonElement?.matchesOf(): List<GrepHit> {
    val array = this?.jsonObjectOrNull?.get("matches") as? JsonArray ?: return emptyList()
    return array.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val path = obj.getStringContent("path") ?: return@mapNotNull null
        GrepHit(
            path = path,
            line = obj["line"]?.jsonPrimitiveOrNull?.intOrNull ?: 0,
            text = obj.getStringContent("text").orEmpty(),
        )
    }
}

@Composable
private fun PathLine(path: String, suffix: String = "") {
    Text(
        text = path.fileLabel() + suffix,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ToolUIContext.dirLabel(): String {
    val raw = arguments.getStringContent("path")?.trim().orEmpty()
    return if (raw.isEmpty() || raw == "/workspace") {
        stringResource(R.string.tool_ui_workspace_root)
    } else {
        raw.fileLabel()
    }
}

private fun String.fileLabel(): String =
    removePrefix("/workspace/").removePrefix("/workspace").ifBlank { this }
        .substringAfterLast('/')
        .ifBlank { this }

private fun String.shortLabel(): String =
    if (length <= 32) this else take(31) + "…"


/** 由文件扩展名推断语法高亮语言 */
private fun languageOf(path: String?): String = when (
    path?.substringAfterLast('.', "")?.lowercase().orEmpty()
) {
    "kt", "kts" -> "kotlin"
    "java" -> "java"
    "js", "mjs", "cjs" -> "javascript"
    "ts" -> "typescript"
    "tsx" -> "tsx"
    "jsx" -> "jsx"
    "py" -> "python"
    "rb" -> "ruby"
    "go" -> "go"
    "rs" -> "rust"
    "c", "h" -> "c"
    "cpp", "cc", "cxx", "hpp", "hxx" -> "cpp"
    "cs" -> "csharp"
    "swift" -> "swift"
    "php" -> "php"
    "sh", "bash", "zsh" -> "bash"
    "json" -> "json"
    "xml" -> "xml"
    "html", "htm" -> "html"
    "css" -> "css"
    "scss" -> "scss"
    "yaml", "yml" -> "yaml"
    "toml" -> "toml"
    "md", "markdown" -> "markdown"
    "sql" -> "sql"
    "gradle" -> "groovy"
    else -> "plaintext"
}
