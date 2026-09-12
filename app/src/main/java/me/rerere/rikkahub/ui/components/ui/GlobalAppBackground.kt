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

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .alpha(safeOpacity)
    ) {
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
                ),
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

internal data class GlobalBackgroundLayer(
    val keepBackgroundMounted: Boolean,
    val glassActive: Boolean,
    val targetBackgroundOpacity: Float,
)

internal fun resolveGlobalBackgroundLayer(
    hasGlobalBackground: Boolean,
    isChatScreen: Boolean,
    backgroundOpacity: Float,
): GlobalBackgroundLayer {
    val shouldShow = hasGlobalBackground && !isChatScreen
    return GlobalBackgroundLayer(
        keepBackgroundMounted = hasGlobalBackground,
        glassActive = shouldShow,
        targetBackgroundOpacity = if (shouldShow) backgroundOpacity.coerceIn(0f, 1f) else 0f,
    )
}

internal data class GlobalGlassAlphas(
    val background: Float,
    val surface: Float,
    val surfaceDim: Float,
    val surfaceBright: Float,
    val surfaceContainerLowest: Float,
    val surfaceContainerLow: Float,
    val surfaceContainer: Float,
    val surfaceContainerHigh: Float,
    val surfaceContainerHighest: Float,
    val surfaceVariant: Float,
    val outline: Float,
    val outlineVariant: Float,
)

internal fun resolveGlobalGlassAlphas(
    active: Boolean,
    surfaceOpacity: Float,
): GlobalGlassAlphas? {
    if (!active) return null
    val safeSurfaceOpacity = surfaceOpacity.coerceIn(0.35f, 1f)
    return GlobalGlassAlphas(
        background = 0.06f,
        surface = safeSurfaceOpacity,
        surfaceDim = (safeSurfaceOpacity + 0.06f).coerceAtMost(1f),
        surfaceBright = safeSurfaceOpacity,
        surfaceContainerLowest = (safeSurfaceOpacity - 0.20f).coerceAtLeast(0.24f),
        surfaceContainerLow = 1f,
        surfaceContainer = safeSurfaceOpacity,
        surfaceContainerHigh = (safeSurfaceOpacity + 0.04f).coerceAtMost(1f),
        surfaceContainerHighest = (safeSurfaceOpacity + 0.08f).coerceAtMost(1f),
        surfaceVariant = safeSurfaceOpacity,
        outline = 0.46f,
        outlineVariant = 0.30f,
    )
}

@Composable
fun GlobalGlassTheme(
    active: Boolean,
    surfaceOpacity: Float = 0.68f,
    content: @Composable () -> Unit,
) {
    val baseScheme = MaterialTheme.colorScheme
    val alphas = resolveGlobalGlassAlphas(active, surfaceOpacity)
    val glassScheme = remember(baseScheme, alphas) {
        if (alphas == null) {
            baseScheme
        } else {
            baseScheme.copy(
                background = baseScheme.background.copy(alpha = alphas.background),
                surface = baseScheme.surface.copy(alpha = alphas.surface),
                surfaceDim = baseScheme.surfaceDim.copy(alpha = alphas.surfaceDim),
                surfaceBright = baseScheme.surfaceBright.copy(alpha = alphas.surfaceBright),
                surfaceContainerLowest = baseScheme.surfaceContainerLowest.copy(
                    alpha = alphas.surfaceContainerLowest
                ),
                surfaceContainerLow = baseScheme.surfaceContainerLow.copy(alpha = alphas.surfaceContainerLow),
                surfaceContainer = baseScheme.surfaceContainer.copy(alpha = alphas.surfaceContainer),
                surfaceContainerHigh = baseScheme.surfaceContainerHigh.copy(alpha = alphas.surfaceContainerHigh),
                surfaceContainerHighest = baseScheme.surfaceContainerHighest.copy(
                    alpha = alphas.surfaceContainerHighest
                ),
                surfaceVariant = baseScheme.surfaceVariant.copy(alpha = alphas.surfaceVariant),
                outline = baseScheme.outline.copy(alpha = alphas.outline),
                outlineVariant = baseScheme.outlineVariant.copy(alpha = alphas.outlineVariant),
            )
        }
    }

    MaterialTheme(
        colorScheme = glassScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
    ) {
        content()
    }
}
