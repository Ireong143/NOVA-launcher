package com.nova.os.launcher.sand

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

/** 새 알림이 올 때 홈 위로 내려오는 유리 배너. */
@Composable
fun SandBannerCard(n: SandNotif, onTap: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalNovaColors.current
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 14.dp), strong = true, onClick = onTap) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row {
                    Text(n.appName, fontSize = 12.sp, color = c.accent, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Text("지금", fontSize = 12.sp, color = c.textSecondary)
                }
                if (n.title.isNotBlank()) Text(n.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (n.text.isNotBlank()) Text(n.text, fontSize = 13.sp, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            CircleIconButton(Icons.Rounded.Close, "닫기", onDismiss, size = 34.dp)
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
                        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
