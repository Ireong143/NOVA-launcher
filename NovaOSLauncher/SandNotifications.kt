package com.nova.os.launcher.sand

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.nova.os.launcher.ui.components.CircleIconButton
import com.nova.os.launcher.ui.components.GlassCard
import com.nova.os.launcher.ui.components.ToolScaffold
import com.nova.os.launcher.ui.theme.LocalNovaColors
import kotlinx.coroutines.delay

class SandNotif(
    val key: String,
    val pkg: String,
    val appName: String,
    val title: String,
    val text: String,
    val whenMs: Long,
    val intent: PendingIntent?,
    val clearable: Boolean,
)

/** 알림 접근 권한으로 받은 알림 목록(같은 프로세스에서 서비스와 화면이 공유). */
object SandNotifications {
    val items = mutableStateListOf<SandNotif>()
    var banner by mutableStateOf<SandNotif?>(null)

    @Volatile
    var service: SandNotificationService? = null

    fun isEnabled(context: Context): Boolean = try {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    } catch (e: Exception) {
        false
    }

    private fun convert(context: Context, sbn: StatusBarNotification): SandNotif? {
        if (sbn.packageName == context.packageName) return null
        val n = sbn.notification ?: return null
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return null
        if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return null
        val extras = n.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras?.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras?.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        val label = try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: Exception) {
            sbn.packageName
        }
        return SandNotif(sbn.key, sbn.packageName, label, title, text, sbn.postTime, n.contentIntent, sbn.isClearable)
    }

    fun sync(s: SandNotificationService) {
        items.clear()
        try {
            val list = ArrayList<SandNotif>()
            for (sbn in s.activeNotifications) {
                val n = convert(s, sbn)
                if (n != null) list.add(n)
            }
            list.sortByDescending { it.whenMs }
            items.addAll(list)
        } catch (e: Exception) {
            // 권한이 막 사라졌을 수 있음
        }
    }

    fun onPosted(context: Context, sbn: StatusBarNotification) {
        val n = convert(context, sbn) ?: return
        items.removeAll { it.key == n.key }
        items.add(0, n)
        if (SandConfig.bannerOn) banner = n
    }

    fun onRemoved(key: String) {
        items.removeAll { it.key == key }
        if (banner?.key == key) banner = null
    }

    fun open(n: SandNotif) {
        try {
            n.intent?.send()
        } catch (e: Exception) {
            // 이미 취소된 알림
        }
        dismiss(n)
    }

    fun dismiss(n: SandNotif) {
        try {
            service?.cancelNotification(n.key)
        } catch (e: Exception) {
            // 무시
        }
        items.removeAll { it.key == n.key }
        if (banner?.key == n.key) banner = null
    }

    fun clearAll() {
        try {
            service?.cancelAllNotifications()
        } catch (e: Exception) {
            // 무시
        }
        items.clear()
        banner = null
    }
}

/** 시스템이 알림을 전달해 주는 서비스. AndroidManifest.xml에 등록되어 있어야 한다. */
class SandNotificationService : NotificationListenerService() {
    override fun onListenerConnected() {
        SandNotifications.service = this
        SandNotifications.sync(this)
    }

    override fun onListenerDisconnected() {
        if (SandNotifications.service === this) SandNotifications.service = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        SandNotifications.onPosted(this, sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        SandNotifications.onRemoved(sbn.key)
    }

    override fun onDestroy() {
        if (SandNotifications.service === this) SandNotifications.service = null
        super.onDestroy()
    }
}

/** 홈 상태 줄의 종 아이콘(알림 개수 배지 포함). */
@Composable
fun SandBell(tint: Color, onClick: () -> Unit) {
    val count = SandNotifications.items.size
    Box(
        Modifier
            .clip(CircleShape)
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Icon(Icons.Rounded.Notifications, "알림", Modifier.size(18.dp), tint = tint)
        if (count > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF453A))
            )
        }
    }
}

/**
 * 알림 앱의 아이콘 전체(full)와, 그 아이콘에서 읽은 배경 그라데이션 두 색.
 * anti가 true면 왼쪽 아래 → 오른쪽 위로, false면 왼쪽 위 → 오른쪽 아래로 흐른다.
 * light는 배경이 밝아서 글자를 어둡게 써야 한다는 뜻.
 */
class SandNotifIcon(
    val full: ImageBitmap,
    val c1: Color,
    val c2: Color,
    val anti: Boolean,
    val light: Boolean,
)

object SandNotifIcons {
    private val cache = HashMap<String, SandNotifIcon>()

    fun of(context: Context, pkg: String): SandNotifIcon? {
        val hit = cache[pkg]
        if (hit != null) return hit
        val made = try {
            make(context, pkg)
        } catch (e: Exception) {
            null
        }
        if (made != null) cache[pkg] = made
        return made
    }

    private fun render(d: Drawable, px: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val dd = d.constantState?.newDrawable()?.mutate() ?: d
        dd.setBounds(0, 0, px, px)
        dd.draw(Canvas(bmp))
        return bmp
    }

    /** 사각형 영역에서 채도·밝기가 큰 픽셀을 더 크게 반영한 평균색. 색이 거의 없으면(흰색·회색 아이콘) 단순 평균. */
    private fun region(b: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int): Color {
        var r = 0.0
        var g = 0.0
        var bl = 0.0
        var w = 0.0
        var ar = 0.0
        var ag = 0.0
        var ab = 0.0
        var n = 0
        val hsv = FloatArray(3)
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val p = b.getPixel(x, y)
                if (android.graphics.Color.alpha(p) < 200) continue
                val pr = android.graphics.Color.red(p)
                val pg = android.graphics.Color.green(p)
                val pb = android.graphics.Color.blue(p)
                android.graphics.Color.colorToHSV(p, hsv)
                val wt = (hsv[1] * hsv[2]).toDouble()
                r += pr * wt
                g += pg * wt
                bl += pb * wt
                w += wt
                ar += pr
                ag += pg
                ab += pb
                n++
            }
        }
        if (n == 0) return Color(0xFF8E8E93)
        return if (w > n * 0.08) {
            Color((r / w).toInt(), (g / w).toInt(), (bl / w).toInt())
        } else {
            Color((ar / n).toInt(), (ag / n).toInt(), (ab / n).toInt())
        }
    }

    private fun dist(a: Color, b: Color): Float {
        val dr = (a.red - b.red) * 255f
        val dg = (a.green - b.green) * 255f
        val db = (a.blue - b.blue) * 255f
        return Math.sqrt((dr * dr + dg * dg + db * db).toDouble()).toFloat()
    }

    private fun adjust(c: Color, vMul: Float, sMul: Float): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toArgb(), hsv)
        hsv[1] = (hsv[1] * sMul).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] * vMul).coerceIn(0f, 1f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    private fun make(context: Context, pkg: String): SandNotifIcon? {
        val d = context.packageManager.getApplicationIcon(pkg)
        val size = 144
        val bmp = render(d, size)
        val q = (size * 0.45f).toInt()
        val far = size - q
        val tl = region(bmp, 0, 0, q, q)
        val br = region(bmp, far, far, size, size)
        val bl = region(bmp, 0, far, q, size)
        val tr = region(bmp, far, 0, size, q)
        val dMain = dist(tl, br)
        val dAnti = dist(bl, tr)
        val c1: Color
        val c2: Color
        var anti = false
        if (maxOf(dMain, dAnti) > 70f) {
            // 아이콘 자체에 그라데이션이 있으면(예: 인스타그램) 가장 색이 달라지는 대각선 방향을 그대로 따른다.
            if (dMain >= dAnti) {
                c1 = tl
                c2 = br
            } else {
                c1 = bl
                c2 = tr
                anti = true
            }
        } else {
            // 단색 아이콘(예: 듀오링고 초록)은 같은 색을 밝게→어둡게 이어서 입체감만 준다.
            val base = region(bmp, 0, 0, size, size)
            c1 = adjust(base, 1.10f, 0.95f)
            c2 = adjust(base, 0.74f, 1.0f)
        }
        val mr = (c1.red + c2.red) / 2f
        val mg = (c1.green + c2.green) / 2f
        val mb = (c1.blue + c2.blue) / 2f
        val light = (0.299f * mr + 0.587f * mg + 0.114f * mb) > 0.66f
        return SandNotifIcon(bmp.asImageBitmap(), c1, c2, anti, light)
    }
}

/** 알림 앱 아이콘(그대로). 아이콘을 못 찾으면 종 모양. */
@Composable
fun SandNotifIconTile(icon: SandNotifIcon?, size: Dp, ring: Boolean = false) {
    val c = LocalNovaColors.current
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .then(if (ring) Modifier.border(0.6.dp, Color.White.copy(alpha = 0.35f), shape) else Modifier)
            .background(if (icon == null) c.glass else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Image(bitmap = icon.full, contentDescription = null, modifier = Modifier.size(size))
        } else {
            Icon(Icons.Rounded.Notifications, null, Modifier.size(size * 0.55f), tint = c.text)
        }
    }
}

/** 새 알림이 올 때 홈 위로 내려오는 작은 배너. 배경은 알림 앱 아이콘의 색·그라데이션을 그대로 따른다. */
@Composable
fun SandBannerCard(n: SandNotif, onTap: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalNovaColors.current
    val ctx = LocalContext.current
    val icon = remember(n.pkg) { SandNotifIcons.of(ctx, n.pkg) }
    val shape = RoundedCornerShape(22.dp)
    val c1 = icon?.c1 ?: c.panel
    val c2 = icon?.c2 ?: c.panel
    val onBg = if (icon != null && icon.light) Color(0xFF1C1C1E) else Color.White
    val bg = if (icon != null && icon.anti) {
        Brush.linearGradient(listOf(c1, c2), start = Offset(0f, 400f), end = Offset(900f, 0f))
    } else {
        Brush.linearGradient(listOf(c1, c2), start = Offset(0f, 0f), end = Offset(900f, 400f))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp)
            .clip(shape)
            .background(bg)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent)))
            .border(0.6.dp, Color.White.copy(alpha = 0.22f), shape)
            .clickable { onTap() }
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SandNotifIconTile(icon, 36.dp, ring = true)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n.appName, fontSize = 11.sp, color = onBg.copy(alpha = 0.78f), fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Text("지금", fontSize = 11.sp, color = onBg.copy(alpha = 0.6f))
            }
            if (n.title.isNotBlank()) {
                Text(n.title, fontSize = 13.sp, color = onBg, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (n.text.isNotBlank()) {
                Text(n.text, fontSize = 12.sp, color = onBg.copy(alpha = 0.82f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(onBg.copy(alpha = 0.16f)).clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Close, "닫기", Modifier.size(16.dp), tint = onBg)
        }
    }
}

@Composable
fun SandNotificationScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalNovaColors.current
    var check by remember { mutableIntStateOf(0) }
    val enabled = remember(check) { SandNotifications.isEnabled(ctx) }

    // 설정에서 돌아오면 다시 확인
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            check++
        }
    }

    ToolScaffold(
        "알림", onClose,
        actions = {
            if (enabled && SandNotifications.items.isNotEmpty()) {
                CircleIconButton(Icons.Rounded.Delete, "모두 지우기", { SandNotifications.clearAll() })
            }
        }
    ) {
        if (!enabled) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("알림 접근을 허용해 주세요", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "SAND OS가 다른 앱의 알림을 모아서 보여주려면 설정에서 '알림 접근'을 한 번 켜야 해요. 알림 내용은 이 기기 밖으로 나가지 않아요.",
                    fontSize = 14.sp, color = c.textSecondary, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))
                SandButton(
                    "알림 접근 설정 열기",
                    {
                        try {
                            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) {
                            // 설정 화면을 열 수 없음
                        }
                    }
                )
            }
        } else if (SandNotifications.items.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("새 알림이 없어요", color = c.textSecondary, fontSize = 15.sp)
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(SandNotifications.items.toList(), key = { it.key }) { n ->
                    GlassCard(Modifier.fillMaxWidth(), onClick = { SandNotifications.open(n) }) {
                        Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            SandNotifIconTile(remember(n.pkg) { SandNotifIcons.of(ctx, n.pkg) }, 38.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(n.appName, fontSize = 12.sp, color = c.accent, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Spacer(Modifier.width(8.dp))
                                    Text(sandAgo(n.whenMs), fontSize = 12.sp, color = c.textSecondary)
                                }
                                if (n.title.isNotBlank()) Text(n.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (n.text.isNotBlank()) Text(n.text, fontSize = 13.sp, color = c.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                            if (n.clearable) {
                                CircleIconButton(Icons.Rounded.Close, "지우기", { SandNotifications.dismiss(n) }, size = 34.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}
