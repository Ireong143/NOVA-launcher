package com.nova.os.launcher.ui.control

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.launcher.NovaViewModel
import com.nova.os.launcher.data.Overlay
import com.nova.os.launcher.data.ThemeMode
import com.nova.os.launcher.system.SystemActions
import com.nova.os.launcher.ui.components.CircleIconButton
import com.nova.os.launcher.ui.components.PillSlider
import com.nova.os.launcher.ui.components.Pressable
import com.nova.os.launcher.ui.theme.LocalNovaColors
import com.nova.os.launcher.ui.theme.LocalNovaMotion
import kotlinx.coroutines.delay

@Composable
fun ControlCenter(vm: NovaViewModel) {
    val c = LocalNovaColors.current
    val context = LocalContext.current
    val battery by vm.battery.collectAsState()
    val torch = vm.torch

    var wifi by remember { mutableStateOf(SystemActions.isWifiEnabled(context)) }
    var bluetooth by remember { mutableStateOf(SystemActions.isBluetoothEnabled(context)) }
    var autoRotate by remember { mutableStateOf(SystemActions.isAutoRotate(context)) }
    var canWrite by remember { mutableStateOf(SystemActions.canWriteSettings(context)) }
    var brightness by remember { mutableFloatStateOf(SystemActions.readBrightness(context)) }
    var volume by remember { mutableFloatStateOf(SystemActions.readVolume(context)) }

    // Refresh tile states while visible and whenever the launcher resumes (e.g. back from Settings).
    LaunchedEffect(vm.resumeTick) {
        brightness = SystemActions.readBrightness(context)
        volume = SystemActions.readVolume(context)
        while (true) {
            wifi = SystemActions.isWifiEnabled(context)
            bluetooth = SystemActions.isBluetoothEnabled(context)
            autoRotate = SystemActions.isAutoRotate(context)
            canWrite = SystemActions.canWriteSettings(context)
            delay(1200)
        }
    }

    val panelShape = RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -60.dp.toPx()) vm.close() },
                    onDragCancel = { total = 0f },
                    onVerticalDrag = { _, dy -> total += dy },
                )
            }
    ) {
        // Scrim: tap outside the panel to close.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    vm.close()
                }
        )

        Column(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .clip(panelShape)
                .background(c.sheet)
                .border(1.dp, c.glassBorder, panelShape)
                .pointerInput(Unit) { detectTapGestures { } }
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("제어 센터", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("NOVA OS", fontSize = 12.sp, letterSpacing = 3.sp, color = c.textSecondary)
                }
                CircleIconButton(Icons.Rounded.Settings, "설정", { vm.open(Overlay.Settings) })
            }

            // Battery card
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(c.glass)
                    .border(1.dp, c.glassBorder, RoundedCornerShape(26.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BatteryGauge(battery.percent, battery.charging)
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (battery.valid) "배터리 ${battery.percent}%" else "배터리 정보 없음",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val status = when {
                        battery.charging -> "충전 중"
                        battery.plugged -> "연결됨"
                        else -> "사용 중"
                    }
                    Text(
                        "$status · ${"%.1f".format(battery.tempC)}°C · 상태 ${battery.health}",
                        fontSize = 12.sp,
                        color = c.textSecondary,
                    )
                }
            }

            // Toggle tiles
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ControlTile(
                    Icons.Rounded.Wifi, "Wi-Fi", if (wifi) "켜짐" else "꺼짐", wifi,
                    { SystemActions.openWifi(context) }, Modifier.weight(1f),
                )
                ControlTile(
                    Icons.Rounded.Bluetooth, "블루투스", if (bluetooth) "켜짐" else "꺼짐", bluetooth,
                    { SystemActions.openBluetooth(context) }, Modifier.weight(1f),
                )
                ControlTile(
                    Icons.Rounded.ScreenRotation, "화면 회전", if (autoRotate) "자동" else "세로 고정", autoRotate,
                    {
                        if (SystemActions.setAutoRotate(context, !autoRotate)) {
                            autoRotate = !autoRotate
                        } else {
                            vm.toast("'시스템 설정 수정' 권한을 허용해 주세요")
                            SystemActions.openWriteSettings(context)
                        }
                    },
                    Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ControlTile(
                    if (c.isDark) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                    "다크 모드", if (c.isDark) "다크" else "라이트", c.isDark,
                    {
                        vm.update { it.copy(themeMode = if (c.isDark) ThemeMode.LIGHT else ThemeMode.DARK) }
                    },
                    Modifier.weight(1f),
                )
                ControlTile(
                    if (torch.isOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                    "손전등", if (!torch.available) "지원 안 함" else if (torch.isOn) "켜짐" else "꺼짐", torch.isOn,
                    { if (!torch.toggle()) vm.toast("손전등을 사용할 수 없어요") },
                    Modifier.weight(1f),
                )
                ControlTile(
                    Icons.Rounded.AirplanemodeActive, "비행기 모드", "설정 열기", false,
                    { SystemActions.openAirplane(context) }, Modifier.weight(1f),
                )
            }

            // Sliders
            PillSlider(
                value = brightness,
                onValueChange = { v ->
                    brightness = v
                    if (!SystemActions.setSystemBrightness(context, v)) SystemActions.setWindowBrightness(context, v)
                },
                icon = Icons.Rounded.BrightnessMedium,
            )
            if (!canWrite) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "지금은 NOVA 화면에서만 밝기가 바뀌어요.",
                        fontSize = 12.sp,
                        color = c.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { SystemActions.openWriteSettings(context) }) { Text("전체 적용 허용") }
                }
            }
            PillSlider(
                value = volume,
                onValueChange = { v ->
                    volume = v
                    SystemActions.setVolume(context, v)
                },
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
            )

            // Shortcuts
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Shortcut(Icons.Rounded.Calculate, "계산기", Modifier.weight(1f)) { vm.open(Overlay.Calculator) }
                Shortcut(Icons.Rounded.EditNote, "메모", Modifier.weight(1f)) { vm.open(Overlay.Notes) }
                Shortcut(Icons.Rounded.Schedule, "시계", Modifier.weight(1f)) { vm.open(Overlay.Clock) }
                Shortcut(Icons.Rounded.Settings, "설정", Modifier.weight(1f)) { vm.open(Overlay.Settings) }
            }

            // Handle: swipe up to close.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .pointerInput(Unit) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = { if (total < -30.dp.toPx()) vm.close() },
                            onDragCancel = { total = 0f },
                            onVerticalDrag = { _, dy -> total += dy },
                        )
                    }
                    .clickable { vm.close() },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(44.dp).height(4.dp).clip(CircleShape).background(c.text.copy(alpha = 0.35f)))
            }
        }
    }
}

@Composable
private fun ControlTile(
    icon: ImageVector,
    label: String,
    state: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalNovaColors.current
    val motion = LocalNovaMotion.current
    val bg by animateColorAsState(if (active) c.accent else c.glass, motion.spec(240), label = "tileBg")
    val fg = if (active) c.onAccent else c.text
    val shape = RoundedCornerShape(24.dp)
    Pressable(
        modifier
            .height(96.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, c.glassBorder, shape),
        onClick = onClick,
    ) {
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(icon, null, Modifier.size(26.dp), tint = fg)
            Column {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
                Text(state, fontSize = 11.sp, color = fg.copy(alpha = 0.75f), maxLines = 1)
            }
        }
    }
}

@Composable
private fun Shortcut(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalNovaColors.current
    val shape = RoundedCornerShape(20.dp)
    Pressable(
        modifier.clip(shape).background(c.glass).border(1.dp, c.glassBorder, shape),
        onClick = onClick,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(icon, null, Modifier.size(24.dp), tint = c.accent)
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
private fun BatteryGauge(percent: Int, charging: Boolean) {
    val c = LocalNovaColors.current
    val color = when {
        charging -> c.good
        percent <= 15 -> c.danger
        percent <= 30 -> c.warn
        else -> c.accent
    }
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            val style = Stroke(width = stroke, cap = StrokeCap.Round)
            drawArc(color.copy(alpha = 0.2f), 0f, 360f, false, topLeft, arcSize, style = style)
            drawArc(color, -90f, 360f * percent.coerceIn(0, 100) / 100f, false, topLeft, arcSize, style = style)
        }
        Icon(
            if (charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryFull,
            null,
            Modifier.size(24.dp),
            tint = color,
        )
    }
}
