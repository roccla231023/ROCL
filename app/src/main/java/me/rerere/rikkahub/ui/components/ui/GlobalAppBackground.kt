package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun GlobalAppBackground(
    background: String,
    opacity: Float,
    blurRadius: Float,
    modifier: Modifier = Modifier,
) {
    val safeOpacity = opacity.coerceIn(0f, 1f)
    val safeBlurRadius = blurRadius.coerceIn(0f, 50f)
    val backgroundColor = MaterialTheme.colorScheme.background.copy(alpha = 1f)

    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        AsyncImage(
            model = background,
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
                .alpha(safeOpacity),
        )
        // 渐变可读性遮罩，保证高反差壁纸下文字与图标依然清晰
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            backgroundColor.copy(alpha = 0.28f),
                            backgroundColor.copy(alpha = 0.42f),
                        )
                    )
                )
        )
    }
}

@Composable
fun GlobalGlassTheme(
    active: Boolean,
    surfaceOpacity: Float = 0.68f,
    content: @Composable () -> Unit,
) {
    if (!active) {
        content()
        return
    }

    val baseScheme = MaterialTheme.colorScheme
    val safeSurfaceOpacity = surfaceOpacity.coerceIn(0.35f, 1f)
    val glassScheme = remember(baseScheme, safeSurfaceOpacity) {
        baseScheme.copy(
            background = baseScheme.background.copy(alpha = 0.06f),
            surface = baseScheme.surface.copy(alpha = safeSurfaceOpacity),
            surfaceDim = baseScheme.surfaceDim.copy(alpha = (safeSurfaceOpacity + 0.06f).coerceAtMost(1f)),
            surfaceBright = baseScheme.surfaceBright.copy(alpha = safeSurfaceOpacity),
            surfaceContainerLowest = baseScheme.surfaceContainerLowest.copy(
                alpha = (safeSurfaceOpacity - 0.20f).coerceAtLeast(0.24f)
            ),
            surfaceContainerLow = baseScheme.surfaceContainerLow.copy(alpha = 1f),
            surfaceContainer = baseScheme.surfaceContainer.copy(alpha = safeSurfaceOpacity),
            surfaceContainerHigh = baseScheme.surfaceContainerHigh.copy(
                alpha = (safeSurfaceOpacity + 0.04f).coerceAtMost(1f)
            ),
            surfaceContainerHighest = baseScheme.surfaceContainerHighest.copy(
                alpha = (safeSurfaceOpacity + 0.08f).coerceAtMost(1f)
            ),
            surfaceVariant = baseScheme.surfaceVariant.copy(alpha = safeSurfaceOpacity),
            outline = baseScheme.outline.copy(alpha = 0.46f),
            outlineVariant = baseScheme.outlineVariant.copy(alpha = 0.30f),
        )
    }

    MaterialTheme(
        colorScheme = glassScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
    ) {
        content()
    }
}
