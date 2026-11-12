package com.nova.os.launcher.sand

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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

/** 알림 앱의 아이콘 모양(배경 없이)과 그 아이콘에서 뽑은 대표색. */
class SandNotifIcon(val glyph: ImageBitmap, val tint: Color)

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

    private fun fresh(d: Drawable): Drawable = d.constantState?.newDrawable()?.mutate() ?: d

    private fun render(d: Drawable, px: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val dd = fresh(d)
        dd.setBounds(0, 0, px, px)
        dd.draw(Canvas(bmp))
        return bmp
    }

    /** 채도와 밝기가 높은 픽셀을 더 크게 반영한 평균색. 색이 거의 없으면 전체 평균. */
    private fun dominant(b: Bitmap): Color {
        var r = 0.0
        var g = 0.0
        var bl = 0.0
        var w = 0.0
        var ar = 0.0
        var ag = 0.0
        var ab = 0.0
        var n = 0
        val hsv = FloatArray(3)
        for (y in 0 until b.height) {
            for (x in 0 until b.width) {
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

    private fun make(context: Context, pkg: String): SandNotifIcon? {
        val d = context.packageManager.getApplicationIcon(pkg)
        val tint = dominant(render(d, 48))
        val size = 144
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val fg = if (Build.VERSION.SDK_INT >= 26 && d is AdaptiveIconDrawable) d.foreground else null
        if (fg != null) {
            // 어댑티브 아이콘은 뒷배경을 빼고 앞면(모양)만 그려서, 아이콘 배경이 알림 색이 되게 한다.
            val o = (size * 0.125f).toInt()
            val f = fresh(fg)
            f.setBounds(-o, -o, size + o, size + o)
            f.draw(cv)
        } else {
            val o = (size * 0.1f).toInt()
            val f = fresh(d)
            f.setBounds(o, o, size - o, size - o)
            f.draw(cv)
        }
        return SandNotifIcon(bmp.asImageBitmap(), tint)
    }
}

/** 알림 아이콘 판: 알림 배경과 같은 계열의 색 위에 앱 아이콘 모양만 올린다. */
@Composable
fun SandNotifIconTile(icon: SandNotifIcon?, size: Dp) {
    val c = LocalNovaColors.current
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background((icon?.tint ?: c.accent).copy(alpha = 0.30f)),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Image(bitmap = icon.glyph, contentDescription = null, modifier = Modifier.size(size))
        } else {
            Icon(Icons.Rounded.Notifications, null, Modifier.size(size * 0.55f), tint = c.text)
        }
    }
}

/** 새 알림이 올 때 홈 위로 내려오는 작은 유리 배너. 배경은 알림 앱 아이콘의 색을 따라간다. */
@Composable
fun SandBannerCard(n: SandNotif, onTap: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalNovaColors.current
    val ctx = LocalContext.current
    val icon = remember(n.pkg) { SandNotifIcons.of(ctx, n.pkg) }
    val tint = icon?.tint ?: c.accent
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp)
            .clip(shape)
            .background(c.panel)
            .background(tint.copy(alpha = 0.34f))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)))
            .border(0.6.dp, Color.White.copy(alpha = 0.18f), shape)
            .clickable { onTap() }
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SandNotifIconTile(icon, 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n.appName, fontSize = 11.sp, color = c.text.copy(alpha = 0.75f), fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Text("지금", fontSize = 11.sp, color = c.textSecondary)
            }
            if (n.title.isNotBlank()) {
                Text(n.title, fontSize = 13.sp, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (n.text.isNotBlank()) {
                Text(n.text, fontSize = 12.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        CircleIconButton(Icons.Rounded.Close, "닫기", onDismiss, size = 28.dp)
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
