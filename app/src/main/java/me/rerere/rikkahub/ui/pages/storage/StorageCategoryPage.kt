package me.rerere.rikkahub.ui.pages.storage

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.repository.StorageCategoryKey
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.UiState
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun StorageCategoryPage(
    category: String,
    vm: StorageCategoryVM = koinViewModel(parameters = { parametersOf(category) }),
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val categoryKey = vm.category
    val assistants by vm.assistants.collectAsStateWithLifecycle()
    val selectedAssistantId by vm.selectedAssistantId.collectAsStateWithLifecycle()
    val usageState by vm.categoryUsage.collectAsStateWithLifecycle()
    val attachmentStatsState by vm.assistantAttachmentStats.collectAsStateWithLifecycle()
    val conversationCountState by vm.assistantConversationCount.collectAsStateWithLifecycle()
    val chatRecordMonthsState by vm.chatRecordMonths.collectAsStateWithLifecycle()
    val assistantImagesState by vm.assistantImages.collectAsStateWithLifecycle()
    val assistantFilesState by vm.assistantFiles.collectAsStateWithLifecycle()
    val orphanScanState by vm.orphanScan.collectAsStateWithLifecycle()
    val cacheTopLevelUsageState by vm.cacheTopLevelUsage.collectAsStateWithLifecycle()
    val actionState by vm.action.collectAsStateWithLifecycle()

    LaunchedEffect(actionState) {
        when (actionState) {
            is UiState.Success -> toaster.show(message = context.getString(storageCategorySuccessToastRes(categoryKey)))
            is UiState.Error -> toaster.show(message = (actionState as UiState.Error).error.message ?: "Error")
            else -> Unit
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(storageCategoryTitleRes(categoryKey))) },
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
        StorageCategoryScaffoldContent(
            category = categoryKey,
            innerPadding = innerPadding,
            usageState = usageState,
            assistants = assistants,
            selectedAssistantId = selectedAssistantId,
            onSelectAssistant = { id -> vm.selectAssistant(id) },
            attachmentStatsState = attachmentStatsState,
            conversationCountState = conversationCountState,
            assistantImagesState = assistantImagesState,
            assistantFilesState = assistantFilesState,
            chatRecordMonthsState = chatRecordMonthsState,
            cacheTopLevelUsageState = cacheTopLevelUsageState,
            onDeleteImages = { assistantId, absolutePaths -> vm.deleteImages(assistantId, absolutePaths) },
            onDeleteFiles = { assistantId, absolutePaths -> vm.deleteFiles(assistantId, absolutePaths) },
            onLoadChatRecordConversationsByYearMonth = { assistantId, yearMonth ->
                vm.getChatRecordConversationsByYearMonth(
                    assistantId = assistantId,
                    yearMonth = yearMonth,
                )
            },
            onClearChatRecordSelection = { assistantId, yearMonths, conversationIds ->
                vm.clearChatRecordsSelection(
                    assistantId = assistantId,
                    yearMonths = yearMonths,
                    conversationIds = conversationIds,
                )
            },
            orphanScanState = orphanScanState,
            onScanOrphans = { vm.scanOrphans() },
            onClearAllOrphans = { vm.clearAllOrphans() },
            onClearCache = { vm.clearCache() },
            onLoadMoreImages = { vm.loadMoreImages() },
            onLoadMoreFiles = { vm.loadMoreFiles() },
        )
    }
}

private fun storageCategoryTitleRes(category: StorageCategoryKey): Int = when (category) {
    StorageCategoryKey.IMAGES -> R.string.storage_category_images
    StorageCategoryKey.FILES -> R.string.storage_category_files
    StorageCategoryKey.CHAT_RECORDS -> R.string.storage_category_chat_records
    StorageCategoryKey.CACHE -> R.string.storage_category_cache
    StorageCategoryKey.HISTORY_FILES -> R.string.storage_category_history_files
}

private fun storageCategorySuccessToastRes(category: StorageCategoryKey): Int = when (category) {
    StorageCategoryKey.IMAGES -> R.string.storage_toast_images_cleared
    StorageCategoryKey.FILES -> R.string.storage_toast_files_cleared
    StorageCategoryKey.CHAT_RECORDS -> R.string.storage_toast_chat_records_cleared
    StorageCategoryKey.CACHE -> R.string.storage_toast_cache_cleared
    StorageCategoryKey.HISTORY_FILES -> R.string.storage_toast_history_cleared
}
