package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.Select
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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_advanced_appearance_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 全局背景
            item {
                CardGroup(
                    title = { Text(stringResource(R.string.setting_advanced_appearance_global_background_section)) },
                    colors = CustomColors.cardColorsOnSurfaceContainer
                ) {
                    FormItem(
                        modifier = Modifier.padding(12.dp),
                        label = { Text(stringResource(R.string.setting_advanced_appearance_global_background_enabled)) },
                        description = { Text(stringResource(R.string.setting_advanced_appearance_global_background_enabled_desc)) },
                        tail = {
                            Switch(
                                checked = appearance.enableGlobalBackground,
                                onCheckedChange = { checked ->
                                    vm.updateAdvancedAppearance { it.copy(enableGlobalBackground = checked) }
                                }
                            )
                        }
                    )

                    if (appearance.enableGlobalBackground) {
                        HorizontalDivider()

                        BackgroundPicker(
                            modifier = Modifier.padding(12.dp),
                            background = appearance.globalBackground,
                            backgroundOpacity = appearance.globalBackgroundOpacity,
                            onUpdate = { uri ->
                                vm.updateAdvancedAppearance { it.copy(globalBackground = uri) }
                            }
                        )

                        if (!appearance.globalBackground.isNullOrBlank()) {
                            HorizontalDivider()

                            // 背景不透明度
                            FormItem(
                                modifier = Modifier.padding(12.dp),
                                label = { Text(stringResource(R.string.setting_advanced_appearance_background_opacity)) },
                                description = { Text(stringResource(R.string.setting_advanced_appearance_background_opacity_desc)) }
                            ) {
                                Slider(
                                    value = appearance.globalBackgroundOpacity.coerceIn(0f, 1f),
                                    onValueChange = { value ->
                                        vm.updateAdvancedAppearance { it.copy(globalBackgroundOpacity = value) }
                                    },
                                    valueRange = 0f..1f,
                                    steps = 19,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = stringResource(
                                        R.string.setting_advanced_appearance_background_opacity_value,
                                        (appearance.globalBackgroundOpacity * 100).roundToInt()
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f),
                                )
                            }

                            HorizontalDivider()

                            // 背景虚化半径
                            FormItem(
                                modifier = Modifier.padding(12.dp),
                                label = { Text(stringResource(R.string.setting_advanced_appearance_background_blur)) },
                                description = { Text(stringResource(R.string.setting_advanced_appearance_background_blur_desc)) }
                            ) {
                                Slider(
                                    value = appearance.globalBackgroundBlurRadius.coerceIn(0f, 50f),
                                    onValueChange = { value ->
                                        vm.updateAdvancedAppearance { it.copy(globalBackgroundBlurRadius = value) }
                                    },
                                    valueRange = 0f..50f,
                                    steps = 25,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = stringResource(
                                        R.string.setting_advanced_appearance_background_blur_value,
                                        appearance.globalBackgroundBlurRadius.roundToInt()
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f),
                                )
                            }

                            HorizontalDivider()

                            // 页面表面透光度（容器透明度）
                            FormItem(
                                modifier = Modifier.padding(12.dp),
                                label = { Text(stringResource(R.string.setting_advanced_appearance_page_surface_opacity)) },
                                description = { Text(stringResource(R.string.setting_advanced_appearance_page_surface_opacity_desc)) }
                            ) {
                                Slider(
                                    value = appearance.pageSurfaceOpacity.coerceIn(0.35f, 1f),
                                    onValueChange = { value ->
                                        vm.updateAdvancedAppearance { it.copy(pageSurfaceOpacity = value) }
                                    },
                                    valueRange = 0.35f..1f,
                                    steps = 13,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = stringResource(
                                        R.string.setting_advanced_appearance_page_surface_opacity_value,
                                        (appearance.pageSurfaceOpacity * 100).roundToInt()
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f),
                                )
                            }

                            HorizontalDivider()

                            // 应用到聊天主界面
                            FormItem(
                                modifier = Modifier.padding(12.dp),
                                label = { Text(stringResource(R.string.setting_advanced_appearance_global_background_apply_chat)) },
                                description = { Text(stringResource(R.string.setting_advanced_appearance_global_background_apply_chat_desc)) },
                                tail = {
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
                    title = { Text(stringResource(R.string.setting_advanced_appearance_chat_input_section)) },
                    colors = CustomColors.cardColorsOnSurfaceContainer
                ) {
                    FormItem(
                        modifier = Modifier.padding(12.dp),
                        label = { Text(stringResource(R.string.setting_advanced_appearance_composer_material)) }
                    ) {
                        Select(
                            value = appearance.composerMaterial,
                            onValueChange = { material ->
                                vm.updateAdvancedAppearance { it.copy(composerMaterial = material) }
                            },
                            values = ChatComposerMaterial.entries,
                            valueToText = { material ->
                                when (material) {
                                    ChatComposerMaterial.TRANSLUCENT -> stringResource(R.string.setting_advanced_appearance_composer_material_translucent)
                                    ChatComposerMaterial.FROSTED -> stringResource(R.string.setting_advanced_appearance_composer_material_frosted)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    HorizontalDivider()

                    FormItem(
                        modifier = Modifier.padding(12.dp),
                        label = { Text(stringResource(R.string.setting_advanced_appearance_composer_opacity)) }
                    ) {
                        Slider(
                            value = appearance.composerOpacity.coerceIn(0.2f, 1f),
                            onValueChange = { value ->
                                vm.updateAdvancedAppearance { it.copy(composerOpacity = value) }
                            },
                            valueRange = 0.2f..1f,
                            steps = 16,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = stringResource(
                                R.string.setting_advanced_appearance_composer_opacity_value,
                                (appearance.composerOpacity * 100).roundToInt()
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f),
                        )
                    }

                    if (appearance.composerMaterial == ChatComposerMaterial.FROSTED) {
                        HorizontalDivider()

                        FormItem(
                            modifier = Modifier.padding(12.dp),
                            label = { Text(stringResource(R.string.setting_advanced_appearance_composer_blur)) }
                        ) {
                            Slider(
                                value = appearance.composerBlurRadius.coerceIn(0f, 30f),
                                onValueChange = { value ->
                                    vm.updateAdvancedAppearance { it.copy(composerBlurRadius = value) }
                                },
                                valueRange = 0f..30f,
                                steps = 15,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = stringResource(
                                    R.string.setting_advanced_appearance_composer_blur_value,
                                    appearance.composerBlurRadius.roundToInt()
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
            }

            // 3. 聊天气泡样式与透明度
            item {
                CardGroup(
                    title = { Text(stringResource(R.string.setting_advanced_appearance_bubble_style_section)) },
                    colors = CustomColors.cardColorsOnSurfaceContainer
                ) {
                    FormItem(
                        modifier = Modifier.padding(12.dp),
                        label = { Text(stringResource(R.string.setting_advanced_appearance_bubble_style)) }
                    ) {
                        Select(
                            value = appearance.chatBubbleStyle,
                            onValueChange = { style ->
                                vm.updateAdvancedAppearance { it.copy(chatBubbleStyle = style) }
                            },
                            values = ChatBubbleStyle.entries,
                            valueToText = { style ->
                                when (style) {
                                    ChatBubbleStyle.DEFAULT -> stringResource(R.string.setting_advanced_appearance_bubble_style_default)
                                    ChatBubbleStyle.OUTLINED -> stringResource(R.string.setting_advanced_appearance_bubble_style_outlined)
                                    ChatBubbleStyle.FROSTED -> stringResource(R.string.setting_advanced_appearance_bubble_style_frosted)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    HorizontalDivider()

                    FormItem(
                        modifier = Modifier.padding(12.dp),
                        label = { Text(stringResource(R.string.setting_advanced_appearance_bubble_opacity)) }
                    ) {
                        Slider(
                            value = appearance.bubbleOpacity.coerceIn(0.2f, 1f),
                            onValueChange = { value ->
                                vm.updateAdvancedAppearance { it.copy(bubbleOpacity = value) }
                            },
                            valueRange = 0.2f..1f,
                            steps = 16,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = stringResource(
                                R.string.setting_advanced_appearance_bubble_opacity_value,
                                (appearance.bubbleOpacity * 100).roundToInt()
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
    }
}
