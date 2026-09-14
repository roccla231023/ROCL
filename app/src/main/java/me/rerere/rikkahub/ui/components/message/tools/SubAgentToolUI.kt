package me.rerere.rikkahub.ui.components.message.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
                        text = "· ${step.toolName}: ${step.outputPreview.ifBlank { step.inputPreview }}",
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
                run.steps.takeLast(4).forEach { step ->
                    Text(
                        text = "· ${step.toolName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = run.summary,
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
            Text(
                text = run.summary,
                style = MaterialTheme.typography.bodyMedium,
            )
            run.steps.forEachIndexed { index, step ->
                StepPreview(index = index + 1, step = step)
            }
        }
    }
}

@Composable
private fun StepPreview(index: Int, step: SubAgentStep) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(R.string.subagent_step, index, step.toolName),
            style = MaterialTheme.typography.labelLarge,
        )
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

private fun UIMessagePart.Tool.subAgentRun(): SubAgentRun? {
    val payload = output.filterIsInstance<UIMessagePart.Text>()
        .firstNotNullOfOrNull { part -> part.metadata?.get(SUBAGENT_METADATA_KEY) }
        ?: return null
    return runCatching { JsonInstant.decodeFromJsonElement<SubAgentRun>(payload) }.getOrNull()
}
