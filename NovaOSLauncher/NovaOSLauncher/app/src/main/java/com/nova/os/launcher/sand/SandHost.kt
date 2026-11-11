package com.nova.os.launcher.sand

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.launcher.NovaViewModel
import com.nova.os.launcher.ui.components.SandAppIcon
import com.nova.os.launcher.ui.components.SandKind
import com.nova.os.launcher.ui.theme.LocalNovaColors
import com.nova.os.launcher.ui.theme.LocalNovaMotion
import kotlinx.coroutines.delay

/** 홈 화면 위에 열리는 SAND 기본 앱들. */
enum class SandPage(val label: String, val kind: SandKind) {
    CALENDAR("캘린더", SandKind.CALENDAR),
    MAP("지도", SandKind.MAPS),
    QR("QR 코드", SandKind.QR),
    PAINT("그림판", SandKind.PAINT),
    NEWS("뉴스", SandKind.NEWS),
    TRANSLATE("번역기", SandKind.TRANSLATE),
    GITHUB("GitHub", SandKind.GITHUB),
    AI("AI 에이전트", SandKind.AI),
    NOTIFICATIONS("알림", SandKind.NOTIFY),
    MORE_SETTINGS("더 많은 설정", SandKind.SANDSET),
    CLOCK("시계", SandKind.CLOCK),
    TERMINAL("터미널", SandKind.TERMINAL),
}

private class PageHolder(var page: SandPage?) {
    var open: Boolean = false
    var origin: TransformOrigin = TransformOrigin.Center
}

private class BannerHolder(var n: SandNotif?)

@Composable
fun SandHost(
    vm: NovaViewModel,
    page: SandPage?,
    onClose: () -> Unit,
    onOpen: (SandPage) -> Unit,
) {
    val motion = LocalNovaMotion.current
    val c = LocalNovaColors.current
    val holder = remember { PageHolder(page) }
    if (page != null) {
        // 닫혀 있다가 열리는 순간의 손가락 위치(앱 아이콘 자리)에서 화면이 커진다.
        if (!holder.open) {
            holder.origin = if (SandConfig.launchFx) SandLaunch.originFraction() else TransformOrigin.Center
            holder.open = true
        }
        holder.page = page
    } else {
        holder.open = false
    }

    // 열릴 때 잠깐 보이는 로딩 화면(앱 아이콘 + 이름 + 점 세 개)
    var splash by remember { mutableStateOf(false) }
    LaunchedEffect(page) {
        if (page != null && SandConfig.launchFx) {
            splash = true
            delay(520)
            splash = false
        } else {
            splash = false
        }
    }
    val corner by animateDpAsState(if (splash) 30.dp else 0.dp, tween(380), label = "sandHostCorner")

    val fx = SandConfig.launchFx
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = page != null,
            enter = fadeIn(motion.spec(300)) + scaleIn(
                motion.spec(420),
                initialScale = if (fx) 0.12f else 0.95f,
                transformOrigin = holder.origin
            ),
            exit = fadeOut(motion.spec(220)) + scaleOut(
                motion.spec(300),
                targetScale = if (fx) 0.12f else 0.97f,
                transformOrigin = holder.origin
            ),
        ) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(corner))) {
                when (holder.page) {
                    SandPage.CALENDAR -> SandCalendarScreen(onClose)
                    SandPage.MAP -> SandMapScreen(onClose)
                    SandPage.QR -> SandQrScreen(onClose)
                    SandPage.PAINT -> SandPaintScreen(onClose)
                    SandPage.NEWS -> SandNewsScreen(onClose)
                    SandPage.TRANSLATE -> SandTranslateScreen(onClose)
                    SandPage.GITHUB -> SandGitHubScreen(onClose)
                    SandPage.AI -> SandAiScreen(vm, onClose, onOpen)
                    SandPage.NOTIFICATIONS -> SandNotificationScreen(onClose)
                    SandPage.MORE_SETTINGS -> SandMoreSettingsScreen(vm, onClose, onOpen)
                    SandPage.CLOCK -> SandClockScreen(vm, onClose)
                    SandPage.TERMINAL -> SandTerminalScreen(vm, onClose)
                    null -> Unit
                }
                val sp = holder.page
                AnimatedVisibility(
                    visible = splash,
                    enter = EnterTransition.None,
                    exit = fadeOut(tween(320)),
                ) {
                    Box(Modifier.fillMaxSize().background(c.panel)) {
                        if (sp != null) {
                            Column(
                                Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                SandAppIcon(sp.kind, 88.dp)
                                Spacer(Modifier.height(18.dp))
                                Text(sp.label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                                Spacer(Modifier.height(16.dp))
                                SandLoadingDots()
                            }
                        }
                    }
                }
            }
        }
        BannerLayer()
    }
}

@Composable
private fun BoxScope.BannerLayer() {
    val b = SandNotifications.banner
    val last = remember { BannerHolder(null) }
    if (b != null) last.n = b

    LaunchedEffect(b) {
        if (b != null) {
            delay(4500)
            if (SandNotifications.banner === b) SandNotifications.banner = null
        }
    }

    AnimatedVisibility(
        visible = b != null,
        modifier = Modifier.align(Alignment.TopCenter),
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        val n = last.n
        if (n != null) {
            Box(Modifier.statusBarsPadding().padding(top = 6.dp)) {
                SandBannerCard(n, { SandNotifications.open(n) }, { SandNotifications.banner = null })
            }
        }
    }
}
