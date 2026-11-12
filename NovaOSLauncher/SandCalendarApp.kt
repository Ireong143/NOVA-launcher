package com.nova.os.launcher.sand

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.launcher.ui.components.CircleIconButton
import com.nova.os.launcher.ui.components.GlassCard
import com.nova.os.launcher.ui.components.ToolScaffold
import com.nova.os.launcher.ui.theme.LocalNovaColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

private val DAY_FMT = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)

class CalEvent(val id: Long, val date: String, val title: String, val time: String)

/** 일정 저장소 (SharedPreferences JSON). AI 에이전트도 이 저장소를 쓴다. */
object SandCalendarStore {
    val events = mutableStateListOf<CalEvent>()
    private var inited = false

    fun init(context: Context) {
        if (inited) return
        inited = true
        try {
            val arr = JSONArray(SandPrefs.of(context).getString("calendar", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                events.add(CalEvent(o.getLong("id"), o.getString("date"), o.getString("title"), o.optString("time", "")))
            }
        } catch (e: Exception) {
            // 저장된 값이 깨졌으면 빈 상태로 시작
        }
    }

    private fun save(context: Context) {
        val arr = JSONArray()
        for (e in events) {
            arr.put(JSONObject().put("id", e.id).put("date", e.date).put("title", e.title).put("time", e.time))
        }
        SandPrefs.of(context).edit().putString("calendar", arr.toString()).apply()
    }

    fun add(context: Context, date: LocalDate, title: String, time: String) {
        init(context)
        val t = if (Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(time.trim())) time.trim() else ""
        events.add(CalEvent(System.currentTimeMillis(), date.toString(), title.trim(), t))
        save(context)
    }

    fun remove(context: Context, id: Long) {
        events.removeAll { it.id == id }
        save(context)
    }

    fun on(date: LocalDate): List<CalEvent> =
        events.filter { it.date == date.toString() }.sortedBy { if (it.time.isEmpty()) "99:99" else it.time }
}

/** 양력 공휴일 + 2026~2027 음력 공휴일(대체공휴일은 포함하지 않음). */
fun sandHoliday(d: LocalDate): String? {
    when (d.monthValue * 100 + d.dayOfMonth) {
        101 -> return "신정"
        301 -> return "삼일절"
        505 -> return "어린이날"
        606 -> return "현충일"
        815 -> return "광복절"
        1003 -> return "개천절"
        1009 -> return "한글날"
        1225 -> return "성탄절"
    }
    return when (d.toString()) {
        "2026-02-16", "2026-02-18", "2027-02-06", "2027-02-08" -> "설날 연휴"
        "2026-02-17", "2027-02-07" -> "설날"
        "2026-05-24", "2027-05-13" -> "부처님오신날"
        "2026-09-24", "2026-09-26", "2027-09-14", "2027-09-16" -> "추석 연휴"
        "2026-09-25", "2027-09-15" -> "추석"
        else -> null
    }
}

@Composable
fun SandCalendarScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    remember { SandCalendarStore.init(ctx); true }
    val c = LocalNovaColors.current
    val today = remember { LocalDate.now() }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var adding by remember { mutableStateOf(false) }

    ToolScaffold(
        "캘린더", onClose,
        actions = {
            CircleIconButton(Icons.Rounded.Today, "오늘", { month = YearMonth.now(); selected = LocalDate.now() })
            Spacer(Modifier.width(8.dp))
            CircleIconButton(Icons.Rounded.Add, "일정 추가", { adding = true })
        }
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(Icons.Rounded.ChevronLeft, "이전 달", { month = month.minusMonths(1) })
                Text(
                    month.year.toString() + "년 " + month.monthValue + "월",
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                CircleIconButton(Icons.Rounded.ChevronRight, "다음 달", { month = month.plusMonths(1) })
            }

            val labels = if (SandConfig.mondayFirst) {
                listOf("월", "화", "수", "목", "금", "토", "일")
            } else {
                listOf("일", "월", "화", "수", "목", "금", "토")
            }
            val first = month.atDay(1)
            val lead = if (SandConfig.mondayFirst) first.dayOfWeek.value - 1 else first.dayOfWeek.value % 7
            val days = month.lengthOfMonth()
            val rows = (lead + days + 6) / 7

            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        for (l in labels) {
                            Text(
                                l,
                                Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                color = if (l == "일") c.danger else if (l == "토") c.accent else c.textSecondary
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    for (r in 0 until rows) {
                        Row(Modifier.fillMaxWidth()) {
                            for (col in 0 until 7) {
                                val n = r * 7 + col - lead + 1
                                if (n in 1..days) {
                                    val date = month.atDay(n)
                                    DayCell(date, date == selected, date == today) { selected = date }
                                } else {
                                    Spacer(Modifier.weight(1f).height(50.dp))
                                }
                            }
                        }
                    }
                }
            }

            Text(
                selected.format(DAY_FMT),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            val holiday = sandHoliday(selected)
            if (holiday != null) {
                Text(holiday, color = c.danger, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            val list = SandCalendarStore.on(selected)
            if (list.isEmpty()) {
                Text("등록된 일정이 없어요. 오른쪽 위 + 버튼으로 추가해 보세요.", color = c.textSecondary, fontSize = 14.sp)
            }
            for (e in list) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            if (e.time.isNotEmpty()) Text(e.time, fontSize = 13.sp, color = c.textSecondary)
                        }
                        CircleIconButton(Icons.Rounded.Delete, "삭제", { SandCalendarStore.remove(ctx, e.id) }, size = 38.dp)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (adding) {
        var title by remember { mutableStateOf("") }
        var time by remember { mutableStateOf("") }
        SandDialog(onDismiss = { adding = false }) {
            Text("일정 추가", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(selected.format(DAY_FMT), color = c.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            SandTextField(title, { title = it }, "일정 제목", Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            SandTextField(time, { time = it }, "시간(예: 14:30, 비워도 돼요)", Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SandButton("취소", { adding = false }, Modifier.weight(1f), danger = false)
                SandButton(
                    "저장",
                    {
                        if (title.isNotBlank()) {
                            SandCalendarStore.add(ctx, selected, title, time)
                            adding = false
                        }
                    },
                    Modifier.weight(1f),
                    enabled = title.isNotBlank()
                )
            }
        }
    }
}

@Composable
private fun RowScope.DayCell(date: LocalDate, selected: Boolean, today: Boolean, onClick: () -> Unit) {
    val c = LocalNovaColors.current
    val hasEvent = SandCalendarStore.events.any { it.date == date.toString() }
    val dow = date.dayOfWeek.value // 월=1 ... 일=7
    val special = dow == 7 || sandHoliday(date) != null
    val numColor = when {
        selected -> c.onAccent
        special -> c.danger
        dow == 6 -> c.accent
        else -> c.text
    }
    Column(
        Modifier
            .weight(1f)
            .height(50.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (selected) c.accent else Color.Transparent)
                .border(if (today && !selected) 1.5.dp else 0.dp, if (today && !selected) c.accent else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(date.dayOfMonth.toString(), fontSize = 15.sp, color = numColor, fontWeight = if (today) FontWeight.Bold else FontWeight.Normal)
        }
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(if (hasEvent) c.accent else Color.Transparent)
        )
    }
}
