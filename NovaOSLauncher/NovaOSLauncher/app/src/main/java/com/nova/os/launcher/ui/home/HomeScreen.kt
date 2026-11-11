package com.nova.os.launcher.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import com.nova.os.launcher.data.*
import com.nova.os.launcher.sand.RollingText
import com.nova.os.launcher.sand.SandChip
import com.nova.os.launcher.sand.SandConfig
import com.nova.os.launcher.sand.SandHost
import com.nova.os.launcher.sand.SandLaunch
import com.nova.os.launcher.sand.SandLaunchLayer
import com.nova.os.launcher.sand.SandLibraryPage
import com.nova.os.launcher.sand.SandNav
import com.nova.os.launcher.sand.SandPage
import com.nova.os.launcher.sand.SandSystemBar
import com.nova.os.launcher.sand.sandLaunchTap
import com.nova.os.launcher.sand.sandStatusBarHeight
import com.nova.os.launcher.sand.sandTrackTouch
import com.nova.os.launcher.system.SystemActions
import com.nova.os.launcher.ui.components.*
import com.nova.os.launcher.ui.theme.LocalNovaBlue
import com.nova.os.launcher.ui.theme.LocalNovaColors
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    // "더 많은 설정"의 유리 진하기 배율
    val k = SandConfig.glass
    fun Color.g(): Color = copy(alpha = (alpha * k).coerceIn(0f, 1f))
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(t.g(), b.g())))
        .border(1.dp, Brush.verticalGradient(listOf(bt.g(), bb.g())), shape)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(vm: NovaViewModel) {
    val settings by vm.settings.collectAsState()
    val dockApps by vm.dockApps.collectAsState()
    val battery by vm.battery.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    remember { SandConfig.init(ctx); true }
    var sandPage by remember { mutableStateOf<SandPage?>(null) }
    var editMode by remember { mutableStateOf(false) }
    val gesturesOn = sandPage == null && !editMode && SandLaunch.spec == null
    // 시스템 상태 바 숨기기(더 많은 설정 → SAND OS를 기기에 정착시키기)
    SandSystemBar(SandConfig.hideSystemBar)
    val topBarH = sandStatusBarHeight()
    // 검색 창 등 다른 화면이 SAND 화면을 열어 달라고 요청하면 여기서 연다.
    LaunchedEffect(SandNav.page) {
        val r = SandNav.page
        if (r != null) {
            sandPage = r
            SandNav.page = null
        }
    }
    // 페이지 종류: 0 메인, 1 위젯, 2 자주 쓰는 앱, 3 앱 라이브러리(폴더 서랍)
    val pageKinds = ArrayList<Int>()
    pageKinds.add(0)
    if (SandConfig.libraryOn) pageKinds.add(3)
    if (settings.homePages >= 2) pageKinds.add(1)
    if (settings.homePages >= 3) pageKinds.add(2)
    val pageCount = pageKinds.size
    val homeAlpha by animateFloatAsState(
        targetValue = if (sandPage != null) 0f else 1f,
        animationSpec = tween(280),
        label = "sandHomeAlpha"
    )
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
    // 나중에 등록한 것이 먼저 처리된다: 편집 모드 → SAND 화면 순
    BackHandler(enabled = editMode) { editMode = false }
    BackHandler(enabled = sandPage != null) { sandPage = null }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged {
                SandLaunch.screenW = it.width.toFloat()
                SandLaunch.screenH = it.height.toFloat()
            }
            .sandTrackTouch()
            .pointerInput(gesturesOn) {
                // SAND 화면이나 편집 모드가 열려 있을 때는 위/아래로 밀어 서랍·컨트롤 센터를 여는 제스처를 끈다.
                if (!gesturesOn) return@pointerInput
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
                .graphicsLayer { alpha = homeAlpha }
                .then(if (SandConfig.hideSystemBar) Modifier.padding(top = topBarH) else Modifier.statusBarsPadding())
                .navigationBarsPadding()
        ) {
            // 콘셉트 보드: 시간 · 상태 아이콘 · 배터리가 한 줄. 검색은 돋보기 버튼(vm.openSearch)으로 연결.
            HomeStatusRow(
                battery = battery,
                use24h = settings.use24h,
                showSearch = settings.showSearchBar,
                onSearch = { vm.openSearch() },
                onBattery = { vm.open(Overlay.Control) },
                onBell = { sandPage = SandPage.NOTIFICATIONS }
            )

            if (editMode) {
                Row(Modifier.fillMaxWidth().padding(end = 26.dp, top = 2.dp), horizontalArrangement = Arrangement.End) {
                    SandChip("완료", true, { editMode = false })
                }
            }

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
                    when (pageKinds.getOrElse(page) { 0 }) {
                        0 -> MainPage(vm, settings, editMode, { sandPage = it }) { quickMenu = true }
                        1 -> WidgetsPage(vm, { sandPage = it }) { quickMenu = true }
                        3 -> SandLibraryPage(vm, { sandPage = it }) { quickMenu = true }
                        else -> FrequentPage(vm, settings) { quickMenu = true }
                    }
                }
            }

            if (pageCount > 1) {
                PageDots(count = pageCount, current = pagerState.currentPage)
            }

            if (editMode) {
                EditBottomBar(vm, settings, { sandPage = it }) { editMode = false }
            } else if (settings.showDock) {
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

        // 캘린더·지도·QR·그림판·뉴스·번역기·GitHub·AI·알림 창·더 많은 설정
        SandHost(vm, sandPage, { sandPage = null }, { sandPage = it })

        // 앱을 탭하면 아이콘 자리에서 커지는 로딩 화면(앱이 열리면 가려지고, 돌아오면 줄어든다)
        SandLaunchLayer()

        if (quickMenu) {
            QuickMenu(vm, { editMode = true }, { sandPage = it }) { quickMenu = false }
        }
    }
}

@Composable
private fun HomeStatusRow(
    battery: BatteryState,
    use24h: Boolean,
    showSearch: Boolean,
    onSearch: () -> Unit,
    onBattery: () -> Unit,
    onBell: () -> Unit
) {
    val now by rememberMinuteClock()
    val status by rememberSysStatus()
    val timeText = remember(now, use24h) { now.clockText(use24h) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 22.dp, top = 12.dp)
            .height(34.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RollingText(
            timeText,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = AuroraWhite
        )

        Spacer(Modifier.weight(1f))

        if (showSearch) {
            Pressable(Modifier.clip(CircleShape), onClick = onSearch) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = "검색",
                    modifier = Modifier.padding(8.dp).size(20.dp),
                    tint = AuroraWhite
                )
            }
        }

        Pressable(Modifier.clip(CircleShape), onClick = onBattery) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                HomeStatusIcons(status, AuroraWhite, onBell)
                HomeBattery(
                    percent = battery.percent,
                    charging = battery.charging,
                    valid = battery.valid,
                    tint = AuroraWhite
                )
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
private fun MainPage(
    vm: NovaViewModel,
    settings: NovaSettings,
    editMode: Boolean,
    onOpenSand: (SandPage) -> Unit,
    onLongPress: () -> Unit
) {
    val favorites by vm.favoriteApps.collectAsState()
    val weather by vm.weather.collectAsState()
    val look = SandConfig.clockLook

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress() })
            }
    ) {
        Spacer(Modifier.height(10.dp))

        if (look == 3) {
            // 작은 시계 바 + 넓은 날씨 위젯
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 26.dp).height(172.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HomeClockBar(settings.use24h, Modifier.fillMaxWidth().height(52.dp)) { onOpenSand(SandPage.CLOCK) }
                if (settings.showWeather) {
                    HomeWeatherCard(weather, Modifier.fillMaxWidth().weight(1f)) { vm.refreshWeather(force = true) }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        } else {
            // 콘셉트 보드: 큰 시계 위젯(왼쪽) + 날씨 위젯(오른쪽)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp)
                    .height(172.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HomeClockCard(
                    use24h = settings.use24h,
                    look = look,
                    modifier = Modifier.weight(if (settings.showWeather) 1.19f else 1f).fillMaxHeight()
                ) { onOpenSand(SandPage.CLOCK) }
                if (settings.showWeather) {
                    HomeWeatherCard(
                        weather = weather,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) { vm.refreshWeather(force = true) }
                }
            }
        }

        Spacer(Modifier.height(30.dp))

        FavoritesGrid(vm, favorites, settings, editMode, onOpenSand)
    }
}

/** 작은 시계 바(보드의 "작은 시계 · 상단 바"). */
@Composable
private fun HomeClockBar(use24h: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val now by rememberMinuteClock()
    val timeText = remember(now, use24h) { now.clockText(use24h) }
    val dateText = remember(now) { now.dateText() }
    Pressable(modifier.homeGlass(RoundedCornerShape(50.dp)), onClick = onClick) {
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            RollingText(timeText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = AuroraWhite)
            Spacer(Modifier.width(12.dp))
            Text(dateText, fontSize = 14.sp, color = AuroraMuted)
        }
    }
}

/** 시계 위젯: 0 큰 카드, 1 아날로그, 2 대형(어두운 카드). 탭하면 시계 화면. */
@Composable
private fun HomeClockCard(use24h: Boolean, look: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val now by rememberMinuteClock()
    val timeText = remember(now, use24h) { now.clockText(use24h) }
    val dateText = remember(now) { now.dateText() }
    val shape = RoundedCornerShape(30.dp)
    if (look == 1) {
        Pressable(modifier.homeGlass(shape, Color(0x8C1E2A44), Color(0x66141C30)), onClick = onClick) {
            Canvas(Modifier.fillMaxSize().padding(16.dp)) {
                val c = center
                val r = size.minDimension / 2f
                for (i in 0 until 12) {
                    val a = (i * PI / 6.0 - PI / 2.0).toFloat()
                    val major = i % 3 == 0
                    drawLine(
                        Color.White.copy(alpha = if (major) 0.95f else 0.55f),
                        Offset(c.x + cos(a) * r * (if (major) 0.80f else 0.86f), c.y + sin(a) * r * (if (major) 0.80f else 0.86f)),
                        Offset(c.x + cos(a) * r * 0.95f, c.y + sin(a) * r * 0.95f),
                        strokeWidth = if (major) 3.dp.toPx() else 1.6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                val minute = now.minute
                val hour = (now.hour % 12) + minute / 60f
                fun hand(angleDeg: Float, len: Float, w: Float) {
                    val a = ((angleDeg - 90f) * PI / 180.0).toFloat()
                    drawLine(
                        Color.White,
                        c,
                        Offset(c.x + cos(a) * r * len, c.y + sin(a) * r * len),
                        strokeWidth = w,
                        cap = StrokeCap.Round
                    )
                }
                hand(hour * 30f, 0.50f, 4.dp.toPx())
                hand(minute * 6f, 0.74f, 3.dp.toPx())
                drawCircle(Color(0xFFFF9F0A), radius = 4.dp.toPx(), center = c)
            }
        }
    } else {
        val big = look == 2
        val mod = if (big) {
            modifier.homeGlass(shape, Color(0xCC252B3B), Color(0xCC171B28))
        } else {
            modifier.homeGlass(shape)
        }
        Pressable(mod, onClick = onClick) {
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RollingText(
                    timeText,
                    fontSize = if (big) 58.sp else 52.sp,
                    lineHeight = if (big) 64.sp else 58.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-1).sp,
                    color = if (big) Color.White else AuroraWhite
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    dateText,
                    fontSize = 14.sp,
                    color = if (big) Color(0xCCFFFFFF) else AuroraMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 보드의 날씨 위젯: 해 아이콘, 큰 온도, 상태, 지역. 탭하면 새로고침. */
@Composable
private fun HomeWeatherCard(weather: WeatherState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Pressable(modifier.homeGlass(RoundedCornerShape(30.dp)), onClick = onClick) {
        when (weather.status) {
            WeatherStatus.READY -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 18.dp)
                ) {
                    if (weather.emoji.startsWith("\u2600")) {
                        HomeSun(Modifier.size(36.dp))
                    } else {
                        Text(weather.emoji, fontSize = 28.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${weather.tempC}°",
                        fontSize = 40.sp,
                        lineHeight = 44.sp,
                        fontWeight = FontWeight.Light,
                        color = AuroraWhite
                    )
                    Text(
                        weather.description,
                        fontSize = 14.sp,
                        color = AuroraMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (weather.place.isNotBlank()) {
                        Text(
                            weather.place,
                            fontSize = 14.sp,
                            color = AuroraMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            WeatherStatus.LOADING -> {
                Column(
                    Modifier.align(Alignment.Center).padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🌡️", fontSize = 28.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("불러오는 중…", color = AuroraMuted, fontSize = 13.sp)
                }
            }
            WeatherStatus.ERROR -> {
                Column(
                    Modifier.align(Alignment.Center).padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("☁️", fontSize = 28.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("날씨를 가져오지\n못했어요", color = AuroraWhite, fontSize = 12.sp, textAlign = TextAlign.Center)
                    Text("탭해서 다시 시도", color = AuroraMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

private class HomeItem(val app: AppInfo?, val page: SandPage?)

@Composable
private fun FavoritesGrid(
    vm: NovaViewModel,
    apps: List<AppInfo>,
    settings: NovaSettings,
    editMode: Boolean,
    onOpenSand: (SandPage) -> Unit
) {
    val c = LocalNovaColors.current
    val cols = settings.favColumns.coerceIn(3, 5)
    val rows = SandConfig.homeRows

    // 콘셉트 보드의 앱(메시지, 캘린더, 사진, 카메라 ...)을 앞에 두고 나머지는 기존 즐겨찾기 순서를 유지.
    val ordered = remember(apps) { apps.sandOrdered(SandDefaultFavorites) }
    val items = ArrayList<HomeItem>()
    for (a in ordered) items.add(HomeItem(a, null))
    val kinds = ordered.mapNotNull { sandKindOf(it) }.toSet()
    // 홈에 추가해 둔 SAND 기본 앱(기본값: 캘린더, 지도). 같은 종류의 실제 앱이 있으면 중복해서 보여주지 않는다.
    for (name in SandConfig.homeBuiltins) {
        val page = SandPage.values().firstOrNull { it.name == name } ?: continue
        if (page == SandPage.CALENDAR && SandKind.CALENDAR in kinds) continue
        if (page == SandPage.MAP && SandKind.MAPS in kinds) continue
        val hint = when (page) {
            SandPage.CALENDAR -> 1
            SandPage.MAP -> 6
            else -> items.size
        }
        items.add(minOf(hint, items.size), HomeItem(null, page))
    }

    if (items.isEmpty()) {
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

    val iconSize = settings.iconSizeDp.dp
    Column(
        Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        items.take(cols * rows).chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { item ->
                    val cellApp = item.app
                    Box(
                        if (cellApp != null) Modifier.weight(1f).sandLaunchTap(!editMode, cellApp, vm)
                        else Modifier.weight(1f)
                    ) {
                        val app = item.app
                        val page = item.page
                        if (app != null) {
                            AppIconCell(app, vm, iconSize, settings.showLabels, Modifier.fillMaxWidth())
                        } else if (page != null) {
                            BuiltinCell(page, iconSize, settings.showLabels) { onOpenSand(page) }
                        }
                        if (editMode) {
                            MinusBadge {
                                if (app != null) {
                                    vm.toggleFavorite(app.packageName)
                                } else if (page != null) {
                                    SandConfig.edit { homeBuiltins = homeBuiltins - page.name }
                                }
                            }
                        }
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BuiltinCell(page: SandPage, size: Dp, showLabel: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Pressable(onClick = onClick) {
            Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SandAppIcon(page.kind, size)
                if (showLabel) {
                    Spacer(Modifier.height(6.dp))
                    Text(page.label, fontSize = 12.sp, maxLines = 1, color = LocalNovaColors.current.text)
                }
            }
        }
    }
}

/** 편집 모드에서 앱 왼쪽 위에 나타나는 빼기(−) 버튼. */
@Composable
private fun BoxScope.MinusBadge(onClick: () -> Unit) {
    Box(
        Modifier
            .align(Alignment.TopStart)
            .padding(start = 4.dp)
            .size(24.dp)
            .clip(CircleShape)
            .background(Color(0xE6E5E5EA))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text("−", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3A3A3C))
    }
}

/** 편집 모드 하단바: 위젯 설정 / 페이지 편집 / 앱 정렬. */
@Composable
private fun EditBottomBar(
    vm: NovaViewModel,
    settings: NovaSettings,
    onOpenSand: (SandPage) -> Unit,
    onDone: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .homeGlass(RoundedCornerShape(32.dp))
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        EditBarItem(Icons.Rounded.Widgets, "위젯 설정") { onOpenSand(SandPage.MORE_SETTINGS) }
        EditBarItem(Icons.Rounded.ViewCarousel, "페이지 " + settings.homePages) {
            vm.update { it.copy(homePages = if (it.homePages >= 3) 1 else it.homePages + 1) }
        }
        EditBarItem(Icons.Rounded.SortByAlpha, "앱 정렬") {
            vm.applySandLayout()
            onDone()
        }
    }
}

@Composable
private fun EditBarItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Pressable(onClick = onClick) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp), tint = AuroraWhite)
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 12.sp, color = AuroraWhite)
        }
    }
}

@Composable
private fun WidgetsPage(vm: NovaViewModel, onOpenSand: (SandPage) -> Unit, onLongPress: () -> Unit) {
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
            ToolTile(Icons.Rounded.Schedule, "시계", Modifier.weight(1f)) { onOpenSand(SandPage.CLOCK) }
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
                        Box(Modifier.weight(1f).sandLaunchTap(true, app, vm)) {
                            AppIconCell(app, vm, settings.iconSizeDp.dp, settings.showLabels, Modifier.fillMaxWidth())
                        }
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
private fun PageDots(count: Int, current: Int) {
    val dark = LocalNovaColors.current.isDark
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .padding(horizontal = 5.dp)
                    .size(8.dp)
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
    // 콘셉트 보드 독 배치(전화, 인터넷, 음악, 설정)를 앞에 두고 나머지는 기존 순서 유지
    val shown = remember(dockApps) { dockApps.sandOrdered(SandDefaultDock).take(4) }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .homeGlass(RoundedCornerShape(32.dp))
    ) {
        // 앱 4개를 홈 그리드 열과 같은 간격으로 배치
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            shown.forEach { app ->
                Box(Modifier.weight(1f).sandLaunchTap(true, app, vm), contentAlignment = Alignment.Center) {
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
private fun QuickMenu(
    vm: NovaViewModel,
    onEdit: () -> Unit,
    onSand: (SandPage) -> Unit,
    onDismiss: () -> Unit
) {
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
            QuickMenuDivider()
            QuickMenuDivider()
            QuickMenuItem(Icons.Rounded.Edit, "홈 화면 편집") {
                onDismiss()
                onEdit()
            }
            QuickMenuDivider()
            QuickMenuItem(Icons.Rounded.Tune, "더 많은 설정") {
                onDismiss()
                onSand(SandPage.MORE_SETTINGS)
            }
            QuickMenuDivider()
            QuickMenuItem(Icons.Rounded.Refresh, "SAND 기본 배치로 정리") {
                onDismiss()
                vm.applySandLayout()
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

private fun LocalDateTime.clockText(use24h: Boolean): String =
    format(DateTimeFormatter.ofPattern(if (use24h) "H:mm" else "h:mm", Locale.getDefault()))

private fun LocalDateTime.dateText(): String =
    format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN))
