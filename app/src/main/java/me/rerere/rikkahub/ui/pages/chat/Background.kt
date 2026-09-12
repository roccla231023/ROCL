package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getCurrentAssistant

@Composable
fun AssistantBackground(setting: Settings, isGroupChat: Boolean, modifier: Modifier) {
    val appearance = setting.advancedAppearanceSetting
    if (appearance.enableGlobalBackground && appearance.applyGlobalBackgroundToChat && !appearance.globalBackground.isNullOrBlank()) {
        val backgroundColor = MaterialTheme.colorScheme.background
        val safeOpacity = appearance.globalBackgroundOpacity.coerceIn(0f, 1f)
        val safeBlurRadius = appearance.globalBackgroundBlurRadius.coerceIn(0f, 50f)
        Box(modifier = modifier.fillMaxSize()) {
            AsyncImage(
                model = appearance.globalBackground,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (safeBlurRadius > 0f) {
                            Modifier.blur(
                                radius = safeBlurRadius.dp,
                                edgeTreatment = BlurredEdgeTreatment.Rectangle,
                            )
                        } else {
                            Modifier
                        }
                    )
                    .alpha(safeOpacity)
            )

            // 全屏渐变遮罩
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                backgroundColor.copy(alpha = 0.28f),
                                backgroundColor.copy(alpha = 0.42f)
                            )
                        )
                    )
            )
        }
        return
    }

    if (isGroupChat) return

    val assistant = setting.getCurrentAssistant()
    if (assistant.useGradientBackground) {
        MeshGradientBackground(modifier = modifier)
        return
    }
    if (assistant.background != null) {
        val backgroundColor = MaterialTheme.colorScheme.background
        val backgroundOpacity = assistant.backgroundOpacity.coerceIn(0f, 1f)
        Box(modifier = modifier) {
            AsyncImage(
                model = assistant.background,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(backgroundOpacity)
            )

            // 全屏渐变遮罩
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                backgroundColor.copy(alpha = 0.2f),
                                backgroundColor.copy(alpha = 0.5f)
                            )
                        )
                    )
            )
        }
    }
}
