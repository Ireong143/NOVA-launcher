package com.nova.os.launcher.sand

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nova.os.launcher.NovaViewModel
import com.nova.os.launcher.ui.components.SandKind
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
}

private class PageHolder(var page: SandPage?)
private class BannerHolder(var n: SandNotif?)

@Composable
fun SandHost(
    vm: NovaViewModel,
    page: SandPage?,
    onClose: () -> Unit,
    onOpen: (SandPage) -> Unit,
) {
    val motion = LocalNovaMotion.current
    val holder = remember { PageHolder(page) }
    if (page != null) holder.page = page

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = page != null,
            enter = fadeIn(motion.spec(300)) + scaleIn(motion.spec(420), initialScale = 0.95f),
            exit = fadeOut(motion.spec(220)) + scaleOut(motion.spec(260), targetScale = 0.97f),
        ) {
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
                null -> Unit
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
