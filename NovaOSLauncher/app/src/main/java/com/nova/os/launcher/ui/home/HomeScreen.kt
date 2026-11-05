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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
import com.nova.os.launcher.ui.theme.LocalNovaBlue
import com.nova.os.launcher.ui.theme.LocalNovaColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
 
// 테마(라이트/다크)에 맞는 글자색
private val AuroraWhite: Color
    @Composable get() = LocalNovaColors.current.text
private val AuroraMuted: Color
    @Composable get() = LocalNovaColors.current.textSecondary
private val GlassStroke = Color(0x42FFFFFF)
 
/**
 * 시안의 유리 패널 질감: 위→아래로 옅어지는 반투명 채움 + 위쪽이 밝은 테두리.
 * 라이트/다크 테마에 따라 색이 바뀐다.
 */
@Composable
private fun Modifier.homeGlass(
    shape: Shape,
    top: Color? = null,
    bottom: Color? = null
): Modifier {
    val dark = LocalNovaColors.current.isDark
    val blue = LocalNovaBlue.current
    val t = top ?: if (blue) Color(0x52FFFFFF) else if (dark) Color(0x38FFFFFF) else Color(0x99FFFFFF)
    val b = bottom ?: if (blue) Color(0x1FFFFFFF) else if (dark) Color(0x12FFFFFF) else Color(0x4DFFFFFF)
    val bt = if (blue) Color(0x80FFFFFF) else if (dark) Color(0x66FFFFFF) else Color(0xE6FFFFFF)
    val bb = if (dark) Color(0x14FFFFFF) else Color(0x4DFFFFFF)
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(t, b)))
        .border(1.dp, Brush.verticalGradient(listOf(bt, bb)), shape)
}
 
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
        // 배경은 NovaApp의 WallpaperLayer(설정의 배경화면 프리셋/사진)가 그린다.
 
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 시간·상태 아이콘·배터리·검색창을 하나의 유리 패널로 묶음.
            // 검색창은 설정(showSearchBar)이 켜져 있으면 첫 페이지에서 보이고,
            // 옆 페이지로 넘기는 만큼 서서히 투명해지며 패널이 접힌다.
            HomeTopBar(
                battery = battery,
                use24h = settings.use24h,
                showSearch = settings.showSearchBar,
                searchProgress = {
                    (1f - (pagerState.currentPage + pagerState.currentPageOffsetFraction)).coerceIn(0f, 1f)
                },
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
    use24h: Boolean,
    showSearch: Boolean,
    searchProgress: () -> Float,
    onSearch: () -> Unit,
    onBattery: () -> Unit
) {
    val now by rememberMinuteClock()
    val status by rememberSysStatus()
    val timeText = remember(now, use24h) {
        now.format(DateTimeFormatter.ofPattern(if (use24h) "H:mm" else "h:mm", Locale.getDefault()))
    }
    // 시안: 둥근 유리 패널 하나 안에 시간 + 상태 아이콘 + 배터리, 그 아래 검색창.
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .homeGlass(RoundedCornerShape(50.dp))
            .padding(
                start = 17.dp,
                end = 17.dp,
                top = 24.dp,
                bottom = 18.dp
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 13.dp, end = 6.dp)
                .height(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                timeText,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = AuroraWhite
            )
 
            Spacer(Modifier.weight(1f))
 
            Pressable(
                Modifier.clip(CircleShape),
                onClick = onBattery
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    HomeStatusIcons(status, AuroraWhite)
                    HomeBattery(
                        percent = battery.percent,
                        charging = battery.charging,
                        valid = battery.valid,
                        tint = AuroraWhite
                    )
                }
            }
        }
 
        if (showSearch) {
            // 페이지를 넘기는 정도(searchProgress)에 맞춰 높이와 투명도가 같이 줄어든다.
            Column(
                Modifier
                    .layout { measurable, constraints ->
                        val p = measurable.measure(constraints)
                        val h = (p.height * searchProgress()).roundToInt()
                        layout(p.width, h) { p.place(0, 0) }
                    }
                    .graphicsLayer { alpha = searchProgress() }
                    .clipToBounds()
            ) {
                Spacer(Modifier.height(30.dp))
                SearchPill(onSearch)
                Spacer(Modifier.height(7.dp))
            }
        }
    }
}
 
/** 시안의 가로형 배터리 아이콘. 잔량만큼 채워지고 충전 중이면 초록색. */
@Composable
private fun HomeBattery(percent: Int, charging: Boolean, valid: Boolean, tint: Color) {
    val fillColor = if (charging) Color(0xFF4ADE80) else tint
    Canvas(Modifier.size(width = 30.dp, height = 14.dp)) {
        val nub = 3.dp.toPx()
        val bw = size.width - nub
        val bh = size.height
        drawRoundRect(
            color = tint.copy(alpha = 0.38f),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(4.dp.toPx())
        )
        val inset = 1.5.dp.toPx()
        val frac = if (valid) (percent.coerceIn(0, 100) / 100f).coerceAtLeast(0.08f) else 1f
        drawRoundRect(
            color = fillColor,
            topLeft = Offset(inset, inset),
            size = Size((bw - inset * 2f) * frac, bh - inset * 2f),
            cornerRadius = CornerRadius(2.5.dp.toPx())
        )
        drawRoundRect(
            color = tint.copy(alpha = 0.5f),
            topLeft = Offset(bw + 1.dp.toPx(), bh * 0.32f),
            size = Size(2.dp.toPx(), bh * 0.36f),
            cornerRadius = CornerRadius(1.dp.toPx())
        )
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
    ) {
        if (settings.showWeather) {
            // 시안: 날씨 카드는 상단 패널보다 12dp 안쪽, 패널과 거의 붙어 있음
            Spacer(Modifier.height(4.dp))
            WeatherCard(weather, Modifier.padding(horizontal = 28.dp)) { vm.refreshWeather(force = true) }
            Spacer(Modifier.height(43.dp))
        } else {
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
        GlassCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
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
 
    // 시안 기본 앱(설정, 갤러리, Play 스토어, 카메라, 전화, 메시지, 인터넷, YouTube)을 앞에 두고
    // 나머지는 기존 즐겨찾기 순서를 유지. 첫 홈 화면에는 두 줄을 배치.
    val ordered = remember(apps) { apps.sandOrdered(SandDefaultFavorites) }
    Column(
        Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp)
    ) {
        ordered.take(cols * 2).chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
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
private fun WeatherCard(weather: WeatherState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Pressable(
        modifier
            .fillMaxWidth()
            .homeGlass(
                RoundedCornerShape(46.dp),
                top = if (LocalNovaBlue.current) Color(0x66A9CCFF)
                else if (LocalNovaColors.current.isDark) Color(0x66627A9E) else Color(0x8C7F98E0),
                bottom = if (LocalNovaBlue.current) Color(0x333B78F0)
                else if (LocalNovaColors.current.isDark) Color(0x33324A6B) else Color(0x5992A9E8)
            ),
        onClick = onClick
    ) {
        when (weather.status) {
            WeatherStatus.READY -> {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 27.dp, end = 22.dp, top = 24.dp, bottom = 19.dp)
                ) {
                    if (weather.place.isNotBlank()) {
                        Row(
                            Modifier.height(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                weather.place,
                                fontSize = 19.sp,
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
                                modifier = Modifier.size(15.dp),
                                tint = AuroraWhite
                            )
                        }
                    } else {
                        Spacer(Modifier.height(24.dp))
                    }
                    Text(
                        "${weather.tempC}°",
                        fontSize = 60.sp,
                        lineHeight = 66.sp,
                        fontWeight = FontWeight.Light,
                        color = AuroraWhite
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        weather.description,
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        color = AuroraWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "최고 ${weather.high}° 최저 ${weather.low}°",
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        color = AuroraWhite
                    )
                }
                if (weather.emoji.startsWith("\u2600")) {
                    // 맑음: 시안처럼 직접 그린 해
                    HomeSun(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 36.dp, end = 24.dp)
                            .size(64.dp)
                    )
                } else {
                    Text(
                        weather.emoji,
                        fontSize = 52.sp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 38.dp, end = 26.dp)
                    )
                }
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = AuroraMuted,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 22.dp, bottom = 19.dp)
                        .size(22.dp)
                )
            }
            WeatherStatus.LOADING -> {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 27.dp, vertical = 34.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🌡️", fontSize = 34.sp)
                    Spacer(Modifier.width(14.dp))
                    Text("날씨를 불러오는 중…", color = AuroraMuted, fontSize = 14.sp)
                }
            }
            WeatherStatus.ERROR -> {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 27.dp, vertical = 34.dp),
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
            .homeGlass(
                CircleShape,
                top = if (LocalNovaBlue.current) Color(0x66FFFFFF)
                else if (LocalNovaColors.current.isDark) Color(0x30FFFFFF) else Color(0xB3FFFFFF),
                bottom = if (LocalNovaBlue.current) Color(0x33FFFFFF)
                else if (LocalNovaColors.current.isDark) Color(0x18FFFFFF) else Color(0x66FFFFFF)
            ),
        onClick = onClick
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(59.dp)
                .padding(start = 18.dp, end = 22.dp),
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
                fontSize = 17.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Rounded.Mic, contentDescription = "음성 검색", modifier = Modifier.size(22.dp), tint = AuroraMuted)
        }
    }
}
 
@Composable
private fun PageDots(count: Int, current: Int) {
    val dark = LocalNovaColors.current.isDark
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .padding(horizontal = 6.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(
                        if (i == current) Color.White
                        else Color.White.copy(alpha = if (dark) 0.45f else 0.75f)
                    )
                    .border(0.5.dp, if (dark) Color.Transparent else Color(0x338A94C8), CircleShape)
            )
        }
    }
}
 
@Composable
private fun Dock(vm: NovaViewModel, dockApps: List<AppInfo>, settings: NovaSettings) {
    val dockIcon = settings.iconSizeDp.coerceIn(40, 70).dp
    // 시안 독 배치(전화, 메시지, 인터넷, 음악)를 앞에 두고 나머지는 기존 순서 유지
    val shown = remember(dockApps) { dockApps.sandOrdered(SandDefaultDock).take(4) }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .homeGlass(RoundedCornerShape(50.dp))
    ) {
        // 앱 4개를 홈 그리드 열과 같은 간격으로 배치
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 21.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            shown.forEach { app ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    AppIconCell(app, vm, dockIcon, showLabel = false, dock = true)
                }
            }
            // 독 앱이 4개 미만이면 빈 칸에 앱 서랍 버튼을 두어 서랍 접근을 유지
            if (shown.size < 4) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    DrawerButton(size = dockIcon) { vm.open(Overlay.Drawer) }
                }
                repeat(3 - shown.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
 
@Composable
private fun DrawerButton(size: Dp, onClick: () -> Unit) {
    Pressable(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.27f))
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
                .background(AuroraWhite.copy(alpha = 0.12f))
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
    val dark = LocalNovaColors.current.isDark
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        if (dark) listOf(Color(0xF21E2A40), Color(0xF2141D2E))
                        else listOf(Color(0xF2FFFFFF), Color(0xF2F1F4FB))
                    )
                )
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
            .background(LocalNovaColors.current.text.copy(alpha = 0.14f))
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
 
 
@Composable
private fun HomeSun(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = center
        val r = size.minDimension
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFE27A), Color(0xFFF6C12F)),
                center = c,
                radius = r * 0.29f
            ),
            radius = r * 0.27f,
            center = c
        )
        for (i in 0 until 8) {
            val a = (i * PI / 4).toFloat()
            val dx = cos(a)
            val dy = sin(a)
            drawLine(
                color = Color(0xFFF2C24B),
                start = Offset(c.x + dx * r * 0.37f, c.y + dy * r * 0.37f),
                end = Offset(c.x + dx * r * 0.47f, c.y + dy * r * 0.47f),
                strokeWidth = r * 0.055f,
                cap = StrokeCap.Round
            )
        }
    }
}
 
