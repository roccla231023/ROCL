package me.rerere.rikkahub.ui.pages.rp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.rp.model.RpCard
import me.rerere.rikkahub.data.rp.model.RpStateField
import me.rerere.rikkahub.data.rp.model.RpStateFieldType
import me.rerere.rikkahub.data.rp.model.RpTurn
import me.rerere.rikkahub.data.rp.model.RpTurnStatus
import me.rerere.rikkahub.data.rp.runtime.RpStateReducer
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.richtext.MarkdownNew
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun RpSessionPage(id: String) {
    val vm: RpSessionVM = koinViewModel(parameters = { parametersOf(id) })
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var showState by remember { mutableStateOf(false) }
    var showMemory by remember { mutableStateOf(false) }
    val scrollState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val session = snapshot?.session
    val turns = snapshot?.turns.orEmpty()

    if (session == null) return

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            session.card.name.ifBlank { stringResource(R.string.rp_page_untitled) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            session.card.genre.ifBlank { session.card.mode.name },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { showState = true }) {
                        Icon(HugeIcons.Menu03, contentDescription = stringResource(R.string.rp_session_state))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = scrollState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item(key = "welcome") {
                    RpSceneHeader(session.card)
                }
                item(key = "memory") {
                    RpStoryMemoryCard(
                        summary = session.storyMemory.summary,
                        facts = session.storyMemory.importantFacts,
                        expanded = showMemory,
                        onToggle = { showMemory = !showMemory },
                    )
                }
                items(turns, key = { it.id }) { turn ->
                    RpTurnItem(
                        turn = turn,
                        onRetry = { vm.retry(turn) },
                        onFork = { vm.fork(turn.id) },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.rp_session_input_hint)) },
                    maxLines = 5,
                    shape = RoundedCornerShape(20.dp),
                )
                FilledIconButton(
                    onClick = {
                        val message = input.trim()
                        if (message.isNotEmpty()) {
                            input = ""
                            vm.submit(message)
                        }
                    },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(HugeIcons.ArrowUp02, contentDescription = stringResource(R.string.rp_session_send))
                }
            }
        }
    }

    if (showState) {
        ModalBottomSheet(
            onDismissRequest = { showState = false },
            sheetState = rememberBottomSheetState(SheetValue.Expanded),
        ) {
            RpStateSheet(
                card = session.card,
                state = session.state,
            )
        }
    }
}

@Composable
private fun RpSceneHeader(card: RpCard) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.rp_session_scene), style = MaterialTheme.typography.labelLarge)
            Text(
                card.description.ifBlank { stringResource(R.string.rp_session_scene_desc) },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RpStoryMemoryCard(
    summary: String,
    facts: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.rp_session_story_memory), style = MaterialTheme.typography.titleSmall)
                    Text(
                        summary.ifBlank { stringResource(R.string.rp_session_no_story_memory) },
                        maxLines = if (expanded) 4 else 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                    contentDescription = null,
                )
            }
            AnimatedVisibility(visible = expanded && facts.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    facts.forEach { fact ->
                        Text("• $fact", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun RpTurnItem(
    turn: RpTurn,
    onRetry: () -> Unit,
    onFork: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(
                turn.input,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (turn.narrative.isNotBlank()) {
            MarkdownNew(
                content = turn.narrative,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        } else if (turn.status == RpTurnStatus.FAILED) {
            Text(
                stringResource(turn.statusTitleRes()),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            turn.error?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            Row(modifier = Modifier.padding(horizontal = 4.dp)) {
                TextButton(onClick = onRetry) { Text(stringResource(turn.retryLabelRes())) }
                TextButton(onClick = onFork) { Text(stringResource(R.string.rp_session_fork)) }
            }
        } else {
            Text(
                stringResource(turn.statusTitleRes()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        turn.outcome?.changes?.takeIf { it.isNotEmpty() }?.let { changes ->
            Text(
                stringResource(R.string.rp_session_state_changes_count, changes.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun RpStateSheet(
    card: RpCard,
    state: JsonObject,
) {
    val visibleState = remember(state, card.stateSchema, card.hiddenStatePaths) {
        RpStateReducer().visibleState(state, card.hiddenStatePaths, card.stateSchema)
    }
    val fields = if (card.stateSchema.fields.isNotEmpty()) {
        card.stateSchema.fields.filter { it.visible && !card.hiddenStatePaths.contains(it.path) }
    } else {
        card.viewFields.map { RpStateField(path = it.path, label = it.label) }
    }
    val fieldsWithValues = fields.filter { readPath(visibleState, it.path) != null }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.rp_session_state), style = MaterialTheme.typography.headlineSmall)
        if (fieldsWithValues.isEmpty()) {
            Text(stringResource(R.string.rp_session_no_state_fields), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            fieldsWithValues.groupBy { it.group }.forEach { (group, groupFields) ->
                CardGroup(title = { Text(group) }) {
                    groupFields.forEach { field ->
                        val value = readPath(visibleState, field.path) ?: return@forEach
                        item(
                            headlineContent = { Text(field.label) },
                            supportingContent = {
                                RpStateValue(field = field, value = value)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RpStateValue(field: RpStateField, value: JsonElement) {
    val text = value.displayText()
    if (field.type == RpStateFieldType.PROGRESS) {
        val number = (value as? JsonPrimitive)?.content?.toDoubleOrNull()
        if (number != null) {
            val min = field.min ?: 0.0
            val max = field.max ?: 100.0
            val fraction = ((number - min) / (max - min)).toFloat().coerceIn(0f, 1f)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text + field.unit.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty())
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            return
        }
    }
    Text(text + field.unit.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty())
}

private fun RpTurn.statusTitleRes(): Int = when (status) {
    RpTurnStatus.ADJUDICATING -> R.string.rp_session_status_adjudicating
    RpTurnStatus.REVIEWING -> R.string.rp_session_status_reviewing
    RpTurnStatus.NARRATING -> R.string.rp_session_status_narrating
    RpTurnStatus.FAILED -> R.string.rp_session_status_failed
    RpTurnStatus.PENDING -> R.string.rp_session_processing
    RpTurnStatus.COMPLETED -> R.string.rp_session_state_updated
}

private fun RpTurn.retryLabelRes(): Int = when {
    event != null -> R.string.rp_session_retry_narration
    outcome == null -> R.string.rp_session_retry_adjudication
    else -> R.string.rp_session_retry_review
}

private fun JsonElement.displayText(): String = when (this) {
    is JsonPrimitive -> content
    else -> toString()
}

private fun readPath(state: JsonObject, path: String): JsonElement? {
    var current: JsonElement = state
    path.split('.').forEach { key ->
        current = (current as? JsonObject)?.get(key) ?: return null
    }
    return current
}
