package com.nova.os.launcher.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.nova.os.launcher.NovaViewModel
import com.nova.os.launcher.data.*
import com.nova.os.launcher.system.SystemActions
import com.nova.os.launcher.ui.components.*
import com.nova.os.launcher.ui.theme.LocalNovaColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.absoluteValue

private val AuroraWhite = Color(0xFFF7FAFF)
private val AuroraMuted = Color(0xFFC4D2DF)
private val GlassStroke = Color(0x42FFFFFF)

/**
 * 시안의 유리 패널 질감: 위→아래로 옅어지는 반투명 채움 + 위쪽이 밝은 테두리.
 */
private fun Modifier.homeGlass(
    shape: Shape,
    top: Color = Color(0x38FFFFFF),
    bottom: Color = Color(0x12FFFFFF)
): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(top, bottom)))
    .border(
        1.dp,
        Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x14FFFFFF))),
        shape
    )

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
        if (vm.homeResetTick > 0 && pagerState.currentPage != 0) {
            pagerState.animateScrollToPage(0)
        }
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
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF10252B),
                        Color(0xFF142D39),
                        Color(0xFF111C29),
                        Color(0xFF101D1C)
                    )
                )
            )
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
                    onVerticalDrag = { _, dy -> total += dy }
                )
            }
    ) {
        // 상단 청록빛 오로라 조명
        Box(
            Modifier
                .fillMaxWidth()
                .height(430.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x6655D8C4),
                            Color(0x334A9EB5),
                            Color.Transparent
                        ),
                        radius = 920f
                    )
                )
        )

        // 오른쪽 중앙의 은은한 푸른빛
        Box(
            Modifier
                .fillMaxWidth()
                .height(510.dp)
                .align(Alignment.TopCenter)
                .offset(x = 78.dp, y = 170.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x454D78C4),
                            Color(0x203F5B91),
                            Color.Transparent
                        ),
                        radius = 960f
                    )
                )
        )

        // 하단의 어두운 청록빛
        Box(
            Modifier
                .fillMaxWidth()
                .height(430.dp)
                .align(Alignment.BottomCenter)
                .offset(x = (-65).dp, y = (-35).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x3339B7A2), Color.Transparent),
                        radius = 900f
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 시안처럼 로고·배터리·검색창을 하나의 유리 패널로 묶음.
            // 검색창은 기존처럼 첫 페이지 + 설정(showSearchBar)이 켜져 있을 때만 표시.
            HomeTopBar(
                battery = battery,
                showSearch = settings.showSearchBar && pagerState.currentPage == 0,
                onSearch = { vm.openSearch() },
                onBattery = { vm.open(Overlay.Control) }
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { page ->
                Box(
                    Modifier.graphicsLayer {
                        val offset = (
                            (pagerState.currentPage - page) +
                                pagerState.currentPageOffsetFraction
                            ).absoluteValue
                        alpha = 1f - offset.coerceIn(0f, 1f) * 0.6f
                        val scale = 1f - offset.coerceIn(0f, 1f) * 0.06f
                        scaleX = scale
                        scaleY = scale
                    }
                ) {
                    when (page) {
                        0 -> MainPage(vm, settings) { quickMenu = true }
                        1 -> WidgetsPage(vm) { quickMenu = true }
                        else -> FrequentPage(vm, settings) { quickMenu = true }
                    }
                }
            }

            if (pageCount > 1) {
                PageDots(count = pageCount, current = pagerState.currentPage)
            }

            if (settings.showDock) {
                Dock(vm, dockApps, settings)
            } else {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    DrawerButton(size = 56.dp) { vm.open(Overlay.Drawer) }
                }
            }
        }

        if (quickMenu) QuickMenu(vm) { quickMenu = false }
    }
}

@Composable
private fun HomeTopBar(
    battery: BatteryState,
    showSearch: Boolean,
    onSearch: () -> Unit,
    onBattery: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize()
            .homeGlass(RoundedCornerShape(36.dp))
            .padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "S A N D   O S",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.8.sp,
                color = AuroraWhite
            )

            Spacer(Modifier.weight(1f))

            Pressable(
                Modifier.clip(CircleShape),
                onClick = onBattery
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (battery.charging) Icons.Rounded.BatteryChargingFull
                        else Icons.Rounded.BatteryFull,
                        contentDescription = "배터리",
                        modifier = Modifier.size(20.dp),
                        tint = if (battery.charging) Color(0xFF8DE8C6) else Color.White
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (battery.valid) "${battery.percent}%" else "--",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        if (showSearch) {
            Spacer(Modifier.height(8.dp))
            SearchPill(onSearch)
        }
    }
}

@Composable
private fun MainPage(vm: NovaViewModel, settings: NovaSettings, onLongPress: () -> Unit) {
    val favorites by vm.favoriteApps.collectAsState()
    val weather by vm.weather.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress() })
            }
            .padding(horizontal = 16.dp)
    ) {
        if (settings.showWeather) {
            WeatherCard(weather) { vm.refreshWeather(force = true) }
            Spacer(Modifier.height(26.dp))
        } else {
            Spacer(Modifier.height(8.dp))
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
                fontSize = 14.sp
            )
        }
        return
    }

    // 스크린샷처럼 첫 홈 화면에는 두 줄의 즐겨찾기 앱을 배치
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        apps.take(cols * 2).chunked(cols).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                row.forEach { app ->
                    AppIconCell(
                        app,
                        vm,
                        settings.iconSizeDp.dp,
                        settings.showLabels,
                        Modifier.weight(1f)
                    )
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
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress() })
            }
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            "오늘",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = AuroraWhite
        )

        GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp)) {
            Row(
                Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BatteryRing(battery.percent, battery.charging, Modifier.size(76.dp))
                Spacer(Modifier.width(18.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    StatBar("저장공간", stats.storageUsed, stats.storageTotal)
                    StatBar("메모리", stats.ramUsed, stats.ramTotal)
                }
            }
        }

        GlassCard(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            onClick = { vm.open(Overlay.Notes) }
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.EditNote, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("최근 메모", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = AuroraWhite)
                        Text("나의 기록", fontSize = 12.sp, color = AuroraMuted)
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = AuroraMuted)
                }

                val recent = notes.filter { it.text.isNotBlank() }
                    .sortedByDescending { it.updatedAt }.take(3)

                if (recent.isEmpty()) {
                    Text("탭해서 첫 메모를 작성해 보세요", color = AuroraMuted, fontSize = 14.sp)
                } else {
                    recent.forEach {
                        Text(
                            it.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 15.sp,
                            color = AuroraWhite
                        )
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
    GlassCard(modifier, shape = RoundedCornerShape(24.dp), onClick = onClick) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            IconBadge(icon, size = 38.dp)
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                color = AuroraWhite
            )
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
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress() })
            }
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text("자주 쓰는 앱", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = AuroraWhite)

        if (apps.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    "앱을 실행하면 자주 쓰는 앱이\n이곳에 자동으로 모여요",
                    Modifier.padding(20.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = c.textSecondary,
                    fontSize = 14.sp
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
    val now by rememberMinuteClock()
    val locale = Locale.getDefault()
    val timeText = remember(now, settings.use24h) {
        now.format(DateTimeFormatter.ofPattern(if (settings.use24h) "HH:mm" else "h:mm", locale))
    }
    val ampm = remember(now) { now.format(DateTimeFormatter.ofPattern("a", locale)) }
    val date = remember(now) {
        now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.linearGradient(listOf(Color(0x35FFFFFF), Color(0x18A7D9D0), Color(0x243F6C9D))))
            .border(1.dp, GlassStroke, RoundedCornerShape(32.dp))
    ) {
        Box(
            Modifier.align(Alignment.TopEnd).size(130.dp).offset(x = 35.dp, y = (-40).dp)
                .background(Brush.radialGradient(listOf(Color(0x4459DCC5), Color.Transparent)))
        )
        Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("오늘", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AuroraMuted)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = Color(0xFFFFD58A), modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    timeText,
                    fontSize = if (settings.clockStyle == ClockStyle.LARGE) 76.sp else 54.sp,
                    lineHeight = if (settings.clockStyle == ClockStyle.LARGE) 82.sp else 60.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-3).sp,
                    color = AuroraWhite,
                    maxLines = 1,
                    softWrap = false
                )
                if (!settings.use24h) {
                    Text(ampm, Modifier.padding(start = 7.dp, bottom = 12.dp), fontSize = 17.sp, color = AuroraMuted)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(date, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AuroraMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WeatherCard(weather: WeatherState, onClick: () -> Unit) {
    Pressable(
        Modifier
            .fillMaxWidth()
            .homeGlass(
                RoundedCornerShape(32.dp),
                top = Color(0x66627A9E),
                bottom = Color(0x33324A6B)
            ),
        onClick = onClick
    ) {
        when (weather.status) {
            WeatherStatus.READY -> {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 22.dp, top = 20.dp, bottom = 18.dp)
                ) {
                    if (weather.place.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                weather.place,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = AuroraWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Rounded.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = AuroraWhite
                            )
                        }
                    }
                    Text(
                        "${weather.tempC}°",
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Light,
                        color = AuroraWhite
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        weather.description,
                        fontSize = 16.sp,
                        color = AuroraWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "최고 ${weather.high}° 최저 ${weather.low}°",
                        fontSize = 16.sp,
                        color = AuroraWhite
                    )
                }
                Text(
                    weather.emoji,
                    fontSize = 52.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 18.dp, end = 24.dp)
                )
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = AuroraMuted,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                        .size(22.dp)
                )
            }
            WeatherStatus.LOADING -> {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 26.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🌡️", fontSize = 34.sp)
                    Spacer(Modifier.width(14.dp))
                    Text("날씨를 불러오는 중…", color = AuroraMuted, fontSize = 14.sp)
                }
            }
            WeatherStatus.ERROR -> {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 26.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("☁️", fontSize = 34.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("날씨 정보를 가져오지 못했어요", color = AuroraWhite, fontSize = 13.sp)
                        Text("탭해서 다시 시도", color = AuroraMuted, fontSize = 12.sp)
                    }
                    Icon(Icons.Rounded.Refresh, contentDescription = "새로고침", tint = AuroraMuted)
                }
            }
        }
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Pressable(
        Modifier
            .fillMaxWidth()
            .homeGlass(CircleShape, top = Color(0x30FFFFFF), bottom = Color(0x18FFFFFF)),
        onClick = onClick
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(start = 14.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xEBFFFFFF))
                    .border(1.dp, Color(0xFF202122), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "W",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    color = Color(0xFF202122)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "위키백과 검색",
                color = AuroraMuted,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Rounded.Mic, contentDescription = "음성 검색", modifier = Modifier.size(21.dp), tint = AuroraMuted)
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .padding(horizontal = 5.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (i == current) Color.White else Color.White.copy(alpha = 0.35f))
            )
        }
    }
}

@Composable
private fun Dock(vm: NovaViewModel, dockApps: List<AppInfo>, settings: NovaSettings) {
    val dockIcon = settings.iconSizeDp.coerceIn(40, 52).dp
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .homeGlass(
                RoundedCornerShape(36.dp),
                top = Color(0x40FFFFFF),
                bottom = Color(0x14FFFFFF)
            )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 스크린샷과 같이 앱 4개 + 앱 서랍 버튼 1개
            dockApps.take(4).forEach { app ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    AppIconCell(app, vm, dockIcon, showLabel = false)
                }
            }
            repeat((4 - dockApps.take(4).size).coerceAtLeast(0)) {
                Spacer(Modifier.weight(1f))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                DrawerButton(size = dockIcon) { vm.open(Overlay.Drawer) }
            }
        }
    }
}

@Composable
private fun DrawerButton(size: Dp, onClick: () -> Unit) {
    Pressable(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.30f))
            .background(Brush.linearGradient(listOf(Color(0xFF62D6C0), Color(0xFF578CCB)))),
        onClick = onClick
    ) {
        Icon(Icons.Rounded.Apps, "앱 서랍", Modifier.align(Alignment.Center).size(size * 0.5f), tint = Color.White)
    }
}

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
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2f
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            val style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawArc(color.copy(alpha = 0.2f), 0f, 360f, false, topLeft, arcSize, style = style)
            drawArc(
                color,
                -90f,
                360f * percent.coerceIn(0, 100) / 100f,
                false,
                topLeft,
                arcSize,
                style = style
            )
        }
        Text("$percent%", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AuroraWhite)
    }
}

@Composable
private fun StatBar(label: String, used: Long, total: Long) {
    val fraction = if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), color = AuroraWhite)
            Text(
                "${SystemActions.formatBytes(used)} / ${SystemActions.formatBytes(total)}",
                fontSize = 11.sp,
                color = AuroraMuted
            )
        }
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
        ) {
            Box(
                Modifier.fillMaxWidth(fraction).height(6.dp).clip(CircleShape)
                    .background(Color(0xFF65D6C1))
            )
        }
    }
}

@Composable
private fun QuickMenu(vm: NovaViewModel, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xF21E2A40), Color(0xF2141D2E))))
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(Color(0x59FFFFFF), Color(0x14FFFFFF))),
                    shape
                )
                .padding(vertical = 8.dp)
        ) {
            QuickMenuItem(Icons.Rounded.Palette, "배경화면 · 테마 변경") {
                onDismiss()
                vm.open(Overlay.Settings)
            }
            QuickMenuDivider()
            QuickMenuItem(Icons.Rounded.Tune, "홈 화면 설정") {
                onDismiss()
                vm.open(Overlay.Settings)
            }
            QuickMenuDivider()
            QuickMenuItem(Icons.Rounded.Apps, "앱 관리") {
                onDismiss()
                vm.open(Overlay.Apps)
            }
        }
    }
}

@Composable
private fun QuickMenuDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 70.dp, end = 18.dp)
            .height(0.5.dp)
            .background(Color(0x26FFFFFF))
    )
}

@Composable
private fun QuickMenuItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    Pressable(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon)
            Spacer(Modifier.width(14.dp))
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = AuroraWhite)
        }
    }
}
