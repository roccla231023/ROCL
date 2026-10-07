package me.rerere.rikkahub.ui.pages.rp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.rp.model.RpCard
import me.rerere.rikkahub.data.rp.model.RpStateChange
import me.rerere.rikkahub.data.rp.model.RpTurn
import me.rerere.rikkahub.data.rp.model.RpTurnStatus
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.richtext.MarkdownNew
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

@Composable
fun RpSessionPage(id: String) {
    val vm: RpSessionVM = koinViewModel(parameters = { parametersOf(id) })
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    var input by remember { mutableStateOf("") }
    var showState by remember { mutableStateOf(false) }
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
                items(turns, key = { it.id }) { turn ->
                    RpTurnItem(
                        turn = turn,
                        onRetry = { vm.retry(turn) },
                        onFork = { vm.fork(turn.id) },
                        onRollback = { vm.rollback(turn.id) },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
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
                onClose = { showState = false },
            )
        }
    }
}

@Composable
private fun RpSceneHeader(card: RpCard) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(
            Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerHigh))
        ).padding(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
private fun RpTurnItem(
    turn: RpTurn,
    onRetry: () -> Unit,
    onFork: () -> Unit,
    onRollback: () -> Unit,
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
                turn.error ?: stringResource(R.string.rp_session_failed),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Row(modifier = Modifier.padding(horizontal = 4.dp)) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.rp_session_retry)) }
                TextButton(onClick = onFork) { Text(stringResource(R.string.rp_session_fork)) }
            }
        } else {
            Text(
                stringResource(R.string.rp_session_processing),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        turn.outcome?.let { outcome ->
            if (outcome.changes.isNotEmpty()) {
                Text(
                    stringResource(R.string.rp_session_state_updated),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun RpStateSheet(
    card: RpCard,
    state: kotlinx.serialization.json.JsonObject,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.rp_session_state), style = MaterialTheme.typography.headlineSmall)
        card.viewFields.forEach { field ->
            val value = readPath(state, field.path)
            if (value != null && !card.hiddenStatePaths.contains(field.path)) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(field.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value.toString(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (card.viewFields.none { readPath(state, it.path) != null }) {
            Text(stringResource(R.string.rp_session_state_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun readPath(state: kotlinx.serialization.json.JsonObject, path: String): kotlinx.serialization.json.JsonElement? {
    var current: kotlinx.serialization.json.JsonElement = state
    path.split('.').forEach { key ->
        current = (current as? kotlinx.serialization.json.JsonObject)?.get(key) ?: return null
    }
    return current
}
