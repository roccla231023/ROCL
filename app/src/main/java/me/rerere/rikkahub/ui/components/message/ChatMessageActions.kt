package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.datetime.toJavaLocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.hugeicons.stroke.FavouriteCircle
import me.rerere.hugeicons.stroke.GitFork
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Share04
import me.rerere.hugeicons.stroke.StopCircle
import me.rerere.hugeicons.stroke.TextSelection
import me.rerere.hugeicons.stroke.Translate
import me.rerere.hugeicons.stroke.VolumeHigh
import me.rerere.hugeicons.stroke.WebDesign01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.MessageToolbarButton
import me.rerere.rikkahub.data.datastore.resolvedToolbar
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.data.datastore.prepareTtsText
import me.rerere.rikkahub.utils.copyMessageToClipboard
import me.rerere.rikkahub.utils.toLocalString
import me.rerere.rikkahub.utils.toMessageTimeString
import java.util.Locale

@Composable
fun ColumnScope.ChatMessageActionButtons(
    message: UIMessage,
    node: MessageNode,
    onUpdate: (MessageNode) -> Unit,
    onRegenerate: () -> Unit,
    onOpenActionSheet: () -> Unit,
    onContinue: (() -> Unit)? = null,
    onTranslate: ((UIMessage, Locale) -> Unit)? = null,
    onClearTranslation: (UIMessage) -> Unit = {},
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onFork: (() -> Unit)? = null,
    onSelectAndCopy: (() -> Unit)? = null,
    onWebViewPreview: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val toolbar = settings.displaySetting.resolvedToolbar(message.role)
    var showTranslateDialog by remember { mutableStateOf(false) }
    var showRegenerateConfirm by remember { mutableStateOf(false) }
    val hasTextContent = message.parts.filterIsInstance<UIMessagePart.Text>().any { it.text.isNotBlank() }

    fun onBar(button: MessageToolbarButton) = toolbar.isOnToolbar(button)

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        val actionIconColor = MaterialTheme.colorScheme.onSurfaceVariant

        if (onBar(MessageToolbarButton.COPY)) {
            Icon(
                imageVector = HugeIcons.Copy01,
                contentDescription = stringResource(R.string.copy),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { context.copyMessageToClipboard(message) }
                    .padding(8.dp)
                    .size(16.dp),
                tint = actionIconColor
            )
        }

        if (onBar(MessageToolbarButton.FORK) && onFork != null) {
            Icon(
                imageVector = HugeIcons.GitFork,
                contentDescription = stringResource(R.string.create_fork),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onFork() }
                    .padding(8.dp)
                    .size(16.dp),
                tint = actionIconColor
            )
        }

        if (onBar(MessageToolbarButton.REGENERATE)) {
            Icon(
                imageVector = HugeIcons.Refresh03,
                contentDescription = stringResource(R.string.regenerate),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        if (message.role == MessageRole.USER) {
                            showRegenerateConfirm = true
                        } else {
                            onRegenerate()
                        }
                    }
                    .padding(8.dp)
                    .size(16.dp),
                tint = actionIconColor
            )
        }

        if (
            message.role == MessageRole.ASSISTANT &&
            onContinue != null &&
            onBar(MessageToolbarButton.CONTINUE)
        ) {
            Icon(
                imageVector = HugeIcons.ArrowRight01,
                contentDescription = stringResource(R.string.continue_generation),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onContinue() }
                    .padding(8.dp)
                    .size(16.dp),
                tint = actionIconColor
            )
        }

        if (message.role == MessageRole.ASSISTANT && onBar(MessageToolbarButton.TTS)) {
            val tts = LocalTTSState.current
            val isSpeaking by tts.isSpeaking.collectAsState()
            val isAvailable by tts.isAvailable.collectAsState()
            Icon(
                imageVector = if (isSpeaking) HugeIcons.StopCircle else HugeIcons.VolumeHigh,
                contentDescription = stringResource(R.string.tts),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        enabled = isAvailable,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = LocalIndication.current,
                        onClick = {
                            if (!isSpeaking) {
                                val textToSpeak = prepareTtsText(message.toText(), settings.displaySetting)
                                if (textToSpeak.isNotBlank()) tts.speak(textToSpeak)
                            } else {
                                tts.stop()
                            }
                        }
                    )
                    .padding(8.dp)
                    .size(16.dp),
                tint = if (isAvailable) actionIconColor else actionIconColor.copy(alpha = 0.38f)
            )
        }

        if (onBar(MessageToolbarButton.EDIT) && onEdit != null) {
            Icon(
                imageVector = HugeIcons.Edit01,
                contentDescription = stringResource(R.string.edit),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onEdit() }
                    .padding(8.dp)
                    .size(16.dp),
                tint = actionIconColor
            )
        }

        if (onBar(MessageToolbarButton.SHARE) && onShare != null) {
            Icon(
                imageVector = HugeIcons.Share04,
                contentDescription = stringResource(R.string.share),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onShare() }
                    .padding(8.dp)
                    .size(16.dp),
                tint = actionIconColor
            )
        }

        if (onBar(MessageToolbarButton.SELECT_AND_COPY) && onSelectAndCopy != null) {
            Icon(
                imageVector = HugeIcons.TextSelection,
                contentDescription = stringResource(R.string.select_and_copy),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onSelectAndCopy() }
                    .padding(8.dp)
                    .size(16.dp),
                tint = actionIconColor
            )
        }

        if (onBar(MessageToolbarButton.WEB_VIEW_PREVIEW) && onWebViewPreview != null && hasTextContent) {
            Icon(
                imageVector = HugeIcons.WebDesign01,
                contentDescription = stringResource(R.string.render_with_webview),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onWebViewPreview() }
                    .padding(8.dp)
                    .size(16.dp),
                tint = actionIconColor
            )
        }

        if (onBar(MessageToolbarButton.DELETE) && onDelete != null) {
            Icon(
                imageVector = HugeIcons.Delete01,
                contentDescription = stringResource(R.string.delete),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onDelete() }
                    .padding(8.dp)
                    .size(16.dp),
                tint = actionIconColor
            )
        }

        if (message.role == MessageRole.ASSISTANT && onTranslate != null) {
            Icon(
                imageVector = HugeIcons.Translate,
                contentDescription = stringResource(R.string.translate),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = LocalIndication.current,
                        onClick = { showTranslateDialog = true }
                    )
                    .padding(8.dp)
                    .size(16.dp),
                tint = actionIconColor
            )
        }

        Icon(
            imageVector = HugeIcons.MoreVertical,
            contentDescription = stringResource(R.string.more_options),
            modifier = Modifier
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = LocalIndication.current,
                    onClick = { onOpenActionSheet() }
                )
                .padding(8.dp)
                .size(16.dp),
            tint = actionIconColor
        )

        ChatMessageBranchSelector(
            node = node,
            onUpdate = onUpdate,
        )

        if (settings.displaySetting.showDateTimeInMessage) {
            Text(
                text = message.createdAt.toJavaLocalDateTime().toMessageTimeString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                maxLines = 1,
            )
        }
    }

    if (showTranslateDialog && onTranslate != null) {
        LanguageSelectionDialog(
            onLanguageSelected = { language ->
                showTranslateDialog = false
                onTranslate(message, language)
            },
            onClearTranslation = {
                showTranslateDialog = false
                onClearTranslation(message)
            },
            onDismissRequest = {
                showTranslateDialog = false
            },
        )
    }

    RikkaConfirmDialog(
        show = showRegenerateConfirm,
        title = stringResource(R.string.regenerate),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showRegenerateConfirm = false
            onRegenerate()
        },
        onDismiss = { showRegenerateConfirm = false },
        text = { Text(stringResource(R.string.regenerate_confirm_message)) }
    )
}

@Composable
fun ChatMessageActionsSheet(
    message: UIMessage,
    model: Model?,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onFork: () -> Unit,
    onSelectAndCopy: () -> Unit,
    onRegenerate: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onContinue: (() -> Unit)? = null,
    onWebViewPreview: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val toolbar = settings.displaySetting.resolvedToolbar(message.role)
    fun inMore(button: MessageToolbarButton) = !toolbar.isOnToolbar(button)
    val hasTextContent = message.parts.filterIsInstance<UIMessagePart.Text>()
        .any { it.text.isNotBlank() }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (inMore(MessageToolbarButton.SELECT_AND_COPY)) {
                ActionSheetRow(
                    icon = HugeIcons.TextSelection,
                    title = stringResource(R.string.select_and_copy),
                    onClick = {
                        onDismissRequest()
                        onSelectAndCopy()
                    },
                )
            }

            if (inMore(MessageToolbarButton.COPY)) {
                ActionSheetRow(
                    icon = HugeIcons.Copy01,
                    title = stringResource(R.string.copy),
                    onClick = {
                        onDismissRequest()
                        context.copyMessageToClipboard(message)
                    },
                )
            }

            if (inMore(MessageToolbarButton.REGENERATE)) {
                ActionSheetRow(
                    icon = HugeIcons.Refresh03,
                    title = stringResource(R.string.regenerate),
                    onClick = {
                        onDismissRequest()
                        onRegenerate()
                    },
                )
            }

            if (inMore(MessageToolbarButton.CONTINUE) && onContinue != null && message.role == MessageRole.ASSISTANT) {
                ActionSheetRow(
                    icon = HugeIcons.ArrowRight01,
                    title = stringResource(R.string.continue_generation),
                    onClick = {
                        onDismissRequest()
                        onContinue()
                    },
                )
            }

            if (inMore(MessageToolbarButton.TTS) && message.role == MessageRole.ASSISTANT) {
                val tts = LocalTTSState.current
                val isSpeaking by tts.isSpeaking.collectAsState()
                ActionSheetRow(
                    icon = if (isSpeaking) HugeIcons.StopCircle else HugeIcons.VolumeHigh,
                    title = stringResource(R.string.tts),
                    onClick = {
                        if (!isSpeaking) {
                            val textToSpeak = prepareTtsText(message.toText(), settings.displaySetting)
                            if (textToSpeak.isNotBlank()) tts.speak(textToSpeak)
                        } else {
                            tts.stop()
                        }
                        onDismissRequest()
                    },
                )
            }

            if (hasTextContent && inMore(MessageToolbarButton.WEB_VIEW_PREVIEW)) {
                ActionSheetRow(
                    icon = HugeIcons.WebDesign01,
                    title = stringResource(R.string.render_with_webview),
                    onClick = {
                        onDismissRequest()
                        onWebViewPreview()
                    },
                )
            }

            if (inMore(MessageToolbarButton.EDIT)) {
                ActionSheetRow(
                    icon = HugeIcons.Edit01,
                    title = stringResource(R.string.edit),
                    onClick = {
                        onDismissRequest()
                        onEdit()
                    },
                )
            }

            if (inMore(MessageToolbarButton.SHARE)) {
                ActionSheetRow(
                    icon = HugeIcons.Share04,
                    title = stringResource(R.string.share),
                    onClick = {
                        onDismissRequest()
                        onShare()
                    },
                )
            }

            if (inMore(MessageToolbarButton.FORK)) {
                ActionSheetRow(
                    icon = HugeIcons.GitFork,
                    title = stringResource(R.string.create_fork),
                    onClick = {
                        onDismissRequest()
                        onFork()
                    },
                )
            }

            if (onToggleFavorite != null) {
                ActionSheetRow(
                    icon = HugeIcons.FavouriteCircle,
                    title = stringResource(
                        if (isFavorite) R.string.chat_message_remove_favorite
                        else R.string.chat_message_add_favorite
                    ),
                    onClick = {
                        onDismissRequest()
                        onToggleFavorite()
                    },
                )
            }

            if (inMore(MessageToolbarButton.DELETE)) {
                ActionSheetRow(
                    icon = HugeIcons.Delete01,
                    title = stringResource(R.string.delete),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    onClick = {
                        onDismissRequest()
                        onDelete()
                    },
                )
            }

            ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                Text(message.createdAt.toJavaLocalDateTime().toLocalString())
                if (model != null) {
                    Text(model.displayName)
                }
            }
        }
    }
}

@Composable
private fun ActionSheetRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    containerColor: Color? = null,
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = if (containerColor != null) {
            CardDefaults.cardColors(containerColor = containerColor)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(4.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
