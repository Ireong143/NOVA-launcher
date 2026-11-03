package com.nova.os.launcher.ui.tools

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.launcher.NovaViewModel
import com.nova.os.launcher.StopwatchState
import com.nova.os.launcher.TimerState
import com.nova.os.launcher.system.SystemActions
import com.nova.os.launcher.ui.components.CircleIconButton
import com.nova.os.launcher.ui.components.GlassCard
import com.nova.os.launcher.ui.components.NovaSegmented
import com.nova.os.launcher.ui.components.Pressable
import com.nova.os.launcher.ui.components.ToolScaffold
import com.nova.os.launcher.ui.theme.LocalNovaColors
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun ClockScreen(vm: NovaViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    ToolScaffold(
        title = "시계",
        onBack = { vm.back() },
        actions = { CircleIconButton(Icons.Rounded.Alarm, "알람 열기", { SystemActions.openAlarms(context) }) },
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            NovaSegmented(listOf("시계", "스톱워치", "타이머"), tab, { tab = it })
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> WorldClockTab()
                1 -> StopwatchTab(vm.stopwatch)
                else -> TimerTab(vm.timer)
            }
        }
    }
}

// ------------------------------------------------------------------ world clock

private class City(val name: String, val zone: String)

private val Cities = listOf(
    City("서울", "Asia/Seoul"),
    City("도쿄", "Asia/Tokyo"),
    City("베이징", "Asia/Shanghai"),
    City("두바이", "Asia/Dubai"),
    City("파리", "Europe/Paris"),
    City("런던", "Europe/London"),
    City("뉴욕", "America/New_York"),
    City("로스앤젤레스", "America/Los_Angeles"),
    City("시드니", "Australia/Sydney"),
)

@Composable
private fun WorldClockTab() {
    val c = LocalNovaColors.current
    val now by produceState(initialValue = ZonedDateTime.now()) {
        while (true) {
            value = ZonedDateTime.now()
            delay(250)
        }
    }
    val locale = Locale.getDefault()
    val hms = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    val date = now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                hms,
                style = Tabular,
                fontSize = 56.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
            )
            Text(date, fontSize = 15.sp, color = c.textSecondary)
            Text(now.zone.id, fontSize = 12.sp, color = c.textSecondary)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(Cities) { _, city ->
                val t = now.withZoneSameInstant(ZoneId.of(city.zone))
                val dayDiff = ChronoUnit.DAYS.between(now.toLocalDate(), t.toLocalDate())
                val diffMin = (t.offset.totalSeconds - now.offset.totalSeconds) / 60
                val dayLabel = when (dayDiff) {
                    -1L -> "어제"
                    0L -> "오늘"
                    1L -> "내일"
                    else -> ""
                }
                val diffText = if (diffMin == 0) {
                    "같은 시간대"
                } else {
                    val a = abs(diffMin)
                    val sign = if (diffMin > 0) "+" else "-"
                    sign + (a / 60) + "시간" + (if (a % 60 != 0) " ${a % 60}분" else "")
                }
                GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(city.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text("$dayLabel · $diffText", fontSize = 12.sp, color = c.textSecondary)
                        }
                        Text(t.format(timeFormat), style = Tabular, fontSize = 28.sp, fontWeight = FontWeight.Light)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ stopwatch

private fun formatStopwatch(ms: Long): String {
    val total = ms / 10
    return String.format(Locale.US, "%02d:%02d.%02d", total / 6000, (total / 100) % 60, total % 100)
}

@Composable
private fun StopwatchTab(state: StopwatchState) {
    val c = LocalNovaColors.current
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.running) {
        while (state.running) {
            now = SystemClock.elapsedRealtime()
            delay(33)
        }
    }
    val elapsed = state.elapsed(now)

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(36.dp))
        Text(formatStopwatch(elapsed), style = Tabular, fontSize = 64.sp, fontWeight = FontWeight.Light, maxLines = 1)
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(
                label = if (state.running || elapsed == 0L) "랩" else "초기화",
                filled = false,
                enabled = state.running || elapsed > 0L,
                onClick = { if (state.running) state.lap() else state.reset() },
            )
            RoundButton(
                label = if (state.running) "정지" else "시작",
                filled = true,
                enabled = true,
                onClick = { state.toggle() },
            )
        }
        Spacer(Modifier.height(20.dp))
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(state.laps) { index, total ->
                val previous = state.laps.getOrNull(index + 1) ?: 0L
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("랩 ${state.laps.size - index}", color = c.textSecondary, modifier = Modifier.weight(1f))
                    Text("+" + formatStopwatch(total - previous), style = Tabular, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text(formatStopwatch(total), style = Tabular, color = c.textSecondary, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RoundButton(label: String, filled: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalNovaColors.current
    Pressable(
        Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(if (filled) c.accent else c.glass)
            .border(1.dp, c.glassBorder, CircleShape),
        onClick = { if (enabled) onClick() },
    ) {
        Text(
            label,
            Modifier.align(Alignment.Center),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = when {
                !enabled -> c.textSecondary.copy(alpha = 0.5f)
                filled -> c.onAccent
                else -> c.text
            },
        )
    }
}

// ------------------------------------------------------------------ timer

private fun formatTimer(ms: Long): String {
    val totalSec = (ms + 999) / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}

@Composable
private fun TimerTab(state: TimerState) {
    val c = LocalNovaColors.current
    val fraction = if (state.totalMs > 0) (state.remainingMs.toFloat() / state.totalMs).coerceIn(0f, 1f) else 0f

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            val ringColor = if (state.finished) c.good else c.accent
            val track = c.text.copy(alpha = 0.12f)
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 12.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(stroke / 2f, stroke / 2f)
                val style = Stroke(width = stroke, cap = StrokeCap.Round)
                drawArc(track, 0f, 360f, false, topLeft, arcSize, style = style)
                drawArc(ringColor, -90f, 360f * fraction, false, topLeft, arcSize, style = style)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatTimer(state.remainingMs),
                    style = Tabular,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                )
                if (state.finished) Text("시간이 끝났어요", color = c.good, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                else if (state.totalMs == 0L) Text("시간을 추가하세요", color = c.textSecondary, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("+30초" to 30, "+1분" to 60, "+5분" to 300, "+10분" to 600).forEach { (label, sec) ->
                Pressable(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (state.running) c.glass.copy(alpha = 0.4f) else c.glass)
                        .border(1.dp, c.glassBorder, RoundedCornerShape(50)),
                    onClick = { state.addSeconds(sec) },
                ) {
                    Text(
                        label,
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (state.running) c.textSecondary else c.text,
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(
                label = "취소",
                filled = false,
                enabled = state.totalMs > 0L,
                onClick = { state.clear() },
            )
            RoundButton(
                label = when {
                    state.running -> "일시정지"
                    state.finished -> "다시"
                    else -> "시작"
                },
                filled = true,
                enabled = state.totalMs > 0L,
                onClick = {
                    when {
                        state.running -> state.pause()
                        state.finished -> {
                            state.reset()
                            state.start()
                        }
                        else -> state.start()
                    }
                },
            )
        }
    }
}
