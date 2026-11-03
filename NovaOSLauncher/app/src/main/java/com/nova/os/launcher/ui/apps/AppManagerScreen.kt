package com.nova.os.launcher.ui.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.launcher.NovaViewModel
import com.nova.os.launcher.data.AppRepository
import com.nova.os.launcher.data.SortMode
import com.nova.os.launcher.system.SystemActions
import com.nova.os.launcher.ui.components.CircleIconButton
import com.nova.os.launcher.ui.components.GlassCard
import com.nova.os.launcher.ui.components.NovaSegmented
import com.nova.os.launcher.ui.components.ToolScaffold
import com.nova.os.launcher.ui.theme.LocalNovaColors

@Composable
fun AppManagerScreen(vm: NovaViewModel) {
    val c = LocalNovaColors.current
    val context = LocalContext.current
    val all by vm.allApps.collectAsState()
    val s by vm.settings.collectAsState()
    var filter by remember { mutableIntStateOf(0) }

    val sorted = remember(all) { AppRepository.sortApps(all, SortMode.NAME_ASC, emptyMap()) }
    val list = remember(sorted, filter, s.hidden) {
        when (filter) {
            1 -> sorted.filter { !it.isSystem }
            2 -> sorted.filter { it.isSystem }
            3 -> sorted.filter { it.packageName in s.hidden }
            else -> sorted
        }
    }

    ToolScaffold("앱 관리", onBack = { vm.back() }) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NovaSegmented(
                options = listOf("전체", "설치한 앱", "시스템", "숨김"),
                selected = filter,
                onSelect = { filter = it },
            )
            Text(
                "총 ${all.size}개 · 설치한 앱 ${all.count { !it.isSystem }}개 · 시스템 ${all.count { it.isSystem }}개 · 숨김 ${s.hidden.size}개",
                fontSize = 12.sp,
                color = c.textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(list, key = { it.key }) { app ->
                val hidden = app.packageName in s.hidden
                GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(app.icon, app.label, Modifier.size(44.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                app.packageName + if (app.versionName.isNotBlank()) " · v${app.versionName}" else "",
                                fontSize = 11.sp,
                                color = c.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        CircleIconButton(
                            Icons.Rounded.Info, "앱 정보",
                            { SystemActions.openAppInfo(context, app.packageName) }, size = 36.dp,
                        )
                        Spacer(Modifier.width(6.dp))
                        CircleIconButton(
                            if (hidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            if (hidden) "다시 표시" else "숨기기",
                            { vm.setHidden(app.packageName, !hidden) }, size = 36.dp, active = hidden,
                        )
                        if (!app.isSystem) {
                            Spacer(Modifier.width(6.dp))
                            CircleIconButton(
                                Icons.Rounded.Delete, "삭제",
                                { SystemActions.uninstall(context, app.packageName) }, size = 36.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}
