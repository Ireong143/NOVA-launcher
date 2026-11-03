package com.nova.os.launcher.ui.settings

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.launcher.NovaViewModel
import com.nova.os.launcher.data.ClockStyle
import com.nova.os.launcher.data.Overlay
import com.nova.os.launcher.data.SortMode
import com.nova.os.launcher.data.ThemeMode
import com.nova.os.launcher.data.WeatherRepository
import com.nova.os.launcher.system.SystemActions
import com.nova.os.launcher.ui.components.AuroraBackground
import com.nova.os.launcher.ui.components.GlassCard
import com.nova.os.launcher.ui.components.NovaSegmented
import com.nova.os.launcher.ui.components.NovaStepper
import com.nova.os.launcher.ui.components.SectionLabel
import com.nova.os.launcher.ui.components.SettingRow
import com.nova.os.launcher.ui.components.SwitchRow
import com.nova.os.launcher.ui.components.ToolScaffold
import com.nova.os.launcher.ui.theme.AccentPalette
import com.nova.os.launcher.ui.theme.LocalNovaColors
import com.nova.os.launcher.ui.theme.Wallpapers
import kotlin.math.roundToInt

private val SortShortLabels = listOf("가나다", "역순", "최근 설치", "자주 사용")

@Composable
fun SettingsScreen(vm: NovaViewModel) {
    val s by vm.settings.collectAsState()
    val custom by vm.customWallpaper.collectAsState()
    val c = LocalNovaColors.current
    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    var iconSize by remember(s.iconSizeDp) { mutableFloatStateOf(s.iconSizeDp.toFloat()) }
    var fontScale by remember(s.fontScale) { mutableFloatStateOf(s.fontScale) }
    val isDefaultHome = remember(vm.resumeTick) { SystemActions.isDefaultHome(context) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) vm.setCustomWallpaper(uri)
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            vm.update { it.copy(useLocationWeather = true) }
            vm.refreshWeather(force = true)
        } else {
            vm.toast("위치 권한이 없어 대략적인 위치(IP)로 날씨를 보여줘요")
        }
    }

    ToolScaffold("설정", onBack = { vm.back() }) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ------------------------------------------------ theme
            SectionLabel("테마")
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    NovaSegmented(
                        options = ThemeMode.values().map { it.label },
                        selected = s.themeMode.ordinal,
                        onSelect = { i -> vm.update { it.copy(themeMode = ThemeMode.values()[i]) } },
                    )
                    Text("강조 색상", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(AccentPalette.size) { i ->
                            val selected = i == s.accentIndex
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(AccentPalette[i].color)
                                    .border(
                                        if (selected) 3.dp else 1.dp,
                                        if (selected) c.text else c.glassBorder,
                                        CircleShape,
                                    )
                                    .clickable { vm.update { it.copy(accentIndex = i) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) Icon(Icons.Rounded.Check, "선택됨", Modifier.size(20.dp), tint = Color.Black)
                            }
                        }
                    }
                }
            }

            // ------------------------------------------------ wallpaper
            SectionLabel("배경화면")
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(Wallpapers) { wp ->
                            WallpaperThumb(selected = s.wallpaperId == wp.id, onClick = {
                                vm.update { it.copy(wallpaperId = wp.id) }
                            }) {
                                AuroraBackground(if (c.isDark) wp.dark else wp.light, animated = false)
                            }
                        }
                        item {
                            WallpaperThumb(selected = s.wallpaperId == "custom", onClick = {
                                if (custom != null) vm.update { it.copy(wallpaperId = "custom") }
                                else pickImage.launch("image/*")
                            }) {
                                val img = custom
                                if (img != null) {
                                    Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Icon(
                                        Icons.Rounded.PhotoLibrary, "내 사진",
                                        Modifier.align(Alignment.Center).size(28.dp), tint = c.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                    SettingRow(
                        Icons.Rounded.PhotoLibrary, "갤러리에서 선택", "내 사진을 NOVA 배경화면으로 사용",
                        onClick = { pickImage.launch("image/*") },
                    )
                    SwitchRow(
                        Icons.Rounded.Wallpaper, "움직이는 배경", "오로라 배경이 천천히 움직여요",
                        s.animatedWallpaper,
                    ) { v -> vm.update { it.copy(animatedWallpaper = v) } }
                    SettingRow(
                        Icons.Rounded.Home, "시스템 배경화면 설정", "Android 기본 배경화면 선택기를 열어요",
                        onClick = { SystemActions.openSystemWallpaper(context) },
                    )
                }
            }

            // ------------------------------------------------ icons & text
            SectionLabel("아이콘 · 글자")
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingRow(Icons.Rounded.Apps, "아이콘 크기", "${iconSize.roundToInt()}dp")
                    Slider(
                        value = iconSize,
                        onValueChange = { iconSize = it },
                        onValueChangeFinished = { vm.update { st -> st.copy(iconSizeDp = iconSize.roundToInt()) } },
                        valueRange = 44f..76f,
                    )
                    SettingRow(Icons.Rounded.FormatSize, "글자 크기", "${(fontScale * 100).roundToInt()}%")
                    Slider(
                        value = fontScale,
                        onValueChange = { fontScale = it },
                        onValueChangeFinished = { vm.update { st -> st.copy(fontScale = fontScale) } },
                        valueRange = 0.85f..1.3f,
                        steps = 8,
                    )
                    SwitchRow(Icons.Rounded.Tune, "앱 이름 표시", null, s.showLabels) { v ->
                        vm.update { it.copy(showLabels = v) }
                    }
                    SettingRow(Icons.Rounded.Dashboard, "앱 서랍 열 수", "한 줄에 보이는 앱 개수") {
                        NovaStepper(s.drawerColumns, 3..6) { v -> vm.update { it.copy(drawerColumns = v) } }
                    }
                }
            }

            // ------------------------------------------------ animation
            SectionLabel("애니메이션")
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SwitchRow(Icons.Rounded.Speed, "애니메이션 사용", "끄면 모든 전환이 즉시 이루어져요", s.animationsEnabled) { v ->
                        vm.update { it.copy(animationsEnabled = v) }
                    }
                    val speedIndex = when {
                        s.animationSpeed < 0.85f -> 0
                        s.animationSpeed > 1.25f -> 2
                        else -> 1
                    }
                    NovaSegmented(
                        options = listOf("느리게", "보통", "빠르게"),
                        selected = speedIndex,
                        onSelect = { i ->
                            vm.update { it.copy(animationSpeed = floatArrayOf(0.7f, 1f, 1.6f)[i]) }
                        },
                    )
                }
            }

            // ------------------------------------------------ home layout
            SectionLabel("홈 화면 구성")
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("홈 화면 페이지", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    NovaSegmented(
                        options = listOf("1페이지", "2페이지", "3페이지"),
                        selected = s.homePages.coerceIn(1, 3) - 1,
                        onSelect = { i -> vm.update { it.copy(homePages = i + 1) } },
                    )
                    Text(
                        "1: 시계·즐겨찾기  /  2: 위젯  /  3: 자주 쓰는 앱",
                        fontSize = 12.sp, color = c.textSecondary,
                    )
                    Text("시계 스타일", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    NovaSegmented(
                        options = ClockStyle.values().map { it.label },
                        selected = s.clockStyle.ordinal,
                        onSelect = { i -> vm.update { it.copy(clockStyle = ClockStyle.values()[i]) } },
                    )
                    SwitchRow(Icons.Rounded.Schedule, "24시간 형식", null, s.use24h) { v ->
                        vm.update { it.copy(use24h = v) }
                    }
                    SwitchRow(Icons.Rounded.Cloud, "날씨 표시", null, s.showWeather) { v ->
                        vm.update { it.copy(showWeather = v) }
                    }
                    SwitchRow(
                        Icons.Rounded.LocationOn, "정확한 위치로 날씨 보기",
                        "끄면 IP 기반 대략적인 위치를 사용해요", s.useLocationWeather,
                    ) { v ->
                        if (!v) {
                            vm.update { it.copy(useLocationWeather = false) }
                            vm.refreshWeather(force = true)
                        } else if (WeatherRepository.hasPermission(context)) {
                            vm.update { it.copy(useLocationWeather = true) }
                            vm.refreshWeather(force = true)
                        } else {
                            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    }
                    SwitchRow(Icons.Rounded.Search, "검색창 표시", null, s.showSearchBar) { v ->
                        vm.update { it.copy(showSearchBar = v) }
                    }
                    SwitchRow(Icons.Rounded.Dashboard, "하단 독 표시", null, s.showDock) { v ->
                        vm.update { it.copy(showDock = v) }
                    }
                    SettingRow(Icons.Rounded.Apps, "즐겨찾기 열 수", "홈 화면 앱 배치") {
                        NovaStepper(s.favColumns, 3..5) { v -> vm.update { it.copy(favColumns = v) } }
                    }
                }
            }

            // ------------------------------------------------ drawer
            SectionLabel("앱 서랍 정렬")
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    NovaSegmented(
                        options = SortShortLabels,
                        selected = s.sort.ordinal,
                        onSelect = { i -> vm.update { it.copy(sort = SortMode.values()[i]) } },
                    )
                }
            }

            // ------------------------------------------------ system
            SectionLabel("앱 · 시스템")
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SettingRow(
                        Icons.Rounded.Apps, "앱 관리", "숨긴 앱 ${s.hidden.size}개 · 정보 · 삭제",
                        onClick = { vm.open(Overlay.Apps) },
                    )
                    SettingRow(
                        Icons.Rounded.Home, "기본 홈 앱",
                        if (isDefaultHome) "NOVA OS가 기본 홈 앱이에요" else "탭해서 NOVA OS를 기본 홈 앱으로 설정",
                        onClick = { SystemActions.openDefaultHomeSettings(context) },
                    )
                    SettingRow(
                        Icons.Rounded.Info, "NOVA OS 정보", "버전 · 기기 정보",
                        onClick = { vm.open(Overlay.About) },
                    )
                    SettingRow(
                        Icons.Rounded.RestartAlt, "설정 초기화", "모든 설정을 기본값으로",
                        onClick = { confirmReset = true },
                    )
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = c.panel,
            titleContentColor = c.text,
            textContentColor = c.textSecondary,
            title = { Text("설정 초기화") },
            text = { Text("모든 설정을 기본값으로 되돌릴까요? 메모는 삭제되지 않아요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    vm.resetSettings()
                }) { Text("초기화") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun WallpaperThumb(selected: Boolean, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val c = LocalNovaColors.current
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .size(width = 70.dp, height = 112.dp)
            .clip(shape)
            .background(c.glass)
            .border(if (selected) 3.dp else 1.dp, if (selected) c.accent else c.glassBorder, shape)
            .clickable(onClick = onClick),
    ) {
        content()
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(c.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = c.onAccent)
            }
        }
    }
}
