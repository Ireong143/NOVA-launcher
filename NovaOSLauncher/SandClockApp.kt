package com.nova.os.launcher.sand

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.AlarmClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.launcher.NovaViewModel
import com.nova.os.launcher.data.Overlay
import com.nova.os.launcher.ui.components.GlassCard
import com.nova.os.launcher.ui.components.NovaSegmented
import com.nova.os.launcher.ui.components.NovaStepper
import com.nova.os.launcher.ui.components.SectionLabel
import com.nova.os.launcher.ui.components.ToolScaffold
import com.nova.os.launcher.ui.theme.LocalNovaColors
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 스톱워치 상태: 화면을 닫아도 계속 흐른다. */
private object SandStopwatch {
    var running by mutableStateOf(false)
    var accMs by mutableStateOf(0L)
    var startedAt by mutableStateOf(0L)
    val laps = mutableStateListOf<Long>()

    fun elapsed(now: Long): Long = if (running) accMs + (now - startedAt) else accMs

    fun toggle() {
        if (running) {
            accMs += SystemClock.elapsedRealtime() - startedAt
            running = false
        } else {
            startedAt = SystemClock.elapsedRealtime()
            running = true
        }
    }

    fun lapOrReset() {
        if (running) {
            laps.add(0, elapsed(SystemClock.elapsedRealtime()))
        } else {
            accMs = 0L
            laps.clear()
        }
    }
}

/** 타이머 상태: 화면을 닫아도 계속 흐르고, 끝나면 진동과 알림음으로 알려 준다. */
private object SandTimer {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    var minutes by mutableStateOf(5)
    var running by mutableStateOf(false)
    var remainMs by mutableStateOf(5 * 60_000L)
    var finished by mutableStateOf(false)
    private var endAt = 0L

    fun setMinutes(m: Int) {
        if (running) return
        minutes = m.coerceIn(1, 180)
        remainMs = minutes * 60_000L
        finished = false
    }

    fun toggle(context: Context) {
        if (running) {
            job?.cancel()
            remainMs = (endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            running = false
            return
        }
        if (remainMs <= 0L) remainMs = minutes * 60_000L
        finished = false
        endAt = SystemClock.elapsedRealtime() + remainMs
        running = true
        val app = context.applicationContext
        job = scope.launch {
            while (true) {
                val left = endAt - SystemClock.elapsedRealtime()
                if (left <= 0L) break
                remainMs = left
                delay(100)
            }
            remainMs = 0L
            running = false
            finished = true
            ring(app)
        }
    }

    fun reset() {
        job?.cancel()
        running = false
        finished = false
        remainMs = minutes * 60_000L
    }

    @Suppress("DEPRECATION")
    private fun ring(context: Context) {
        try {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 250, 500, 250, 500), -1))
            } else {
                v.vibrate(longArrayOf(0, 500, 250, 500, 250, 500), -1)
            }
        } catch (e: Exception) {
            // 진동을 쓸 수 없으면 무시
        }
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            RingtoneManager.getRingtone(context, uri)?.play()
        } catch (e: Exception) {
            // 소리를 낼 수 없으면 무시
        }
    }
}

private class City(val name: String, val zone: String)

private val Cities = listOf(
    City("서울", "Asia/Seoul"),
    City("도쿄", "Asia/Tokyo"),
    City("베이징", "Asia/Shanghai"),
    City("런던", "Europe/London"),
    City("파리", "Europe/Paris"),
    City("뉴욕", "America/New_York"),
    City("로스앤젤레스", "America/Los_Angeles"),
    City("시드니", "Australia/Sydney"),
)

private fun fmtStopwatch(ms: Long): String {
    val total = ms / 10
    val cs = (total % 100).toInt()
    val s = ((total / 100) % 60).toInt()
    val m = (total / 6000).toInt()
    return String.format(Locale.US, "%02d:%02d.%02d", m, s, cs)
}

private fun fmtTimer(ms: Long): String {
    val sec = (ms + 999) / 1000
    val h = (sec / 3600).toInt()
    val m = ((sec / 60) % 60).toInt()
    val s = (sec % 60).toInt()
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}

@Composable
fun SandClockScreen(vm: NovaViewModel, onClose: () -> Unit) {
    val c = LocalNovaColors.current
    var tab by remember { mutableStateOf(0) }
    ToolScaffold("시계", onClose) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            NovaSegmented(listOf("시계", "스톱워치", "타이머"), tab, { tab = it })
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> ClockTab(vm, onClose)
                1 -> StopwatchTab()
                else -> TimerTab()
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "초와 분이 바뀔 때 숫자가 위로 굴러 올라가요",
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            fontSize = 11.sp,
            color = c.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ClockTab(vm: NovaViewModel, onClose: () -> Unit) {
    val c = LocalNovaColors.current
    val ctx = LocalContext.current
    val settings by vm.settings.collectAsState()
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1000L - (System.currentTimeMillis() % 1000L) + 5L)
        }
    }
    val locale = Locale.KOREAN
    val timeText = now.format(DateTimeFormatter.ofPattern(if (settings.use24h) "HH:mm" else "h:mm", locale))
    val secText = now.format(DateTimeFormatter.ofPattern("ss", locale))
    val ampm = now.format(DateTimeFormatter.ofPattern("a", locale))
    val dateText = now.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE", locale))

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 26.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(dateText, fontSize = 14.sp, color = c.textSecondary)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingText(
                        timeText,
                        fontSize = 84.sp,
                        lineHeight = 92.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-2).sp,
                        color = c.text
                    )
                    Column(Modifier.padding(start = 6.dp, bottom = 14.dp)) {
                        if (!settings.use24h) Text(ampm, fontSize = 15.sp, color = c.textSecondary)
                        RollingText(secText, fontSize = 26.sp, fontWeight = FontWeight.Medium, color = c.accent)
                    }
                }
            }
        }

        SectionLabel("세계 시간")
        val localDate = LocalDate.now()
        for (city in Cities) {
            val zt = remember(city.zone, now.minute) { ZonedDateTime.now(ZoneId.of(city.zone)) }
            val diff = (zt.toLocalDate().toEpochDay() - localDate.toEpochDay()).toInt()
            val day = when {
                diff == 0 -> "오늘"
                diff > 0 -> "내일"
                else -> "어제"
            }
            val hhmm = zt.format(DateTimeFormatter.ofPattern(if (settings.use24h) "HH:mm" else "a h:mm", locale))
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(city.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = c.text)
                        Text(day, fontSize = 12.sp, color = c.textSecondary)
                    }
                    RollingText(hhmm, fontSize = 24.sp, fontWeight = FontWeight.Light, color = c.text)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SandButton("알람 열기", {
                sandStart(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS))
            }, Modifier.weight(1f))
            SandButton("이전 시계 화면", {
                onClose()
                vm.open(Overlay.Clock)
            }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StopwatchTab() {
    val c = LocalNovaColors.current
    val tick by produceState(initialValue = SystemClock.elapsedRealtime(), SandStopwatch.running) {
        while (SandStopwatch.running) {
            value = SystemClock.elapsedRealtime()
            delay(33)
        }
    }
    val ms = SandStopwatch.elapsed(if (SandStopwatch.running) tick else 0L)
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 34.dp), contentAlignment = Alignment.Center) {
                Text(
                    fmtStopwatch(ms),
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Light,
                    color = c.text,
                    maxLines = 1,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SandButton(
                if (SandStopwatch.running) "랩" else "초기화",
                { SandStopwatch.lapOrReset() },
                Modifier.weight(1f),
                enabled = SandStopwatch.running || SandStopwatch.accMs > 0L
            )
            SandButton(if (SandStopwatch.running) "정지" else "시작", { SandStopwatch.toggle() }, Modifier.weight(1f))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val laps = SandStopwatch.laps.toList()
            for (i in laps.indices) {
                val prev = if (i + 1 < laps.size) laps[i + 1] else 0L
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                    Text("랩 " + (laps.size - i), Modifier.weight(1f), fontSize = 14.sp, color = c.textSecondary)
                    Text("+" + fmtStopwatch(laps[i] - prev), Modifier.weight(1f), fontSize = 14.sp, color = c.textSecondary)
                    Text(fmtStopwatch(laps[i]), fontSize = 14.sp, color = c.text)
                }
            }
        }
    }
}

@Composable
private fun TimerTab() {
    val c = LocalNovaColors.current
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RollingText(
                    fmtTimer(SandTimer.remainMs),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Light,
                    color = if (SandTimer.finished) c.danger else c.text
                )
                if (SandTimer.finished) {
                    Spacer(Modifier.height(4.dp))
                    Text("시간이 다 됐어요", fontSize = 14.sp, color = c.danger)
                }
            }
        }
        if (!SandTimer.running) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (m in listOf(1, 3, 5, 10, 30)) {
                    SandChip(m.toString() + "분", SandTimer.minutes == m && SandTimer.remainMs == m * 60_000L, { SandTimer.setMinutes(m) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("분 직접 설정", Modifier.weight(1f), fontSize = 14.sp, color = c.textSecondary)
                NovaStepper(SandTimer.minutes, 1..180) { SandTimer.setMinutes(it) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SandButton("초기화", { SandTimer.reset() }, Modifier.weight(1f))
            SandButton(if (SandTimer.running) "일시정지" else "시작", { SandTimer.toggle(ctx) }, Modifier.weight(1f))
        }
    }
}
