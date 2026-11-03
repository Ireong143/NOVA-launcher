package com.nova.os.launcher.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.nova.os.launcher.NovaViewModel
import com.nova.os.launcher.data.AppInfo
import com.nova.os.launcher.data.BatteryState
import com.nova.os.launcher.data.ClockStyle
import com.nova.os.launcher.data.NovaSettings
import com.nova.os.launcher.data.Overlay
import com.nova.os.launcher.data.WeatherState
import com.nova.os.launcher.data.WeatherStatus
import com.nova.os.launcher.system.SystemActions
import com.nova.os.launcher.ui.components.AppIconCell
import com.nova.os.launcher.ui.components.GlassCard
import com.nova.os.launcher.ui.components.IconBadge
import com.nova.os.launcher.ui.components.Pressable
import com.nova.os.launcher.ui.theme.LocalNovaColors
import com.nova.os.launcher.ui.theme.LocalNovaMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.absoluteValue

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(vm: NovaViewModel) {
    val settings by vm.settings.collectAsState()
    val dockApps by vm.dockApps.collectAsState()
    val battery by vm.battery.collectAsState()
    val scope = rememberCoroutineScope()
    val pageCount = settings.homePages.coerceIn(1, 3)
    val pagerState = rememberPagerState(pageCount = { pageCount })
    var quickMenu by remember { mutableStateOf(false) }

    LaunchedEffect(vm.homeResetTick) {
        if (vm.homeResetTick > 0 && pagerState.currentPage != 0) pagerState.animateScrollToPage(0)
    }
    LaunchedEffect(pageCount) {
        if (pagerState.currentPage >= pageCount) pagerState.scrollToPage(0)
    }
    LaunchedEffect(Unit) { vm.refreshWeather() }
    BackHandler(enabled = pagerState.currentPage != 0 && vm.overlay == Overlay.None) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    Box(
        Modifier
            .fillMaxSize()
            // Swipe up: app drawer. Swipe down: control center.
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        val threshold = 80.dp.toPx()
                        if (total < -threshold) vm.open(Overlay.Drawer)
                        else if (total > threshold) vm.open(Overlay.Control)
                        total = 0f
                    },
                    onDragCancel = { total = 0f },
                    onVerticalDrag = { _, dy -> total += dy },
                )
            }
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            HomeTopBar(battery, onClick = { vm.open(Overlay.Control) })
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                Box(
                    Modifier.graphicsLayer {
                        val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                        alpha = 1f - offset.coerceIn(0f, 1f) * 0.6f
                        val s = 1f - offset.coerceIn(0f, 1f) * 0.06f
                        scaleX = s
                        scaleY = s
                    }
                ) {
                    when (page) {
                        0 -> MainPage(vm, settings) { quickMenu = true }
                        1 -> WidgetsPage(vm) { quickMenu = true }
                        else -> FrequentPage(vm, settings) { quickMenu = true }
                    }
                }
            }
            if (pageCount > 1) PageDots(count = pageCount, current = pagerState.currentPage)
            if (settings.showDock) {
                Dock(vm, dockApps, settings)
            } else {
                Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    DrawerButton(size = 56.dp) { vm.open(Overlay.Drawer) }
                }
            }
        }
        if (quickMenu) QuickMenu(vm, onDismiss = { quickMenu = false })
    }
}

// ---------------------------------------------------------------------------- top bar

@Composable
private fun HomeTopBar(battery: BatteryState, onClick: () -> Unit) {
    val c = LocalNovaColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("NOVA", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, color = c.textSecondary)
        Spacer(Modifier.weight(1f))
        Pressable(
            Modifier.clip(RoundedCornerShape(50)).background(c.glass).border(1.dp, c.glassBorder, RoundedCornerShape(50)),
            onClick = onClick,
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (battery.charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryFull,
                    contentDescription = "배터리",
                    modifier = Modifier.size(18.dp),
                    tint = if (battery.charging) c.good else c.text,
                )
                Spacer(Modifier.width(6.dp))
                Text(if (battery.valid) "${battery.percent}%" else "--", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------------------------------------------------------------------------- pages

@Composable
private fun MainPage(vm: NovaViewModel, settings: NovaSettings, onLongPress: () -> Unit) {
    val favorites by vm.favoriteApps.collectAsState()
    val weather by vm.weather.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        ClockWidget(settings)
        Spacer(Modifier.height(16.dp))
        if (settings.showWeather) {
            WeatherCard(weather, onClick = { vm.refreshWeather(force = true) })
            Spacer(Modifier.height(14.dp))
        }
        if (settings.showSearchBar) {
            SearchPill { vm.open(Overlay.Drawer, focusSearch = true) }
            Spacer(Modifier.height(16.dp))
        }
        FavoritesGrid(vm, favorites, settings)
    }
}

@Composable
private fun FavoritesGrid(vm: NovaViewModel, apps: List<AppInfo>, settings: NovaSettings) {
    val c = LocalNovaColors.current
    val cols = settings.favColumns.coerceIn(3, 5)
    if (apps.isEmpty()) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                "앱 서랍에서 앱을 길게 눌러\n즐겨찾기에 추가해 보세요",
                Modifier.padding(20.dp).fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = c.textSecondary,
                fontSize = 14.sp,
            )
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        apps.take(cols * 5).chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { app ->
                    AppIconCell(app, vm, settings.iconSizeDp.dp, settings.showLabels, Modifier.weight(1f))
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun WidgetsPage(vm: NovaViewModel, onLongPress: () -> Unit) {
    val c = LocalNovaColors.current
    val context = LocalContext.current
    val battery by vm.battery.collectAsState()
    val notes by vm.notes.collectAsState()
    val stats by produceState(initialValue = SystemActions.readStats(context)) {
        while (true) {
            delay(5000)
            value = SystemActions.readStats(context)
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Text("오늘", fontSize = 30.sp, fontWeight = FontWeight.Bold)

        GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                BatteryRing(battery.percent, battery.charging, Modifier.size(76.dp))
                Spacer(Modifier.width(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    StatBar("저장공간", stats.storageUsed, stats.storageTotal)
                    StatBar("메모리", stats.ramUsed, stats.ramTotal)
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), onClick = { vm.open(Overlay.Notes) }) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.EditNote, size = 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("최근 메모", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                val recent = notes.filter { it.text.isNotBlank() }.sortedByDescending { it.updatedAt }.take(3)
                if (recent.isEmpty()) {
                    Text("탭해서 첫 메모를 작성해 보세요", color = c.textSecondary, fontSize = 14.sp)
                } else {
                    recent.forEach {
                        Text(it.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolTile(Icons.Rounded.Calculate, "계산기", Modifier.weight(1f)) { vm.open(Overlay.Calculator) }
            ToolTile(Icons.Rounded.EditNote, "메모", Modifier.weight(1f)) { vm.open(Overlay.Notes) }
            ToolTile(Icons.Rounded.Schedule, "시계", Modifier.weight(1f)) { vm.open(Overlay.Clock) }
            ToolTile(Icons.Rounded.Settings, "설정", Modifier.weight(1f)) { vm.open(Overlay.Settings) }
        }
    }
}

@Composable
fun ToolTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassCard(modifier, shape = RoundedCornerShape(22.dp), onClick = onClick) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconBadge(icon, size = 36.dp)
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
private fun FrequentPage(vm: NovaViewModel, settings: NovaSettings, onLongPress: () -> Unit) {
    val c = LocalNovaColors.current
    val apps by vm.frequentApps.collectAsState()
    val cols = settings.favColumns.coerceIn(3, 5)
    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Text("자주 쓰는 앱", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        if (apps.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    "앱을 실행하면 자주 쓰는 앱이\n이곳에 자동으로 모여요",
                    Modifier.padding(20.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = c.textSecondary,
                    fontSize = 14.sp,
                )
            }
        } else {
            apps.chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { app ->
                        AppIconCell(app, vm, settings.iconSizeDp.dp, settings.showLabels, Modifier.weight(1f))
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------- widgets

@Composable
fun rememberMinuteClock(): State<LocalDateTime> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(LocalDateTime.now()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                state.value = LocalDateTime.now()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        state.value = LocalDateTime.now()
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

@Composable
private fun ClockWidget(settings: NovaSettings) {
    val c = LocalNovaColors.current
    val now by rememberMinuteClock()
    val locale = Locale.getDefault()
    val timeText = remember(now, settings.use24h) {
        now.format(DateTimeFormatter.ofPattern(if (settings.use24h) "HH:mm" else "h:mm", locale))
    }
    val ampm = remember(now) { now.format(DateTimeFormatter.ofPattern("a", locale)) }
    val date = remember(now) { now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)) }

    if (settings.clockStyle == ClockStyle.LARGE) {
        Column(Modifier.padding(start = 2.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    timeText,
                    fontSize = 80.sp,
                    lineHeight = 84.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-3).sp,
                    maxLines = 1,
                    softWrap = false,
                )
                if (!settings.use24h) {
                    Text(
                        ampm,
                        Modifier.padding(start = 8.dp, bottom = 14.dp),
                        fontSize = 20.sp,
                        color = c.textSecondary,
                    )
                }
            }
            Text(date, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = c.textSecondary, maxLines = 1)
        }
    } else {
        Row(Modifier.padding(start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(timeText, fontSize = 44.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp, maxLines = 1)
            if (!settings.use24h) Text(ampm, Modifier.padding(start = 6.dp), fontSize = 16.sp, color = c.textSecondary)
            Spacer(Modifier.width(14.dp))
            Text(date, fontSize = 14.sp, color = c.textSecondary, maxLines = 2, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun WeatherCard(weather: WeatherState, onClick: () -> Unit) {
    val c = LocalNovaColors.current
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), onClick = onClick) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (weather.status) {
                WeatherStatus.READY -> {
                    Text(weather.emoji, fontSize = 36.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${weather.tempC}°", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                        val place = if (weather.place.isNotBlank()) " · ${weather.place}" else ""
                        Text(weather.description + place, fontSize = 13.sp, color = c.textSecondary, maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("최고 ${weather.high}°", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("최저 ${weather.low}°", fontSize = 13.sp, color = c.textSecondary)
                    }
                }
                WeatherStatus.LOADING -> {
                    Text("🌡️", fontSize = 30.sp)
                    Spacer(Modifier.width(14.dp))
                    Text("날씨를 불러오는 중…", color = c.textSecondary, fontSize = 14.sp)
                }
                WeatherStatus.ERROR -> {
                    Text("☁️", fontSize = 30.sp)
                    Spacer(Modifier.width(14.dp))
                    Text("날씨를 불러오지 못했어요\n탭해서 다시 시도", color = c.textSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    val c = LocalNovaColors.current
    GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(50), onClick = onClick) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, Modifier.size(22.dp), tint = c.textSecondary)
            Spacer(Modifier.width(12.dp))
            Text("앱, 웹 검색", color = c.textSecondary, fontSize = 16.sp)
        }
    }
}

// ---------------------------------------------------------------------------- dock & dots

@Composable
private fun PageDots(count: Int, current: Int) {
    val c = LocalNovaColors.current
    val motion = LocalNovaMotion.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val selected = i == current
            val w by animateDpAsState(if (selected) 20.dp else 7.dp, motion.spec(250), label = "dotWidth")
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = w, height = 7.dp)
                    .clip(CircleShape)
                    .background(if (selected) c.text else c.text.copy(alpha = 0.3f))
            )
        }
    }
}

@Composable
private fun Dock(vm: NovaViewModel, dockApps: List<AppInfo>, settings: NovaSettings) {
    GlassCard(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(32.dp),
        strong = true,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val dockIcon = settings.iconSizeDp.coerceAtMost(60).dp
            dockApps.forEach { app ->
                AppIconCell(app, vm, dockIcon, showLabel = false)
            }
            DrawerButton(size = dockIcon) { vm.open(Overlay.Drawer) }
        }
    }
}

@Composable
private fun DrawerButton(size: Dp, onClick: () -> Unit) {
    val c = LocalNovaColors.current
    Pressable(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.linearGradient(listOf(c.accent, c.accent.copy(alpha = 0.65f)))),
        onClick = onClick,
    ) {
        Icon(Icons.Rounded.Apps, "앱 서랍", Modifier.align(Alignment.Center).size(size * 0.5f), tint = c.onAccent)
    }
}

// ---------------------------------------------------------------------------- rings & bars

@Composable
fun BatteryRing(percent: Int, charging: Boolean, modifier: Modifier = Modifier) {
    val c = LocalNovaColors.current
    val color = when {
        charging -> c.good
        percent <= 15 -> c.danger
        percent <= 30 -> c.warn
        else -> c.accent
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2f
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            val style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            drawArc(color.copy(alpha = 0.2f), 0f, 360f, false, topLeft, arcSize, style = style)
            drawArc(color, -90f, 360f * percent.coerceIn(0, 100) / 100f, false, topLeft, arcSize, style = style)
        }
        Text("$percent%", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatBar(label: String, used: Long, total: Long) {
    val c = LocalNovaColors.current
    val fraction = if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(
                "${SystemActions.formatBytes(used)} / ${SystemActions.formatBytes(total)}",
                fontSize = 11.sp,
                color = c.textSecondary,
            )
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(c.text.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(CircleShape).background(c.accent))
        }
    }
}

// ---------------------------------------------------------------------------- quick menu

@Composable
private fun QuickMenu(vm: NovaViewModel, onDismiss: () -> Unit) {
    val c = LocalNovaColors.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(c.panel)
                .border(1.dp, c.glassBorder, RoundedCornerShape(28.dp))
                .padding(10.dp),
        ) {
            QuickMenuItem(Icons.Rounded.Palette, "배경화면 · 테마 변경") { onDismiss(); vm.open(Overlay.Settings) }
            QuickMenuItem(Icons.Rounded.Tune, "홈 화면 설정") { onDismiss(); vm.open(Overlay.Settings) }
            QuickMenuItem(Icons.Rounded.Apps, "앱 관리") { onDismiss(); vm.open(Overlay.Apps) }
        }
    }
}

@Composable
private fun QuickMenuItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    Pressable(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)), onClick = onClick) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon)
            Spacer(Modifier.width(14.dp))
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}
