package me.rerere.rikkahub.ui.pages.storage

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Clean
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.File01
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.repository.StorageCategoryKey
import me.rerere.rikkahub.data.repository.StorageOverview
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun StorageManagerPage(
    vm: StorageManagerVM = koinViewModel(),
) {
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val overviewState by vm.overview.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.storage_manager_title)) },
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { vm.refresh(force = true) }) {
                        Icon(HugeIcons.Refresh01, contentDescription = null)
                    }
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "overview") {
                StorageOverviewGroup(overviewState = overviewState)
            }
            item(key = "categories") {
                StorageCategoriesGroup(
                    overviewState = overviewState,
                    onOpenCategory = { category ->
                        navController.navigate(Screen.StorageCategory(category.key))
                    },
                )
            }
        }
    }
}

@Composable
private fun StorageOverviewGroup(
    overviewState: UiState<StorageOverview>,
) {
    val context = LocalContext.current
    val subtitleText = when (overviewState) {
        is UiState.Error -> overviewState.error.message ?: "Error"
        else -> null
    }
    val trailing: (@Composable () -> Unit)? = when (overviewState) {
        is UiState.Success -> {
            val totalText = runCatching { Formatter.formatShortFileSize(context, overviewState.data.totalBytes) }
                .getOrNull()
                ?: "${overviewState.data.totalBytes} B"
            {
                Text(
                    text = totalText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        UiState.Idle, UiState.Loading -> {
            {
                Text(
                    text = stringResource(R.string.storage_manager_loading_placeholder),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        else -> null
    }

    CardGroup(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = { Text(stringResource(R.string.storage_manager_overview)) },
    ) {
        item(
            leadingContent = {
                Icon(
                    imageVector = HugeIcons.Database02,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = trailing,
            supportingContent = subtitleText?.let { { Text(it) } },
            headlineContent = { Text(stringResource(R.string.storage_manager_total)) },
        )
    }
}

@Composable
private fun StorageCategoriesGroup(
    overviewState: UiState<StorageOverview>,
    onOpenCategory: (StorageCategoryKey) -> Unit,
) {
    val context = LocalContext.current
    val byKey = (overviewState as? UiState.Success<StorageOverview>)
        ?.data
        ?.categories
        ?.associateBy { it.category }
        .orEmpty()
    val placeholderText = when (overviewState) {
        UiState.Idle, UiState.Loading -> stringResource(R.string.storage_manager_loading_placeholder)
        is UiState.Error -> overviewState.error.message ?: "Error"
        is UiState.Success -> null
    }
    val subtitles = StorageCategoryKey.entries.associateWith { category ->
        placeholderText ?: run {
            val usage = byKey[category]
            val bytes = usage?.bytes ?: 0L
            val count = usage?.fileCount ?: 0
            val bytesText = runCatching { Formatter.formatShortFileSize(context, bytes) }
                .getOrNull()
                ?: "$bytes B"
            stringResource(R.string.storage_category_subtitle, bytesText, count)
        }
    }

    CardGroup(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = { Text(stringResource(R.string.storage_manager_categories)) },
    ) {
        StorageCategoryKey.entries.forEach { category ->
            val subtitleText = subtitles.getValue(category)
            item(
                onClick = { onOpenCategory(category) },
                leadingContent = {
                    Icon(
                        imageVector = categoryIcon(category),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                supportingContent = { Text(subtitleText) },
                headlineContent = { Text(stringResource(categoryTitleRes(category))) },
            )
        }
    }
}

private fun categoryTitleRes(category: StorageCategoryKey): Int = when (category) {
    StorageCategoryKey.IMAGES -> R.string.storage_category_images
    StorageCategoryKey.FILES -> R.string.storage_category_files
    StorageCategoryKey.CHAT_RECORDS -> R.string.storage_category_chat_records
    StorageCategoryKey.CACHE -> R.string.storage_category_cache
    StorageCategoryKey.HISTORY_FILES -> R.string.storage_category_history_files
}

private fun categoryIcon(category: StorageCategoryKey): ImageVector = when (category) {
    StorageCategoryKey.IMAGES -> HugeIcons.Image02
    StorageCategoryKey.FILES -> HugeIcons.File01
    StorageCategoryKey.CHAT_RECORDS -> HugeIcons.Database02
    StorageCategoryKey.CACHE -> HugeIcons.Clean
    StorageCategoryKey.HISTORY_FILES -> HugeIcons.Delete02
}
