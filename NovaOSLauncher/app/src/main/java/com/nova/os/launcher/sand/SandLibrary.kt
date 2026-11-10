package com.nova.os.launcher.sand

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.launcher.NovaViewModel
import com.nova.os.launcher.data.AppInfo
import com.nova.os.launcher.data.SearchUtil
import com.nova.os.launcher.ui.components.AppIconCell
import com.nova.os.launcher.ui.components.GlassCard
import com.nova.os.launcher.ui.components.Pressable
import com.nova.os.launcher.ui.components.SandAppIcon
import com.nova.os.launcher.ui.components.SectionLabel
import com.nova.os.launcher.ui.components.sandKindOf
import com.nova.os.launcher.ui.theme.LocalNovaColors
import org.json.JSONArray
import org.json.JSONObject

// ───────────────────────── 사용자 폴더 저장소 ─────────────────────────

data class SandFolder(val id: Long, val name: String, val apps: List<String>)

object SandFolders {
    val folders = mutableStateListOf<SandFolder>()
    private var inited = false

    fun init(context: Context) {
        if (inited) return
        inited = true
        try {
            val arr = JSONArray(SandPrefs.of(context).getString("folders", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val a = o.getJSONArray("apps")
                val list = ArrayList<String>()
                for (j in 0 until a.length()) list.add(a.getString(j))
                folders.add(SandFolder(o.getLong("id"), o.getString("name"), list))
            }
        } catch (e: Exception) {
            // 깨진 데이터는 무시
        }
    }

    private fun save(context: Context) {
        val arr = JSONArray()
        for (f in folders) {
            arr.put(JSONObject().put("id", f.id).put("name", f.name).put("apps", JSONArray(f.apps)))
        }
        SandPrefs.of(context).edit().putString("folders", arr.toString()).apply()
    }

    fun create(context: Context, name: String, apps: List<String>) {
        folders.add(SandFolder(System.currentTimeMillis(), name.trim().ifEmpty { "새 폴더" }, apps))
        save(context)
    }

    fun update(context: Context, id: Long, name: String, apps: List<String>) {
        val i = folders.indexOfFirst { it.id == id }
        if (i >= 0) {
            folders[i] = SandFolder(id, name.trim().ifEmpty { "새 폴더" }, apps)
            save(context)
        }
    }

    fun delete(context: Context, id: Long) {
        folders.removeAll { it.id == id }
        save(context)
    }
}

// ───────────────────────── 자동 분류 ─────────────────────────

val SandCategories = listOf(
    "소셜·소통", "엔터테인먼트", "사진·카메라", "생산성", "금융",
    "쇼핑·배달", "지도·교통", "게임", "유틸리티", "기타",
)

private fun has(s: String, vararg keys: String): Boolean {
    for (k in keys) if (s.contains(k)) return true
    return false
}

/** 앱이 선언한 카테고리(가능하면)와 이름 키워드로 분류. */
fun sandCategoryOf(app: AppInfo, pm: PackageManager): String {
    val s = (app.packageName + " " + app.label).lowercase()
    var cat = -1
    if (Build.VERSION.SDK_INT >= 26) {
        cat = try {
            pm.getApplicationInfo(app.packageName, 0).category
        } catch (e: Exception) {
            -1
        }
    }
    // ApplicationInfo.CATEGORY_*: GAME 0, AUDIO 1, VIDEO 2, IMAGE 3, SOCIAL 4, NEWS 5, MAPS 6, PRODUCTIVITY 7
    if (cat == 0 || has(s, "game", "게임")) return "게임"
    if (has(s, "bank", "은행", "card", "카드", "securit", "증권", "toss", "finance", "insurance", "보험", "payco", "페이")) return "금융"
    if (cat == 4 || has(s, "kakao", "카카오", "jp.naver.line", "facebook", "instagram", "twitter", "telegram", "whatsapp", "messag", "메시지", "dialer", "전화", "contacts", "연락처", "discord")) return "소셜·소통"
    if (cat == 3 || has(s, "camera", "카메라", "gallery", "갤러리", "photo", "사진", "snow", "b612")) return "사진·카메라"
    if (cat == 1 || cat == 2 || has(s, "music", "음악", "youtube", "netflix", "video", "tving", "wavve", "spotify", "melon", "멜론", "podcast", "radio", "webtoon", "웹툰")) return "엔터테인먼트"
    if (cat == 6 || has(s, "map", "지도", "navi", "내비", "tmap", "taxi", "택시", "subway", "지하철", "travel", "여행", "korail", "ktx")) return "지도·교통"
    if (has(s, "shop", "쇼핑", "coupang", "쿠팡", "gmarket", "11st", "baemin", "배달", "market", "마켓", "mall", "daangn", "당근", "delivery", "yogiyo")) return "쇼핑·배달"
    if (cat == 7 || cat == 5 || has(s, "calendar", "캘린더", "note", "메모", "docs", "office", "drive", "mail", "메일", "outlook", "pdf", "keep", "reminder", "excel", "sheets", "slides", "news", "뉴스", "naver", "네이버", "daum")) return "생산성"
    if (has(s, "settings", "설정", "clock", "시계", "calculator", "계산기", "file", "파일", "weather", "날씨", "tools", "vpn", "security", "clean", "battery", "배터리", "launcher", "recorder", "녹음", "browser", "chrome", "인터넷", "internet", "store", "스토어", "update", "backup")) return "유틸리티"
    return "기타"
}

// ───────────────────────── 화면 조각 ─────────────────────────

@Composable
private fun MiniIcon(app: AppInfo, size: Dp) {
    val kind = remember(app.packageName, app.label) { sandKindOf(app) }
    if (kind != null) {
        SandAppIcon(kind, size)
    } else {
        Image(
            bitmap = app.icon,
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.27f))
        )
    }
}

@Composable
private fun FolderTile(name: String, apps: List<AppInfo>, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier, onClick = onClick) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.height(78.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val first = apps.take(4)
                for (r in 0 until 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (cc in 0 until 2) {
                            val a = first.getOrNull(r * 2 + cc)
                            if (a != null) MiniIcon(a, 34.dp) else Spacer(Modifier.size(34.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AppPickerDialog(
    title: String,
    initialName: String,
    allApps: List<AppInfo>,
    initialSelected: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, List<String>) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val c = LocalNovaColors.current
    var name by remember { mutableStateOf(initialName) }
    val selected = remember { mutableStateListOf<String>().also { it.addAll(initialSelected) } }
    SandDialog(onDismiss) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        SandTextField(name, { name = it }, "폴더 이름", Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Text("폴더에 넣을 앱 (" + selected.size + "개 선택)", fontSize = 13.sp, color = c.textSecondary)
        LazyColumn(Modifier.heightIn(max = 300.dp)) {
            items(allApps, key = { it.key }) { app ->
                val on = app.packageName in selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { if (on) selected.remove(app.packageName) else selected.add(app.packageName) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniIcon(app, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(app.label, Modifier.weight(1f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Checkbox(checked = on, onCheckedChange = { if (it) selected.add(app.packageName) else selected.remove(app.packageName) })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onDelete != null) SandButton("폴더 삭제", onDelete, Modifier.weight(1f), danger = true)
            SandButton("취소", onDismiss, Modifier.weight(1f))
            SandButton("저장", { onSave(name, selected.toList()) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FolderSheet(
    vm: NovaViewModel,
    title: String,
    apps: List<AppInfo>,
    onEdit: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val c = LocalNovaColors.current
    SandDialog(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            if (onEdit != null) SandChip("편집", false, onEdit)
        }
        Spacer(Modifier.height(10.dp))
        if (apps.isEmpty()) {
            Text("아직 앱이 없어요. 편집에서 앱을 추가해 보세요.", color = c.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(apps, key = { it.key }) { app ->
                AppIconCell(app, vm, 52.dp, true)
            }
        }
    }
}

private class OpenFolder(val title: String, val apps: List<AppInfo>, val userId: Long)

/** 홈에서 왼쪽으로 넘기면 나오는 앱 라이브러리: SAND 앱 + 내 폴더 + 자동 분류. */
@Composable
fun SandLibraryPage(
    vm: NovaViewModel,
    onOpenPage: (SandPage) -> Unit,
    onLongPress: () -> Unit,
) {
    val ctx = LocalContext.current
    remember { SandFolders.init(ctx); true }
    val c = LocalNovaColors.current
    val settings by vm.settings.collectAsState()
    val all by vm.allApps.collectAsState()
    val pm = ctx.packageManager
    val visible = remember(all, settings.hidden) {
        all.filter { it.packageName !in settings.hidden }.sortedBy { it.label.lowercase() }
    }
    val grouped = remember(visible) { visible.groupBy { sandCategoryOf(it, pm) } }
    var query by remember { mutableStateOf("") }
    var openedFolder by remember { mutableStateOf<OpenFolder?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SandFolder?>(null) }
    var builtinMenu by remember { mutableStateOf<SandPage?>(null) }

    val byPkg = remember(visible) { visible.associateBy { it.packageName } }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SandTextField(query, { query = it }, "앱 라이브러리 검색", Modifier.fillMaxWidth()) }

        if (query.isNotBlank()) {
            val found = visible.filter { SearchUtil.matches(it, query) }
            item { SandAppGrid(vm, found) }
        } else {
            item { SectionLabel("SAND 앱") }
            item {
                val pages = SandPage.values().toList()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (row in pages.chunked(4)) {
                        Row(Modifier.fillMaxWidth()) {
                            for (p in row) {
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Pressable(onLongClick = { builtinMenu = p }, onClick = { onOpenPage(p) }) {
                                        Column(
                                            Modifier.padding(4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            SandAppIcon(p.kind, 56.dp)
                                            Spacer(Modifier.height(4.dp))
                                            Text(p.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("내 폴더", Modifier.weight(1f))
                    SandChip("＋ 새 폴더", false, { creating = true })
                }
            }
            if (SandFolders.folders.isEmpty()) {
                item { Text("＋ 새 폴더로 나만의 폴더를 만들어 보세요.", color = c.textSecondary, fontSize = 13.sp) }
            }
            for (row in SandFolders.folders.toList().chunked(2)) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (f in row) {
                            val list = f.apps.mapNotNull { byPkg[it] }
                            FolderTile(f.name, list, Modifier.weight(1f)) { openedFolder = OpenFolder(f.name, list, f.id) }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            item { SectionLabel("자동 분류") }
            val cats = SandCategories.filter { grouped.containsKey(it) }
            for (row in cats.chunked(2)) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (cat in row) {
                            val list = grouped[cat].orEmpty()
                            FolderTile(cat, list, Modifier.weight(1f)) { openedFolder = OpenFolder(cat, list, 0L) }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    val o = openedFolder
    if (o != null) {
        val userFolder = SandFolders.folders.firstOrNull { it.id == o.userId }
        FolderSheet(
            vm, o.title, o.apps,
            onEdit = if (userFolder != null) { { editing = userFolder; openedFolder = null } } else null,
            onDismiss = { openedFolder = null }
        )
    }
    if (creating) {
        AppPickerDialog(
            "새 폴더", "", visible, emptyList(),
            onDismiss = { creating = false },
            onSave = { n, apps -> SandFolders.create(ctx, n, apps); creating = false },
            onDelete = null
        )
    }
    val ed = editing
    if (ed != null) {
        AppPickerDialog(
            "폴더 편집", ed.name, visible, ed.apps,
            onDismiss = { editing = null },
            onSave = { n, apps -> SandFolders.update(ctx, ed.id, n, apps); editing = null },
            onDelete = { SandFolders.delete(ctx, ed.id); editing = null }
        )
    }
    val bm = builtinMenu
    if (bm != null) {
        val onHome = bm.name in SandConfig.homeBuiltins
        SandDialog({ builtinMenu = null }) {
            Text(bm.label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            SandButton(
                if (onHome) "홈 화면에서 빼기" else "홈 화면에 추가",
                {
                    SandConfig.edit { homeBuiltins = if (onHome) homeBuiltins - bm.name else homeBuiltins + bm.name }
                    builtinMenu = null
                },
                Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            SandButton("열기", { onOpenPage(bm); builtinMenu = null }, Modifier.fillMaxWidth())
        }
    }
}

/** 앱 아이콘을 4열 격자로(LazyColumn 안에서 쓰므로 직접 행으로 나눈다). */
@Composable
fun SandAppGrid(vm: NovaViewModel, apps: List<AppInfo>, emptyText: String? = "검색 결과가 없어요") {
    val c = LocalNovaColors.current
    if (apps.isEmpty()) {
        if (emptyText != null) Text(emptyText, color = c.textSecondary, fontSize = 14.sp)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in apps.chunked(4)) {
            Row(Modifier.fillMaxWidth()) {
                for (a in row) AppIconCell(a, vm, 52.dp, true, Modifier.weight(1f))
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
