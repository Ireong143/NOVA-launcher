package com.nova.os.launcher.ui.search

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.launcher.NovaViewModel
import com.nova.os.launcher.data.Overlay
import com.nova.os.launcher.data.SearchUtil
import com.nova.os.launcher.sand.ContactRow
import com.nova.os.launcher.sand.FileRow
import com.nova.os.launcher.sand.SandAppGrid
import com.nova.os.launcher.sand.SandButton
import com.nova.os.launcher.sand.SandCalendarStore
import com.nova.os.launcher.sand.SandChip
import com.nova.os.launcher.sand.SandFinder
import com.nova.os.launcher.sand.SandHttp
import com.nova.os.launcher.sand.SandMath
import com.nova.os.launcher.sand.SandNav
import com.nova.os.launcher.sand.SandPage
import com.nova.os.launcher.sand.SandPrefs
import com.nova.os.launcher.sand.SandText
import com.nova.os.launcher.sand.SandTextField
import com.nova.os.launcher.sand.sandCopy
import com.nova.os.launcher.sand.sandOpenUrl
import com.nova.os.launcher.sand.sandStart
import com.nova.os.launcher.system.SystemActions
import com.nova.os.launcher.ui.components.GlassCard
import com.nova.os.launcher.ui.components.SectionLabel
import com.nova.os.launcher.ui.components.ToolScaffold
import com.nova.os.launcher.ui.theme.LocalNovaColors
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class SearchResult(val title: String, val content: String, val url: String)

private const val SEARCH_ENDPOINT = "https://sand-os-search.l234567967.workers.dev/search?q="

private fun parseResults(body: String): List<SearchResult> {
    val t = body.trim()
    val arr: JSONArray = if (t.startsWith("[")) {
        JSONArray(t)
    } else {
        val o = JSONObject(t)
        o.optJSONArray("results") ?: o.optJSONArray("items") ?: JSONArray()
    }
    val out = ArrayList<SearchResult>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val title = o.optString("title", "").trim()
        val content = o.optString("content", "").ifEmpty { o.optString("snippet", "") }.trim()
        val url = o.optString("url", "").ifEmpty { o.optString("link", "") }.trim()
        if (title.isNotEmpty()) out.add(SearchResult(title, content, url))
    }
    return out
}

private class RowAction(val icon: ImageVector, val label: String, val run: () -> Unit)

private class UniRow(
    val key: String,
    val title: String,
    val sub: String,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit,
    val actions: List<RowAction> = emptyList(),
)

private val Tabs = listOf("전체", "앱", "연락처", "문서", "파일", "메모·일정", "설정", "웹")

private const val T_ALL = 0
private const val T_APP = 1
private const val T_CONTACT = 2
private const val T_DOC = 3
private const val T_FILE = 4
private const val T_NOTE = 5
private const val T_SETTING = 6
private const val T_WEB = 7

private val Blue = Color(0xFF4F8CFF)
private val Green = Color(0xFF2FBF71)
private val Purple = Color(0xFF9B6BFF)
private val Pink = Color(0xFFFF5C7A)
private val Orange = Color(0xFFFF9F0A)
private val Teal = Color(0xFF27B6C9)

private fun dateOf(epochSec: Long): String =
    Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault()).toLocalDate().toString()

@Composable
private fun ResultRow(r: UniRow) {
    val c = LocalNovaColors.current
    GlassCard(Modifier.fillMaxWidth(), onClick = r.onClick) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(r.tint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(r.icon, null, Modifier.size(22.dp), tint = r.tint)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    r.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = c.text
                )
                if (r.sub.isNotEmpty()) {
                    Text(r.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = c.textSecondary)
                }
            }
            for (a in r.actions) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(c.glass).clickable { a.run() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(a.icon, a.label, Modifier.size(18.dp), tint = c.text)
                }
            }
        }
    }
}

/** 유니버설 검색: 앱 · 연락처 · 문서 · 파일 · 메모 · 일정 · 설정 · 계산 · 위키백과 · 웹을 한 번에 찾는다. */
@Composable
fun SearchScreen(vm: NovaViewModel) {
    val ctx = LocalContext.current
    val c = LocalNovaColors.current
    val apps by vm.allApps.collectAsState()
    val settings by vm.settings.collectAsState()
    val notes by vm.notes.collectAsState()
    val prefs = remember { SandPrefs.of(ctx) }
    remember { SandCalendarStore.init(ctx); true }

    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(T_ALL) }
    var wiki by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var wikiLoading by remember { mutableStateOf(false) }
    var wikiError by remember { mutableStateOf("") }
    var contactRows by remember { mutableStateOf<List<ContactRow>>(emptyList()) }
    var fileRows by remember { mutableStateOf<List<FileRow>>(emptyList()) }
    var contactsOk by remember { mutableStateOf(SandFinder.hasContacts(ctx)) }
    var fileLevel by remember { mutableStateOf(SandFinder.fileAccess(ctx)) }
    var message by remember { mutableStateOf("") }
    var recent by remember {
        mutableStateOf(prefs.getString("recent_q", "").orEmpty().split("\n").filter { it.isNotBlank() })
    }
    val focus = remember { FocusRequester() }
    val q = query.trim()
    val terminalCmd = q.startsWith(">")
    val calc = remember(q) { SandMath.eval(q) }

    val contactPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        contactsOk = SandFinder.hasContacts(ctx)
    }
    val storagePerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        fileLevel = SandFinder.fileAccess(ctx)
    }

    fun askAllFiles() {
        if (Build.VERSION.SDK_INT >= 30) {
            val ok = sandStart(
                ctx,
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName))
            )
            if (!ok) sandStart(ctx, Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        } else {
            storagePerm.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }

    fun askMedia() {
        if (Build.VERSION.SDK_INT >= 33) {
            storagePerm.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            )
        } else {
            storagePerm.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }

    fun saveRecent(text: String) {
        val t = text.trim()
        if (t.isEmpty() || t.startsWith(">")) return
        val list = ArrayList<String>()
        list.add(t)
        for (r in recent) if (r != t && list.size < 8) list.add(r)
        recent = list
        prefs.edit().putString("recent_q", list.joinToString("\n")).apply()
    }

    fun go(block: () -> Unit) {
        saveRecent(query)
        block()
    }

    fun openUrl(url: String) = sandOpenUrl(ctx, url)

    fun runInTerminal() {
        SandNav.terminalInput = q.removePrefix(">").trim()
        SandNav.page = SandPage.TERMINAL
        vm.back()
    }

    LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (e: Exception) {
            // 아직 화면에 붙기 전이면 무시
        }
    }

    // 권한 설정 화면에 다녀오면 자동으로 다시 확인한다.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1200)
            val c1 = SandFinder.hasContacts(ctx)
            val f1 = SandFinder.fileAccess(ctx)
            if (c1 != contactsOk) contactsOk = c1
            if (f1 != fileLevel) fileLevel = f1
        }
    }

    // 연락처·파일(기기 안에서만 검색)
    LaunchedEffect(query, contactsOk, fileLevel) {
        val s = query.trim()
        if (s.isEmpty() || s.startsWith(">")) {
            contactRows = emptyList()
            fileRows = emptyList()
            return@LaunchedEffect
        }
        delay(150)
        contactRows = withContext(Dispatchers.IO) { SandFinder.contacts(ctx, s) }
        fileRows = if (s.length >= 2 && !SandText.isJamo(s)) {
            withContext(Dispatchers.IO) { SandFinder.files(ctx, s) }
        } else {
            emptyList()
        }
    }

    // 위키백과(인터넷)
    LaunchedEffect(query) {
        val s = query.trim()
        if (s.length < 2 || s.startsWith(">") || SandMath.eval(s) != null) {
            wiki = emptyList()
            wikiError = ""
            wikiLoading = false
            return@LaunchedEffect
        }
        delay(450)
        wikiLoading = true
        wikiError = ""
        try {
            val body = withContext(Dispatchers.IO) { SandHttp.get(SEARCH_ENDPOINT + URLEncoder.encode(s, "UTF-8")) }
            wiki = parseResults(body)
        } catch (e: Exception) {
            wikiError = "위키백과를 불러오지 못했어요. 인터넷 연결을 확인해 주세요."
            wiki = emptyList()
        }
        wikiLoading = false
    }

    val appHits = remember(q, apps, settings.hidden) {
        if (q.isEmpty() || q.startsWith(">")) emptyList()
        else apps.filter { it.packageName !in settings.hidden && SearchUtil.matches(it, q) }.take(12)
    }

    val sandRows = ArrayList<UniRow>()
    val settingRows = ArrayList<UniRow>()
    val docRows = ArrayList<UniRow>()
    val fileOnlyRows = ArrayList<UniRow>()
    val contactUi = ArrayList<UniRow>()
    val noteRows = ArrayList<UniRow>()

    if (q.isNotEmpty() && !terminalCmd) {
        // SAND 기본 앱과 SAND 화면
        for (p in SandPage.values()) {
            if (SandText.matches(p.label, q) || p.name.contains(q, ignoreCase = true)) {
                sandRows.add(
                    UniRow("sand_" + p.name, p.label, "SAND 앱", Icons.Rounded.Apps, Blue, {
                        go {
                            SandNav.page = p
                            vm.back()
                        }
                    })
                )
            }
        }
        val targets = listOf(
            Triple("설정 (SAND)", "settings setting 설정 배경화면 테마", Overlay.Settings),
            Triple("메모", "note memo 메모 노트", Overlay.Notes),
            Triple("계산기", "calculator calc 계산기 계산", Overlay.Calculator),
            Triple("앱 관리", "apps manage 앱 관리 숨기기", Overlay.Apps),
        )
        for (t in targets) {
            if (SandText.matches(t.first, q) || t.second.contains(q, ignoreCase = true)) {
                sandRows.add(
                    UniRow("ov_" + t.first, t.first, "SAND 화면", Icons.Rounded.Settings, Teal, {
                        go { vm.open(t.third) }
                    })
                )
            }
        }
        // 시스템 설정 바로가기
        for (s in SandFinder.matchShortcuts(q)) {
            settingRows.add(
                UniRow("set_" + s.title, s.title, "시스템 설정", Icons.Rounded.Settings, Orange, {
                    go {
                        if (!SandFinder.openShortcut(ctx, s)) message = "이 설정 화면을 열 수 없어요."
                    }
                })
            )
        }
        // 연락처
        for (r in contactRows) {
            contactUi.add(
                UniRow(
                    "ct_" + r.id, r.name, r.number, Icons.Rounded.Person, Green,
                    {
                        go {
                            if (!SandFinder.openContact(ctx, r)) message = "연락처를 열 수 없어요."
                        }
                    },
                    listOf(
                        RowAction(Icons.Rounded.Call, "전화") { go { SandFinder.dial(ctx, r.number) } },
                        RowAction(Icons.Rounded.Message, "문자") { go { SandFinder.sms(ctx, r.number) } },
                    )
                )
            )
        }
        // 파일과 문서
        for (f in fileRows) {
            val cat = SandFinder.categoryOf(f.mime, f.name)
            val icon = when (cat) {
                SandFinder.CAT_DOC -> Icons.Rounded.Description
                SandFinder.CAT_IMAGE -> Icons.Rounded.Image
                SandFinder.CAT_VIDEO -> Icons.Rounded.Movie
                SandFinder.CAT_AUDIO -> Icons.Rounded.MusicNote
                else -> Icons.Rounded.Folder
            }
            val tint = when (cat) {
                SandFinder.CAT_DOC -> Blue
                SandFinder.CAT_IMAGE -> Green
                SandFinder.CAT_VIDEO -> Purple
                SandFinder.CAT_AUDIO -> Pink
                else -> Teal
            }
            val sub = (if (f.folder.isNotEmpty()) f.folder + "  ·  " else "") +
                SystemActions.formatBytes(f.size) + "  ·  " + dateOf(f.modified)
            val row = UniRow("f_" + f.id, f.name, sub, icon, tint, {
                go {
                    if (!SandFinder.openFile(ctx, f)) message = "이 파일을 열 수 있는 앱이 없어요."
                }
            })
            if (cat == SandFinder.CAT_DOC) docRows.add(row) else fileOnlyRows.add(row)
        }
        // 메모
        val noteHits = notes
            .filter { it.text.isNotBlank() && (SandText.matches(it.title, q) || it.text.contains(q, ignoreCase = true)) }
            .sortedByDescending { it.updatedAt }
            .take(5)
        for (i in noteHits.indices) {
            val n = noteHits[i]
            noteRows.add(
                UniRow("n_" + i, n.title, n.text.replace("\n", " ").take(60), Icons.Rounded.EditNote, Orange, {
                    go { vm.open(Overlay.Notes) }
                })
            )
        }
        // 캘린더 일정
        for (e in SandCalendarStore.events.filter { it.title.contains(q, ignoreCase = true) || it.date.contains(q) }.take(5)) {
            noteRows.add(
                UniRow("e_" + e.id, e.title, e.date + (if (e.time.isNotEmpty()) "  " + e.time else ""), Icons.Rounded.Event, Pink, {
                    go {
                        SandNav.page = SandPage.CALENDAR
                        vm.back()
                    }
                })
            )
        }
    }

    val all = tab == T_ALL
    val limit = if (all) 5 else 60
    val needPerm = !terminalCmd && tab != T_APP && tab != T_SETTING && tab != T_WEB && tab != T_NOTE &&
        (!contactsOk || fileLevel < 2)

    ToolScaffold("검색", { vm.back() }) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SandTextField(
                query, { query = it }, "앱 · 연락처 · 파일 · 문서 · 웹 검색",
                Modifier.fillMaxWidth(), imeAction = ImeAction.Search,
                onAction = {
                    if (terminalCmd) {
                        runInTerminal()
                    } else if (q.isNotEmpty()) {
                        go { openUrl("https://ko.wikipedia.org/w/index.php?search=" + URLEncoder.encode(q, "UTF-8")) }
                    }
                },
                focusRequester = focus
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in Tabs.indices) SandChip(Tabs[i], tab == i, { tab = i })
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (message.isNotEmpty()) item { Text(message, color = c.danger, fontSize = 13.sp) }

            if (q.isEmpty()) {
                if (recent.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel("최근 검색", Modifier.weight(1f))
                            SandChip("지우기", false, {
                                recent = emptyList()
                                prefs.edit().remove("recent_q").apply()
                            })
                        }
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (r in recent) SandChip(r, false, { query = r })
                        }
                    }
                }
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("한 번에 찾아요", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                            Text("앱 · 연락처 · 문서 · 파일 · 메모 · 일정 · 설정 · 위키백과 · 웹", fontSize = 13.sp, color = c.textSecondary)
                            Text("예) 홍길동   보고서.pdf   ㅅㅅ(초성)   3*(4+5)   > ls", fontSize = 13.sp, color = c.textSecondary)
                        }
                    }
                }
            } else if (terminalCmd) {
                item {
                    ResultRow(
                        UniRow(
                            "term", "터미널에서 실행", q.removePrefix(">").trim().ifEmpty { "터미널 열기" },
                            Icons.Rounded.Code, Green, { runInTerminal() }
                        )
                    )
                }
            } else {
                val cv = calc
                if (all && cv != null) {
                    item {
                        ResultRow(
                            UniRow("calc", "= " + SandMath.format(cv), "계산 결과 · 눌러서 복사", Icons.Rounded.Calculate, Blue, {
                                sandCopy(ctx, "calc", SandMath.format(cv))
                            })
                        )
                    }
                }
                if ((all || tab == T_APP) && appHits.isNotEmpty()) {
                    item { SectionLabel("앱") }
                    item { SandAppGrid(vm, appHits) }
                }
                if ((all || tab == T_SETTING) && (sandRows.isNotEmpty() || settingRows.isNotEmpty())) {
                    item { SectionLabel("SAND · 설정") }
                    items(sandRows.take(if (all) 4 else 30), key = { it.key }) { ResultRow(it) }
                    items(settingRows.take(if (all) 4 else 30), key = { it.key }) { ResultRow(it) }
                }
                if ((all || tab == T_CONTACT) && contactUi.isNotEmpty()) {
                    item { SectionLabel("연락처") }
                    items(contactUi.take(limit), key = { it.key }) { ResultRow(it) }
                }
                if ((all || tab == T_DOC) && docRows.isNotEmpty()) {
                    item { SectionLabel("문서") }
                    items(docRows.take(limit), key = { it.key }) { ResultRow(it) }
                }
                if ((all || tab == T_FILE) && fileOnlyRows.isNotEmpty()) {
                    item { SectionLabel("파일") }
                    items(fileOnlyRows.take(limit), key = { it.key }) { ResultRow(it) }
                }
                if ((all || tab == T_NOTE) && noteRows.isNotEmpty()) {
                    item { SectionLabel("메모 · 일정") }
                    items(noteRows.take(limit), key = { it.key }) { ResultRow(it) }
                }
                if (all || tab == T_WEB) {
                    if (wikiLoading) item { Text("위키백과 검색 중…", color = c.textSecondary, fontSize = 14.sp) }
                    if (wikiError.isNotEmpty()) item { Text(wikiError, color = c.danger, fontSize = 14.sp) }
                    if (wiki.isNotEmpty()) item { SectionLabel("위키백과") }
                    items(wiki.take(if (all) 3 else 10)) { r ->
                        GlassCard(Modifier.fillMaxWidth(), onClick = { if (r.url.isNotBlank()) go { openUrl(r.url) } }) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    r.title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = c.text
                                )
                                if (r.content.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(r.content, fontSize = 13.sp, color = c.textSecondary, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    item {
                        SandButton(
                            "웹에서 '" + q + "' 검색",
                            { go { openUrl("https://www.google.com/search?q=" + URLEncoder.encode(q, "UTF-8")) } },
                            Modifier.fillMaxWidth()
                        )
                    }
                }
                val nothing = appHits.isEmpty() && sandRows.isEmpty() && settingRows.isEmpty() && contactUi.isEmpty() &&
                    docRows.isEmpty() && fileOnlyRows.isEmpty() && noteRows.isEmpty() && wiki.isEmpty() && calc == null
                if (nothing && !wikiLoading) {
                    item { Text("기기 안에서는 찾지 못했어요.", color = c.textSecondary, fontSize = 14.sp) }
                }
            }

            if (needPerm) {
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("더 많이 찾으려면 허용이 필요해요", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                            Text(
                                "연락처·파일·문서는 허용한 뒤에만 기기 안에서 검색하고, 밖으로 보내지 않아요.",
                                fontSize = 12.sp,
                                color = c.textSecondary
                            )
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (!contactsOk) SandChip("연락처 허용", false, { contactPerm.launch(Manifest.permission.READ_CONTACTS) })
                                if (fileLevel < 2) SandChip("파일·문서 허용", false, { askAllFiles() })
                                if (fileLevel == 0) SandChip("사진·음악만", false, { askMedia() })
                            }
                        }
                    }
                }
            }
        }
    }
}
