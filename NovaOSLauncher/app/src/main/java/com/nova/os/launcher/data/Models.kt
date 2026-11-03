
package com.nova.os.launcher.data

import android.content.ComponentName
import android.os.UserHandle
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/** Full-screen layers that can be shown above the home screen. */
enum class Overlay {
    None, Drawer, Control, Settings,
    Calculator, Notes, Clock, Apps, About, Search
}

@Immutable
data class AppInfo(
    val key: String,
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    val component: ComponentName,
    val user: UserHandle,
    val isSystem: Boolean,
    val versionName: String,
    val installTime: Long,
)

enum class ThemeMode(val label: String) { SYSTEM("시스템"), LIGHT("라이트"), DARK("다크") }
enum class ClockStyle(val label: String) { LARGE("큰 시계"), COMPACT("컴팩트") }
enum class SortMode(val label: String) {
    NAME_ASC("이름 가나다순"),
    NAME_DESC("이름 역순"),
    RECENT("최근 설치순"),
    FREQUENT("자주 사용순"),
}

@Immutable
data class NovaSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentIndex: Int = 0,
    val wallpaperId: String = "aurora",
    val animatedWallpaper: Boolean = true,
    val iconSizeDp: Int = 58,
    val showLabels: Boolean = true,
    val fontScale: Float = 1f,
    val animationsEnabled: Boolean = true,
    val animationSpeed: Float = 1f,
    val homePages: Int = 2,
    val clockStyle: ClockStyle = ClockStyle.LARGE,
    val use24h: Boolean = true,
    val showWeather: Boolean = true,
    val showSearchBar: Boolean = true,
    val showDock: Boolean = true,
    val favColumns: Int = 4,
    val drawerColumns: Int = 4,
    val useLocationWeather: Boolean = false,
    val sort: SortMode = SortMode.NAME_ASC,
    val favorites: List<String> = emptyList(),
    val dockApps: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val setupDone: Boolean = false,
)

@Immutable
data class Note(
    val id: Long,
    val text: String,
    val updatedAt: Long,
    val pinned: Boolean = false,
) {
    val title: String
        get() = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty().ifEmpty { "새 메모" }
    val preview: String
        get() = text.lineSequence().filter { it.isNotBlank() }.drop(1).joinToString(" ").trim()
}

enum class WeatherStatus { LOADING, READY, ERROR }

@Immutable
data class WeatherState(
    val status: WeatherStatus = WeatherStatus.LOADING,
    val tempC: Int = 0,
    val high: Int = 0,
    val low: Int = 0,
    val code: Int = 0,
    val isDay: Boolean = true,
    val place: String = "",
    val updatedAt: Long = 0L,
) {
    val emoji: String
        get() = when (code) {
            0 -> if (isDay) "☀️" else "🌙"
            1, 2 -> if (isDay) "🌤️" else "☁️"
            3 -> "☁️"
            45, 48 -> "🌫️"
            in 51..57 -> "🌦️"
            in 61..67, in 80..82 -> "🌧️"
            in 71..77, 85, 86 -> "❄️"
            in 95..99 -> "⛈️"
            else -> "🌡️"
        }

    val description: String
        get() = when (code) {
            0 -> "맑음"
            1 -> "대체로 맑음"
            2 -> "구름 조금"
            3 -> "흐림"
            45, 48 -> "안개"
            in 51..57 -> "이슬비"
            in 61..67 -> "비"
            in 71..77 -> "눈"
            in 80..82 -> "소나기"
            85, 86 -> "눈 소나기"
            in 95..99 -> "뇌우"
            else -> "날씨"
        }
}

@Immutable
data class BatteryState(
    val percent: Int = 0,
    val charging: Boolean = false,
    val tempC: Float = 0f,
    val plugged: Boolean = false,
    val health: String = "",
    val valid: Boolean = false,
)
