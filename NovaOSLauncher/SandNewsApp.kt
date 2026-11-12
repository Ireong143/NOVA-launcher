package com.nova.os.launcher.sand

import android.util.Xml
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.launcher.ui.components.CircleIconButton
import com.nova.os.launcher.ui.components.GlassCard
import com.nova.os.launcher.ui.components.ToolScaffold
import com.nova.os.launcher.ui.theme.LocalNovaColors
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

class NewsItem(val title: String, val source: String, val link: String, val time: Long)

private val NEWS_TOPICS = listOf(
    "주요" to "", "세계" to "WORLD", "국내" to "NATION", "비즈니스" to "BUSINESS",
    "IT·기술" to "TECHNOLOGY", "연예" to "ENTERTAINMENT", "스포츠" to "SPORTS",
    "과학" to "SCIENCE", "건강" to "HEALTH",
)

private fun newsFeedUrl(topic: String): String =
    if (topic.isEmpty()) {
        "https://news.google.com/rss?hl=ko&gl=KR&ceid=KR:ko"
    } else {
        "https://news.google.com/rss/headlines/section/topic/" + topic + "?hl=ko&gl=KR&ceid=KR:ko"
    }

private fun parseRss(xml: String): List<NewsItem> {
    val out = ArrayList<NewsItem>()
    val p = Xml.newPullParser()
    p.setInput(StringReader(xml))
    val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH)
    var event = p.eventType
    var inItem = false
    var tag = ""
    var title = ""
    var link = ""
    var source = ""
    var pub = ""
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                tag = p.name
                if (tag == "item") {
                    inItem = true
                    title = ""; link = ""; source = ""; pub = ""
                }
            }
            XmlPullParser.TEXT -> if (inItem) {
                val t = p.text
                when (tag) {
                    "title" -> title += t
                    "link" -> link += t
                    "pubDate" -> pub += t
                    "source" -> source += t
                }
            }
            XmlPullParser.END_TAG -> {
                if (p.name == "item") {
                    var clean = title.trim()
                    val src = source.trim()
                    if (src.isNotEmpty() && clean.endsWith(" - $src")) clean = clean.removeSuffix(" - $src")
                    val time = try {
                        fmt.parse(pub.trim())?.time ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
                    if (clean.isNotEmpty() && link.isNotBlank()) out.add(NewsItem(clean, src, link.trim(), time))
                    inItem = false
                }
                tag = ""
            }
        }
        event = p.next()
    }
    return out
}

@Composable
fun SandNewsScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalNovaColors.current
    var topic by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var news by remember { mutableStateOf<List<NewsItem>>(emptyList()) }

    LaunchedEffect(topic, tick) {
        loading = true
        error = ""
        try {
            val xml = withContext(Dispatchers.IO) { SandHttp.get(newsFeedUrl(NEWS_TOPICS[topic].second)) }
            news = withContext(Dispatchers.Default) { parseRss(xml) }
        } catch (e: Exception) {
            error = "뉴스를 불러오지 못했어요. 인터넷 연결을 확인해 주세요."
            news = emptyList()
        }
        loading = false
    }

    ToolScaffold(
        "뉴스", onClose,
        actions = { CircleIconButton(Icons.Rounded.Refresh, "새로고침", { tick++ }) }
    ) {
        LazyRow(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(NEWS_TOPICS.indices.toList()) { i ->
                SandChip(NEWS_TOPICS[i].first, i == topic, { topic = i })
            }
        }
        Spacer(Modifier.height(6.dp))
        if (loading) {
            Text("불러오는 중…", Modifier.padding(20.dp), color = c.textSecondary)
        } else if (error.isNotEmpty()) {
            Text(error, Modifier.padding(20.dp), color = c.danger)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(news) { n ->
                GlassCard(
                    Modifier.fillMaxWidth(),
                    onClick = { sandOpenUrl(ctx, n.link) }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(n.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        Row {
                            Text(n.source, fontSize = 12.sp, color = c.accent, maxLines = 1)
                            if (n.time > 0) {
                                Spacer(Modifier.width(8.dp))
                                Text(sandAgo(n.time), fontSize = 12.sp, color = c.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}
