package com.nova.os.launcher.sand

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.launcher.NovaViewModel
import com.nova.os.launcher.data.Overlay
import com.nova.os.launcher.data.SearchUtil
import com.nova.os.launcher.ui.components.CircleIconButton
import com.nova.os.launcher.ui.components.ToolScaffold
import com.nova.os.launcher.ui.theme.LocalNovaColors
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private class AiMsg(val fromUser: Boolean, val text: String)

/** 다른 화면을 열어도 대화와 진행 중인 작업이 끊기지 않도록 앱 전체 범위로 보관한다. */
private object AiSession {
    val messages = mutableStateListOf<AiMsg>()
    val history = JSONArray()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    var busy by mutableStateOf(false)
}

private const val AI_SYSTEM =
    "당신은 SAND OS 런처 안에서 동작하는 개인 비서 'SAND 에이전트'입니다. 한국어로 짧고 친절하게 답하세요. " +
        "사용자가 앱 실행, 화면 열기, 일정 추가, 메모 작성, 날씨 확인, 지도 검색, 웹 검색, 알림 확인을 요청하면 " +
        "반드시 제공된 도구를 사용해 실제로 실행하세요. 도구 결과를 바탕으로 무엇을 했는지 한두 문장으로 알려주세요. " +
        "날짜는 yyyy-MM-dd 형식을 쓰고, 오늘 날짜는 도구 호출 시점 기준으로 다음 값입니다: "

private fun tool(name: String, desc: String, props: JSONObject, required: List<String>): JSONObject {
    val schema = JSONObject().put("type", "object").put("properties", props)
    schema.put("required", JSONArray(required))
    return JSONObject().put("name", name).put("description", desc).put("input_schema", schema)
}

private fun str(desc: String): JSONObject = JSONObject().put("type", "string").put("description", desc)

private fun aiTools(): JSONArray {
    val arr = JSONArray()
    arr.put(tool("launch_app", "설치된 앱을 이름으로 실행한다.", JSONObject().put("name", str("앱 이름(예: 카메라, 유튜브)")), listOf("name")))
    arr.put(
        tool(
            "open_screen",
            "SAND OS의 화면을 연다. screen 값: calendar, map, qr, paint, news, translate, github, notifications, more_settings, settings, notes, clock, calculator, search",
            JSONObject().put("screen", str("열 화면 이름")), listOf("screen")
        )
    )
    arr.put(
        tool(
            "add_calendar_event", "캘린더에 일정을 추가한다.",
            JSONObject().put("date", str("yyyy-MM-dd")).put("title", str("일정 제목")).put("time", str("HH:mm, 없으면 빈 문자열")),
            listOf("date", "title")
        )
    )
    arr.put(tool("list_calendar_events", "특정 날짜의 일정을 읽는다.", JSONObject().put("date", str("yyyy-MM-dd")), listOf("date")))
    arr.put(tool("create_note", "새 메모를 만든다.", JSONObject().put("text", str("메모 내용")), listOf("text")))
    arr.put(tool("get_weather", "현재 날씨와 오늘 최고/최저 기온을 읽는다.", JSONObject(), emptyList()))
    arr.put(tool("web_search", "브라우저에서 웹 검색을 연다.", JSONObject().put("query", str("검색어")), listOf("query")))
    arr.put(tool("open_map_search", "지도 화면을 열고 장소를 검색한다.", JSONObject().put("query", str("장소나 주소")), listOf("query")))
    arr.put(tool("get_notifications", "최근 알림 목록을 읽는다.", JSONObject(), emptyList()))
    return arr
}

private fun pageByName(name: String): SandPage? = when (name.lowercase()) {
    "calendar" -> SandPage.CALENDAR
    "map" -> SandPage.MAP
    "qr" -> SandPage.QR
    "paint" -> SandPage.PAINT
    "news" -> SandPage.NEWS
    "translate" -> SandPage.TRANSLATE
    "github" -> SandPage.GITHUB
    "notifications" -> SandPage.NOTIFICATIONS
    "more_settings" -> SandPage.MORE_SETTINGS
    else -> null
}

@Composable
fun SandAiScreen(vm: NovaViewModel, onClose: () -> Unit, onOpen: (SandPage) -> Unit) {
    val ctx = LocalContext.current
    val c = LocalNovaColors.current
    val scope = AiSession.scope
    val prefs = remember { SandPrefs.of(ctx) }
    var key by remember { mutableStateOf(prefs.getString("ai_key", "").orEmpty()) }
    var model by remember { mutableStateOf(prefs.getString("ai_model", "claude-sonnet-5-5").orEmpty()) }
    var keyIn by remember { mutableStateOf(key) }
    var modelIn by remember { mutableStateOf(model) }
    var showSetup by remember { mutableStateOf(key.isBlank()) }
    var input by remember { mutableStateOf("") }
    val messages = AiSession.messages
    val history = AiSession.history
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, AiSession.busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun execTool(name: String, input: JSONObject): String {
        when (name) {
            "launch_app" -> {
                val q = input.optString("name", "").trim()
                val apps = vm.allApps.value
                val app = apps.firstOrNull { it.label.equals(q, ignoreCase = true) }
                    ?: apps.firstOrNull { SearchUtil.matches(it, q) }
                    ?: return "'$q' 앱을 찾지 못했어요."
                vm.launchApp(app)
                return app.label + " 앱을 실행했어요."
            }
            "open_screen" -> {
                val s = input.optString("screen", "").lowercase()
                val page = pageByName(s)
                if (page != null) {
                    onOpen(page)
                    return page.label + " 화면을 열었어요."
                }
                when (s) {
                    "settings" -> { onClose(); vm.open(Overlay.Settings) }
                    "notes" -> { onClose(); vm.open(Overlay.Notes) }
                    "clock" -> { onClose(); vm.open(Overlay.Clock) }
                    "calculator" -> { onClose(); vm.open(Overlay.Calculator) }
                    "search" -> { onClose(); vm.openSearch() }
                    else -> return "알 수 없는 화면: $s"
                }
                return s + " 화면을 열었어요."
            }
            "add_calendar_event" -> {
                val date = try {
                    LocalDate.parse(input.optString("date", ""))
                } catch (e: Exception) {
                    return "날짜 형식이 올바르지 않아요(yyyy-MM-dd)."
                }
                val title = input.optString("title", "").trim()
                if (title.isEmpty()) return "제목이 비어 있어요."
                SandCalendarStore.add(ctx, date, title, input.optString("time", ""))
                return date.toString() + " '" + title + "' 일정을 추가했어요."
            }
            "list_calendar_events" -> {
                SandCalendarStore.init(ctx)
                val date = try {
                    LocalDate.parse(input.optString("date", ""))
                } catch (e: Exception) {
                    return "날짜 형식이 올바르지 않아요."
                }
                val list = SandCalendarStore.on(date)
                val hol = sandHoliday(date)
                if (list.isEmpty()) return (if (hol != null) "공휴일: $hol. " else "") + "등록된 일정이 없어요."
                return (if (hol != null) "공휴일: $hol. " else "") +
                    list.joinToString("; ") { (if (it.time.isNotEmpty()) it.time + " " else "") + it.title }
            }
            "create_note" -> {
                val id = vm.createNote()
                vm.updateNote(id, input.optString("text", ""))
                return "메모를 만들었어요."
            }
            "get_weather" -> {
                val w = vm.weather.value
                return w.place + " " + w.tempC + "°C, " + w.description + ", 최고 " + w.high + "° 최저 " + w.low + "°"
            }
            "web_search" -> {
                val q = input.optString("query", "")
                sandOpenUrl(ctx, "https://www.google.com/search?q=" + URLEncoder.encode(q, "UTF-8"))
                return "'$q' 검색을 열었어요."
            }
            "open_map_search" -> {
                SandBridge.mapQuery = input.optString("query", "")
                onOpen(SandPage.MAP)
                return "지도에서 검색했어요."
            }
            "get_notifications" -> {
                val list = SandNotifications.items.take(10)
                if (list.isEmpty()) return "알림이 없거나 알림 접근 권한이 꺼져 있어요."
                return list.joinToString("; ") { it.appName + ": " + it.title + " " + it.text }
            }
        }
        return "지원하지 않는 도구: $name"
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || AiSession.busy || key.isBlank()) return
        input = ""
        messages.add(AiMsg(true, text))
        history.put(JSONObject().put("role", "user").put("content", text))
        AiSession.busy = true
        scope.launch {
            try {
                val tools = aiTools()
                var rounds = 0
                var finished = false
                while (rounds < 6 && !finished) {
                    rounds++
                    val body = JSONObject()
                        .put("model", model)
                        .put("max_tokens", 1500)
                        .put("system", AI_SYSTEM + LocalDate.now().toString())
                        .put("tools", tools)
                        .put("messages", history)
                    val resp = withContext(Dispatchers.IO) {
                        SandHttp.postJson(
                            "https://api.anthropic.com/v1/messages", body.toString(),
                            mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01")
                        )
                    }
                    val content = JSONObject(resp).getJSONArray("content")
                    history.put(JSONObject().put("role", "assistant").put("content", content))
                    val results = JSONArray()
                    for (i in 0 until content.length()) {
                        val b = content.getJSONObject(i)
                        when (b.optString("type")) {
                            "text" -> {
                                val t = b.optString("text", "").trim()
                                if (t.isNotEmpty()) messages.add(AiMsg(false, t))
                            }
                            "tool_use" -> {
                                val out = try {
                                    execTool(b.getString("name"), b.optJSONObject("input") ?: JSONObject())
                                } catch (e: Exception) {
                                    "오류: " + e.message
                                }
                                results.put(
                                    JSONObject().put("type", "tool_result")
                                        .put("tool_use_id", b.getString("id")).put("content", out)
                                )
                            }
                        }
                    }
                    if (results.length() == 0) {
                        finished = true
                    } else {
                        history.put(JSONObject().put("role", "user").put("content", results))
                    }
                }
            } catch (e: Exception) {
                val m = e.message.orEmpty()
                messages.add(
                    AiMsg(
                        false,
                        if (m.contains("401")) "API 키가 올바르지 않아요. 오른쪽 위 설정에서 다시 입력해 주세요."
                        else if (m.contains("404")) "모델 이름을 찾을 수 없어요. 설정에서 모델 이름을 확인해 주세요."
                        else if (m.contains("429")) "요청이 너무 많아요. 잠시 후 다시 시도해 주세요."
                        else "연결하지 못했어요: " + m.take(120)
                    )
                )
            }
            AiSession.busy = false
        }
    }

    ToolScaffold(
        "AI 에이전트", onClose,
        actions = {
            CircleIconButton(Icons.Rounded.Delete, "새 대화", { messages.clear(); while (history.length() > 0) history.remove(0) })
            Spacer(Modifier.padding(start = 8.dp))
            CircleIconButton(Icons.Rounded.Settings, "설정", { showSetup = !showSetup })
        }
    ) {
        if (showSetup) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SandTextField(keyIn, { keyIn = it }, "Anthropic API 키 (sk-ant-…)", Modifier.fillMaxWidth(), password = true)
                SandTextField(modelIn, { modelIn = it }, "모델 이름", Modifier.fillMaxWidth())
                SandButton(
                    "저장",
                    {
                        key = keyIn.trim()
                        model = modelIn.trim().ifEmpty { "claude-sonnet-5-5" }
                        prefs.edit().putString("ai_key", key).putString("ai_model", model).apply()
                        showSetup = key.isBlank()
                    },
                    Modifier.fillMaxWidth()
                )
                Text(
                    "키는 이 기기에만 저장되고, 대화는 Anthropic API(api.anthropic.com)로 전송돼요. 키는 console.anthropic.com에서 만들 수 있어요.",
                    fontSize = 12.sp, color = c.textSecondary
                )
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "무엇이든 말해 보세요.\n예) \"내일 오후 3시에 치과 일정 추가해줘\"\n예) \"카메라 켜줘\"  \"강남역 지도 보여줘\"  \"오늘 날씨 알려줘\"",
                        fontSize = 14.sp, color = c.textSecondary, modifier = Modifier.padding(top = 24.dp)
                    )
                }
            }
            items(messages.toList()) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
                    Box(
                        Modifier
                            .widthIn(max = 300.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (m.fromUser) c.accent else c.glassStrong)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(m.text, fontSize = 15.sp, color = if (m.fromUser) c.onAccent else c.text)
                    }
                }
            }
            if (AiSession.busy) {
                item { Text("생각하는 중…", fontSize = 13.sp, color = c.textSecondary) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SandTextField(
                input, { input = it }, if (key.isBlank()) "먼저 오른쪽 위 설정에서 API 키를 입력하세요" else "에이전트에게 부탁하기",
                Modifier.weight(1f), imeAction = ImeAction.Send, onAction = { send() }
            )
            SandButton("전송", { send() }, enabled = input.isNotBlank() && !AiSession.busy && key.isNotBlank())
        }
    }
}
