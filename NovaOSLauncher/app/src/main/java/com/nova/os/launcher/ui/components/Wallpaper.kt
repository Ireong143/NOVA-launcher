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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
 
/** 시안의 부드러운 물결 배경(라이트/다크/블루) 색 구성. */
class WaveStyle(
    val baseTop: Color,
    val baseBottom: Color,
    val glowA: Color,
    val glowB: Color,
    /** 물결 4겹의 채움 색(알파 포함). */
    val layers: List<Color>,
    val edge: Color,
    val edgeAlpha: Float,
)
 
val SandWaveLight = WaveStyle(
    baseTop = Color(0xFFDCDFF8),
    baseBottom = Color(0xFFF4F5FD),
    glowA = Color(0xFFFFFFFF),
    glowB = Color(0xFFAAB6F2),
    layers = listOf(Color(0xA6FFFFFF), Color(0x7A9FB0F2), Color(0x99FFFFFF), Color(0x66AEBBF3)),
    edge = Color(0xFFFFFFFF),
    edgeAlpha = 0.85f,
)
 
val SandWaveDark = WaveStyle(
    baseTop = Color(0xFF040A1C),
    baseBottom = Color(0xFF0B1630),
    glowA = Color(0xFF1B3A8C),
    glowB = Color(0xFF0E2A6B),
    layers = listOf(Color(0x990F2A66), Color(0x8C1B3F94), Color(0x800A1C4A), Color(0x802650B8)),
    edge = Color(0xFF7B96FF),
    edgeAlpha = 0.55f,
)
 
val SandWaveBlue = WaveStyle(
    baseTop = Color(0xFF1D63F5),
    baseBottom = Color(0xFF2E7DFF),
    glowA = Color(0xFF8DBBFF),
    glowB = Color(0xFF1448D8),
    layers = listOf(Color(0x805E9BFF), Color(0x59FFFFFF), Color(0x801648E0), Color(0x668CBBFF)),
    edge = Color(0xFFFFFFFF),
    edgeAlpha = 0.6f,
)
 
/** [y0, c1x, c1y, c2x, c2y, y1] — 화면 크기에 대한 비율. 왼쪽 끝에서 오른쪽 끝까지 이어지는 곡선. */
private val WaveRibbons = listOf(
    floatArrayOf(0.24f, 0.30f, 0.20f, 0.55f, 0.06f, 0.05f),
    floatArrayOf(0.52f, 0.28f, 0.48f, 0.55f, 0.30f, 0.28f),
    floatArrayOf(0.78f, 0.30f, 0.84f, 0.55f, 0.52f, 0.50f),
    floatArrayOf(0.96f, 0.35f, 0.92f, 0.62f, 0.74f, 0.70f),
)
 
@Composable
fun WaveBackground(style: WaveStyle, animated: Boolean, modifier: Modifier = Modifier) {
    val phase: State<Float> = if (animated) {
        rememberInfiniteTransition(label = "wave").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse),
            label = "wavePhase",
        )
    } else {
        remember { mutableFloatStateOf(0.5f) }
    }
    Canvas(modifier.fillMaxSize()) {
        val p = phase.value
        val w = size.width
        val h = size.height
        drawRect(
            Brush.linearGradient(
                colors = listOf(style.baseTop, style.baseBottom),
                start = Offset(0f, 0f),
                end = Offset(w * 0.4f, h),
            )
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(style.glowA.copy(alpha = 0.55f), style.glowA.copy(alpha = 0f)),
                center = Offset(w * 0.85f, h * 0.12f),
                radius = w * 0.9f,
            ),
            radius = w * 0.9f,
            center = Offset(w * 0.85f, h * 0.12f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(style.glowB.copy(alpha = 0.50f), style.glowB.copy(alpha = 0f)),
                center = Offset(w * 0.10f, h * 0.62f),
                radius = w * 1.0f,
            ),
            radius = w * 1.0f,
            center = Offset(w * 0.10f, h * 0.62f),
        )
 
        val drift = (p - 0.5f) * h * 0.03f
        WaveRibbons.forEachIndexed { i, r ->
            val dy = drift * (if (i % 2 == 0) 1f else -1f) * (1f + i * 0.3f)
            val y0 = r[0] * h + dy
            val c1x = r[1] * w
            val c1y = r[2] * h - dy
            val c2x = r[3] * w
            val c2y = r[4] * h + dy
            val y1 = r[5] * h - dy
 
            val fillColor = style.layers[i % style.layers.size]
            val fill = Path()
            fill.moveTo(0f, y0)
            fill.cubicTo(c1x, c1y, c2x, c2y, w, y1)
            fill.lineTo(w, h)
            fill.lineTo(0f, h)
            fill.close()
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    colors = listOf(fillColor, fillColor.copy(alpha = 0f)),
                    startY = minOf(y0, y1),
                    endY = h,
                ),
            )
 
            val curve = Path()
            curve.moveTo(0f, y0)
            curve.cubicTo(c1x, c1y, c2x, c2y, w, y1)
            drawPath(
                path = curve,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        style.edge.copy(alpha = 0f),
                        style.edge.copy(alpha = style.edgeAlpha),
                        style.edge.copy(alpha = style.edgeAlpha * 0.2f),
                    ),
                ),
                style = Stroke(width = 1.2.dp.toPx()),
            )
        }
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
            val animated = settings.animatedWallpaper && settings.animationsEnabled
            // wave_system: 시스템(테마) 라이트/다크를 따라간다. 나머지는 고정.
            val wave = when (preset.id) {
                "wave_system" -> if (c.isDark) SandWaveDark else SandWaveLight
                "wave_light" -> SandWaveLight
                "wave_dark" -> SandWaveDark
                "wave_blue" -> SandWaveBlue
                else -> null
            }
            if (wave != null) {
                WaveBackground(style = wave, animated = animated)
            } else {
                AuroraBackground(
                    palette = if (c.isDark) preset.dark else preset.light,
                    animated = animated,
                )
            }
        }
    }
}
 
