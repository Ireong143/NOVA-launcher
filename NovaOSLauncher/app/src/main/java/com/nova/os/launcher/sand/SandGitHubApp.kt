package com.nova.os.launcher.sand

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.launcher.ui.components.CircleIconButton
import com.nova.os.launcher.ui.components.GlassCard
import com.nova.os.launcher.ui.components.NovaSegmented
import com.nova.os.launcher.ui.components.SectionLabel
import com.nova.os.launcher.ui.components.ToolScaffold
import com.nova.os.launcher.ui.theme.LocalNovaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class GhRepo(
    val fullName: String, val description: String, val stars: Int, val forks: Int,
    val issues: Int, val branch: String, val pushed: Long, val isPrivate: Boolean,
)

class GhCommit(val message: String, val author: String, val time: Long, val url: String)

class GhRun(
    val title: String, val status: String, val conclusion: String, val branch: String,
    val time: Long, val url: String, val number: Int,
)

private fun isoToMs(s: String): Long = try {
    java.time.Instant.parse(s).toEpochMilli()
} catch (e: Exception) {
    0L
}

private fun ghHeaders(token: String): Map<String, String> {
    val m = HashMap<String, String>()
    m["Accept"] = "application/vnd.github+json"
    if (token.isNotBlank()) m["Authorization"] = "Bearer " + token.trim()
    return m
}

private fun ghGet(path: String, token: String): String = SandHttp.get("https://api.github.com$path", ghHeaders(token))

@Composable
fun SandGitHubScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalNovaColors.current
    val prefs = remember { SandPrefs.of(ctx) }
    var owner by remember { mutableStateOf(prefs.getString("gh_owner", "").orEmpty()) }
    var repo by remember { mutableStateOf(prefs.getString("gh_repo", "NOVA-launcher").orEmpty()) }
    var token by remember { mutableStateOf(prefs.getString("gh_token", "").orEmpty()) }
    var ownerIn by remember { mutableStateOf(owner) }
    var repoIn by remember { mutableStateOf(repo) }
    var tokenIn by remember { mutableStateOf(token) }
    var tab by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<GhRepo?>(null) }
    var commits by remember { mutableStateOf<List<GhCommit>>(emptyList()) }
    var runs by remember { mutableStateOf<List<GhRun>>(emptyList()) }
    val ready = owner.isNotBlank() && repo.isNotBlank()
    val base = "https://github.com/" + owner + "/" + repo

    LaunchedEffect(owner, repo, token, tab, tick) {
        if (!ready) return@LaunchedEffect
        loading = true
        error = ""
        try {
            val path = "/repos/" + owner + "/" + repo
            when (tab) {
                0 -> {
                    val o = JSONObject(withContext(Dispatchers.IO) { ghGet(path, token) })
                    info = GhRepo(
                        o.optString("full_name"), o.optString("description", ""),
                        o.optInt("stargazers_count"), o.optInt("forks_count"), o.optInt("open_issues_count"),
                        o.optString("default_branch", "main"), isoToMs(o.optString("pushed_at")),
                        o.optBoolean("private", false)
                    )
                }
                1 -> {
                    val arr = JSONArray(withContext(Dispatchers.IO) { ghGet("$path/commits?per_page=20", token) })
                    val list = ArrayList<GhCommit>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val cm = o.getJSONObject("commit")
                        list.add(
                            GhCommit(
                                cm.optString("message", "").lineSequence().firstOrNull().orEmpty(),
                                cm.optJSONObject("author")?.optString("name", "") ?: "",
                                isoToMs(cm.optJSONObject("author")?.optString("date", "") ?: ""),
                                o.optString("html_url", "")
                            )
                        )
                    }
                    commits = list
                }
                else -> {
                    val o = JSONObject(withContext(Dispatchers.IO) { ghGet("$path/actions/runs?per_page=15", token) })
                    val arr = o.getJSONArray("workflow_runs")
                    val list = ArrayList<GhRun>()
                    for (i in 0 until arr.length()) {
                        val r = arr.getJSONObject(i)
                        val title = r.optString("display_title", "").ifEmpty { r.optString("name", "") }
                        list.add(
                            GhRun(
                                title, r.optString("status", ""), r.optString("conclusion", ""),
                                r.optString("head_branch", ""), isoToMs(r.optString("created_at")),
                                r.optString("html_url", ""), r.optInt("run_number")
                            )
                        )
                    }
                    runs = list
                }
            }
        } catch (e: Exception) {
            val m = e.message.orEmpty()
            error = if (m.contains("404")) {
                "저장소를 찾을 수 없어요. 이름을 확인하거나, 비공개 저장소라면 토큰을 입력해 주세요."
            } else if (m.contains("403")) {
                "요청이 너무 많거나 권한이 없어요. 잠시 후 다시 시도하거나 토큰을 입력해 주세요."
            } else {
                "불러오지 못했어요. 인터넷 연결을 확인해 주세요."
            }
        }
        loading = false
    }

    ToolScaffold(
        "GitHub", onClose,
        actions = { CircleIconButton(Icons.Rounded.Refresh, "새로고침", { tick++ }) }
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!ready || tab == 0) {
                SectionLabel("저장소")
                SandTextField(ownerIn, { ownerIn = it }, "GitHub 사용자 이름", Modifier.fillMaxWidth())
                SandTextField(repoIn, { repoIn = it }, "저장소 이름", Modifier.fillMaxWidth())
                SandTextField(tokenIn, { tokenIn = it }, "개인 액세스 토큰(비공개 저장소일 때만, 선택)", Modifier.fillMaxWidth(), password = true)
                SandButton(
                    "저장하고 불러오기",
                    {
                        owner = ownerIn.trim()
                        repo = repoIn.trim()
                        token = tokenIn.trim()
                        prefs.edit().putString("gh_owner", owner).putString("gh_repo", repo).putString("gh_token", token).apply()
                    },
                    Modifier.fillMaxWidth(),
                    enabled = ownerIn.isNotBlank() && repoIn.isNotBlank()
                )
            }
            if (ready) {
                NovaSegmented(listOf("개요", "커밋", "Actions"), tab, { tab = it })
                if (loading) Text("불러오는 중…", color = c.textSecondary, fontSize = 14.sp)
                if (error.isNotEmpty()) Text(error, color = c.danger, fontSize = 14.sp)

                when (tab) {
                    0 -> {
                        val i = info
                        if (i != null) {
                            GlassCard(Modifier.fillMaxWidth(), strong = true) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(i.fullName + if (i.isPrivate) "  (비공개)" else "", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                    if (i.description.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(i.description, fontSize = 14.sp, color = c.textSecondary)
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        "★ " + i.stars + "   포크 " + i.forks + "   열린 이슈 " + i.issues,
                                        fontSize = 13.sp, color = c.textSecondary
                                    )
                                    Text(
                                        "기본 브랜치 " + i.branch + (if (i.pushed > 0) "   마지막 푸시 " + sandAgo(i.pushed) else ""),
                                        fontSize = 13.sp, color = c.textSecondary
                                    )
                                }
                            }
                        }
                        SandButton("저장소 열기", { sandOpenUrl(ctx, base) }, Modifier.fillMaxWidth())
                        SandButton("Actions(빌드) 열기", { sandOpenUrl(ctx, "$base/actions") }, Modifier.fillMaxWidth())
                        SandButton(
                            "파일 올리기 페이지 열기",
                            { sandOpenUrl(ctx, base + "/upload/" + (info?.branch ?: "main")) },
                            Modifier.fillMaxWidth()
                        )
                        SandButton("이슈 열기", { sandOpenUrl(ctx, "$base/issues") }, Modifier.fillMaxWidth())
                    }
                    1 -> for (cm in commits) {
                        GlassCard(Modifier.fillMaxWidth(), onClick = { if (cm.url.isNotBlank()) sandOpenUrl(ctx, cm.url) }) {
                            Column(Modifier.padding(14.dp)) {
                                Text(cm.message, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Text(cm.author + (if (cm.time > 0) " · " + sandAgo(cm.time) else ""), fontSize = 12.sp, color = c.textSecondary)
                            }
                        }
                    }
                    else -> for (r in runs) {
                        val done = r.status == "completed"
                        val label = if (!done) {
                            if (r.status == "queued") "대기 중" else "진행 중"
                        } else when (r.conclusion) {
                            "success" -> "성공"
                            "failure" -> "실패"
                            "cancelled" -> "취소됨"
                            "skipped" -> "건너뜀"
                            "timed_out" -> "시간 초과"
                            else -> r.conclusion
                        }
                        val tone: Color = if (!done) c.warn else when (r.conclusion) {
                            "success" -> c.good
                            "failure", "timed_out" -> c.danger
                            else -> c.textSecondary
                        }
                        GlassCard(Modifier.fillMaxWidth(), onClick = { if (r.url.isNotBlank()) sandOpenUrl(ctx, r.url) }) {
                            Row(Modifier.padding(14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "#" + r.number + " · " + r.branch + (if (r.time > 0) " · " + sandAgo(r.time) else ""),
                                        fontSize = 12.sp, color = c.textSecondary
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = tone)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
