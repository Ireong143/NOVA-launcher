package com.nova.os.launcher.sand

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** SAND 기능들이 함께 쓰는 SharedPreferences. */
object SandPrefs {
    fun of(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences("sand_os", Context.MODE_PRIVATE)
}

/** 브라우저(또는 GitHub 앱 등)로 주소 열기. */
fun sandOpenUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        // 열 수 있는 앱이 없음
    }
}

/** 아주 단순한 HTTP 클라이언트(백그라운드 스레드에서만 호출할 것). */
object SandHttp {
    private const val UA = "SANDOS-Launcher/1.0 (Android)"

    fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 12000): String =
        String(request(url, null, headers, timeoutMs), Charsets.UTF_8)

    fun getBytes(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 12000): ByteArray =
        request(url, null, headers, timeoutMs)

    fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 90000): String =
        String(request(url, body, headers, timeoutMs), Charsets.UTF_8)

    private fun request(url: String, body: String?, headers: Map<String, String>, timeoutMs: Int): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.setRequestProperty("User-Agent", UA)
            for ((k, v) in headers) c.setRequestProperty(k, v)
            if (body != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            if (code !in 200..299) {
                throw IOException("HTTP " + code + " " + String(bytes, Charsets.UTF_8).take(300))
            }
            return bytes
        } finally {
            c.disconnect()
        }
    }
}

/** 갤러리(Pictures/SAND)에 PNG 저장. Android 10(API 29) 이상에서만 동작. */
object SandMedia {
    fun savePng(context: Context, bitmap: Bitmap, name: String): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SAND")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }
}

/** "더 많은 설정"에서 바꾸는 값들. 앱을 다시 켜도 유지된다. */
object SandConfig {
    private var prefs: SharedPreferences? = null

    /** 홈 첫 페이지의 앱 줄 수 (2~4) */
    var homeRows by mutableIntStateOf(3)

    /** 유리 진하기 배율 (0.4 ~ 1.6) */
    var glass by mutableFloatStateOf(1f)

    /** 홈 시계 위젯 모양: 0 큰 카드, 1 아날로그, 2 대형, 3 작은 바 */
    var clockLook by mutableIntStateOf(0)

    var showSound by mutableStateOf(true)
    var showCell by mutableStateOf(true)
    var showWifi by mutableStateOf(true)
    var showBell by mutableStateOf(true)

    /** 홈에서 왼쪽으로 넘기면 나오는 앱 라이브러리 페이지 */
    var libraryOn by mutableStateOf(true)

    /** 새 알림이 오면 홈 위에 배너 표시 */
    var bannerOn by mutableStateOf(true)

    var mondayFirst by mutableStateOf(false)

    /** 시스템 상태 바(통신사·시간·와이파이·데이터)를 숨기고 SAND 상태 줄만 보이게 한다. 기본은 꺼짐. */
    var hideSystemBar by mutableStateOf(false)

    /** 앱을 열 때 아이콘에서 커지는 로딩 화면 모션 */
    var launchFx by mutableStateOf(true)

    /** 홈 그리드에 직접 추가한 SAND 기본 앱(SandPage 이름) */
    var homeBuiltins by mutableStateOf<List<String>>(listOf("CALENDAR", "MAP"))

    fun init(context: Context) {
        if (prefs != null) return
        val p = SandPrefs.of(context)
        prefs = p
        homeRows = p.getInt("homeRows", 3).coerceIn(2, 4)
        glass = p.getFloat("glass", 1f).coerceIn(0.4f, 1.6f)
        clockLook = p.getInt("clockLook", 0).coerceIn(0, 3)
        showSound = p.getBoolean("showSound", true)
        showCell = p.getBoolean("showCell", true)
        showWifi = p.getBoolean("showWifi", true)
        showBell = p.getBoolean("showBell", true)
        libraryOn = p.getBoolean("libraryOn", true)
        bannerOn = p.getBoolean("bannerOn", true)
        mondayFirst = p.getBoolean("mondayFirst", false)
        hideSystemBar = p.getBoolean("hideSystemBar", false)
        launchFx = p.getBoolean("launchFx", true)
        homeBuiltins = p.getString("homeBuiltins", "CALENDAR|MAP").orEmpty().split('|').filter { it.isNotBlank() }
    }

    /** 값을 바꾸고 바로 저장한다: `SandConfig.edit { homeRows = 3 }` */
    fun edit(block: SandConfig.() -> Unit) {
        block()
        save()
    }

    fun save() {
        val p = prefs ?: return
        p.edit()
            .putInt("homeRows", homeRows)
            .putFloat("glass", glass)
            .putInt("clockLook", clockLook)
            .putBoolean("showSound", showSound)
            .putBoolean("showCell", showCell)
            .putBoolean("showWifi", showWifi)
            .putBoolean("showBell", showBell)
            .putBoolean("libraryOn", libraryOn)
            .putBoolean("bannerOn", bannerOn)
            .putBoolean("mondayFirst", mondayFirst)
            .putBoolean("hideSystemBar", hideSystemBar)
            .putBoolean("launchFx", launchFx)
            .putString("homeBuiltins", homeBuiltins.joinToString("|"))
            .apply()
    }

    fun resetDefaults() {
        homeRows = 3
        glass = 1f
        clockLook = 0
        showSound = true
        showCell = true
        showWifi = true
        showBell = true
        libraryOn = true
        bannerOn = true
        mondayFirst = false
        hideSystemBar = false
        launchFx = true
        homeBuiltins = listOf("CALENDAR", "MAP")
        save()
    }
}
