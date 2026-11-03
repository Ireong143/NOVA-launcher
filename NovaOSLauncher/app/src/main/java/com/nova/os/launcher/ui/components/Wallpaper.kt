package com.nova.os.launcher.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nova.os.launcher.data.NovaSettings
import com.nova.os.launcher.ui.theme.LocalNovaColors
import com.nova.os.launcher.ui.theme.wallpaperById

/** Soft "aurora" gradient wallpaper. palette = [base, blob1, blob2, blob3]. */
@Composable
fun AuroraBackground(palette: List<Color>, animated: Boolean, modifier: Modifier = Modifier) {
    val phase: State<Float> = if (animated) {
        rememberInfiniteTransition(label = "aurora").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Reverse),
            label = "auroraPhase",
        )
    } else {
        remember { mutableFloatStateOf(0.5f) }
    }
    Canvas(modifier.fillMaxSize()) {
        val p = phase.value
        val w = size.width
        val h = size.height
        val r = maxOf(w, h)
        drawRect(palette[0])
        fun blob(color: Color, cx: Float, cy: Float, radius: Float, alpha: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
                    center = Offset(cx, cy),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(cx, cy),
            )
        }
        blob(palette[1], w * (0.15f + 0.35f * p), h * (0.18f + 0.10f * p), r * 0.75f, 0.55f)
        blob(palette[2], w * (0.90f - 0.30f * p), h * (0.50f + 0.10f * (1f - p)), r * 0.80f, 0.50f)
        blob(palette[3], w * (0.30f + 0.30f * (1f - p)), h * (0.95f - 0.15f * p), r * 0.70f, 0.45f)
    }
}

/** The system-wide wallpaper layer. Blurs (Android 12+) while an overlay is open. */
@Composable
fun WallpaperLayer(settings: NovaSettings, custom: ImageBitmap?, blurRadius: Dp) {
    val c = LocalNovaColors.current
    val blurModifier = if (blurRadius > 0.5.dp) Modifier.blur(blurRadius) else Modifier
    Box(Modifier.fillMaxSize().then(blurModifier)) {
        if (settings.wallpaperId == "custom" && custom != null) {
            Image(
                bitmap = custom,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (c.isDark) Color(0x59000000) else Color(0x40FFFFFF))
            )
        } else {
            val preset = wallpaperById(settings.wallpaperId)
            AuroraBackground(
                palette = if (c.isDark) preset.dark else preset.light,
                animated = settings.animatedWallpaper && settings.animationsEnabled,
            )
        }
    }
}
