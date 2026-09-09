package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.hugeicons.stroke.GitFork
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Share04
import me.rerere.hugeicons.stroke.TextSelection
import me.rerere.hugeicons.stroke.VolumeHigh
import me.rerere.hugeicons.stroke.WebDesign01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.ASSISTANT_TOOLBAR_BUTTONS
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.MessageToolbarButton
import me.rerere.rikkahub.data.datastore.MessageToolbarConfig
import me.rerere.rikkahub.data.datastore.USER_TOOLBAR_BUTTONS
import me.rerere.rikkahub.data.datastore.resolvedToolbar
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingMessageToolbarPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings(settings.copy(displaySetting = setting))
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_page_message_toolbar)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_page_message_toolbar_hint)) },
                    )
                }
            }
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_message_toolbar_user)) },
                ) {
                    USER_TOOLBAR_BUTTONS.forEach { button ->
                        toolbarToggle(button, displaySetting.userMessageToolbar) { next ->
                            updateDisplaySetting(displaySetting.copy(userMessageToolbar = next))
                        }
                    }
                }
            }
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_message_toolbar_assistant)) },
                ) {
                    ASSISTANT_TOOLBAR_BUTTONS.forEach { button ->
                        toolbarToggle(button, displaySetting.resolvedToolbar(MessageRole.ASSISTANT)) { next ->
                            updateDisplaySetting(
                                displaySetting.copy(
                                    assistantMessageToolbar = next,
                                    showContinueOnAssistantToolbar = next.isOnToolbar(MessageToolbarButton.CONTINUE),
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun CardGroupScope.toolbarToggle(
    button: MessageToolbarButton,
    config: MessageToolbarConfig,
    onChange: (MessageToolbarConfig) -> Unit,
) {
    item(
        headlineContent = { Text(buttonTitle(button)) },
        leadingContent = {
            Icon(imageVector = button.icon(), contentDescription = null)
        },
        supportingContent = {
            Text(
                stringResource(
                    if (config.isOnToolbar(button)) {
                        R.string.setting_page_message_toolbar_on_bar
                    } else {
                        R.string.setting_page_message_toolbar_in_more
                    }
                )
            )
        },
        trailingContent = {
            Switch(
                checked = config.isOnToolbar(button),
                onCheckedChange = { onChange(config.toggle(button)) },
            )
        },
    )
}

@Composable
private fun buttonTitle(button: MessageToolbarButton): String = when (button) {
    MessageToolbarButton.COPY -> stringResource(R.string.copy)
    MessageToolbarButton.FORK -> stringResource(R.string.create_fork)
    MessageToolbarButton.REGENERATE -> stringResource(R.string.regenerate)
    MessageToolbarButton.CONTINUE -> stringResource(R.string.continue_generation)
    MessageToolbarButton.TTS -> stringResource(R.string.tts)
    MessageToolbarButton.EDIT -> stringResource(R.string.edit)
    MessageToolbarButton.SHARE -> stringResource(R.string.share)
    MessageToolbarButton.SELECT_AND_COPY -> stringResource(R.string.select_and_copy)
    MessageToolbarButton.WEB_VIEW_PREVIEW -> stringResource(R.string.render_with_webview)
    MessageToolbarButton.DELETE -> stringResource(R.string.delete)
}

private fun MessageToolbarButton.icon(): ImageVector = when (this) {
    MessageToolbarButton.COPY -> HugeIcons.Copy01
    MessageToolbarButton.FORK -> HugeIcons.GitFork
    MessageToolbarButton.REGENERATE -> HugeIcons.Refresh03
    MessageToolbarButton.CONTINUE -> HugeIcons.ArrowRight01
    MessageToolbarButton.TTS -> HugeIcons.VolumeHigh
    MessageToolbarButton.EDIT -> HugeIcons.Edit01
    MessageToolbarButton.SHARE -> HugeIcons.Share04
    MessageToolbarButton.SELECT_AND_COPY -> HugeIcons.TextSelection
    MessageToolbarButton.WEB_VIEW_PREVIEW -> HugeIcons.WebDesign01
    MessageToolbarButton.DELETE -> HugeIcons.Delete01
}
