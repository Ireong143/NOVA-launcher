package com.nova.os.launcher.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.launcher.NovaViewModel
import com.nova.os.launcher.system.SystemActions
import com.nova.os.launcher.ui.components.GlassCard
import com.nova.os.launcher.ui.components.SectionLabel
import com.nova.os.launcher.ui.components.SettingRow
import com.nova.os.launcher.ui.components.ToolScaffold
import com.nova.os.launcher.ui.theme.LocalNovaColors

@Suppress("DEPRECATION")
@Composable
fun AboutScreen(vm: NovaViewModel) {
    val c = LocalNovaColors.current
    val context = LocalContext.current
    val apps by vm.allApps.collectAsState()
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
    val density = context.resources.displayMetrics

    ToolScaffold("NOVA OS 정보", onBack = { vm.back() }) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF7C3AED), Color(0xFF06B6D4)))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("N", fontSize = 54.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text("NOVA OS", fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Text("버전 $version", fontSize = 14.sp, color = c.textSecondary)
            Text(
                "프리미엄 런처 · Kotlin + Jetpack Compose",
                fontSize = 13.sp,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
            )

            SectionLabel("기기 정보")
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoRow("기기", "${Build.MANUFACTURER} ${Build.MODEL}")
                    InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    InfoRow("화면", "${density.widthPixels} × ${density.heightPixels} · ${density.densityDpi}dpi")
                    InfoRow("설치된 앱", "${apps.size}개")
                }
            }

            SectionLabel("제스처")
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoRow("위로 스와이프", "앱 서랍")
                    InfoRow("아래로 스와이프", "제어 센터")
                    InfoRow("좌우 스와이프", "홈 페이지 이동")
                    InfoRow("빈 곳 길게 누르기", "빠른 메뉴")
                    InfoRow("앱 길게 누르기", "즐겨찾기 · 독 · 정보 · 삭제")
                }
            }

            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    SettingRow(
                        Icons.Rounded.Home, "기본 홈 앱으로 설정", "시스템 설정 화면이 열려요",
                        onClick = { SystemActions.openDefaultHomeSettings(context) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val c = LocalNovaColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = c.textSecondary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
    }
}
