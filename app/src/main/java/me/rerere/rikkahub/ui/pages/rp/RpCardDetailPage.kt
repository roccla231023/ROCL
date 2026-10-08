package me.rerere.rikkahub.ui.pages.rp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.rp.model.RpCard
import me.rerere.rikkahub.data.rp.model.RpPlayMode
import me.rerere.rikkahub.data.rp.model.RpStateField
import me.rerere.rikkahub.data.rp.model.RpStateFieldType
import me.rerere.rikkahub.data.rp.model.RpStateSchema
import me.rerere.rikkahub.data.rp.model.RpViewField
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.rikkahub.utils.plus
import kotlinx.serialization.json.jsonObject
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun RpCardDetailPage(id: String) {
    val vm: RpCardDetailVM = koinViewModel(parameters = { parametersOf(id) })
    val card by vm.card.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    var advancedExpanded by remember(card.id) { mutableStateOf(false) }
    var stateText by remember(card.id) { mutableStateOf(JsonInstantPretty.encodeToString(card.initialState)) }
    var schemaText by remember(card.id) { mutableStateOf(card.stateSchema.toEditorText()) }
    var schemaHasInvalidLines by remember(card.id) { mutableStateOf(false) }
    var viewFieldsText by remember(card.id) {
        mutableStateOf(card.viewFields.joinToString("\n") { "${it.path}=${it.label}" })
    }
    var hiddenPathsText by remember(card.id) { mutableStateOf(card.hiddenStatePaths.joinToString("\n")) }
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
                    title = { Text(stringResource(R.string.rp_card_basic)) },
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
                            value = card.description,
                            onValueChange = { vm.update(card.copy(description = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.rp_card_description)) },
                            minLines = 2,
                            maxLines = 4,
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
                    item(
                        supportingContent = {
                            Text(
                                stringResource(R.string.rp_card_mode),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        headlineContent = {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RpPlayMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = card.mode == mode,
                                        onClick = { vm.update(card.copy(mode = mode)) },
                                        label = { Text(mode.localizedName()) },
                                    )
                                }
                            }
                        },
                    )
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
                    modifier = Modifier.padding(horizontal = 8.dp).animateContentSize(),
                    title = { Text(stringResource(R.string.rp_card_models)) },
                ) {
                    item(
                        supportingContent = {
                            Text(stringResource(R.string.rp_model_narrator_desc))
                        },
                        trailingContent = {
                            RpModelSelector(
                                modelId = card.modelBindings.narratorModelId,
                                settings = settings,
                                onSelect = { vm.setModels(card.modelBindings.copy(narratorModelId = it)) },
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.rp_model_narrator)) },
                    )
                    item(
                        supportingContent = {
                            Text(stringResource(R.string.rp_model_adjudicator_desc))
                        },
                        trailingContent = {
                            RpModelSelector(
                                modelId = card.modelBindings.adjudicatorModelId,
                                settings = settings,
                                onSelect = { vm.setModels(card.modelBindings.copy(adjudicatorModelId = it)) },
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.rp_model_adjudicator)) },
                    )
                    item(
                        supportingContent = {
                            Text(stringResource(R.string.rp_model_reviewer_desc))
                        },
                        trailingContent = {
                            RpModelSelector(
                                modelId = card.modelBindings.reviewerModelId,
                                settings = settings,
                                onSelect = { vm.setModels(card.modelBindings.copy(reviewerModelId = it)) },
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.rp_model_reviewer)) },
                    )
                    item(
                        supportingContent = {
                            Text(stringResource(R.string.rp_model_state_keeper_desc))
                        },
                        trailingContent = {
                            RpModelSelector(
                                modelId = card.modelBindings.stateKeeperModelId,
                                settings = settings,
                                onSelect = { vm.setModels(card.modelBindings.copy(stateKeeperModelId = it)) },
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.rp_model_state_keeper)) },
                    )
                    item(
                        supportingContent = {
                            Text(stringResource(R.string.rp_model_follow_global))
                        },
                        headlineContent = { Text(stringResource(R.string.rp_card_models_desc)) },
                    )
                }
            }
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp).animateContentSize(),
                ) {
                    item(
                        onClick = { advancedExpanded = !advancedExpanded },
                        headlineContent = { Text(stringResource(R.string.rp_card_advanced)) },
                        supportingContent = { Text(stringResource(R.string.rp_card_advanced_desc)) },
                    )
                }
            }
            item {
                AnimatedVisibility(visible = advancedExpanded) {
                    CardGroup(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        title = { Text(stringResource(R.string.rp_card_advanced)) },
                    ) {
                        item {
                            OutlinedTextField(
                                value = stateText,
                                onValueChange = { value ->
                                    stateText = value
                                    runCatching { JsonInstant.parseToJsonElement(value).jsonObject }
                                        .onSuccess { vm.update(card.copy(initialState = it)) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.rp_card_state_json)) },
                                minLines = 5,
                                maxLines = 12,
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = schemaText,
                                onValueChange = { value ->
                                    schemaText = value
                                    val parsed = value.parseStateSchema()
                                    schemaHasInvalidLines = parsed.hadInvalidLines
                                    if (!parsed.hadInvalidLines) {
                                        vm.update(card.copy(stateSchema = parsed.schema))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.rp_card_state_schema)) },
                                supportingText = {
                                    Text(
                                        if (schemaHasInvalidLines) {
                                            stringResource(R.string.rp_card_state_schema_invalid)
                                        } else {
                                            stringResource(R.string.rp_card_state_schema_help)
                                        }
                                    )
                                },
                                minLines = 5,
                                maxLines = 12,
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = hiddenPathsText,
                                onValueChange = { value ->
                                    hiddenPathsText = value
                                    vm.update(
                                        card.copy(
                                            hiddenStatePaths = value.lines()
                                                .map(String::trim)
                                                .filter(String::isNotBlank)
                                                .toSet(),
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.rp_card_hidden_paths_advanced)) },
                                minLines = 2,
                                maxLines = 6,
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = viewFieldsText,
                                onValueChange = { value ->
                                    viewFieldsText = value
                                    vm.update(card.copy(viewFields = value.parseViewFields()))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.rp_card_view_fields)) },
                                minLines = 2,
                                maxLines = 6,
                            )
                        }
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
private fun RpModelSelector(
    modelId: kotlin.uuid.Uuid?,
    settings: Settings,
    onSelect: (kotlin.uuid.Uuid?) -> Unit,
) {
    ModelSelector(
        modelId = modelId,
        providers = settings.providers,
        type = ModelType.CHAT,
        allowClear = true,
        onSelect = { model -> onSelect(model.modelId.takeIf(String::isNotBlank)?.let { model.id }) },
    )
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

private data class ParsedStateSchema(
    val schema: RpStateSchema,
    val hadInvalidLines: Boolean,
)

private fun String.parseStateSchema(): ParsedStateSchema {
    val fields = mutableListOf<RpStateField>()
    var hadInvalidLines = false
    lines().forEach { line ->
        if (line.isBlank()) return@forEach
        val parts = line.split('|').map(String::trim)
        if (parts.size < 3) {
            hadInvalidLines = true
            return@forEach
        }
        val path = parts[0]
        val label = parts[1]
        val type = parts[2].uppercase().let { value ->
            runCatching { RpStateFieldType.valueOf(value) }.getOrNull()
        }
        val validPath = path.matches(Regex("[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*"))
        if (!validPath || label.isBlank() || type == null) {
            hadInvalidLines = true
            return@forEach
        }
        val min = parts.getOrNull(4)?.takeIf(String::isNotBlank)?.toDoubleOrNull()
        val max = parts.getOrNull(5)?.takeIf(String::isNotBlank)?.toDoubleOrNull()
        if ((parts.getOrNull(4)?.isNotBlank() == true && min == null) ||
            (parts.getOrNull(5)?.isNotBlank() == true && max == null)
        ) {
            hadInvalidLines = true
            return@forEach
        }
        fields += RpStateField(
            path = path,
            label = label,
            type = type,
            group = parts.getOrNull(3)?.takeIf(String::isNotBlank) ?: "状态",
            min = min,
            max = max,
        )
    }
    return ParsedStateSchema(RpStateSchema(fields), hadInvalidLines)
}

private fun RpStateSchema.toEditorText(): String = fields.joinToString("\n") { field ->
    listOf(
        field.path,
        field.label,
        field.type.name,
        field.group,
        field.min?.toString().orEmpty(),
        field.max?.toString().orEmpty(),
    ).joinToString(" | ")
}

private fun String.parseViewFields(): List<RpViewField> = lines().mapNotNull { line ->
    val separator = line.indexOf('=')
    if (separator <= 0) return@mapNotNull null
    val path = line.substring(0, separator).trim()
    val label = line.substring(separator + 1).trim()
    if (path.isBlank() || label.isBlank()) null else RpViewField(path, label)
}
