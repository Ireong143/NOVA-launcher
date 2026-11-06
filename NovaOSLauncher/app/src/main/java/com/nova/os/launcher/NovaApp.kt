
package com.nova.os.launcher

import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.nova.os.launcher.data.NovaSettings
import com.nova.os.launcher.data.Overlay
import com.nova.os.launcher.system.findActivity
import com.nova.os.launcher.ui.apps.AppManagerScreen
import com.nova.os.launcher.ui.components.Pressable
import com.nova.os.launcher.ui.components.WallpaperLayer
import com.nova.os.launcher.ui.control.ControlCenter
import com.nova.os.launcher.ui.drawer.AppDrawer
import com.nova.os.launcher.ui.home.HomeScreen
import com.nova.os.launcher.ui.search.SearchScreen
import com.nova.os.launcher.ui.settings.AboutScreen
import com.nova.os.launcher.ui.settings.SettingsScreen
import com.nova.os.launcher.ui.theme.LocalNovaColors
import com.nova.os.launcher.ui.theme.LocalNovaMotion
import com.nova.os.launcher.ui.theme.NovaMotion
import com.nova.os.launcher.ui.theme.NovaTheme
import com.nova.os.launcher.ui.tools.CalculatorScreen
import com.nova.os.launcher.ui.tools.ClockScreen
import com.nova.os.launcher.ui.tools.NotesScreen
import kotlinx.coroutines.delay

/* ───────────────────────── 공용 팝업 / 토스트 ─────────────────────────
 *
 * 어느 화면에서든 아래처럼 호출하면 시안(콘셉트 보드)의 유리 팝업이 뜬다.
 *
 *   val popup = LocalNovaPopup.current
 *   popup.toast("설정이 저장되었습니다")
 *   popup.confirm(
 *       title = "앱을 삭제할까요?",
 *       message = "이 앱과 관련된 데이터도 모두 삭제됩니다.",
 *       confirmText = "삭제",
 *       destructive = true,
 *   ) { /* 삭제 실행 */ }
 */

class NovaDialogRequest(
    val title: String,
    val message: String?,
    val confirmText: String,
    val cancelText: String?,
    val destructive: Boolean,
    val onConfirm: () -> Unit,
)

@Stable
class NovaPopupController {
    var toastMessage by mutableStateOf<String?>(null)
        private set
    var toastTick by mutableIntStateOf(0)
        private set
    var dialog by mutableStateOf<NovaDialogRequest?>(null)
        private set

    fun toast(message: String) {
        toastMessage = message
        toastTick++
    }

    fun confirm(
        title: String,
        message: String? = null,
        confirmText: String = "확인",
        cancelText: String? = "취소",
        destructive: Boolean = false,
        onConfirm: () -> Unit,
    ) {
        dialog = NovaDialogRequest(title, message, confirmText, cancelText, destructive, onConfirm)
    }

    fun dismissDialog() {
        dialog = null
    }

    internal fun clearToast() {
        toastMessage = null
    }
}

val LocalNovaPopup = staticCompositionLocalOf { NovaPopupController() }

/* ───────────────────────── 앱 루트 ───────────────────────── */

@Composable
fun NovaApp(vm: NovaViewModel) {
    val settings by vm.settings.collectAsState()
    val base = LocalDensity.current
    // The in-app font scale setting multiplies the system font scale.
    val density = remember(base.density, base.fontScale, settings.fontScale) {
        Density(base.density, base.fontScale * settings.fontScale)
    }
    val popup = remember { NovaPopupController() }
    CompositionLocalProvider(
        LocalDensity provides density,
        LocalNovaPopup provides popup,
    ) {
        NovaTheme(settings) { NovaRoot(vm, settings, popup) }
    }
}

@Composable
private fun NovaRoot(vm: NovaViewModel, settings: NovaSettings, popup: NovaPopupController) {
    val colors = LocalNovaColors.current
    val motion = LocalNovaMotion.current
    val overlay = vm.overlay
    val active = overlay != Overlay.None
    val customWallpaper by vm.customWallpaper.collectAsState()

    SystemBarsEffect(dark = colors.isDark)

    // The launcher must never "exit" on Back. Layers registered later take priority.
    BackHandler(enabled = true) { }
    BackHandler(enabled = active) { vm.back() }

    val blur by animateDpAsState(
        targetValue = if (active) 24.dp else 0.dp,
        animationSpec = motion.spec(420),
        label = "wallpaperBlur",
    )
    val homeScale by animateFloatAsState(
        targetValue = if (active) 0.94f else 1f,
        animationSpec = motion.spec(420),
        label = "homeScale",
    )
    val homeAlpha by animateFloatAsState(
        targetValue = if (active) 0f else 1f,
        animationSpec = motion.spec(320),
        label = "homeAlpha",
    )

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        WallpaperLayer(settings, customWallpaper, blur)

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = homeScale
                    scaleY = homeScale
                    alpha = homeAlpha
                }
        ) {
            HomeScreen(vm)
        }

        AnimatedVisibility(
            visible = overlay == Overlay.Drawer,
            enter = slideInVertically(motion.spec(460)) { it } + fadeIn(motion.spec(300)),
            exit = slideOutVertically(motion.spec(360)) { it } + fadeOut(motion.spec(260)),
        ) { AppDrawer(vm) }

        AnimatedVisibility(
            visible = overlay == Overlay.Control,
            enter = slideInVertically(motion.spec(460)) { -it } + fadeIn(motion.spec(300)),
            exit = slideOutVertically(motion.spec(340)) { -it } + fadeOut(motion.spec(240)),
        ) { ControlCenter(vm) }

        ToolLayer(overlay == Overlay.Settings, motion) { SettingsScreen(vm) }
        ToolLayer(overlay == Overlay.Apps, motion) { AppManagerScreen(vm) }
        ToolLayer(overlay == Overlay.About, motion) { AboutScreen(vm) }
        ToolLayer(overlay == Overlay.Calculator, motion) { CalculatorScreen(vm) }
        ToolLayer(overlay == Overlay.Notes, motion) { NotesScreen(vm) }
        ToolLayer(overlay == Overlay.Clock, motion) { ClockScreen(vm) }
        ToolLayer(overlay == Overlay.Search, motion) { SearchScreen(vm) }

        // 모든 화면 위에 뜨는 유리 토스트/팝업 (항상 맨 마지막에 그려야 한다)
        PopupHost(popup)
    }
}

@Composable
private fun ToolLayer(visible: Boolean, motion: NovaMotion, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motion.spec(300)) + scaleIn(motion.spec(420), initialScale = 0.92f),
        exit = fadeOut(motion.spec(220)) + scaleOut(motion.spec(260), targetScale = 0.96f),
    ) { content() }
}

@Suppress("DEPRECATION")
@Composable
private fun SystemBarsEffect(dark: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context.findActivity() as? Activity)?.window ?: return@SideEffect
            // 시안처럼 배경화면이 상태바/내비게이션바 뒤까지 이어지도록 투명 처리
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 29) {
                window.isNavigationBarContrastEnforced = false
            }
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
}

/* ───────────────────────── 팝업 / 토스트 화면 ───────────────────────── */

@Composable
private fun PopupHost(popup: NovaPopupController) {
    val motion = LocalNovaMotion.current

    // 토스트: 2.4초 뒤 자동으로 사라짐
    val toast = popup.toastMessage
    var lastToast by remember { mutableStateOf("") }
    if (toast != null && toast != lastToast) lastToast = toast
    LaunchedEffect(popup.toastTick) {
        if (popup.toastMessage != null) {
            delay(2400)
            popup.clearToast()
        }
    }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = toast != null,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically(motion.spec(320)) { -it } + fadeIn(motion.spec(240)),
            exit = slideOutVertically(motion.spec(260)) { -it } + fadeOut(motion.spec(200)),
        ) {
            ToastBubble(lastToast)
        }
    }

    // 확인/취소 팝업
    val dialog = popup.dialog
    var lastDialog by remember { mutableStateOf<NovaDialogRequest?>(null) }
    if (dialog != null) lastDialog = dialog
    BackHandler(enabled = dialog != null) { popup.dismissDialog() }
    val shown = lastDialog
    if (shown != null) {
        AnimatedVisibility(
            visible = dialog != null,
            enter = fadeIn(motion.spec(200)) + scaleIn(motion.spec(260), initialScale = 0.94f),
            exit = fadeOut(motion.spec(160)) + scaleOut(motion.spec(200), targetScale = 0.96f),
        ) {
            DialogLayer(
                request = shown,
                onCancel = { popup.dismissDialog() },
                onConfirm = {
                    popup.dismissDialog()
                    shown.onConfirm()
                },
            )
        }
    }
}

@Composable
private fun ToastBubble(text: String) {
    val c = LocalNovaColors.current
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier
            .statusBarsPadding()
            .padding(top = 12.dp, start = 20.dp, end = 20.dp)
            .clip(shape)
            .background(c.panel.copy(alpha = 0.94f))
            .border(1.dp, c.glassBorder, shape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckBadge(Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CheckBadge(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawCircle(Color(0xFF34C759))
        val w = size.width
        val h = size.height
        val check = Path().apply {
            moveTo(w * 0.28f, h * 0.52f)
            lineTo(w * 0.44f, h * 0.67f)
            lineTo(w * 0.74f, h * 0.34f)
        }
        drawPath(
            check,
            Color.White,
            style = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun DialogLayer(
    request: NovaDialogRequest,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val c = LocalNovaColors.current
    val shape = RoundedCornerShape(32.dp)
    val noRipple = remember { MutableInteractionSource() }
    val noRipple2 = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .clickable(interactionSource = noRipple, indication = null) { onCancel() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 36.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(c.panel.copy(alpha = 0.97f), c.panel.copy(alpha = 0.92f))
                    )
                )
                .border(1.dp, c.glassBorder, shape)
                .clickable(interactionSource = noRipple2, indication = null) { }
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                request.title,
                color = c.text,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (request.message != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    request.message,
                    color = c.textSecondary,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(22.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (request.cancelText != null) {
                    DialogButton(
                        label = request.cancelText,
                        filled = false,
                        tint = c.accent,
                        onTint = c.accent,
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                    )
                }
                DialogButton(
                    label = request.confirmText,
                    filled = true,
                    tint = if (request.destructive) c.danger else c.accent,
                    onTint = if (request.destructive) Color.White else c.onAccent,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DialogButton(
    label: String,
    filled: Boolean,
    tint: Color,
    onTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Pressable(
        modifier
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (filled) tint else tint.copy(alpha = 0.16f)),
        onClick = onClick,
    ) {
        Text(
            label,
            Modifier.align(Alignment.Center),
            color = if (filled) onTint else tint,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
