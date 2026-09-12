package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.ChatBubbleStyle
import me.rerere.rikkahub.data.datastore.ChatComposerMaterial
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.pages.assistant.detail.BackgroundPicker
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

@Composable
fun SettingPreferencesAdvancedAppearancePage(
    vm: SettingVM = koinViewModel()
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val appearance = settings.advancedAppearanceSetting
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_advanced_appearance_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 全局背景
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_advanced_appearance_global_background_section)) },
                ) {
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_advanced_appearance_global_background_enabled)) },
                        supportingContent = { Text(stringResource(R.string.setting_advanced_appearance_global_background_enabled_desc)) },
                        trailingContent = {
                            Switch(
                                checked = appearance.enableGlobalBackground,
                                onCheckedChange = { checked ->
                                    vm.updateAdvancedAppearance { it.copy(enableGlobalBackground = checked) }
                                }
                            )
                        }
                    )

                    if (appearance.enableGlobalBackground) {
                        item(
                            headlineContent = {
                                BackgroundPicker(
                                    background = appearance.globalBackground,
                                    backgroundOpacity = appearance.globalBackgroundOpacity,
                                    onUpdate = { uri ->
                                        vm.updateAdvancedAppearance { it.copy(globalBackground = uri) }
                                    }
                                )
                            }
                        )

                        if (!appearance.globalBackground.isNullOrBlank()) {
                            item(
                                headlineContent = { Text(stringResource(R.string.setting_advanced_appearance_background_opacity)) },
                                supportingContent = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Slider(
                                            value = appearance.globalBackgroundOpacity.coerceIn(0f, 1f),
                                            onValueChange = { value ->
                                                vm.updateAdvancedAppearance { it.copy(globalBackgroundOpacity = value) }
                                            },
                                            valueRange = 0f..1f,
                                            steps = 19,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(text = "${(appearance.globalBackgroundOpacity * 100).roundToInt()}%")
                                    }
                                }
                            )

                            item(
                                headlineContent = { Text(stringResource(R.string.setting_advanced_appearance_background_blur)) },
                                supportingContent = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Slider(
                                            value = appearance.globalBackgroundBlurRadius.coerceIn(0f, 50f),
                                            onValueChange = { value ->
                                                vm.updateAdvancedAppearance { it.copy(globalBackgroundBlurRadius = value) }
                                            },
                                            valueRange = 0f..50f,
                                            steps = 25,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(text = "${appearance.globalBackgroundBlurRadius.roundToInt()} dp")
                                    }
                                }
                            )

                            item(
                                headlineContent = { Text(stringResource(R.string.setting_advanced_appearance_page_surface_opacity)) },
                                supportingContent = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Slider(
                                            value = appearance.pageSurfaceOpacity.coerceIn(0.35f, 1f),
                                            onValueChange = { value ->
                                                vm.updateAdvancedAppearance { it.copy(pageSurfaceOpacity = value) }
                                            },
                                            valueRange = 0.35f..1f,
                                            steps = 13,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(text = "${(appearance.pageSurfaceOpacity * 100).roundToInt()}%")
                                    }
                                }
                            )

                            item(
                                headlineContent = { Text(stringResource(R.string.setting_advanced_appearance_global_background_apply_chat)) },
                                supportingContent = { Text(stringResource(R.string.setting_advanced_appearance_global_background_apply_chat_desc)) },
                                trailingContent = {
                                    Switch(
                                        checked = appearance.applyGlobalBackgroundToChat,
                                        onCheckedChange = { checked ->
                                            vm.updateAdvancedAppearance { it.copy(applyGlobalBackgroundToChat = checked) }
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // 2. 聊天输入框材质与透明度
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_advanced_appearance_chat_input_section)) },
                ) {
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_advanced_appearance_composer_material)) },
                        supportingContent = {
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                ChatComposerMaterial.entries.forEachIndexed { index, material ->
                                    SegmentedButton(
                                        selected = appearance.composerMaterial == material,
                                        onClick = {
                                            vm.updateAdvancedAppearance { it.copy(composerMaterial = material) }
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = index,
                                            count = ChatComposerMaterial.entries.size
                                        ),
                                        icon = {},
                                        label = {
                                            Text(
                                                text = when (material) {
                                                    ChatComposerMaterial.TRANSLUCENT -> stringResource(R.string.setting_advanced_appearance_composer_material_translucent)
                                                    ChatComposerMaterial.FROSTED -> stringResource(R.string.setting_advanced_appearance_composer_material_frosted)
                                                },
                                                maxLines = 1,
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    )

                    item(
                        headlineContent = { Text(stringResource(R.string.setting_advanced_appearance_composer_opacity)) },
                        supportingContent = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Slider(
                                    value = appearance.composerOpacity.coerceIn(0.2f, 1f),
                                    onValueChange = { value ->
                                        vm.updateAdvancedAppearance { it.copy(composerOpacity = value) }
                                    },
                                    valueRange = 0.2f..1f,
                                    steps = 16,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(text = "${(appearance.composerOpacity * 100).roundToInt()}%")
                            }
                        }
                    )

                    if (appearance.composerMaterial == ChatComposerMaterial.FROSTED) {
                        item(
                            headlineContent = { Text(stringResource(R.string.setting_advanced_appearance_composer_blur)) },
                            supportingContent = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Slider(
                                        value = appearance.composerBlurRadius.coerceIn(0f, 30f),
                                        onValueChange = { value ->
                                            vm.updateAdvancedAppearance { it.copy(composerBlurRadius = value) }
                                        },
                                        valueRange = 0f..30f,
                                        steps = 15,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(text = "${appearance.composerBlurRadius.roundToInt()} dp")
                                }
                            }
                        )
                    }
                }
            }

            // 3. 聊天气泡样式与透明度
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_advanced_appearance_bubble_style_section)) },
                ) {
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_advanced_appearance_bubble_style)) },
                        supportingContent = {
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                ChatBubbleStyle.entries.forEachIndexed { index, style ->
                                    SegmentedButton(
                                        selected = appearance.chatBubbleStyle == style,
                                        onClick = {
                                            vm.updateAdvancedAppearance { it.copy(chatBubbleStyle = style) }
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = index,
                                            count = ChatBubbleStyle.entries.size
                                        ),
                                        icon = {},
                                        label = {
                                            Text(
                                                text = when (style) {
                                                    ChatBubbleStyle.DEFAULT -> stringResource(R.string.setting_advanced_appearance_bubble_style_default)
                                                    ChatBubbleStyle.OUTLINED -> stringResource(R.string.setting_advanced_appearance_bubble_style_outlined)
                                                    ChatBubbleStyle.FROSTED -> stringResource(R.string.setting_advanced_appearance_bubble_style_frosted)
                                                },
                                                maxLines = 1,
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    )

                    item(
                        headlineContent = { Text(stringResource(R.string.setting_advanced_appearance_bubble_opacity)) },
                        supportingContent = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Slider(
                                    value = appearance.bubbleOpacity.coerceIn(0.2f, 1f),
                                    onValueChange = { value ->
                                        vm.updateAdvancedAppearance { it.copy(bubbleOpacity = value) }
                                    },
                                    valueRange = 0.2f..1f,
                                    steps = 16,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(text = "${(appearance.bubbleOpacity * 100).roundToInt()}%")
                            }
                        }
                    )
                }
            }
        }
    }
}
