package com.nova.os.launcher.sand

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.launcher.NovaViewModel
import com.nova.os.launcher.data.SearchUtil
import com.nova.os.launcher.system.SystemActions
import com.nova.os.launcher.ui.components.ToolScaffold
import com.nova.os.launcher.ui.theme.LocalNovaColors
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** kind: 0 입력(프롬프트+명령), 1 출력, 2 오류, 3 안내 */
private class TermLine(val kind: Int, val text: String, val path: String = "")

private const val SENT = "__SAND_END_9c1e__"
private val AnsiRegex = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")
private val EndRegex = Regex("^:(\\d+):(.*)$")

private val TGreen = Color(0xFF5CE1A8)
private val TBlue = Color(0xFF7FB2FF)
private val TFg = Color(0xFFD7DEEA)
private val TErr = Color(0xFFFF8A8A)
private val TInfo = Color(0xFF8FB4FF)

private val BuiltinNames = listOf(
    "help", "clear", "history", "apps", "open", "battery", "weather", "sand", "calc", "curl", "reset", "exit"
)
private val ShellNames = listOf(
    "ls", "ll", "cat", "cd", "pwd", "echo", "grep", "head", "tail", "wc", "sort", "find", "cp", "mv", "rm",
    "mkdir", "rmdir", "touch", "df", "du", "ps", "top", "date", "uname", "id", "ping", "getprop", "chmod",
    "stat", "env", "export", "which", "sleep", "kill", "tr", "cut", "sed", "uptime", "free", "basename", "dirname"
)

/**
 * 터미널 세션. /system/bin/sh 를 계속 살려 두기 때문에 cd·export·변수가 명령 사이에 유지된다.
 * 화면을 닫아도 기록과 세션이 남는다.
 */
private object SandTerm {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val lines = mutableStateListOf<TermLine>()
    val history = ArrayList<String>()
    var histIdx = -1
    var cwd by mutableStateOf("")
    var home = ""
    var busy by mutableStateOf(false)
    var fontSp by mutableStateOf(13f)

    private var ready = false
    private var sh: Process? = null
    private var shOut: OutputStream? = null
    private var gen = 0
    private var cmdLines = 0

    fun pathText(): String {
        if (cwd == home) return "~"
        if (cwd.startsWith(home + "/")) return "~" + cwd.substring(home.length)
        return cwd
    }

    fun add(kind: Int, text: String, path: String = "") {
        lines.add(TermLine(kind, text, path))
        if (lines.size > 3000) {
            repeat(600) { lines.removeAt(0) }
        }
    }

    fun ensure(ctx: Context) {
        if (ready) return
        ready = true
        home = ctx.filesDir.absolutePath
        cwd = home
        try {
            val h = SandPrefs.of(ctx).getString("term_hist", "").orEmpty()
            for (l in h.split("\n")) if (l.isNotBlank()) history.add(l)
        } catch (e: Exception) {
            // 기록을 못 읽으면 빈 기록으로 시작
        }
        add(3, "SAND Terminal 2.0")
        add(3, "Android " + Build.VERSION.RELEASE + " · " + Build.MODEL)
        add(3, "'help' 로 명령어 보기 · Tab 으로 자동완성 · ↑↓ 로 이전 명령")
        add(3, "root 가 아닌 SAND OS 앱 권한으로 실행돼요. cd·변수는 명령 사이에 유지돼요.")
    }

    private fun saveHistory(ctx: Context) {
        try {
            val tail = if (history.size > 80) history.subList(history.size - 80, history.size) else history
            SandPrefs.of(ctx).edit().putString("term_hist", tail.joinToString("\n")).apply()
        } catch (e: Exception) {
            // 저장 실패는 무시
        }
    }

    // ───────────── 셸 세션 ─────────────

    private fun startSession(ctx: Context): Boolean {
        return try {
            val pb = ProcessBuilder("/system/bin/sh")
            val dir = File(cwd)
            pb.directory(if (dir.isDirectory) dir else ctx.filesDir)
            pb.redirectErrorStream(true)
            val env = pb.environment()
            env["HOME"] = home
            env["TERM"] = "dumb"
            env["PS1"] = ""
            env["USER"] = "sand"
            val p = pb.start()
            sh = p
            shOut = p.outputStream
            gen++
            val g = gen
            scope.launch(Dispatchers.IO) { readLoop(p, g) }
            true
        } catch (e: Exception) {
            add(2, "셸을 시작하지 못했어요: " + (e.message ?: ""))
            false
        }
    }

    private suspend fun post(g: Int, ls: List<String>) {
        if (ls.isEmpty()) return
        withContext(Dispatchers.Main) {
            if (g == gen) {
                for (l in ls) add(1, l)
                cmdLines += ls.size
                if (cmdLines > 8000) kill("… 출력이 너무 많아서 중단했어요")
            }
        }
    }

    private suspend fun finish(g: Int, code: String, pwd: String) {
        withContext(Dispatchers.Main) {
            if (g == gen) {
                if (pwd.isNotEmpty()) cwd = pwd
                busy = false
                if (code != "0") add(3, "[종료 코드 " + code + "]")
            }
        }
    }

    private suspend fun readLoop(p: Process, g: Int) {
        val batch = ArrayList<String>()
        try {
            BufferedReader(InputStreamReader(p.inputStream, Charsets.UTF_8)).use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    val idx = line.indexOf(SENT)
                    var done = false
                    var code = ""
                    var pwd = ""
                    if (idx >= 0) {
                        val m = EndRegex.find(line.substring(idx + SENT.length))
                        if (m != null) {
                            done = true
                            code = m.groupValues[1]
                            pwd = m.groupValues[2]
                        }
                    }
                    if (done) {
                        val before = line.substring(0, idx)
                        if (before.isNotEmpty()) batch.add(AnsiRegex.replace(before, ""))
                        post(g, batch.toList())
                        batch.clear()
                        finish(g, code, pwd)
                    } else {
                        batch.add(AnsiRegex.replace(line, ""))
                        if (batch.size >= 25 || !r.ready()) {
                            post(g, batch.toList())
                            batch.clear()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 세션이 닫히면 읽기가 끝난다
        }
        post(g, batch.toList())
        withContext(Dispatchers.Main) {
            if (g == gen && sh === p) {
                sh = null
                shOut = null
                if (busy) {
                    busy = false
                    add(3, "[셸이 종료됐어요]")
                }
            }
        }
    }

    /** 실행 중인 셸을 끊는다. 다음 명령을 보내면 마지막 폴더에서 새 셸이 시작된다. */
    fun kill(msg: String) {
        gen++
        val p = sh
        sh = null
        shOut = null
        busy = false
        if (p != null) p.destroyForcibly()
        add(3, msg)
    }

    fun interrupt() {
        if (busy) kill("^C")
    }

    private fun send(ctx: Context, cmd: String) {
        val cur = sh
        if (cur == null || !cur.isAlive) {
            sh = null
            if (!startSession(ctx)) return
        }
        busy = true
        cmdLines = 0
        val out = shOut
        val g = gen
        scope.launch(Dispatchers.IO) {
            try {
                if (out == null) throw java.io.IOException("no shell")
                out.write((cmd + "\n").toByteArray(Charsets.UTF_8))
                out.write(("echo \"" + SENT + ":\$?:\$PWD\"\n").toByteArray(Charsets.UTF_8))
                out.flush()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (g == gen) {
                        busy = false
                        sh = null
                        add(2, "명령을 보내지 못했어요. 다시 입력하면 셸을 새로 시작해요.")
                    }
                }
            }
        }
    }

    // ───────────── 명령 처리 ─────────────

    private fun help() {
        val l = listOf(
            "── SAND 내장 명령어 ──",
            "help            이 도움말",
            "clear (cls)     화면 지우기",
            "apps [검색어]   설치된 앱 목록",
            "open <앱 이름>  앱 실행 (예: open 카메라)",
            "battery         배터리 상태",
            "weather         현재 날씨",
            "sand            기기·저장공간 정보",
            "calc <식>       계산 (예: calc 3*(4+5))",
            "curl <주소>     웹 페이지 앞부분 가져오기",
            "history         이전 명령어",
            "reset           셸 다시 시작",
            "exit            터미널 닫기",
            "── 셸 명령어 (파이프 | 와 > 도 돼요) ──",
            "ls ll cat grep head tail wc sort find cp mv rm mkdir touch",
            "cd ~ / cd /sdcard / cd ..   (폴더 이동은 계속 유지돼요)",
            "df -h · du -sh * · ps · date · uname -a · id · env · export A=1",
            "ping -c 3 <주소> · getprop ro.product.model",
            "Tab 자동완성 · ↑↓ 이전 명령 · Ctrl+C 중단",
        )
        for (s in l) add(1, s)
    }

    fun submit(vm: NovaViewModel, ctx: Context, raw: String, onClose: () -> Unit) {
        ensure(ctx)
        val cmd = raw.trim()
        if (busy) {
            add(3, "이전 명령이 아직 실행 중이에요. Ctrl+C 로 멈출 수 있어요.")
            return
        }
        add(0, cmd, pathText())
        if (cmd.isEmpty()) return
        if (history.isEmpty() || history[history.size - 1] != cmd) {
            history.add(cmd)
            saveHistory(ctx)
        }
        histIdx = -1
        val parts = cmd.split(Regex("\\s+"), 2)
        val name = parts[0].lowercase()
        val arg = if (parts.size > 1) parts[1].trim() else ""
        when (name) {
            "clear", "cls" -> lines.clear()
            "help", "?" -> help()
            "exit", "quit" -> onClose()
            "reset" -> kill("셸을 다시 시작해요.")
            "history" -> {
                for (i in history.indices) add(1, (i + 1).toString() + "  " + history[i])
            }
            "apps" -> {
                val all = vm.allApps.value.sortedBy { it.label.lowercase() }
                val list = if (arg.isEmpty()) all else all.filter { SearchUtil.matches(it, arg) || it.packageName.contains(arg, true) }
                if (list.isEmpty()) {
                    add(2, "일치하는 앱이 없어요.")
                } else {
                    for (a in list.take(300)) add(1, a.label + "  ·  " + a.packageName)
                    add(3, "— " + list.size + "개")
                }
            }
            "open" -> {
                if (arg.isEmpty()) {
                    add(2, "사용법: open <앱 이름>")
                } else {
                    val apps = vm.allApps.value
                    val app = apps.firstOrNull { it.label.equals(arg, ignoreCase = true) }
                        ?: apps.firstOrNull { SearchUtil.matches(it, arg) || it.packageName.contains(arg, true) }
                    if (app == null) {
                        add(2, "'" + arg + "' 앱을 찾지 못했어요.")
                    } else {
                        add(3, app.label + " 실행")
                        vm.launchApp(app)
                    }
                }
            }
            "battery" -> {
                val b = vm.battery.value
                if (b.valid) add(1, "배터리 " + b.percent + "%" + (if (b.charging) " (충전 중)" else ""))
                else add(2, "배터리 정보를 읽을 수 없어요.")
            }
            "weather" -> {
                val w = vm.weather.value
                if (w.status.name == "READY") {
                    add(1, w.place + "  " + w.tempC + "°C  " + w.description + "  (최고 " + w.high + "° / 최저 " + w.low + "°)")
                } else {
                    add(2, "날씨 정보가 아직 없어요. 홈 화면의 날씨 위젯을 눌러 새로고침해 보세요.")
                }
            }
            "sand", "neofetch", "info" -> {
                val st = SystemActions.readStats(ctx)
                add(3, "SAND OS · " + ctx.packageName)
                add(1, "기기        " + Build.MANUFACTURER + " " + Build.MODEL)
                add(1, "안드로이드  " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
                add(1, "저장공간    " + SystemActions.formatBytes(st.storageUsed) + " / " + SystemActions.formatBytes(st.storageTotal))
                add(1, "메모리      " + SystemActions.formatBytes(st.ramUsed) + " / " + SystemActions.formatBytes(st.ramTotal))
                add(1, "앱 폴더     " + home)
            }
            "calc" -> {
                val v = SandMath.eval(arg)
                if (v == null) add(2, "계산할 수 없는 식이에요. 예: calc 3*(4+5)") else add(1, "= " + SandMath.format(v))
            }
            "curl" -> {
                if (!arg.startsWith("http")) {
                    add(2, "사용법: curl https://주소")
                } else {
                    busy = true
                    scope.launch {
                        try {
                            val body = withContext(Dispatchers.IO) { SandHttp.get(arg) }
                            val ls = body.lineSequence().take(60).toList()
                            for (x in ls) add(1, x.take(300))
                            if (body.lineSequence().drop(60).any()) add(3, "…(앞부분만 보여 줬어요)")
                        } catch (e: Exception) {
                            add(2, "curl: " + (e.message ?: "실패"))
                        }
                        busy = false
                    }
                }
            }
            "ll" -> send(ctx, "ls -l " + arg)
            "la" -> send(ctx, "ls -la " + arg)
            "dir" -> send(ctx, "ls -l " + arg)
            else -> send(ctx, cmd)
        }
    }

    // ───────────── 자동완성 ─────────────

    private fun commonPrefix(xs: List<String>): String {
        var p = xs[0]
        for (x in xs) {
            while (!x.startsWith(p)) p = p.dropLast(1)
        }
        return p
    }

    private fun fileCandidates(tok: String): List<String> {
        val slash = tok.lastIndexOf('/')
        val dirPart = if (slash >= 0) tok.substring(0, slash + 1) else ""
        val namePart = tok.substring(slash + 1)
        val base = when {
            dirPart.startsWith("/") -> File(dirPart)
            dirPart.startsWith("~") -> File(home, dirPart.removePrefix("~").trimStart('/'))
            dirPart.isEmpty() -> File(cwd)
            else -> File(cwd, dirPart)
        }
        val list = base.listFiles() ?: return emptyList()
        return list
            .filter { it.name.startsWith(namePart) && (namePart.startsWith(".") || !it.name.startsWith(".")) }
            .sortedBy { it.name }
            .map { dirPart + it.name + (if (it.isDirectory) "/" else "") }
    }

    /** Tab: 입력 줄을 채우고, 후보가 여러 개면 목록도 돌려준다. */
    fun complete(input: String): Pair<String, List<String>> {
        val sp = input.lastIndexOf(' ')
        val head = if (sp >= 0) input.substring(0, sp + 1) else ""
        val tok = input.substring(sp + 1)
        val firstWord = sp < 0
        val cands: List<String> = if (firstWord) {
            (BuiltinNames + ShellNames).filter { it.startsWith(tok) }.distinct().sorted()
        } else {
            fileCandidates(tok)
        }
        if (cands.isEmpty()) return Pair(input, emptyList())
        if (cands.size == 1) {
            val only = cands[0]
            val tail = if (only.endsWith("/")) "" else " "
            return Pair(head + only + tail, emptyList())
        }
        return Pair(head + commonPrefix(cands), cands)
    }
}

private fun lineColor(kind: Int): Color = when (kind) {
    2 -> TErr
    3 -> TInfo
    else -> TFg
}

@Composable
private fun TermLineView(l: TermLine, sz: Float) {
    val mono = FontFamily.Monospace
    if (l.kind == 0) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = TGreen, fontWeight = FontWeight.Bold)) { append("sand@os") }
                withStyle(SpanStyle(color = TFg)) { append(":") }
                withStyle(SpanStyle(color = TBlue)) { append(l.path) }
                withStyle(SpanStyle(color = TFg)) { append("$ ") }
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold)) { append(l.text) }
            },
            fontFamily = mono,
            fontSize = sz.sp,
            lineHeight = (sz * 1.4f).sp
        )
    } else {
        Text(l.text, fontFamily = mono, fontSize = sz.sp, lineHeight = (sz * 1.4f).sp, color = lineColor(l.kind))
    }
}

@Composable
fun SandTerminalScreen(vm: NovaViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalNovaColors.current
    remember { SandTerm.ensure(ctx); true }
    val start = SandNav.terminalInput.orEmpty()
    var field by remember { mutableStateOf(TextFieldValue(start, TextRange(start.length))) }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val mono = FontFamily.Monospace
    val panelShape = RoundedCornerShape(22.dp)
    val sz = SandTerm.fontSp

    LaunchedEffect(Unit) {
        if (SandNav.terminalInput != null) SandNav.terminalInput = null
        delay(350)
        try {
            focus.requestFocus()
        } catch (e: Exception) {
            // 아직 화면에 붙기 전이면 무시
        }
    }
    LaunchedEffect(SandTerm.lines.size, SandTerm.busy) {
        listState.scrollToItem(SandTerm.lines.size)
    }

    fun setText(t: String) {
        field = TextFieldValue(t, TextRange(t.length))
    }

    fun run(text: String) {
        SandTerm.submit(vm, ctx, text, onClose)
        setText("")
    }

    fun insert(s: String) {
        val t = field.text
        val a = field.selection.min
        val b = field.selection.max
        val nt = t.substring(0, a) + s + t.substring(b)
        field = TextFieldValue(nt, TextRange(a + s.length))
    }

    fun histMove(dir: Int) {
        val h = SandTerm.history
        if (h.isEmpty()) return
        var i = SandTerm.histIdx
        i = if (dir < 0) {
            if (i == -1) h.size - 1 else maxOf(0, i - 1)
        } else {
            if (i == -1) -1 else i + 1
        }
        if (i >= h.size) i = -1
        SandTerm.histIdx = i
        setText(if (i == -1) "" else h[i])
    }

    fun tab() {
        val r = SandTerm.complete(field.text)
        if (r.second.size > 1) {
            SandTerm.add(0, field.text, SandTerm.pathText())
            SandTerm.add(1, r.second.joinToString("   "))
        }
        setText(r.first)
    }

    fun move(delta: Int) {
        val p = (field.selection.start + delta).coerceIn(0, field.text.length)
        field = field.copy(selection = TextRange(p))
    }

    fun copyAll() {
        val sb = StringBuilder()
        for (l in SandTerm.lines) {
            if (l.kind == 0) sb.append("sand@os:").append(l.path).append("$ ").append(l.text) else sb.append(l.text)
            sb.append("\n")
        }
        sandCopy(ctx, "terminal", sb.toString())
    }

    ToolScaffold("터미널", onClose) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(panelShape)
                    .background(Brush.verticalGradient(listOf(Color(0xF2121A2B), Color(0xF2070B14))))
                    .border(1.dp, c.glassBorder, panelShape)
            ) {
                // 창 제목줄: 빨강=닫기, 노랑=지우기, 초록=복사
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFFFF5F57)).clickable { onClose() })
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFFFEBC2E)).clickable { SandTerm.lines.clear() })
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF28C840)).clickable { copyAll() })
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "sand@os: " + SandTerm.pathText(),
                        fontFamily = mono,
                        fontSize = 12.sp,
                        color = Color(0xFF9AA6BC),
                        maxLines = 1
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            try {
                                focus.requestFocus()
                            } catch (e: Exception) {
                                // 입력줄이 없을 때(실행 중)는 무시
                            }
                        }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 14.dp)
                    ) {
                        items(SandTerm.lines) { l -> TermLineView(l, sz) }
                        item {
                            if (SandTerm.busy) {
                                Text("실행 중…  (Ctrl+C 로 중단)", fontFamily = mono, fontSize = sz.sp, color = TInfo)
                            } else {
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(
                                        buildAnnotatedString {
                                            withStyle(SpanStyle(color = TGreen, fontWeight = FontWeight.Bold)) { append("sand@os") }
                                            withStyle(SpanStyle(color = TFg)) { append(":") }
                                            withStyle(SpanStyle(color = TBlue)) { append(SandTerm.pathText()) }
                                            withStyle(SpanStyle(color = TFg)) { append("$ ") }
                                        },
                                        fontFamily = mono,
                                        fontSize = sz.sp,
                                        lineHeight = (sz * 1.4f).sp
                                    )
                                    BasicTextField(
                                        value = field,
                                        onValueChange = {
                                            if (it.text.contains('\n')) {
                                                run(it.text.replace("\n", ""))
                                            } else {
                                                field = it
                                            }
                                        },
                                        textStyle = TextStyle(
                                            color = Color.White,
                                            fontFamily = mono,
                                            fontSize = sz.sp,
                                            lineHeight = (sz * 1.4f).sp
                                        ),
                                        cursorBrush = SolidColor(TGreen),
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = KeyboardCapitalization.None,
                                            imeAction = ImeAction.Send
                                        ),
                                        keyboardActions = KeyboardActions(onSend = { run(field.text) }),
                                        modifier = Modifier.weight(1f).focusRequester(focus)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // 모바일 키보드에 없는 키를 모아 둔 줄
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SandChip("Tab", false, { tab() })
                SandChip("Esc", false, { setText("") })
                SandChip("Ctrl+C", false, { SandTerm.interrupt() })
                SandChip("↑", false, { histMove(-1) })
                SandChip("↓", false, { histMove(1) })
                SandChip("←", false, { move(-1) })
                SandChip("→", false, { move(1) })
                for (k in listOf("/", "-", "~", "|", ">", "*", "&", "$", "\"")) {
                    SandChip(k, false, { insert(k) })
                }
                SandChip("A−", false, { SandTerm.fontSp = maxOf(9f, SandTerm.fontSp - 1f) })
                SandChip("A＋", false, { SandTerm.fontSp = minOf(22f, SandTerm.fontSp + 1f) })
                SandChip("복사", false, { copyAll() })
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
