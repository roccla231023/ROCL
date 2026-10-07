package me.rerere.rikkahub.ui.pages.rp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.rp.model.RpCard
import me.rerere.rikkahub.data.rp.model.RpPlayMode
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.JsonInstantPretty
import kotlinx.serialization.json.jsonObject
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun RpCardDetailPage(id: String) {
    val vm: RpCardDetailVM = koinViewModel(parameters = { parametersOf(id) })
    val card by vm.card.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    var stateText by remember(card.id) { mutableStateOf(JsonInstantPretty.encodeToString(card.initialState)) }
    var viewFieldsText by remember(card.id) {
        mutableStateOf(card.viewFields.joinToString("\\n") { "${it.path}=${it.label}" })
    }
    var hiddenPathsText by remember(card.id) { mutableStateOf(card.hiddenStatePaths.joinToString("\\n")) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(card.name.ifBlank { stringResource(R.string.rp_page_untitled) }) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = padding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.rp_card_identity)) },
                ) {
                    item {
                        OutlinedTextField(
                            value = card.name,
                            onValueChange = { vm.update(card.copy(name = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.rp_card_name)) },
                            singleLine = true,
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = card.genre,
                            onValueChange = { vm.update(card.copy(genre = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.rp_card_genre)) },
                            singleLine = true,
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = card.description,
                            onValueChange = { vm.update(card.copy(description = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.rp_card_description)) },
                            minLines = 2,
                            maxLines = 4,
                        )
                    }
                }
            }
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.rp_card_mode)) },
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RpPlayMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = card.mode == mode,
                                    onClick = { vm.update(card.copy(mode = mode)) },
                                    label = { Text(mode.displayName()) },
                                )
                            }
                        }
                    }
                }
            }
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.rp_card_world)) },
                ) {
                    item {
                        OutlinedTextField(
                            value = card.worldPrompt,
                            onValueChange = { vm.update(card.copy(worldPrompt = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.rp_card_world_prompt)) },
                            minLines = 5,
                            maxLines = 12,
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = card.rulesPrompt,
                            onValueChange = { vm.update(card.copy(rulesPrompt = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.rp_card_rules_prompt)) },
                            minLines = 4,
                            maxLines = 10,
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = stateText,
                            onValueChange = { value ->
                                stateText = value
                                runCatching { JsonInstant.parseToJsonElement(value).jsonObject }
                                    .onSuccess { vm.update(card.copy(initialState = it)) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.rp_card_initial_state)) },
                            minLines = 5,
                            maxLines = 12,
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = viewFieldsText,
                            onValueChange = { value ->
                                viewFieldsText = value
                                val fields = value.lines().mapNotNull { line ->
                                    val separator = line.indexOf('=')
                                    if (separator <= 0) null else me.rerere.rikkahub.data.rp.model.RpViewField(
                                        path = line.substring(0, separator).trim(),
                                        label = line.substring(separator + 1).trim(),
                                    )
                                }
                                vm.update(card.copy(viewFields = fields))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.rp_card_view_fields)) },
                            minLines = 3,
                            maxLines = 8,
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = hiddenPathsText,
                            onValueChange = { value ->
                                hiddenPathsText = value
                                vm.update(card.copy(hiddenStatePaths = value.lines().map { it.trim() }.filter { it.isNotBlank() }.toSet()))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.rp_card_hidden_paths)) },
                            minLines = 2,
                            maxLines = 6,
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = card.narrativeStyle,
                            onValueChange = { vm.update(card.copy(narrativeStyle = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.rp_card_narrative_style)) },
                            minLines = 3,
                            maxLines = 8,
                        )
                    }
                }
            }
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.rp_card_models)) },
                ) {
                    item {
                        RpModelRow(
                            label = stringResource(R.string.rp_model_narrator),
                            modelId = card.modelBindings.narratorModelId,
                            settings = settings,
                            onSelect = { model ->
                                vm.setModels(card.modelBindings.copy(narratorModelId = model.id))
                            },
                        )
                    }
                    item {
                        RpModelRow(
                            label = stringResource(R.string.rp_model_adjudicator),
                            modelId = card.modelBindings.adjudicatorModelId,
                            settings = settings,
                            onSelect = { model ->
                                vm.setModels(card.modelBindings.copy(adjudicatorModelId = model.id))
                            },
                        )
                    }
                    item {
                        RpModelRow(
                            label = stringResource(R.string.rp_model_reviewer),
                            modelId = card.modelBindings.reviewerModelId,
                            settings = settings,
                            onSelect = { model ->
                                vm.setModels(card.modelBindings.copy(reviewerModelId = model.id.takeIf { model.modelId.isNotBlank() }))
                            },
                            allowClear = true,
                        )
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        vm.startSession { sessionId ->
                            navController.navigate(Screen.RpSession(sessionId.toString()))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(stringResource(R.string.rp_card_start))
                }
            }
        }
    }
}

@Composable
private fun RpModelRow(
    label: String,
    modelId: kotlin.uuid.Uuid?,
    settings: me.rerere.rikkahub.data.datastore.Settings,
    onSelect: (Model) -> Unit,
    allowClear: Boolean = false,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        ModelSelector(
            modelId = modelId,
            providers = settings.providers,
            type = ModelType.CHAT,
            allowClear = allowClear,
            onSelect = onSelect,
        )
    }
}

private fun RpPlayMode.displayName(): String = when (this) {
    RpPlayMode.CHARACTER -> "角色"
    RpPlayMode.SIMULATION -> "沙盒"
    RpPlayMode.COLLABORATIVE -> "共创"
    RpPlayMode.MYSTERY -> "推理"
    RpPlayMode.TABLETOP -> "跑团"
    RpPlayMode.CUSTOM -> "自定义"
}
