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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import me.rerere.rikkahub.data.ai.subagent.SUBAGENT_TOOL_NAME
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
            run != null -> stringResource(
                R.string.subagent_title_done,
                run.steps.size,
            )
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
                FilledTonalButton(onClick = { registry.requestAbort() }) {
                    Text(stringResource(R.string.subagent_stop))
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
            }
        }
    }

    @Composable
    override fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        val run = context.tool.subAgentRun()
        if (run == null) {
            DefaultToolPreview(context = context)
            return
        }
        Column(
            modifier = Modifier
                .fillMaxHeight(0.8f)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.subagent_preview_title, run.steps.size, run.status),
                style = MaterialTheme.typography.titleMedium,
            )
            // 摘要按 markdown 渲染, 跟工作区工具预览一致 (之前是把原文丢进裸 Text, 星号会原样显示)
            MarkdownBlock(
                content = run.summary,
                modifier = Modifier.fillMaxWidth(),
                compact = true,
            )
            run.steps.forEachIndexed { index, step ->
                StepPreview(index = index + 1, step = step)
            }
        }
    }
}

private const val SUMMARY_STEP_LIMIT = 4

@Composable
private fun StepPreview(index: Int, step: SubAgentStep) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(R.string.subagent_step, index, step.toolName),
            style = MaterialTheme.typography.labelLarge,
        )
        step.path?.let { EvidenceRow(R.string.subagent_evidence_path, it) }
        step.command?.let { EvidenceRow(R.string.subagent_evidence_command, it) }
        step.query?.let { EvidenceRow(R.string.subagent_evidence_query, it) }
        step.url?.let { EvidenceRow(R.string.subagent_evidence_url, it) }
        if (step.inputPreview.isNotBlank()) {
            Text(
                text = step.inputPreview,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (step.outputPreview.isNotBlank()) {
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
private fun SubAgentStep.evidenceTarget(): String =
    command ?: query ?: url ?: path ?: inputPreview

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
