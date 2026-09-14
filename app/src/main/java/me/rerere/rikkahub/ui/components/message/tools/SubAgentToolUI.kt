package me.rerere.rikkahub.ui.components.message.tools

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import me.rerere.rikkahub.utils.JsonInstant
import org.koin.compose.koinInject

object SubAgentToolUI : ToolUIRenderer {
    override val toolName: String = SUBAGENT_TOOL_NAME

    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.AiBrain01

    override fun hasSummary(context: ToolUIContext): Boolean = true

    @Composable
    override fun title(context: ToolUIContext): String {
        val registry: SubAgentRunRegistry = koinInject()
        val progress by registry.active.collectAsStateWithLifecycle()
        val run = context.tool.subAgentRun()
        val running = context.loading && !context.tool.isExecuted
        return when {
            running && progress != null -> stringResource(
                R.string.subagent_title_running,
                progress!!.step,
                progress!!.total,
                progress!!.phase,
            )
            run != null -> when (run.status) {
                // 状态必须分开显示: failed / aborted / 没执行, 看起来都不该像"正常跑完"
                STATUS_COMPLETED -> stringResource(R.string.subagent_title_done, run.steps.size)
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
            if (running) {
                val recent = progress?.recent.orEmpty().takeLast(3)
                recent.forEach { step ->
                    Text(
                        text = "· ${step.toolName}  ${step.evidenceTarget()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 小号文字按钮, 不占满宽度
                TextButton(onClick = { registry.requestAbort() }) {
                    Text(
                        text = stringResource(R.string.subagent_stop),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            } else if (run != null) {
                // 折叠态只看最近几步, 但要说清楚被省掉了多少, 免得看起来"一共就这么几步"
                val shownSteps = run.steps.takeLast(SUMMARY_STEP_LIMIT)
                val hiddenSteps = run.steps.size - shownSteps.size
                if (hiddenSteps > 0) {
                    Text(
                        text = stringResource(R.string.subagent_summary_more, hiddenSteps),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                shownSteps.forEach { step ->
                    Text(
                        text = "· ${step.toolName}  ${step.evidenceTarget()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    // 折叠态是纯 Text, 不渲染 markdown, 所以先把 ** 之类去掉,
                    // 免得气泡里出现字面的星号; 完整渲染在 Preview 里。
                    text = run.summary.stripMarkdownEmphasis(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                // 没有任何结果时的提示。运行中已经在上面的分支处理过了。
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

    /**
     * 预览面板的顺序是刻意的: **先引擎观测到的, 再模型叙述的**。
     *
     * 1. 标题 (状态 + 步数)
     * 2. 请求 —— 模型入参里的路径 / 命令 / 搜索词 / 链接: 只证明"它要做什么"
     * 3. 结果 —— 工具返回里抽出的真实路径 + 每步成功失败: 这才证明"做成了什么"
     * 4. 报告 —— 模型自己写的那篇, 单独标出来, 提醒读者它可能不准
     * 5. 逐步明细
     *
     * 依据: 实测中子代理会在报告里断言工具返回里不存在的东西, 所以引擎记录必须放前面。
     */
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                run != null -> CompletedPreview(run)
                running -> RunningPreview(progress)
                legacyText.isNotBlank() -> LegacyPreview(legacyText)
                else -> UnfinishedPreview(input = context.tool.input)
            }
        }
    }
}

private const val STATUS_COMPLETED = "completed"

private const val STATUS_ABORTED = "aborted"

private const val STATUS_UNAVAILABLE = "unavailable"

private const val SUMMARY_STEP_LIMIT = 4

private const val RUNNING_STEP_LIMIT = 8

/** 证据里的路径超过这个长度就中间省略, 保证文件名看得见 */
private const val EVIDENCE_PATH_MAX = 64

private const val EVIDENCE_TEXT_MAX = 96

@Composable
private fun CompletedPreview(run: SubAgentRun) {
    Text(
        text = stringResource(R.string.subagent_preview_title, run.steps.size, run.status),
        style = MaterialTheme.typography.titleMedium,
    )
    RequestDigest(run.steps)
    ResultDigest(run.steps)
    HorizontalDivider()
    Text(
        text = stringResource(R.string.subagent_summary_title),
        style = MaterialTheme.typography.labelLarge,
    )
    // 摘要按 markdown 渲染, 跟工作区工具预览一致
    MarkdownBlock(
        content = run.summary,
        modifier = Modifier.fillMaxWidth(),
        compact = true,
    )
    if (run.steps.isNotEmpty()) {
        HorizontalDivider()
        run.steps.forEachIndexed { index, step ->
            StepPreview(index = index + 1, step = step)
        }
    }
}

@Composable
private fun RunningPreview(progress: SubAgentProgress?) {
    Text(
        text = if (progress != null) {
            stringResource(
                R.string.subagent_title_running,
                progress.step,
                progress.total,
                progress.phase,
            )
        } else {
            stringResource(R.string.subagent_title_default)
        },
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = stringResource(R.string.subagent_preview_running_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val recent = progress?.recent.orEmpty().takeLast(RUNNING_STEP_LIMIT)
    if (recent.isNotEmpty()) {
        HorizontalDivider()
        recent.forEach { step ->
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = step.toolName,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = step.evidenceTarget(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 1.264 及更早的卡片没有 subagent_run metadata, 只能显示当时的输出, 并说明这一点 */
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
    HorizontalDivider()
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
        HorizontalDivider()
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

/** 模型**请求**了什么。只是入参, 不能当成既成事实。 */
@Composable
private fun RequestDigest(steps: List<SubAgentStep>) {
    val paths = steps.mapNotNull { it.path?.takeIf(String::isNotBlank) }.distinct()
    val commands = steps.mapNotNull { it.command?.takeIf(String::isNotBlank) }.distinct()
    val queries = steps.mapNotNull { it.query?.takeIf(String::isNotBlank) }.distinct()
    val urls = steps.mapNotNull { it.url?.takeIf(String::isNotBlank) }.distinct()
    if (paths.isEmpty() && commands.isEmpty() && queries.isEmpty() && urls.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.subagent_request_title),
            style = MaterialTheme.typography.labelLarge,
        )
        DigestGroup(R.string.subagent_evidence_path, paths, shortenPath = true)
        DigestGroup(R.string.subagent_evidence_command, commands)
        DigestGroup(R.string.subagent_evidence_query, queries)
        DigestGroup(R.string.subagent_evidence_url, urls)
    }
}

/** 工具**实际**返回了什么。这一层是引擎观测, 模型编不了。 */
@Composable
private fun ResultDigest(steps: List<SubAgentStep>) {
    if (steps.isEmpty()) return
    val okCount = steps.count { it.success == true }
    val failures = steps.filter { it.success == false }
    val resultPaths = steps.flatMap { it.resultPaths }.distinct()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.subagent_result_title),
            style = MaterialTheme.typography.labelLarge,
        )
        if (steps.any { it.success != null }) {
            Text(
                text = buildString {
                    append(stringResource(R.string.subagent_result_ok))
                    append(" · ")
                    append(okCount)
                    if (failures.isNotEmpty()) {
                        append(" / ")
                        append(stringResource(R.string.subagent_result_failed))
                        append(" · ")
                        append(failures.size)
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        failures.forEach { step ->
            Text(
                text = "· ${step.toolName}: ${step.error ?: ""}".shortenText(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        DigestGroup(R.string.subagent_evidence_path, resultPaths, shortenPath = true)
    }
}

@Composable
private fun DigestGroup(@StringRes labelRes: Int, values: List<String>, shortenPath: Boolean = false) {
    if (values.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = "${stringResource(labelRes)} · ${values.size}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        values.forEach { value ->
            Text(
                text = if (shortenPath) value.shortenPath() else value.shortenText(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun StepPreview(index: Int, step: SubAgentStep) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.subagent_step, index, step.toolName),
                style = MaterialTheme.typography.labelLarge,
            )
            if (step.success == false) {
                Text(
                    text = stringResource(R.string.subagent_result_failed),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        step.path?.let { EvidenceRow(R.string.subagent_evidence_path, it) }
        step.command?.let { EvidenceRow(R.string.subagent_evidence_command, it) }
        step.query?.let { EvidenceRow(R.string.subagent_evidence_query, it) }
        step.url?.let { EvidenceRow(R.string.subagent_evidence_url, it) }
        if (step.error != null) {
            Text(
                text = step.error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        // 已经有结构化结果时不再重复铺一遍原始输出, 不然同一件事看两遍还很吵
        if (step.resultPaths.isEmpty() && step.outputPreview.isNotBlank()) {
            Text(
                text = step.outputPreview,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EvidenceRow(@StringRes labelRes: Int, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
        )
    }
}

/**
 * 折叠态一行里"这一步到底看了什么"。
 * 优先用结构化证据; 老的步骤没有证据字段, 退回 200 字预览。
 */
private fun SubAgentStep.evidenceTarget(): String {
    val target = command ?: query ?: url ?: path ?: inputPreview
    return if (path != null && command == null && query == null && url == null) {
        target.shortenPath()
    } else {
        target.shortenText()
    }
}

/** 长路径中间省略, 去掉 /workspace 前缀, 保证文件名那一截看得见。 */
private fun String.shortenPath(): String {
    val trimmed = removePrefix("/workspace/")
    if (trimmed.length <= EVIDENCE_PATH_MAX) return trimmed
    val keep = EVIDENCE_PATH_MAX / 2 - 2
    return trimmed.take(keep) + "…" + trimmed.takeLast(keep)
}

/** 命令/搜索词这类单行文本超长时尾部省略。 */
private fun String.shortenText(): String =
    if (length <= EVIDENCE_TEXT_MAX) this else take(EVIDENCE_TEXT_MAX) + "…"

/** 折叠态不渲染 markdown, 先把 **粗体** 和 `代码` 的记号去掉, 避免出现字面星号。 */
private fun String.stripMarkdownEmphasis(): String =
    replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        .replace(Regex("`([^`]+?)`"), "$1")
        .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")

private fun UIMessagePart.Tool.subAgentRun(): SubAgentRun? {
    val payload = output.filterIsInstance<UIMessagePart.Text>()
        .firstNotNullOfOrNull { part -> part.metadata?.get(SUBAGENT_METADATA_KEY) }
        ?: return null
    return runCatching { JsonInstant.decodeFromJsonElement<SubAgentRun>(payload) }.getOrNull()
}
