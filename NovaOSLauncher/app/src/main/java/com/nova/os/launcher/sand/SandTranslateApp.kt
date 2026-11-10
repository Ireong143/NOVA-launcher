package com.nova.os.launcher.sand

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.launcher.ui.components.GlassCard
import com.nova.os.launcher.ui.components.SectionLabel
import com.nova.os.launcher.ui.components.ToolScaffold
import com.nova.os.launcher.ui.theme.LocalNovaColors
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val LANGS = listOf(
    "한국어" to "ko", "영어" to "en", "일본어" to "ja", "중국어" to "zh-CN", "스페인어" to "es",
    "프랑스어" to "fr", "독일어" to "de", "베트남어" to "vi", "태국어" to "th", "러시아어" to "ru",
)

private fun detectLang(text: String): String {
    for (ch in text) {
        if (ch in '\uAC00'..'\uD7A3') return "ko"
        if (ch in '\u3040'..'\u30FF') return "ja"
    }
    return "en"
}

private fun unescapeHtml(s: String): String =
    s.replace("&#39;", "'").replace("&quot;", "\"").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")

@Composable
fun SandTranslateScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalNovaColors.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var from by remember { mutableIntStateOf(-1) } // -1 = 자동 감지
    var to by remember { mutableIntStateOf(1) }

    fun run() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        val src = if (from < 0) detectLang(text) else LANGS[from].second
        var dst = LANGS[to].second
        if (src == dst) dst = if (src == "ko") "en" else "ko"
        busy = true
        status = ""
        scope.launch {
            try {
                val q = URLEncoder.encode(text.take(450), "UTF-8")
                val pair = URLEncoder.encode(src + "|" + dst, "UTF-8")
                val body = withContext(Dispatchers.IO) {
                    SandHttp.get("https://api.mymemory.translated.net/get?q=" + q + "&langpair=" + pair)
                }
                val o = JSONObject(body)
                val translated = o.getJSONObject("responseData").optString("translatedText", "")
                if (translated.isBlank()) {
                    status = "번역 결과가 없어요"
                } else {
                    result = unescapeHtml(translated)
                    status = src + " → " + dst
                }
            } catch (e: Exception) {
                status = "번역하지 못했어요. 인터넷 연결을 확인해 주세요."
            }
            busy = false
        }
    }

    ToolScaffold("번역기", onClose) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionLabel("출발 언어")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { SandChip("자동 감지", from < 0, { from = -1 }) }
                items(LANGS.indices.toList()) { i -> SandChip(LANGS[i].first, from == i, { from = i }) }
            }
            SectionLabel("도착 언어")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LANGS.indices.toList()) { i -> SandChip(LANGS[i].first, to == i, { to = i }) }
            }
            SandTextField(
                input, { input = it }, "번역할 내용을 입력하세요 (최대 450자)",
                Modifier.fillMaxWidth(), singleLine = false, maxLines = 6
            )
            SandButton(if (busy) "번역 중…" else "번역하기", { run() }, Modifier.fillMaxWidth(), enabled = input.isNotBlank() && !busy)
            if (status.isNotEmpty()) Text(status, fontSize = 12.sp, color = c.textSecondary)
            if (result.isNotEmpty()) {
                GlassCard(Modifier.fillMaxWidth(), strong = true) {
                    Column(Modifier.padding(16.dp)) {
                        Text(result, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SandButton("복사", { clipboard.setText(AnnotatedString(result)) }, Modifier.weight(1f))
                            SandButton(
                                "공유",
                                {
                                    try {
                                        val send = Intent(Intent.ACTION_SEND)
                                        send.type = "text/plain"
                                        send.putExtra(Intent.EXTRA_TEXT, result)
                                        ctx.startActivity(Intent.createChooser(send, "공유").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    } catch (e: Exception) {
                                        // 공유할 앱이 없음
                                    }
                                },
                                Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
