package com.nova.os.launcher.ui.components
 
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nova.os.launcher.data.AppInfo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
 
/** 시안에 나오는 기본 앱 종류. 설치된 앱과 매칭되면 시안풍으로 직접 그린 아이콘을 쓴다. */
enum class SandKind {
    SETTINGS, GALLERY, PLAY, CAMERA, PHONE, MESSAGES, INTERNET, YOUTUBE, MUSIC,
    CALENDAR, MAIL, CLOCK, MAPS, WEATHER, NOTES, REMINDERS,
}
 
private class SandSpec(val kind: SandKind, val packages: Set<String>, val labels: List<String>)
 
private val SandSpecs = listOf(
    SandSpec(SandKind.SETTINGS, setOf("com.android.settings"), listOf("설정", "Settings")),
    SandSpec(SandKind.GALLERY, setOf("com.sec.android.gallery3d"), listOf("갤러리", "Gallery")),
    SandSpec(SandKind.PLAY, setOf("com.android.vending"), listOf("Play 스토어", "Play Store", "Google Play 스토어")),
    SandSpec(SandKind.CAMERA, setOf("com.sec.android.app.camera"), listOf("카메라", "Camera")),
    SandSpec(
        SandKind.PHONE,
        setOf("com.samsung.android.dialer", "com.android.dialer", "com.google.android.dialer"),
        listOf("전화", "Phone")
    ),
    SandSpec(
        SandKind.MESSAGES,
        setOf("com.samsung.android.messaging", "com.google.android.apps.messaging"),
        listOf("메시지", "Messages")
    ),
    SandSpec(SandKind.INTERNET, setOf("com.sec.android.app.sbrowser"), listOf("인터넷", "Internet", "Samsung Internet")),
    SandSpec(SandKind.YOUTUBE, setOf("com.google.android.youtube"), listOf("YouTube")),
    SandSpec(
        SandKind.MUSIC,
        setOf("com.sec.android.app.music", "com.google.android.apps.youtube.music"),
        listOf("음악", "Music", "YouTube Music")
    ),
    SandSpec(
        SandKind.CALENDAR,
        setOf("com.samsung.android.calendar", "com.google.android.calendar", "com.android.calendar"),
        listOf("캘린더", "Calendar", "Google 캘린더")
    ),
    SandSpec(
        SandKind.MAIL,
        setOf("com.samsung.android.email.provider", "com.google.android.gm"),
        listOf("메일", "이메일", "Email", "Gmail")
    ),
    SandSpec(
        SandKind.CLOCK,
        setOf("com.sec.android.app.clockpackage", "com.google.android.deskclock", "com.android.deskclock"),
        listOf("시계", "Clock")
    ),
    SandSpec(SandKind.MAPS, setOf("com.google.android.apps.maps"), listOf("지도", "Maps", "Google 지도")),
    SandSpec(SandKind.WEATHER, setOf("com.sec.android.daemonservice"), listOf("날씨", "Weather")),
    SandSpec(
        SandKind.NOTES,
        setOf("com.samsung.android.app.notes", "com.google.android.keep"),
        listOf("메모", "Samsung Notes", "Keep 메모", "Notes")
    ),
    SandSpec(
        SandKind.REMINDERS,
        setOf("com.samsung.android.app.reminder", "com.google.android.apps.tasks"),
        listOf("미리 알림", "리마인더", "Reminder", "Tasks", "할 일")
    ),
)
 
fun sandKindOf(app: AppInfo): SandKind? {
    val label = app.label.trim()
    for (s in SandSpecs) {
        if (app.packageName in s.packages) return s.kind
        if (s.labels.any { it.equals(label, ignoreCase = true) }) return s.kind
    }
    return null
}
 
/** 콘셉트 보드의 홈 화면 배치(4열 × 3줄) */
val SandDefaultFavorites = listOf(
    SandKind.MESSAGES, SandKind.CALENDAR, SandKind.GALLERY, SandKind.CAMERA,
    SandKind.MAIL, SandKind.CLOCK, SandKind.MAPS, SandKind.WEATHER,
    SandKind.NOTES, SandKind.REMINDERS, SandKind.PLAY, SandKind.SETTINGS,
)

/** 콘셉트 보드의 독 배치 */
val SandDefaultDock = listOf(SandKind.PHONE, SandKind.INTERNET, SandKind.MUSIC, SandKind.SETTINGS)

class SandLayout(val favorites: List<String>, val dock: List<String>)

private fun sandRank(app: AppInfo, kind: SandKind): Int {
    val spec = SandSpecs.first { it.kind == kind }
    val i = spec.packages.toList().indexOf(app.packageName)
    return if (i >= 0) i else 100
}

/** 설치된 앱에서 보드 배치에 해당하는 앱의 패키지 이름을 순서대로 고른다(없는 앱은 건너뜀). */
fun sandLayoutOf(apps: List<AppInfo>): SandLayout {
    fun pick(kind: SandKind): String? =
        apps.filter { sandKindOf(it) == kind }.minByOrNull { sandRank(it, kind) }?.packageName
    return SandLayout(
        favorites = SandDefaultFavorites.mapNotNull { pick(it) }.distinct(),
        dock = SandDefaultDock.mapNotNull { pick(it) }.distinct(),
    )
}

/** order에 있는 앱을 그 순서대로 앞에 두고, 나머지는 원래 순서를 유지한다(안정 정렬). */
fun List<AppInfo>.sandOrdered(order: List<SandKind>): List<AppInfo> =
    sortedBy { app ->
        val i = sandKindOf(app)?.let { order.indexOf(it) } ?: -1
        if (i < 0) order.size + 1 else i
    }
 
@Composable
fun SandAppIcon(kind: SandKind, size: Dp, modifier: Modifier = Modifier, dock: Boolean = false) {
    val shape = RoundedCornerShape(size * 0.27f)
    Box(
        modifier
            .size(size)
            .shadow(4.dp, shape, clip = false, ambientColor = Color(0x33000000), spotColor = Color(0x40000000))
            .clip(shape)
            .border(0.6.dp, Color(0x33FFFFFF), shape)
    ) {
        when (kind) {
            SandKind.PHONE -> Box(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF63E772), Color(0xFF1BAF38)))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Call, null, Modifier.size(size * 0.56f), tint = Color.White) }
 
            SandKind.MUSIC -> Box(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFFFF6A78), Color(0xFFE5213B)))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.MusicNote, null, Modifier.size(size * 0.62f), tint = Color.White) }
 
            SandKind.CALENDAR -> SandCalendarFace(size)

            else -> Canvas(Modifier.fillMaxSize()) {
                when (kind) {
                    SandKind.SETTINGS -> drawSandSettings()
                    SandKind.GALLERY -> drawSandGallery()
                    SandKind.PLAY -> drawSandPlay()
                    SandKind.CAMERA -> drawSandCamera()
                    SandKind.MESSAGES -> drawSandMessages()
                    SandKind.INTERNET -> if (dock) drawSandCompass() else drawSandPlanet()
                    SandKind.YOUTUBE -> drawSandYoutube()
                    SandKind.MAIL -> drawSandMail()
                    SandKind.CLOCK -> drawSandClock()
                    SandKind.MAPS -> drawSandMaps()
                    SandKind.WEATHER -> drawSandWeather()
                    SandKind.NOTES -> drawSandNotes()
                    SandKind.REMINDERS -> drawSandReminders()
                    else -> Unit
                }
            }
        }
    }
}
 
private fun DrawScope.drawSandSettings() {
    val s = size.minDimension
    val c = center
    drawRect(Brush.verticalGradient(listOf(Color(0xFFF3F4F7), Color(0xFFB2B5BE))))
    val dark = Color(0xFF4B4D55)
    for (i in 0 until 12) {
        val a = (i * 2.0 * PI / 12.0).toFloat()
        drawLine(
            color = dark,
            start = Offset(c.x + cos(a) * s * 0.27f, c.y + sin(a) * s * 0.27f),
            end = Offset(c.x + cos(a) * s * 0.39f, c.y + sin(a) * s * 0.39f),
            strokeWidth = s * 0.085f,
            cap = StrokeCap.Butt
        )
    }
    drawCircle(
        brush = Brush.verticalGradient(listOf(Color(0xFF6D6F78), Color(0xFF383A41))),
        radius = s * 0.30f,
        center = c
    )
    drawCircle(Color(0xFFD9DBE1), radius = s * 0.19f, center = c, style = Stroke(s * 0.025f))
    for (i in 0 until 3) {
        val a = (i * 2.0 * PI / 3.0 - PI / 2.0).toFloat()
        drawLine(
            Color(0xFFD9DBE1),
            c,
            Offset(c.x + cos(a) * s * 0.19f, c.y + sin(a) * s * 0.19f),
            strokeWidth = s * 0.025f
        )
    }
    drawCircle(Color(0xFF2E3037), radius = s * 0.07f, center = c)
}
 
private fun DrawScope.drawSandGallery() {
    val s = size.minDimension
    val c = center
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFEDEFF4))))
    val petals = listOf(
        Color(0xFFEA4335), Color(0xFFFB8C00), Color(0xFFFBBC04), Color(0xFF34A853),
        Color(0xFF00ACC1), Color(0xFF4285F4), Color(0xFF8E24AA), Color(0xFFE91E63),
    )
    petals.forEachIndexed { i, col ->
        val a = (i * PI / 4.0 - PI / 2.0).toFloat()
        drawCircle(
            color = col.copy(alpha = 0.88f),
            radius = s * 0.165f,
            center = Offset(c.x + cos(a) * s * 0.15f, c.y + sin(a) * s * 0.15f)
        )
    }
}
 
private fun DrawScope.drawSandPlay() {
    val s = size.minDimension
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFECEFF5))))
    val ax = 0.30f * s; val ay = 0.20f * s
    val bx = 0.30f * s; val by = 0.80f * s
    val cx = 0.78f * s; val cy = 0.50f * s
    val px = 0.47f * s; val py = 0.50f * s
    val tx = 0.564f * s; val ty = 0.365f * s
    val ux = 0.564f * s; val uy = 0.635f * s
    fun poly(color: Color, pts: List<Offset>) {
        val p = Path()
        p.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) p.lineTo(pts[i].x, pts[i].y)
        p.close()
        drawPath(p, color)
    }
    poly(Color(0xFF1E88F5), listOf(Offset(ax, ay), Offset(px, py), Offset(bx, by)))
    poly(Color(0xFF00D26A), listOf(Offset(ax, ay), Offset(tx, ty), Offset(px, py)))
    poly(Color(0xFFFF3D4F), listOf(Offset(bx, by), Offset(ux, uy), Offset(px, py)))
    poly(Color(0xFFFFC800), listOf(Offset(tx, ty), Offset(cx, cy), Offset(ux, uy), Offset(px, py)))
}
 
private fun DrawScope.drawSandCamera() {
    val s = size.minDimension
    drawRect(Brush.verticalGradient(listOf(Color(0xFFEDEEF2), Color(0xFFB4B6BE))))
    val body = Color(0xFF26272B)
    drawRoundRect(body, Offset(0.36f * s, 0.23f * s), Size(0.28f * s, 0.12f * s), CornerRadius(0.04f * s))
    drawRoundRect(body, Offset(0.17f * s, 0.30f * s), Size(0.66f * s, 0.44f * s), CornerRadius(0.09f * s))
    val lens = Offset(0.5f * s, 0.52f * s)
    drawCircle(Color.White, radius = 0.135f * s, center = lens, style = Stroke(0.04f * s))
    drawCircle(Color(0xFF4A4C55), radius = 0.095f * s, center = lens)
    drawCircle(Color(0xFFB8BAC4), radius = 0.022f * s, center = Offset(0.72f * s, 0.38f * s))
}
 
private fun DrawScope.drawSandMessages() {
    val s = size.minDimension
    drawRect(Brush.verticalGradient(listOf(Color(0xFF63E772), Color(0xFF1BAF38))))
    drawOval(Color.White, Offset(0.16f * s, 0.22f * s), Size(0.68f * s, 0.50f * s))
    val tail = Path()
    tail.moveTo(0.27f * s, 0.62f * s)
    tail.lineTo(0.21f * s, 0.79f * s)
    tail.lineTo(0.45f * s, 0.69f * s)
    tail.close()
    drawPath(tail, Color.White)
}

private fun DrawScope.drawSandPlanet() {
    val s = size.minDimension
    val c = center
    drawRect(Brush.verticalGradient(listOf(Color(0xFF8A6BFF), Color(0xFF3B2FD0))))
    drawCircle(
        brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFD3D6FF))),
        radius = 0.27f * s,
        center = c
    )
    rotate(-28f, pivot = c) {
        drawOval(
            color = Color.White.copy(alpha = 0.92f),
            topLeft = Offset(0.13f * s, 0.40f * s),
            size = Size(0.74f * s, 0.20f * s),
            style = Stroke(0.04f * s)
        )
    }
}
 
private fun DrawScope.drawSandCompass() {
    val s = size.minDimension
    val c = center
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE4E8F1))))
    drawCircle(
        brush = Brush.linearGradient(listOf(Color(0xFF63D3FF), Color(0xFF1F7BEF))),
        radius = 0.36f * s,
        center = c
    )
    drawCircle(Color.White.copy(alpha = 0.85f), radius = 0.31f * s, center = c, style = Stroke(0.014f * s))
    rotate(45f, pivot = c) {
        val red = Path()
        red.moveTo(c.x, c.y - 0.29f * s)
        red.lineTo(c.x - 0.065f * s, c.y)
        red.lineTo(c.x + 0.065f * s, c.y)
        red.close()
        drawPath(red, Color(0xFFFF3B30))
        val white = Path()
        white.moveTo(c.x, c.y + 0.29f * s)
        white.lineTo(c.x - 0.065f * s, c.y)
        white.lineTo(c.x + 0.065f * s, c.y)
        white.close()
        drawPath(white, Color.White)
    }
    drawCircle(Color.White, radius = 0.018f * s, center = c)
}
 
private fun DrawScope.drawSandYoutube() {
    val s = size.minDimension
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFEDEFF4))))
    drawRoundRect(Color(0xFFFF0000), Offset(0.18f * s, 0.28f * s), Size(0.64f * s, 0.44f * s), CornerRadius(0.13f * s))
    val tri = Path()
    tri.moveTo(0.44f * s, 0.40f * s)
    tri.lineTo(0.44f * s, 0.60f * s)
    tri.lineTo(0.62f * s, 0.50f * s)
    tri.close()
    drawPath(tri, Color.White)
}

@Composable
private fun SandCalendarFace(size: Dp) {
    val today = remember { java.time.LocalDate.now() }
    val dow = remember(today) {
        today.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.KOREAN)
    }
    // Dp -> sp (글꼴 배율의 영향을 받지 않게)
    val dowSize = with(LocalDensity.current) { (size * 0.17f).toSp() }
    val daySize = with(LocalDensity.current) { (size * 0.52f).toSp() }
    val dayLine = with(LocalDensity.current) { (size * 0.56f).toSp() }
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFEEF0F5)))),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(dow, fontSize = dowSize, fontWeight = FontWeight.SemiBold, color = Color(0xFFFF3B30))
        Text(
            today.dayOfMonth.toString(),
            fontSize = daySize,
            lineHeight = dayLine,
            fontWeight = FontWeight.Light,
            color = Color(0xFF1C1C1E)
        )
    }
}

private fun DrawScope.drawSandMail() {
    val s = size.minDimension
    drawRect(Brush.verticalGradient(listOf(Color(0xFF3FAEFF), Color(0xFF1572F0))))
    drawRoundRect(Color.White, Offset(0.18f * s, 0.30f * s), Size(0.64f * s, 0.42f * s), CornerRadius(0.07f * s))
    val flap = Path()
    flap.moveTo(0.21f * s, 0.34f * s)
    flap.lineTo(0.50f * s, 0.56f * s)
    flap.lineTo(0.79f * s, 0.34f * s)
    drawPath(flap, Color(0xFF2A86F5), style = Stroke(width = 0.035f * s, cap = StrokeCap.Round))
}

private fun DrawScope.drawSandClock() {
    val s = size.minDimension
    val c = center
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2E2E33), Color(0xFF050506))))
    for (i in 0 until 12) {
        val a = (i * PI / 6.0 - PI / 2.0).toFloat()
        val major = i % 3 == 0
        drawLine(
            color = Color.White.copy(alpha = if (major) 0.95f else 0.6f),
            start = Offset(c.x + cos(a) * s * (if (major) 0.32f else 0.35f), c.y + sin(a) * s * (if (major) 0.32f else 0.35f)),
            end = Offset(c.x + cos(a) * s * 0.41f, c.y + sin(a) * s * 0.41f),
            strokeWidth = s * (if (major) 0.026f else 0.014f),
            cap = StrokeCap.Round
        )
    }
    fun hand(deg: Float, len: Float, width: Float, color: Color) {
        val a = ((deg - 90f) * PI / 180.0).toFloat()
        drawLine(
            color,
            c,
            Offset(c.x + cos(a) * s * len, c.y + sin(a) * s * len),
            strokeWidth = s * width,
            cap = StrokeCap.Round
        )
    }
    hand(304.5f, 0.21f, 0.034f, Color.White)
    hand(54f, 0.31f, 0.024f, Color.White)
    hand(150f, 0.29f, 0.012f, Color(0xFFFF9F0A))
    drawCircle(Color(0xFFFF9F0A), radius = 0.026f * s, center = c)
}

private fun DrawScope.drawSandMaps() {
    val s = size.minDimension
    drawRect(Color(0xFFEFF2E6))
    drawCircle(Color(0xFF8AD07A), radius = 0.30f * s, center = Offset(0.14f * s, 0.84f * s))
    drawCircle(Color(0xFFB0DE92), radius = 0.22f * s, center = Offset(0.88f * s, 0.14f * s))
    drawLine(Color(0xFFFFD34D), Offset(0f, 0.76f * s), Offset(s, 0.30f * s), strokeWidth = 0.09f * s)
    drawLine(Color.White, Offset(0.12f * s, 0f), Offset(0.60f * s, s), strokeWidth = 0.06f * s)
    drawCircle(Color(0xFF2F7BF5), radius = 0.17f * s, center = center)
    drawCircle(Color.White, radius = 0.17f * s, center = center, style = Stroke(0.026f * s))
    val arrow = Path()
    arrow.moveTo(0.50f * s, 0.37f * s)
    arrow.lineTo(0.585f * s, 0.62f * s)
    arrow.lineTo(0.50f * s, 0.565f * s)
    arrow.lineTo(0.415f * s, 0.62f * s)
    arrow.close()
    drawPath(arrow, Color.White)
}

private fun DrawScope.drawSandWeather() {
    val s = size.minDimension
    drawRect(Brush.verticalGradient(listOf(Color(0xFF5AB2FF), Color(0xFF2A74EA))))
    drawCircle(Color(0xFFFFD23F), radius = 0.15f * s, center = Offset(0.64f * s, 0.34f * s))
    val cloud = Color.White.copy(alpha = 0.97f)
    drawCircle(cloud, radius = 0.13f * s, center = Offset(0.36f * s, 0.64f * s))
    drawCircle(cloud, radius = 0.18f * s, center = Offset(0.52f * s, 0.57f * s))
    drawCircle(cloud, radius = 0.125f * s, center = Offset(0.70f * s, 0.64f * s))
    drawRoundRect(cloud, Offset(0.26f * s, 0.62f * s), Size(0.56f * s, 0.17f * s), CornerRadius(0.085f * s))
}

private fun DrawScope.drawSandNotes() {
    val s = size.minDimension
    drawRect(Brush.verticalGradient(listOf(Color.White, Color(0xFFF1F2F5))))
    drawRect(Color(0xFFFFD43B), Offset.Zero, Size(size.width, 0.22f * s))
    for (y in listOf(0.38f, 0.52f, 0.66f, 0.80f)) {
        drawLine(Color(0xFFD9DCE2), Offset(0f, y * s), Offset(size.width, y * s), strokeWidth = 0.014f * s)
    }
}

private fun DrawScope.drawSandReminders() {
    val s = size.minDimension
    drawRect(Brush.verticalGradient(listOf(Color.White, Color(0xFFF1F2F5))))
    val dots = listOf(Color(0xFFFF3B30), Color(0xFF0A84FF), Color(0xFFFF9F0A))
    listOf(0.28f, 0.50f, 0.72f).forEachIndexed { i, y ->
        drawCircle(dots[i], radius = 0.055f * s, center = Offset(0.24f * s, y * s))
        drawLine(
            Color(0xFFD9DCE2),
            Offset(0.36f * s, y * s),
            Offset(0.84f * s, y * s),
            strokeWidth = 0.018f * s,
            cap = StrokeCap.Round
        )
    }
}
