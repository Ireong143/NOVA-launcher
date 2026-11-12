package com.nova.os.launcher.sand

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.LocationManager
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nova.os.launcher.ui.components.GlassCard
import com.nova.os.launcher.ui.components.ToolScaffold
import com.nova.os.launcher.ui.theme.LocalNovaColors
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** AI 에이전트가 지도를 열 때 검색어를 넘기는 통로. */
object SandBridge {
    @Volatile
    var mapQuery: String? = null
}

class MapPlace(val name: String, val lat: Double, val lon: Double)

private const val TILE = 256.0

private fun worldX(lon: Double, z: Int): Double = (lon + 180.0) / 360.0 * TILE * Math.pow(2.0, z.toDouble())

private fun worldY(lat: Double, z: Int): Double {
    val r = Math.toRadians(lat)
    return (1.0 - Math.log(Math.tan(r) + 1.0 / Math.cos(r)) / Math.PI) / 2.0 * TILE * Math.pow(2.0, z.toDouble())
}

private fun lonFromX(x: Double, z: Int): Double = x / (TILE * Math.pow(2.0, z.toDouble())) * 360.0 - 180.0

private fun latFromY(y: Double, z: Int): Double {
    val n = Math.PI - 2.0 * Math.PI * y / (TILE * Math.pow(2.0, z.toDouble()))
    return Math.toDegrees(Math.atan(Math.sinh(n)))
}

/** OpenStreetMap 타일 캐시. */
private object SandTiles {
    private val cache = LruCache<String, ImageBitmap>(160)

    fun key(z: Int, x: Int, y: Int) = "$z/$x/$y"

    fun peek(z: Int, x: Int, y: Int): ImageBitmap? = cache.get(key(z, x, y))

    fun fetch(z: Int, x: Int, y: Int): Boolean {
        val k = key(z, x, y)
        if (cache.get(k) != null) return false
        return try {
            val bytes = SandHttp.getBytes("https://tile.openstreetmap.org/$k.png", timeoutMs = 10000)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp != null) {
                cache.put(k, bmp.asImageBitmap())
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}

@Composable
fun SandMapScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalNovaColors.current
    val scope = rememberCoroutineScope()

    var lat by remember { mutableDoubleStateOf(37.5665) }
    var lon by remember { mutableDoubleStateOf(126.9780) }
    var zoom by remember { mutableFloatStateOf(12f) }
    var tick by remember { mutableIntStateOf(0) }
    var w by remember { mutableIntStateOf(1) }
    var h by remember { mutableIntStateOf(1) }
    var query by remember { mutableStateOf("") }
    var marker by remember { mutableStateOf<MapPlace?>(null) }
    var results by remember { mutableStateOf<List<MapPlace>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }

    fun search(q: String) {
        val text = q.trim()
        if (text.isEmpty() || searching) return
        searching = true
        message = ""
        scope.launch {
            try {
                val body = withContext(Dispatchers.IO) {
                    SandHttp.get(
                        "https://nominatim.openstreetmap.org/search?format=json&limit=5&accept-language=ko&q=" +
                            URLEncoder.encode(text, "UTF-8")
                    )
                }
                val arr = JSONArray(body)
                val list = ArrayList<MapPlace>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(MapPlace(o.optString("display_name", ""), o.getString("lat").toDouble(), o.getString("lon").toDouble()))
                }
                results = list
                if (list.isEmpty()) message = "검색 결과가 없어요"
            } catch (e: Exception) {
                message = "검색하지 못했어요. 인터넷 연결을 확인해 주세요."
            }
            searching = false
        }
    }

    fun locateMe() {
        try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var loc = try {
                lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } catch (e: SecurityException) {
                null
            }
            if (loc == null) {
                loc = try {
                    lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                } catch (e: SecurityException) {
                    null
                }
            }
            if (loc != null) {
                lat = loc.latitude
                lon = loc.longitude
                zoom = 15f
                marker = MapPlace("내 위치", loc.latitude, loc.longitude)
            } else {
                message = "현재 위치를 아직 알 수 없어요. 위치 서비스를 켜 주세요."
            }
        } catch (e: Exception) {
            message = "위치를 가져오지 못했어요"
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locateMe() else message = "위치 권한이 필요해요"
    }

    // AI 에이전트 등에서 넘어온 검색어가 있으면 바로 검색
    LaunchedEffect(Unit) {
        val q = SandBridge.mapQuery
        if (!q.isNullOrBlank()) {
            SandBridge.mapQuery = null
            query = q
            search(q)
        }
    }

    // 화면에 필요한 타일을 불러온다(움직임이 멈추면 시작).
    LaunchedEffect(lat, lon, zoom, w, h) {
        delay(150)
        val zi = Math.floor(zoom.toDouble()).toInt().coerceIn(2, 18)
        val k = Math.pow(2.0, zoom.toDouble() - zi)
        val cx = worldX(lon, zi)
        val cy = worldY(lat, zi)
        val n = 1 shl zi
        val x0 = Math.floor((cx - w / 2.0 / k) / TILE).toInt()
        val x1 = Math.floor((cx + w / 2.0 / k) / TILE).toInt()
        val y0 = Math.floor((cy - h / 2.0 / k) / TILE).toInt().coerceAtLeast(0)
        val y1 = Math.floor((cy + h / 2.0 / k) / TILE).toInt().coerceAtMost(n - 1)
        val need = ArrayList<IntArray>()
        for (ty in y0..y1) for (tx in x0..x1) {
            val wx = ((tx % n) + n) % n
            if (SandTiles.peek(zi, wx, ty) == null) need.add(intArrayOf(zi, wx, ty))
        }
        for (chunk in need.chunked(6)) {
            coroutineScope {
                chunk.map { t -> async(Dispatchers.IO) { SandTiles.fetch(t[0], t[1], t[2]) } }.forEach { it.await() }
            }
            tick++
        }
    }

    ToolScaffold("지도", onClose) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SandTextField(
                    query, { query = it }, "장소나 주소를 검색하세요",
                    Modifier.weight(1f), imeAction = ImeAction.Search, onAction = { search(query) }
                )
                SandButton(if (searching) "…" else "검색", { search(query) }, enabled = query.isNotBlank() && !searching)
            }
            if (message.isNotEmpty()) Text(message, color = c.danger, fontSize = 13.sp)
            if (results.isNotEmpty()) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column {
                        for (p in results) {
                            Text(
                                p.name,
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        lat = p.lat
                                        lon = p.lon
                                        zoom = 15f
                                        marker = p
                                        results = emptyList()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                fontSize = 14.sp,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFFE8EAF0))
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { w = it.width; h = it.height }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoomChange, _ ->
                            val zi = Math.floor(zoom.toDouble()).toInt().coerceIn(2, 18)
                            val k = Math.pow(2.0, zoom.toDouble() - zi)
                            val nx = worldX(lon, zi) - pan.x / k
                            var ny = worldY(lat, zi) - pan.y / k
                            val maxY = TILE * Math.pow(2.0, zi.toDouble())
                            ny = ny.coerceIn(0.0, maxY)
                            lon = lonFromX(nx, zi).let { l -> if (l > 180.0) l - 360.0 else if (l < -180.0) l + 360.0 else l }
                            lat = latFromY(ny, zi).coerceIn(-85.0, 85.0)
                            if (zoomChange != 1f) {
                                zoom = (zoom + (Math.log(zoomChange.toDouble()) / Math.log(2.0)).toFloat()).coerceIn(2f, 18f)
                            }
                        }
                    }
            ) {
                tick // 타일이 도착하면 다시 그리도록 상태를 읽는다
                val zi = Math.floor(zoom.toDouble()).toInt().coerceIn(2, 18)
                val k = Math.pow(2.0, zoom.toDouble() - zi)
                val cx = worldX(lon, zi)
                val cy = worldY(lat, zi)
                val n = 1 shl zi
                val x0 = Math.floor((cx - size.width / 2.0 / k) / TILE).toInt()
                val x1 = Math.floor((cx + size.width / 2.0 / k) / TILE).toInt()
                val y0 = Math.floor((cy - size.height / 2.0 / k) / TILE).toInt().coerceAtLeast(0)
                val y1 = Math.floor((cy + size.height / 2.0 / k) / TILE).toInt().coerceAtMost(n - 1)
                for (ty in y0..y1) for (tx in x0..x1) {
                    val wx = ((tx % n) + n) % n
                    val img = SandTiles.peek(zi, wx, ty) ?: continue
                    val sx = size.width / 2.0 + (tx * TILE - cx) * k
                    val sy = size.height / 2.0 + (ty * TILE - cy) * k
                    drawImage(
                        image = img,
                        dstOffset = IntOffset(Math.round(sx).toInt(), Math.round(sy).toInt()),
                        dstSize = IntSize(Math.ceil(TILE * k).toInt() + 1, Math.ceil(TILE * k).toInt() + 1)
                    )
                }
                val m = marker
                if (m != null) {
                    val px = (size.width / 2.0 + (worldX(m.lon, zi) - cx) * k).toFloat()
                    val py = (size.height / 2.0 + (worldY(m.lat, zi) - cy) * k).toFloat()
                    drawCircle(Color(0xFFE53935), radius = 11.dp.toPx(), center = Offset(px, py))
                    drawCircle(Color.White, radius = 11.dp.toPx(), center = Offset(px, py), style = Stroke(3.dp.toPx()))
                    drawCircle(Color.White, radius = 3.5.dp.toPx(), center = Offset(px, py))
                }
            }
            Text(
                "© OpenStreetMap contributors",
                Modifier
                    .align(Alignment.BottomStart)
                    .background(Color(0xB3FFFFFF))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                fontSize = 10.sp,
                color = Color(0xFF333333)
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SandButton("＋", { zoom = (zoom + 1f).coerceAtMost(18f) }, Modifier.weight(0.5f))
            SandButton("－", { zoom = (zoom - 1f).coerceAtLeast(2f) }, Modifier.weight(0.5f))
            SandButton(
                "내 위치",
                {
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        locateMe()
                    } else {
                        permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                },
                Modifier.weight(1f)
            )
            SandButton(
                "길찾기",
                {
                    val m = marker
                    val dest = if (m != null) m.lat.toString() + "," + m.lon else lat.toString() + "," + lon
                    sandOpenUrl(ctx, "https://www.google.com/maps/dir/?api=1&destination=$dest")
                },
                Modifier.weight(1f)
            )
        }
    }
}
