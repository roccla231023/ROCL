package me.rerere.rikkahub.ui.pages.rp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.rp.model.RpCard
import me.rerere.rikkahub.data.rp.model.RpPlayMode
import me.rerere.rikkahub.data.rp.model.RpSession
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel

@Composable
fun RpPage(vm: RpVM = koinViewModel()) {
    val cards by vm.cards.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    var deleteTarget by remember { mutableStateOf<RpCard?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.rp_page_title)) },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = {
                        val card = vm.createCard()
                        navController.navigate(Screen.RpCardDetail(card.id.toString()))
                    }) {
                        Icon(HugeIcons.Add01, contentDescription = stringResource(R.string.rp_page_create))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        if (cards.isEmpty()) {
            EmptyRpState(
                modifier = Modifier.fillMaxSize().padding(padding),
                onCreate = {
                    val card = vm.createCard()
                    navController.navigate(Screen.RpCardDetail(card.id.toString()))
                },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "intro") {
                    Text(
                        text = stringResource(R.string.rp_page_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
                items(cards, key = { it.id }) { card ->
                    RpCardItem(
                        card = card,
                        recentSession = sessions.firstOrNull { it.card.id == card.id },
                        onOpen = { navController.navigate(Screen.RpCardDetail(card.id.toString())) },
                        onContinue = { session ->
                            navController.navigate(Screen.RpSession(session.id.toString()))
                        },
                        onDelete = { deleteTarget = card },
                    )
                }
            }
        }
    }

    RikkaConfirmDialog(
        show = deleteTarget != null,
        title = stringResource(R.string.rp_page_delete),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        text = { Text(stringResource(R.string.rp_page_delete)) },
        onConfirm = {
            deleteTarget?.let(vm::delete)
            deleteTarget = null
        },
        onDismiss = { deleteTarget = null },
    )
}

@Composable
private fun EmptyRpState(modifier: Modifier = Modifier, onCreate: () -> Unit) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("✦", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.rp_page_empty_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.rp_page_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        FilledTonalButton(onClick = onCreate) { Text(stringResource(R.string.rp_page_create)) }
    }
}

@Composable
private fun RpCardItem(
    card: RpCard,
    recentSession: RpSession?,
    onOpen: () -> Unit,
    onContinue: (RpSession) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(start = 18.dp, top = 16.dp, end = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).clickable(onClick = onOpen).padding(vertical = 2.dp)) {
                    Text(
                        card.name.ifBlank { stringResource(R.string.rp_page_untitled) },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        card.genre.ifBlank { card.mode.localizedName() },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(HugeIcons.Delete01, contentDescription = stringResource(R.string.rp_page_delete))
                }
                Icon(HugeIcons.ArrowRight01, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Text(
                card.description.ifBlank { stringResource(R.string.rp_page_empty_desc) },
                modifier = Modifier.padding(top = 10.dp, end = 8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            recentSession?.let { session ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.rp_page_recent_story),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onContinue(session) }) {
                        Text(stringResource(R.string.rp_page_continue_story))
                    }
                }
            }
        }
    }
}

@Composable
private fun RpPlayMode.localizedName(): String = when (this) {
    RpPlayMode.CHARACTER -> stringResource(R.string.rp_mode_character)
    RpPlayMode.SIMULATION -> stringResource(R.string.rp_mode_simulation)
    RpPlayMode.COLLABORATIVE -> stringResource(R.string.rp_mode_collaborative)
    RpPlayMode.MYSTERY -> stringResource(R.string.rp_mode_mystery)
    RpPlayMode.TABLETOP -> stringResource(R.string.rp_mode_tabletop)
    RpPlayMode.CUSTOM -> stringResource(R.string.rp_mode_custom)
}
