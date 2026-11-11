package com.nova.os.launcher.sand

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class TermLine(val text: String, val kind: Int) // 0 입력, 1 출력, 2 오류, 3 안내

private val AnsiRegex = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")

/** 터미널 세션: 화면을 닫아도 기록과 현재 폴더가 유지된다. */
private object SandTerm {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val lines = mutableStateListOf<TermLine>()
    val history = ArrayList<String>()
    var histIdx = -1
    var cwd: File? = null
    private var prevDir: File? = null
    var cwdText by mutableStateOf("~")
    var busy by mutableStateOf(false)

    @Volatile
    var proc: Process? = null
    private var ready = false

    fun add(text: String, kind: Int) {
        lines.add(TermLine(text, kind))
        if (lines.size > 2500) {
            repeat(500) { lines.removeAt(0) }
        }
    }

    fun prompt(): String = "sand@os:" + cwdText + "$ "

    private fun display(dir: File, home: File): String {
        val p = dir.absolutePath
        val h = home.absolutePath
        return when {
            p == h -> "~"
            p.startsWith(h + "/") -> "~" + p.substring(h.length)
            else -> p
        }
    }

    fun ensure(ctx: Context) {
        if (ready) return
        ready = true
        cwd = ctx.filesDir
        cwdText = "~"
        add("SAND Terminal 1.0", 3)
        add("'help'를 입력하면 명령어를 볼 수 있어요.", 3)
        add("이 터미널은 SAND OS 앱의 권한으로 실행돼요(root 아님). 기기의 다른 앱 데이터는 건드리지 못해요.", 3)
    }

    fun interrupt() {
        val p = proc
        if (p != null) {
            p.destroyForcibly()
            add("^C", 3)
        }
    }

    @Suppress("DEPRECATION")
    private fun cd(ctx: Context, arg: String) {
        val home = ctx.filesDir
        val cur = cwd ?: home
        val target: File = when {
            arg.isEmpty() || arg == "~" -> home
            arg == "-" -> prevDir ?: cur
            arg.startsWith("~/") -> File(home, arg.substring(2))
            arg == "/sdcard" || arg.startsWith("/sdcard/") ->
                File(Environment.getExternalStorageDirectory(), arg.removePrefix("/sdcard"))
            arg.startsWith("/") -> File(arg)
            else -> File(cur, arg)
        }
        val canon = try {
            target.canonicalFile
        } catch (e: Exception) {
            target
        }
        when {
            !canon.exists() -> add("cd: " + arg + ": 그런 폴더가 없어요", 2)
            !canon.isDirectory -> add("cd: " + arg + ": 폴더가 아니에요", 2)
            !canon.canRead() -> add("cd: " + arg + ": 권한이 없어요", 2)
            else -> {
                prevDir = cur
                cwd = canon
                cwdText = display(canon, home)
            }
        }
    }

    private fun help() {
        val l = listOf(
            "── SAND 내장 명령어 ──",
            "help            이 도움말",
            "clear (cls)     화면 지우기",
            "cd <폴더>       폴더 이동 (~ 홈, /sdcard 내장 저장소, .. 위로)",
            "pwd             현재 폴더",
            "dir             ls -l 과 같음",
            "apps [검색어]   설치된 앱 목록",
            "open <앱 이름>  앱 실행 (예: open 카메라)",
            "battery         배터리 상태",
            "weather         현재 날씨",
            "sand            기기·저장공간 정보",
            "calc <식>       계산 (예: calc 3*(4+5))",
            "curl <주소>     웹 페이지 가져오기(앞부분만)",
            "history         이전 명령어",
            "exit            터미널 닫기",
            "── 기본 셸 명령어 ──",
            "ls cat echo grep head tail wc sort find cp mv rm mkdir touch",
            "df du ps top -n 1 date uname -a id ping -c 3 <주소> getprop",
            "예) ls -la / cat 파일 / grep -r 단어 . / df -h",
            "Ctrl+C 칩으로 실행 중인 명령을 멈출 수 있어요.",
        )
        for (s in l) add(s, 1)
    }

    fun submit(vm: NovaViewModel, ctx: Context, raw: String, onClose: () -> Unit) {
        val cmd = raw.trim()
        if (cmd.isEmpty()) return
        ensure(ctx)
        if (busy) {
            add("이전 명령이 아직 실행 중이에요. Ctrl+C로 멈출 수 있어요.", 3)
            return
        }
        add(prompt() + cmd, 0)
        if (history.isEmpty() || history[history.size - 1] != cmd) history.add(cmd)
        histIdx = -1
        val parts = cmd.split(Regex("\\s+"), 2)
        val name = parts[0].lowercase()
        val arg = if (parts.size > 1) parts[1].trim() else ""
        when (name) {
            "clear", "cls" -> lines.clear()
            "help", "?" -> help()
            "exit", "quit" -> onClose()
            "cd" -> cd(ctx, arg)
            "pwd" -> add((cwd ?: ctx.filesDir).absolutePath, 1)
            "history" -> {
                for (i in history.indices) add((i + 1).toString() + "  " + history[i], 1)
            }
            "apps" -> {
                val all = vm.allApps.value.sortedBy { it.label.lowercase() }
                val list = if (arg.isEmpty()) all else all.filter { SearchUtil.matches(it, arg) || it.packageName.contains(arg, true) }
                if (list.isEmpty()) {
                    add("일치하는 앱이 없어요.", 2)
                } else {
                    for (a in list.take(300)) add(a.label + "  ·  " + a.packageName, 1)
                    add("— " + list.size + "개", 3)
                }
            }
            "open" -> {
                if (arg.isEmpty()) {
                    add("사용법: open <앱 이름>", 2)
                } else {
                    val apps = vm.allApps.value
                    val app = apps.firstOrNull { it.label.equals(arg, ignoreCase = true) }
                        ?: apps.firstOrNull { SearchUtil.matches(it, arg) || it.packageName.contains(arg, true) }
                    if (app == null) {
                        add("'" + arg + "' 앱을 찾지 못했어요.", 2)
                    } else {
                        add(app.label + " 실행", 3)
                        vm.launchApp(app)
                    }
                }
            }
            "battery" -> {
                val b = vm.battery.value
                if (b.valid) add("배터리 " + b.percent + "%" + (if (b.charging) " (충전 중)" else ""), 1)
                else add("배터리 정보를 읽을 수 없어요.", 2)
            }
            "weather" -> {
                val w = vm.weather.value
                if (w.status.name == "READY") {
                    add(w.place + "  " + w.tempC + "°C  " + w.description + "  (최고 " + w.high + "° / 최저 " + w.low + "°)", 1)
                } else {
                    add("날씨 정보가 아직 없어요. 홈 화면의 날씨 위젯을 눌러 새로고침해 보세요.", 2)
                }
            }
            "sand", "neofetch", "info" -> {
                val st = SystemActions.readStats(ctx)
                add("SAND OS · " + ctx.packageName, 3)
                add("기기      " + Build.MANUFACTURER + " " + Build.MODEL, 1)
                add("안드로이드  " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")", 1)
                add("저장공간   " + SystemActions.formatBytes(st.storageUsed) + " / " + SystemActions.formatBytes(st.storageTotal), 1)
                add("메모리     " + SystemActions.formatBytes(st.ramUsed) + " / " + SystemActions.formatBytes(st.ramTotal), 1)
                add("앱 폴더    " + ctx.filesDir.absolutePath, 1)
            }
            "calc" -> {
                val v = SandMath.eval(arg)
                if (v == null) add("계산할 수 없는 식이에요. 예: calc 3*(4+5)", 2) else add("= " + SandMath.format(v), 1)
            }
            "curl" -> {
                if (!arg.startsWith("http")) {
                    add("사용법: curl https://주소", 2)
                } else {
                    busy = true
                    scope.launch {
                        try {
                            val body = withContext(Dispatchers.IO) { SandHttp.get(arg) }
                            val ls = body.lineSequence().take(60).toList()
                            for (x in ls) add(x.take(300), 1)
                            if (body.lineSequence().drop(60).any()) add("…(앞부분만 보여 줬어요)", 3)
                        } catch (e: Exception) {
                            add("curl: " + (e.message ?: "실패"), 2)
                        }
                        busy = false
                    }
                }
            }
            "dir" -> runShell(ctx, "ls -l " + arg)
            else -> runShell(ctx, cmd)
        }
    }

    private fun runShell(ctx: Context, cmd: String) {
        busy = true
        val dir = cwd ?: ctx.filesDir
        scope.launch {
            try {
                shell(ctx, cmd, dir)
            } catch (e: Exception) {
                add("오류: " + (e.message ?: e.toString()), 2)
            }
            busy = false
        }
    }

    private suspend fun post(lines: List<String>, kind: Int) {
        withContext(Dispatchers.Main) {
            for (l in lines) add(l, kind)
        }
    }

    private suspend fun shell(ctx: Context, cmd: String, dir: File) {
        withContext(Dispatchers.IO) {
            val pb = ProcessBuilder("/system/bin/sh", "-c", cmd)
            pb.directory(dir)
            pb.redirectErrorStream(true)
            pb.environment()["HOME"] = ctx.filesDir.absolutePath
            pb.environment()["TERM"] = "dumb"
            var failure: String? = null
            val started: Process? = try {
                pb.start()
            } catch (e: Exception) {
                failure = e.message ?: "알 수 없는 오류"
                null
            }
            if (started == null) {
                post(listOf("실행하지 못했어요: " + failure), 2)
                return@withContext
            }
            proc = started
            try {
                started.outputStream.close()
            } catch (e: Exception) {
                // 입력 스트림이 이미 닫혔으면 무시
            }
            val watchdog = launch {
                delay(60_000)
                started.destroyForcibly()
            }
            try {
                val buf = ArrayList<String>()
                var total = 0
                BufferedReader(InputStreamReader(started.inputStream, Charsets.UTF_8)).use { r ->
                    while (true) {
                        val line = r.readLine() ?: break
                        buf.add(AnsiRegex.replace(line, ""))
                        total++
                        if (buf.size >= 30) {
                            post(buf.toList(), 1)
                            buf.clear()
                        }
                        if (total >= 3000) {
                            started.destroyForcibly()
                            post(listOf("… 출력이 너무 길어서 중단했어요"), 3)
                            break
                        }
                    }
                }
                if (buf.isNotEmpty()) post(buf.toList(), 1)
                val code = started.waitFor()
                if (code != 0) post(listOf("[종료 코드 " + code + "]"), 3)
            } finally {
                watchdog.cancel()
                proc = null
                started.destroy()
            }
        }
    }
}

private fun lineColor(kind: Int): Color = when (kind) {
    0 -> Color(0xFFFFFFFF)
    2 -> Color(0xFFFF8A8A)
    3 -> Color(0xFF8FB4FF)
    else -> Color(0xFFD3DBE8)
}

@Composable
fun SandTerminalScreen(vm: NovaViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalNovaColors.current
    remember { SandTerm.ensure(ctx); true }
    var input by remember { mutableStateOf(SandNav.terminalInput.orEmpty()) }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val mono = FontFamily.Monospace
    val panelShape = RoundedCornerShape(20.dp)
    val panelColor = Color(0xE60B1020)

    LaunchedEffect(Unit) {
        if (SandNav.terminalInput != null) SandNav.terminalInput = null
        delay(350)
        try {
            focus.requestFocus()
        } catch (e: Exception) {
            // 아직 화면에 붙기 전이면 무시
        }
    }
    LaunchedEffect(SandTerm.lines.size) {
        if (SandTerm.lines.isNotEmpty()) listState.scrollToItem(SandTerm.lines.size - 1)
    }

    fun run(text: String) {
        SandTerm.submit(vm, ctx, text, onClose)
        input = ""
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
        input = if (i == -1) "" else h[i]
    }

    ToolScaffold("터미널", onClose) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(panelShape)
                    .background(panelColor)
                    .border(1.dp, c.glassBorder, panelShape)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    items(SandTerm.lines) { l ->
                        Text(
                            l.text,
                            fontFamily = mono,
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            color = lineColor(l.kind),
                            fontWeight = if (l.kind == 0) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SandChip("help", false, { run("help") })
                SandChip("ls", false, { run("ls") })
                SandChip("cd ..", false, { run("cd ..") })
                SandChip("sand", false, { run("sand") })
                SandChip("↑", false, { histMove(-1) })
                SandChip("↓", false, { histMove(1) })
                SandChip("Ctrl+C", false, { SandTerm.interrupt() })
                SandChip("clear", false, { run("clear") })
                SandChip("복사", false, { sandCopy(ctx, "terminal", SandTerm.lines.joinToString("\n") { it.text }) })
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(panelColor)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(SandTerm.prompt(), fontFamily = mono, fontSize = 13.sp, color = Color(0xFF5CE1A8), maxLines = 1)
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontFamily = mono, fontSize = 14.sp),
                    cursorBrush = SolidColor(Color(0xFF5CE1A8)),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onAny = { run(input) }),
                    modifier = Modifier.weight(1f).focusRequester(focus)
                )
                Text(
                    "실행",
                    Modifier.clip(RoundedCornerShape(10.dp)).clickable { run(input) }.padding(horizontal = 10.dp, vertical = 6.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF8FB4FF)
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
