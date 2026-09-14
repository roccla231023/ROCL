package me.rerere.rikkahub.ui.components.message.tools

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.decodeFromJsonElement
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiBrain01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.subagent.SUBAGENT_METADATA_KEY
import me.rerere.rikkahub.data.ai.subagent.SUBAGENT_PREVIEW_CHARS
import me.rerere.rikkahub.data.ai.subagent.SUBAGENT_TOOL_NAME
import me.rerere.rikkahub.data.ai.subagent.SubAgentProgress
import me.rerere.rikkahub.data.ai.subagent.SubAgentRun
import me.rerere.rikkahub.data.ai.subagent.SubAgentRunRegistry
import me.rerere.rikkahub.data.ai.subagent.SubAgentStep
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.modifier.shimmer
import me.rerere.rikkahub.utils.JsonInstant
import org.koin.compose.koinInject

/**
 * 子代理一张脸: 气泡和点进去共用时间线, 报告只在 Sheet 底.
 * 进度从 registry.steps 读全量, 不再靠 recent 三步.
 */
object SubAgentToolUI : ToolUIRenderer {
    override val toolName: String = SUBAGENT_TOOL_NAME

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.AiBrain01

    override fun hasSummary(context: ToolUIContext): Boolean = true

    @Composable
    override fun title(context: ToolUIContext): String {
        val run = context.tool.subAgentRun()
        val running = context.loading && !context.tool.isExecuted
        return when {
            running -> stringResource(R.string.subagent_title_running)
            run != null -> when (run.status) {
                STATUS_COMPLETED -> if (run.steps.isEmpty()) {
                    stringResource(R.string.subagent_title_no_tools)
                } else {
                    stringResource(R.string.subagent_title_done, run.steps.size)
                }
                STATUS_ABORTED -> stringResource(R.string.subagent_title_aborted, run.steps.size)
                STATUS_UNAVAILABLE -> stringResource(R.string.subagent_title_unavailable)
                else -> stringResource(R.string.subagent_title_failed, run.steps.size)
            }
            else -> stringResource(R.string.subagent_title_default)
        }
    }

    @Composable
    override fun Summary(context: ToolUIContext) {
        val registry: SubAgentRunRegistry = koinInject()
        val progress by registry.active.collectAsStateWithLifecycle()
        val run = context.tool.subAgentRun()
        val running = context.loading && !context.tool.isExecuted

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when {
                running -> {
                    EngineLedgerLine(progress?.steps.orEmpty())
                    val shown = progress?.steps.orEmpty().takeLast(SUMMARY_STEP_LIMIT)
                    shown.forEach { step -> TimelineRow(step = step, oneLine = true) }
                    val pending = progress?.pendingToolName
                    if (pending != null) {
                        TimelinePending(toolName = pending)
                    }
                    TextButton(
                        onClick = { registry.requestAbort() },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.subagent_stop),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                run != null && run.steps.isEmpty() && run.status == STATUS_COMPLETED -> {
                    Text(
                        text = stringResource(R.string.subagent_no_tools_body),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                run != null -> {
                    EngineLedgerLine(run.steps)
                    val shown = run.steps.takeLast(SUMMARY_STEP_LIMIT)
                    val hidden = run.steps.size - shown.size
                    if (hidden > 0) {
                        Text(
                            text = stringResource(R.string.subagent_summary_more, hidden),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    shown.forEach { step -> TimelineRow(step = step, oneLine = true) }
                }
                else -> {
                    Text(
                        text = stringResource(R.string.subagent_preview_unfinished_body),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    @Composable
    override fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        val registry: SubAgentRunRegistry = koinInject()
        val progress by registry.active.collectAsStateWithLifecycle()
        val run = context.tool.subAgentRun()
        val running = context.loading && !context.tool.isExecuted
        val legacyText = context.tool.output
            .filterIsInstance<UIMessagePart.Text>()
            .joinToString("\n") { it.text }

        Column(
            modifier = Modifier
                .fillMaxHeight(0.8f)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                run != null -> LivePreview(
                    title = previewTitle(run),
                    steps = run.steps,
                    report = run.summary.takeIf { run.status != STATUS_UNAVAILABLE },
                    stop = null,
                    pendingToolName = null,
                )
                running -> LivePreview(
                    title = if (progress != null) {
                        stringResource(R.string.subagent_title_running)
                    } else {
                        stringResource(R.string.subagent_title_default)
                    },
                    steps = progress?.steps.orEmpty(),
                    report = null,
                    stop = { registry.requestAbort() },
                    pendingToolName = progress?.pendingToolName,
                )
                legacyText.isNotBlank() -> LegacyPreview(legacyText)
                else -> UnfinishedPreview(input = context.tool.input)
            }
        }
    }
}

private const val STATUS_COMPLETED = "completed"

private const val STATUS_ABORTED = "aborted"

private const val STATUS_UNAVAILABLE = "unavailable"

private const val SUMMARY_STEP_LIMIT = 3

private const val EVIDENCE_TEXT_MAX = 72

@Composable
private fun previewTitle(run: SubAgentRun): String = when (run.status) {
    STATUS_COMPLETED -> if (run.steps.isEmpty()) {
        stringResource(R.string.subagent_title_no_tools)
    } else {
        stringResource(R.string.subagent_title_done, run.steps.size)
    }
    STATUS_ABORTED -> stringResource(R.string.subagent_title_aborted, run.steps.size)
    STATUS_UNAVAILABLE -> stringResource(R.string.subagent_title_unavailable)
    else -> stringResource(R.string.subagent_title_failed, run.steps.size)
}

@Composable
private fun LivePreview(
    title: String,
    steps: List<SubAgentStep>,
    report: String?,
    stop: (() -> Unit)?,
    pendingToolName: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (stop != null) {
            TextButton(onClick = stop, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(
                    text = stringResource(R.string.subagent_stop),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
    if (steps.isNotEmpty()) {
        EngineLedgerLine(steps)
        steps.forEach { step -> TimelineRow(step = step, oneLine = false) }
    }
    if (pendingToolName != null) {
        TimelinePending(toolName = pendingToolName)
    }
    if (steps.isEmpty() && pendingToolName == null && report == null) {
        Text(
            text = stringResource(R.string.subagent_preview_running_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (!report.isNullOrBlank()) {
        Text(
            text = stringResource(R.string.subagent_summary_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MarkdownBlock(
            content = report,
            modifier = Modifier.fillMaxWidth(),
            compact = true,
        )
    }
}

@Composable
private fun EngineLedgerLine(steps: List<SubAgentStep>) {
    if (steps.isEmpty()) return
    val ok = steps.count { it.success == true }
    val fail = steps.count { it.success == false }
    Text(
        text = if (fail > 0) {
            stringResource(R.string.subagent_ledger_with_fail, steps.size, ok, fail)
        } else {
            stringResource(R.string.subagent_ledger, steps.size, ok)
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TimelineRow(step: SubAgentStep, oneLine: Boolean) {
    val verb = stringResource(step.verbRes())
    val target = step.displayTarget(compact = oneLine)
    val failed = step.success == false
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = verb,
                style = MaterialTheme.typography.labelSmall,
                color = if (failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = target,
                style = MaterialTheme.typography.labelSmall,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                maxLines = if (oneLine) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (!oneLine && failed && !step.error.isNullOrBlank()) {
            Text(
                text = step.error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // “要的是”只用于 read_file 请求路径和实际结果路径不一致的异常情况。
        // ls 的结果本来就是目录下的子项，grep 的结果本来就是命中文件，不能误报。
        if (!oneLine && step.toolName == "workspace_read_file") {
            val asked = step.path?.takeIf { it.isNotBlank() }
            val got = step.resultPaths.firstOrNull()
            if (asked != null && got != null && asked.trimEnd('/') != got.trimEnd('/')) {
                Text(
                    text = stringResource(R.string.subagent_asked, asked.displayPath(compact = false)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TimelinePending(toolName: String) {
    val verb = stringResource(verbRes(toolName))
    Text(
        text = verb,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.shimmer(isLoading = true),
    )
}

@Composable
private fun LegacyPreview(text: String) {
    Text(
        text = stringResource(R.string.subagent_legacy_title),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = stringResource(R.string.subagent_legacy_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = if (text.length > SUBAGENT_PREVIEW_CHARS * 8) text.take(SUBAGENT_PREVIEW_CHARS * 8) + "…" else text,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun UnfinishedPreview(input: String) {
    Text(
        text = stringResource(R.string.subagent_preview_unfinished_title),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = stringResource(R.string.subagent_preview_unfinished_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (input.isNotBlank()) {
        Text(
            text = stringResource(R.string.subagent_preview_input_label),
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = input,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@StringRes
private fun SubAgentStep.verbRes(): Int = verbRes(toolName)

@StringRes
private fun verbRes(toolName: String): Int = when (toolName) {
    "workspace_read_file" -> R.string.subagent_verb_read
    "workspace_ls" -> R.string.subagent_verb_ls
    "workspace_grep" -> R.string.subagent_verb_grep
    "workspace_shell" -> R.string.subagent_verb_shell
    "search_web" -> R.string.subagent_verb_web
    "scrape_web" -> R.string.subagent_verb_scrape
    else -> R.string.subagent_verb_other
}

private fun SubAgentStep.displayTarget(compact: Boolean): String = when (toolName) {
    // ls 的结果路径是目录里的条目，主目标必须是请求的目录。
    "workspace_ls" -> path?.displayPath(compact) ?: inputPreview.shortenText()

    // grep 的结果路径是命中文件，主目标必须是搜索词。
    "workspace_grep" -> (query ?: path ?: inputPreview).shortenText()

    // read_file 的结果路径代表实际读到的文件，优先保留引擎事实。
    "workspace_read_file" -> {
        val result = resultPaths.firstOrNull()
        when {
            result != null -> result.displayPath(compact)
            path != null -> path.displayPath(compact)
            else -> inputPreview.shortenText()
        }
    }

    "workspace_shell" -> (command ?: inputPreview).shortenText()
    "search_web" -> (query ?: inputPreview).shortenText()
    "scrape_web" -> (url ?: inputPreview).shortenText()
    else -> (command ?: query ?: url ?: path ?: inputPreview).shortenText()
}

private fun String.displayPath(compact: Boolean): String {
    val normalized = removePrefix("/workspace/")
        .removePrefix("/workspace")
        .trim('/')
        .ifBlank { this }
    if (!compact) return this

    val parts = normalized.split('/').filter { it.isNotEmpty() }
    return if (parts.size <= 2) normalized else parts.takeLast(2).joinToString("/")
}

private fun String.shortenText(): String =
    if (length <= EVIDENCE_TEXT_MAX) this else take(EVIDENCE_TEXT_MAX) + "…"

private fun UIMessagePart.Tool.subAgentRun(): SubAgentRun? {
    val payload = output.filterIsInstance<UIMessagePart.Text>()
        .firstNotNullOfOrNull { part -> part.metadata?.get(SUBAGENT_METADATA_KEY) }
        ?: return null
    return runCatching { JsonInstant.decodeFromJsonElement<SubAgentRun>(payload) }.getOrNull()
}
