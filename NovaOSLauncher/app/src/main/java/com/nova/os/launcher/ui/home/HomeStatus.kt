package com.nova.os.launcher.ui.home

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nova.os.launcher.sand.SandBell
import com.nova.os.launcher.sand.SandConfig

/** 홈 상단 패널에 표시할 시스템 상태. */
@Immutable
data class SysStatus(
    val wifi: Boolean = false,
    /** 0..3 */
    val wifiLevel: Int = 3,
    val cell: Boolean = false,
    val ringer: Int = AudioManager.RINGER_MODE_NORMAL,
    /** 방해 금지(알림 제한) 켜짐 */
    val dnd: Boolean = false,
)

private fun wifiLevelFromDbm(dbm: Int): Int = when {
    dbm >= -55 -> 3
    dbm >= -67 -> 2
    else -> 1
}

private fun readSysStatus(context: Context): SysStatus {
    var wifi = false
    var wifiLevel = 3
    var cell = false
    try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
        val nets = cm.allNetworks
        for (n in nets) {
            val caps = cm.getNetworkCapabilities(n) ?: continue
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                wifi = true
                if (Build.VERSION.SDK_INT >= 29) {
                    val s = caps.signalStrength
                    if (s != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) wifiLevel = wifiLevelFromDbm(s)
                }
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) cell = true
        }
    } catch (e: Exception) {
        // ACCESS_NETWORK_STATE 권한이 없거나 조회 실패: 연결 아이콘은 꺼진 상태로 표시
    }

    var ringer = AudioManager.RINGER_MODE_NORMAL
    try {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        ringer = audio.ringerMode
    } catch (e: Exception) {
    }

    var dnd = false
    try {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val f = nm.currentInterruptionFilter
        dnd = f != NotificationManager.INTERRUPTION_FILTER_ALL &&
            f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    } catch (e: Exception) {
    }

    return SysStatus(wifi, wifiLevel, cell, ringer, dnd)
}

/** 와이파이/데이터 연결, 소리 모드, 방해 금지 변경을 감지해 상태를 갱신한다. */
@Composable
fun rememberSysStatus(): State<SysStatus> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(readSysStatus(context)) }

    DisposableEffect(context) {
        val app = context.applicationContext
        state.value = readSysStatus(app)

        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                state.value = readSysStatus(app)
            }

            override fun onLost(network: Network) {
                state.value = readSysStatus(app)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                state.value = readSysStatus(app)
            }
        }
        var registered = false
        try {
            cm?.registerDefaultNetworkCallback(callback)
            registered = true
        } catch (e: Exception) {
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                state.value = readSysStatus(app)
            }
        }
        val filter = IntentFilter().apply {
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        onDispose {
            try {
                app.unregisterReceiver(receiver)
            } catch (e: Exception) {
            }
            if (registered) {
                try {
                    cm?.unregisterNetworkCallback(callback)
                } catch (e: Exception) {
                }
            }
        }
    }
    return state
}

@Composable
fun HomeStatusIcons(status: SysStatus, tint: Color, onBell: () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        // 소리 상태: 소리/진동/무음 ("더 많은 설정"에서 끌 수 있음)
        if (SandConfig.showSound) {
            when (status.ringer) {
                AudioManager.RINGER_MODE_SILENT ->
                    Icon(Icons.Rounded.VolumeOff, "무음", Modifier.size(16.dp), tint = tint)
                AudioManager.RINGER_MODE_VIBRATE ->
                    Icon(Icons.Rounded.Vibration, "진동", Modifier.size(16.dp), tint = tint)
                else ->
                    Icon(Icons.Rounded.VolumeUp, "소리", Modifier.size(16.dp), tint = tint.copy(alpha = 0.55f))
            }
        }
        // 알림 상태: 방해 금지가 켜져 있으면 알림 꺼짐 표시
        if (status.dnd) {
            Icon(Icons.Rounded.NotificationsOff, "방해 금지", Modifier.size(15.dp), tint = tint)
        }
        if (SandConfig.showCell) CellBars(connected = status.cell, tint = tint)
        if (SandConfig.showWifi) WifiGlyph(connected = status.wifi, level = status.wifiLevel, tint = tint)
        // 새 알림 종(점 배지) → 알림 창
        if (SandConfig.showBell) SandBell(tint, onBell)
    }
}

/** 모바일 데이터 신호 막대. 연결이 없으면 흐리게 표시. */
@Composable
private fun CellBars(connected: Boolean, tint: Color) {
    Canvas(Modifier.size(width = 17.dp, height = 12.dp)) {
        val bw = 3.dp.toPx()
        val gap = 1.6.dp.toPx()
        for (i in 0 until 4) {
            val h = size.height * (0.35f + 0.65f * i / 3f)
            drawRoundRect(
                color = tint.copy(alpha = if (connected) 1f else 0.3f),
                topLeft = Offset(i * (bw + gap), size.height - h),
                size = Size(bw, h),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
        }
    }
}

/** 와이파이 부채꼴 3개 + 점. 신호 세기만큼 진하게, 연결이 없으면 모두 흐리게. */
@Composable
private fun WifiGlyph(connected: Boolean, level: Int, tint: Color) {
    Canvas(Modifier.size(width = 17.dp, height = 13.dp)) {
        val cx = size.width / 2f
        val cy = size.height
        val stroke = 1.9.dp.toPx()
        val radii = listOf(size.height * 0.40f, size.height * 0.70f, size.height * 0.98f)
        radii.forEachIndexed { i, r ->
            val on = connected && i < level.coerceIn(1, 3)
            drawArc(
                color = tint.copy(alpha = if (on) 1f else 0.3f),
                startAngle = -135f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        drawCircle(
            color = tint.copy(alpha = if (connected) 1f else 0.3f),
            radius = 1.4.dp.toPx(),
            center = Offset(cx, cy - 1.4.dp.toPx())
        )
    }
}
