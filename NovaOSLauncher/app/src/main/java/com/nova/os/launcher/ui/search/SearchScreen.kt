
package com.nova.os.launcher.ui.search

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.launcher.NovaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private data class SearchResult(
    val title: String,
    val url: String,
    val content: String
)

@Composable
fun SearchScreen(vm: NovaViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun search() {
        val keyword = query.trim()
        if (keyword.isEmpty() || loading) return

        loading = true
        error = ""
        results = emptyList()

        scope.launch {
            try {
                val found = withContext(Dispatchers.IO) {
                    val encoded = URLEncoder.encode(keyword, "UTF-8")
                    val connection = URL(
                        "https://sand-os-search.l234567967.workers.dev/search?q=$encoded"
                    ).openConnection() as HttpURLConnection

                    try {
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 10000
                        connection.readTimeout = 15000

                        val responseCode = connection.responseCode
                        val stream = if (responseCode in 200..299) {
                            connection.inputStream
                        } else {
                            connection.errorStream
                        }

                        val body = stream.bufferedReader().use { it.readText() }

                        if (responseCode !in 200..299) {
                            throw Exception("서버 응답 오류: $responseCode")
                        }

                        val json = JSONObject(body)
                        if (json.optString("status") != "success") {
                            throw Exception("검색 결과를 불러오지 못했어요.")
                        }

                        val array = json.optJSONArray("results")
                        val list = mutableListOf<SearchResult>()

                        if (array != null) {
                            for (i in 0 until array.length()) {
                                val item = array.optJSONObject(i) ?: continue
                                val title = item.optString("title")
                                val url = item.optString("url")
                                val content = item.optString("content")

                                if (title.isNotBlank() && url.startsWith("https://")) {
                                    list.add(
                                        SearchResult(
                                            title = title,
                                            url = url,
                                            content = content
                                        )
                                    )
                                }
                            }
                        }

                        list
                    } finally {
                        connection.disconnect()
                    }
                }

                results = found
                if (found.isEmpty()) {
                    error = "검색 결과가 없어요."
                }
            } catch (e: Exception) {
                error = e.message ?: "검색 중 오류가 발생했어요."
            } finally {
                loading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101827))
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { vm.back() }) {
                Text("뒤로", color = Color.White)
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "SAND 검색",
                color = Color.White,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            placeholder = { Text("무엇이든 검색해 보세요") },
            trailingIcon = {
                TextButton(
                    onClick = { search() },
                    enabled = !loading
                ) {
                    Text("검색")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { search() }
            )
        )

        Spacer(modifier = Modifier.height(18.dp))

        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(30.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF9CCBFF))
            }
        }

        if (error.isNotBlank()) {
            Text(
                text = error,
                color = Color(0xFFFFB4AB),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(results) { result ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFF202D40),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(result.url)
                            )
                            context.startActivity(intent)
                        }
                        .padding(18.dp)
                ) {
                    Text(
                        text = result.title,
                        color = Color(0xFFB8D9FF),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(7.dp))

                    Text(
                        text = result.content,
                        color = Color(0xFFE1E8F0),
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "열어서 보기",
                        color = Color(0xFF9CCBFF),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

