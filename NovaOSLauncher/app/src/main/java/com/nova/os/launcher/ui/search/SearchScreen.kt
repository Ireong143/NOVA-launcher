package com.nova.os.launcher.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.launcher.NovaViewModel
import com.nova.os.launcher.data.SearchUtil
import com.nova.os.launcher.sand.SandAppGrid
import com.nova.os.launcher.sand.SandButton
import com.nova.os.launcher.sand.SandHttp
import com.nova.os.launcher.sand.SandTextField
import com.nova.os.launcher.sand.sandOpenUrl
import com.nova.os.launcher.ui.components.GlassCard
import com.nova.os.launcher.ui.components.SectionLabel
import com.nova.os.launcher.ui.components.ToolScaffold
import com.nova.os.launcher.ui.theme.LocalNovaColors
import java.net.URLEncoder
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

/** 위키백과 검색 화면: 현재 테마/배경 색을 따르는 유리 질감. 앱 검색 결과도 함께 보여준다. */
@Composable
fun SearchScreen(vm: NovaViewModel) {
    val ctx = LocalContext.current
    val c = LocalNovaColors.current
    val apps by vm.allApps.collectAsState()
    val settings by vm.settings.collectAsState()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (e: Exception) {
            // 아직 화면에 붙기 전이면 무시
        }
    }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList()
            error = ""
            loading = false
            return@LaunchedEffect
        }
        delay(450)
        loading = true
        error = ""
        try {
            val body = withContext(Dispatchers.IO) { SandHttp.get(SEARCH_ENDPOINT + URLEncoder.encode(q, "UTF-8")) }
            results = parseResults(body)
        } catch (e: Exception) {
            error = "검색하지 못했어요. 인터넷 연결을 확인해 주세요."
            results = emptyList()
        }
        loading = false
    }

    val matched = remember(query, apps, settings.hidden) {
        if (query.isBlank()) emptyList()
        else apps.filter { it.packageName !in settings.hidden && SearchUtil.matches(it, query) }.take(8)
    }

    fun openUrl(url: String) = sandOpenUrl(ctx, url)

    ToolScaffold("검색", { vm.back() }) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SandTextField(
                query, { query = it }, "위키백과 · 앱 검색",
                Modifier.fillMaxWidth(), imeAction = ImeAction.Search,
                onAction = { if (query.isNotBlank()) openUrl("https://ko.wikipedia.org/w/index.php?search=" + URLEncoder.encode(query.trim(), "UTF-8")) },
                focusRequester = focus
            )
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (matched.isNotEmpty()) {
                item { SectionLabel("앱") }
                item { SandAppGrid(vm, matched) }
            }
            if (loading) item { Text("검색 중…", color = c.textSecondary, fontSize = 14.sp) }
            if (error.isNotEmpty()) item { Text(error, color = c.danger, fontSize = 14.sp) }
            if (results.isNotEmpty()) item { SectionLabel("위키백과") }
            items(results) { r ->
                GlassCard(Modifier.fillMaxWidth(), onClick = { if (r.url.isNotBlank()) openUrl(r.url) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(r.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (r.content.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(r.content, fontSize = 13.sp, color = c.textSecondary, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (query.isNotBlank()) {
                item {
                    SandButton(
                        "웹에서 '" + query.trim() + "' 검색",
                        { openUrl("https://www.google.com/search?q=" + URLEncoder.encode(query.trim(), "UTF-8")) },
                        Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
