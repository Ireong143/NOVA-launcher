package com.nova.os.launcher.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.nova.os.launcher.data.NovaSettings
import com.nova.os.launcher.data.ThemeMode

/** NOVA design tokens. Every screen reads colors from here instead of using raw Material colors. */
@Immutable
data class NovaColors(
    val isDark: Boolean,
    val accent: Color,
    val onAccent: Color,
    val text: Color,
    val textSecondary: Color,
    val glass: Color,
    val glassStrong: Color,
    val glassBorder: Color,
    val sheet: Color,
    val panel: Color,
    val danger: Color,
    val good: Color,
    val warn: Color,
) {
    val accentSoft: Color get() = accent.copy(alpha = 0.22f)
}

/** 끝에서 길게 감속하는 부드러운 곡선(easeOutQuint 계열). 모든 화면 전환의 기본값. */
val SandEase: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

class NovaMotion(val enabled: Boolean, val speed: Float) {
    // 기본 길이를 35% 늘려 모든 모션이 더 느긋하고 부드럽게 보이게 한다.
    fun ms(base: Int): Int =
        if (!enabled) 0 else (base * 1.35f / speed.coerceIn(0.3f, 3f))
            .toInt()
            .coerceAtLeast(1)

    fun <T> spec(base: Int, easing: Easing = SandEase): TweenSpec<T> =
        tween(durationMillis = ms(base), easing = easing)
}

class AccentOption(val name: String, val color: Color)

val AccentPalette = listOf(
    AccentOption("오로라", Color(0xFF7C9CFF)),
    AccentOption("바이올렛", Color(0xFFA78BFA)),
    AccentOption("민트", Color(0xFF3DDBB0)),
    AccentOption("코랄", Color(0xFFFF7F6E)),
    AccentOption("앰버", Color(0xFFFFB84D)),
    AccentOption("로즈", Color(0xFFFF72AE)),
)

/** palette = [base, blob1, blob2, blob3] */
class WallpaperPreset(
    val id: String,
    val name: String,
    val dark: List<Color>,
    val light: List<Color>
)

val Wallpapers = listOf(
    // SAND 물결 배경 3종(라이트/다크/블루) (Wallpaper.kt의 WaveBackground가 그린다). 미리보기용 팔레트.
    WallpaperPreset(
        "wave_light", "SAND 라이트",
        listOf(
            Color(0xFFEDEFFC),
            Color(0xFFB5C0F4),
            Color(0xFFFFFFFF),
            Color(0xFF9FB0F2)
        ),
        listOf(
            Color(0xFFEDEFFC),
            Color(0xFFB5C0F4),
            Color(0xFFFFFFFF),
            Color(0xFF9FB0F2)
        ),
    ),
    WallpaperPreset(
        "wave_dark", "SAND 다크",
        listOf(
            Color(0xFF040A1C),
            Color(0xFF1B3F94),
            Color(0xFF2650B8),
            Color(0xFF7B96FF)
        ),
        listOf(
            Color(0xFF040A1C),
            Color(0xFF1B3F94),
            Color(0xFF2650B8),
            Color(0xFF7B96FF)
        ),
    ),
    WallpaperPreset(
        "wave_blue", "SAND 블루",
        listOf(
            Color(0xFF1D63F5),
            Color(0xFF5E9BFF),
            Color(0xFF1648E0),
            Color(0xFF8CBBFF)
        ),
        listOf(
            Color(0xFF1D63F5),
            Color(0xFF5E9BFF),
            Color(0xFF1648E0),
            Color(0xFF8CBBFF)
        ),
    ),
    WallpaperPreset(
        "aurora", "오로라",
        listOf(
            Color(0xFF070B1A),
            Color(0xFF5B6CFF),
            Color(0xFF9B5CFF),
            Color(0xFF18C8D8)
        ),
        listOf(
            Color(0xFFEEF1FF),
            Color(0xFF9DB0FF),
            Color(0xFFD2B8FF),
            Color(0xFF9FE3EE)
        ),
    ),
    WallpaperPreset(
        "sunset", "선셋",
        listOf(
            Color(0xFF140A14),
            Color(0xFFFF6B4A),
            Color(0xFFFF3D87),
            Color(0xFF7B3DFF)
        ),
        listOf(
            Color(0xFFFFF1E8),
            Color(0xFFFFB38F),
            Color(0xFFFF9EBF),
            Color(0xFFC9B0FF)
        ),
    ),
    WallpaperPreset(
        "emerald", "에메랄드",
        listOf(
            Color(0xFF06130F),
            Color(0xFF18C98B),
            Color(0xFF0FA3B1),
            Color(0xFF7BE35A)
        ),
        listOf(
            Color(0xFFEAF8F1),
            Color(0xFF8FE3BE),
            Color(0xFF8FD6E0),
            Color(0xFFCDF0A0)
        ),
    ),
    WallpaperPreset(
        "ocean", "오션",
        listOf(
            Color(0xFF050D1C),
            Color(0xFF1D6BFF),
            Color(0xFF0FB5E8),
            Color(0xFF3B3FD8)
        ),
        listOf(
            Color(0xFFEAF3FF),
            Color(0xFF9CC4FF),
            Color(0xFFA2E2F7),
            Color(0xFFB5B8F5)
        ),
    ),
    WallpaperPreset(
        "rose", "로즈",
        listOf(
            Color(0xFF160812),
            Color(0xFFFF4D9D),
            Color(0xFFB93DFF),
            Color(0xFFFF7A5A)
        ),
        listOf(
            Color(0xFFFFEEF5),
            Color(0xFFFFA3CB),
            Color(0xFFE0AEFF),
            Color(0xFFFFC2B0)
        ),
    ),
    WallpaperPreset(
        "graphite", "그라파이트",
        listOf(
            Color(0xFF0A0B0E),
            Color(0xFF3A4152),
            Color(0xFF22262F),
            Color(0xFF5A6377)
        ),
        listOf(
            Color(0xFFF1F2F5),
            Color(0xFFD3D8E3),
            Color(0xFFE3E6EE),
            Color(0xFFC4CBDA)
        ),
    ),
)

fun wallpaperById(id: String): WallpaperPreset =
    Wallpapers.firstOrNull { it.id == id } ?: Wallpapers[0]

val LocalNovaColors = staticCompositionLocalOf<NovaColors> {
    error("NovaTheme is missing")
}

val LocalNovaMotion = staticCompositionLocalOf {
    NovaMotion(enabled = true, speed = 1f)
}

/** SAND 블루 테마가 선택돼 있는지. 유리 질감을 파랗게 바꾸는 데 쓴다. */
val LocalNovaBlue = staticCompositionLocalOf { false }

private fun buildColors(
    dark: Boolean,
    base: Color,
    blue: Boolean,
    tint: Color?
): NovaColors =
    if (dark) {
        NovaColors(
            isDark = true,
            accent = if (blue) Color(0xFFBFD8FF) else base,
            onAccent = Color(0xFF0B1020),
            text = Color(0xFFF4F6FF),
            textSecondary = Color(0xB3F4F6FF),
            glass = if (blue) Color(0x33FFFFFF) else Color(0x1FFFFFFF),
            glassStrong = if (blue) Color(0x4DFFFFFF) else Color(0x33FFFFFF),
            glassBorder = Color(0x40FFFFFF),
            // 현재 배경 색을 반투명하게 얹은 유리 시트(뒤의 흐려진 배경이 비친다)
            sheet = tint?.copy(alpha = 0.62f) ?: Color(0xE60A0C16),
            panel = if (blue) Color(0xFF1A4FD0) else Color(0xFF14172B),
            danger = Color(0xFFFF6B6B),
            good = Color(0xFF4ADE80),
            warn = Color(0xFFFBBF24),
        )
    } else {
        NovaColors(
            isDark = false,
            accent = lerp(base, Color.Black, 0.28f),
            onAccent = Color.White,
            text = Color(0xFF12141C),
            textSecondary = Color(0xAA12141C),
            glass = Color(0x99FFFFFF),
            glassStrong = Color(0xCCFFFFFF),
            glassBorder = Color(0xCCFFFFFF),
            sheet = tint?.copy(alpha = 0.66f) ?: Color(0xEBF4F5FA),
            panel = Color(0xFFF7F8FC),
            danger = Color(0xFFDC2626),
            good = Color(0xFF16A34A),
            warn = Color(0xFFD97706),
        )
    }

private fun buildScheme(c: NovaColors, blue: Boolean): ColorScheme =
    if (c.isDark) {
        darkColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            secondary = c.accent,
            background = if (blue) Color(0xFF1648E0) else Color(0xFF0A0C16),
            onBackground = c.text,
            surface = c.panel,
            onSurface = c.text,
            surfaceVariant = if (blue) Color(0xFF2A5FE0) else Color(0xFF232842),
            onSurfaceVariant = c.textSecondary,
            surfaceContainer = if (blue) Color(0xFF1A4FD0) else Color(0xFF1B1F36),
            surfaceContainerHigh = if (blue) Color(0xFF2457DD) else Color(0xFF1F2440),
            outline = c.glassBorder,
            error = c.danger,
        )
    } else {
        lightColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            secondary = c.accent,
            background = Color(0xFFF4F5FA),
            onBackground = c.text,
            surface = c.panel,
            onSurface = c.text,
            surfaceVariant = Color(0xFFE6E8F2),
            onSurfaceVariant = c.textSecondary,
            surfaceContainer = Color(0xFFFFFFFF),
            surfaceContainerHigh = Color(0xFFFFFFFF),
            outline = Color(0x33000000),
            error = c.danger,
        )
    }

@Composable
fun NovaTheme(settings: NovaSettings, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()

    // SAND 라이트/다크/블루 배경을 고르면 글자색도 그 배경에 맞춰 고정(블루는 다크 계열 글자).
    val dark = when (settings.wallpaperId) {
        "wave_light", "wave_system" -> false // wave_system: 예전에 저장된 값은 라이트로 취급
        "wave_dark", "wave_blue" -> true
        else -> when (settings.themeMode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    }

    val base = AccentPalette[
        settings.accentIndex.coerceIn(0, AccentPalette.lastIndex)
    ].color

    val blue = settings.wallpaperId == "wave_blue"

    // 현재 배경화면의 기본 색(사진 배경은 제외). 모든 앱 화면의 유리 시트 색으로 쓴다.
    val tint: Color? = remember(settings.wallpaperId, dark) {
        if (settings.wallpaperId == "custom") null
        else wallpaperById(settings.wallpaperId).let {
            if (dark) it.dark[0] else it.light[0]
        }
    }

    val colors = remember(dark, base, blue, tint) {
        buildColors(dark, base, blue, tint)
    }

    val scheme = remember(colors, blue) {
        buildScheme(colors, blue)
    }

    val motion = remember(settings.animationsEnabled, settings.animationSpeed) {
        NovaMotion(settings.animationsEnabled, settings.animationSpeed)
    }

    CompositionLocalProvider(
        LocalNovaColors provides colors,
        LocalNovaMotion provides motion,
        LocalNovaBlue provides blue,
        LocalContentColor provides colors.text,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(),
            content = content
        )
    }
}
