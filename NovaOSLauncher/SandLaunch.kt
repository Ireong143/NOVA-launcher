package com.nova.os.launcher.sand

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.nova.os.launcher.NovaViewModel
import com.nova.os.launcher.data.AppInfo
import com.nova.os.launcher.ui.components.SandAppIcon
import com.nova.os.launcher.ui.components.SandKind
import com.nova.os.launcher.ui.components.sandKindOf
import com.nova.os.launcher.ui.theme.LocalNovaColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** 실행 중인 앱 하나의 정보(아이콘 위치, 이름, 아이콘, 실제로 앱을 여는 동작). */
class LaunchSpec(
    val cx: Float,
    val cy: Float,
    val label: String,
    val kind: SandKind?,
    val icon: ImageBitmap?,
    val go: () -> Unit,
)

object SandLaunch {
    /** 마지막으로 손가락이 닿은 화면 좌표(px). SAND 화면이 열릴 때 커지기 시작하는 점으로 쓴다. */
    @Volatile var lastX: Float = 540f
    @Volatile var lastY: Float = 1500f
    @Volatile var screenW: Float = 1080f
    @Volatile var screenH: Float = 2340f

    var spec by mutableStateOf<LaunchSpec?>(null)

    @Volatile var pausedSeen: Boolean = false
    @Volatile var resumedAfterPause: Boolean = false

    fun originFraction(): TransformOrigin = TransformOrigin(
        (lastX / screenW).coerceIn(0f, 1f),
        (lastY / screenH).coerceIn(0f, 1f)
    )

    /** 외부 앱 실행: 아이콘 자리에서 로딩 화면이 커진 뒤 실제로 앱을 연다. */
    fun launchApp(ctx: Context, x: Float, y: Float, app: AppInfo, vm: NovaViewModel) {
        if (spec != null) return
        val kind = sandKindOf(app)
        pausedSeen = false
        resumedAfterPause = false
        spec = LaunchSpec(x, y, app.label, kind, if (kind == null) app.icon else null) {
            vm.launchApp(app)
            sandOverrideTransition(ctx)
        }
    }
}

/**
 * 앱이 열린 직후 호출해서 시스템의 "옆으로 밀려 들어오는" 전환 대신
 * 살짝 커지며 나타나는 전환(res/anim/sand_app_in)을 쓰게 한다. 실패해도 앱 실행에는 영향이 없다.
 */
@Suppress("DEPRECATION")
fun sandOverrideTransition(ctx: Context) {
    val act = sandActivityOf(ctx) ?: return
    try {
        val enter = act.resources.getIdentifier("sand_app_in", "anim", act.packageName)
        val exit = act.resources.getIdentifier("sand_app_hold", "anim", act.packageName)
        if (enter != 0 && exit != 0) act.overridePendingTransition(enter, exit)
    } catch (e: Exception) {
        // 전환 효과만 못 쓰는 것이므로 무시
    }
}

/** 화면 어디든 손가락이 닿은 위치를 기록만 한다(터치를 가로채지 않는다). */
fun Modifier.sandTrackTouch(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            val d = e.changes.firstOrNull { it.changedToDown() }
            if (d != null) {
                SandLaunch.lastX = d.position.x
                SandLaunch.lastY = d.position.y
            }
        }
    }
}

/**
 * 앱 아이콘을 짧게 탭하면 실제 실행 대신 "커지는 로딩 화면" 모션을 먼저 보여 주고 앱을 연다.
 * 길게 누르기·드래그는 건드리지 않아서 기존 길게 누르기 메뉴가 그대로 동작한다.
 */
fun Modifier.sandLaunchTap(enabled: Boolean, app: AppInfo, vm: NovaViewModel): Modifier = composed {
    val ctx = LocalContext.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    if (!enabled || !SandConfig.launchFx) {
        Modifier
    } else {
        Modifier
            .onGloballyPositioned { origin = it.positionInWindow() }
            .pointerInput(app.packageName) {
                val slop = viewConfiguration.touchSlop
                val longMs = viewConfiguration.longPressTimeoutMillis
                awaitPointerEventScope {
                    while (true) {
                        var ev = awaitPointerEvent(PointerEventPass.Initial)
                        val first = ev.changes.firstOrNull { it.changedToDown() } ?: continue
                        val id = first.id
                        val startPos = first.position
                        val startT = first.uptimeMillis
                        var cancelled = false
                        var up: PointerInputChange? = null
                        while (true) {
                            ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == id } ?: break
                            if (ev.changes.size > 1) cancelled = true
                            if ((ch.position - startPos).getDistance() > slop) cancelled = true
                            if (ch.changedToUp()) {
                                up = ch
                                break
                            }
                            if (!ch.pressed) break
                        }
                        val u = up
                        if (u != null && !cancelled && u.uptimeMillis - startT < longMs) {
                            u.consume()
                            SandLaunch.launchApp(ctx, origin.x + u.position.x, origin.y + u.position.y, app, vm)
                        }
                    }
                }
            }
    }
}

private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private val ExpandEase = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/** 홈 화면 위에 얹는 층: 아이콘 자리에서 화면 전체로 커지는 로딩 화면. */
@Composable
fun SandLaunchLayer() {
    val ctx = LocalContext.current
    val c = LocalNovaColors.current
    val density = LocalDensity.current
    val progress = remember { Animatable(0f) }
    var shown by remember { mutableStateOf<LaunchSpec?>(null) }
    val cur = SandLaunch.spec

    val owner = remember(ctx) { sandActivityOf(ctx) as? LifecycleOwner }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (SandLaunch.spec != null) {
                if (e == Lifecycle.Event.ON_PAUSE) SandLaunch.pausedSeen = true
                if (e == Lifecycle.Event.ON_RESUME && SandLaunch.pausedSeen) SandLaunch.resumedAfterPause = true
            }
        }
        owner?.lifecycle?.addObserver(obs)
        onDispose {
            owner?.lifecycle?.removeObserver(obs)
            SandLaunch.spec = null
        }
    }

    LaunchedEffect(cur) {
        if (cur != null) {
            shown = cur
            progress.snapTo(0f)
            progress.animateTo(1f, tween(360, easing = ExpandEase))
            cur.go()
            // 앱이 화면을 가져갔다가(일시정지) 돌아올 때까지, 아니면 최대 3초 기다린다.
            var waited = 0
            while (!SandLaunch.resumedAfterPause && !(waited > 3000 && !SandLaunch.pausedSeen)) {
                delay(40)
                waited += 40
            }
            progress.animateTo(0f, tween(300, easing = ExpandEase))
            shown = null
            SandLaunch.spec = null
        }
    }

    val s = shown ?: return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sw = constraints.maxWidth.toFloat()
        val sh = constraints.maxHeight.toFloat()
        val p = progress.value
        val base = with(density) { 60.dp.toPx() }
        val w = mix(base, sw, p)
        val h = mix(base, sh, p)
        val x = mix(s.cx - base / 2f, 0f, p)
        val y = mix(s.cy - base / 2f, 0f, p)
        val r = mix(base * 0.27f, 0f, p)
        Box(
            Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                .clip(RoundedCornerShape(with(density) { r.toDp() }))
                .background(c.panel)
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        val a = ((p - 0.3f) / 0.5f).coerceIn(0f, 1f)
                        alpha = a
                        scaleX = 0.85f + 0.15f * a
                        scaleY = 0.85f + 0.15f * a
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (s.kind != null) {
                    SandAppIcon(s.kind, 88.dp)
                } else if (s.icon != null) {
                    Image(
                        bitmap = s.icon,
                        contentDescription = null,
                        modifier = Modifier.size(88.dp).clip(RoundedCornerShape(24.dp))
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(s.label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1)
                Spacer(Modifier.height(16.dp))
                SandLoadingDots()
            }
        }
    }
}

/** 점 세 개가 차례로 밝아지는 로딩 표시. */
@Composable
fun SandLoadingDots() {
    val c = LocalNovaColors.current
    val t = rememberInfiniteTransition(label = "sandDots")
    val v by t.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "sandDotsValue"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until 3) {
            val on = v.toInt() == i
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(c.text.copy(alpha = if (on) 0.9f else 0.28f))
            )
        }
    }
}

/**
 * 시스템 상태 바(통신사·시간·와이파이·데이터)를 숨긴다. 위로 쓸어내리면 잠깐 나타났다 사라진다.
 * hide가 꺼지면 원래대로 되돌린다.
 */
@Composable
fun SandSystemBar(hide: Boolean) {
    val ctx = LocalContext.current
    val activity = remember(ctx) { sandActivityOf(ctx) }
    DisposableEffect(hide, activity) {
        val act = activity
        if (act == null) {
            onDispose { }
        } else {
            fun apply() {
                try {
                    val window = act.window
                    val ctl = WindowCompat.getInsetsController(window, window.decorView)
                    if (hide) {
                        if (Build.VERSION.SDK_INT >= 28) {
                            // 구멍 카메라 영역까지 화면을 쓰게 해서 위쪽에 검은 띠가 생기지 않게 한다.
                            val lp = window.attributes
                            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                            window.attributes = lp
                        }
                        ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        ctl.hide(WindowInsetsCompat.Type.statusBars())
                    } else {
                        ctl.show(WindowInsetsCompat.Type.statusBars())
                    }
                } catch (e: Exception) {
                    // 창이 아직 준비되지 않았으면 다음 ON_RESUME에서 다시 시도
                }
            }
            apply()
            val lifecycleOwner = act as? LifecycleOwner
            val obs = LifecycleEventObserver { _, e ->
                if (e == Lifecycle.Event.ON_RESUME) apply()
            }
            lifecycleOwner?.lifecycle?.addObserver(obs)
            onDispose {
                lifecycleOwner?.lifecycle?.removeObserver(obs)
                try {
                    val window = act.window
                    WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.statusBars())
                } catch (e: Exception) {
                    // 무시
                }
            }
        }
    }
}

/** 시스템 상태 바의 실제 높이(dp). 상태 바를 숨겼을 때 같은 높이만큼 위쪽을 비워 두는 데 쓴다. */
@Composable
fun sandStatusBarHeight(): androidx.compose.ui.unit.Dp {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    return remember(ctx) {
        val id = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        val px = if (id > 0) ctx.resources.getDimensionPixelSize(id) else 0
        with(density) { px.toDp() }
    }
}
