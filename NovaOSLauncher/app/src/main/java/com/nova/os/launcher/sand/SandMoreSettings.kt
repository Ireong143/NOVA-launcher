package com.nova.os.launcher.sand

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.launcher.NovaViewModel
import com.nova.os.launcher.data.Overlay
import com.nova.os.launcher.ui.components.NovaSegmented
import com.nova.os.launcher.ui.components.NovaStepper
import com.nova.os.launcher.ui.components.PillSlider
import com.nova.os.launcher.ui.components.SectionLabel
import com.nova.os.launcher.ui.components.SettingRow
import com.nova.os.launcher.ui.components.SwitchRow
import com.nova.os.launcher.ui.components.ToolScaffold
import com.nova.os.launcher.ui.theme.LocalNovaColors

/** 기존 설정 화면에 없는 SAND 전용 옵션 모음. */
@Composable
fun SandMoreSettingsScreen(vm: NovaViewModel, onClose: () -> Unit, onOpen: (SandPage) -> Unit) {
    val c = LocalNovaColors.current
    ToolScaffold("더 많은 설정", onClose) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionLabel("홈 화면")
            SettingRow(Icons.Rounded.Apps, "홈 앱 줄 수", "첫 홈 화면에 보이는 앱 줄 (2~4)") {
                NovaStepper(SandConfig.homeRows, 2..4) { SandConfig.edit { homeRows = it } }
            }
            Text("시계 위젯 모양", fontSize = 14.sp, color = c.textSecondary, modifier = Modifier.padding(start = 6.dp))
            NovaSegmented(
                listOf("큰 카드", "아날로그", "대형", "작은 바"), SandConfig.clockLook,
                { SandConfig.edit { clockLook = it } }
            )
            Text("유리 질감 진하기", fontSize = 14.sp, color = c.textSecondary, modifier = Modifier.padding(start = 6.dp))
            PillSlider((SandConfig.glass - 0.4f) / 1.2f, { SandConfig.edit { glass = 0.4f + it * 1.2f } }, Icons.Rounded.Opacity)
            SwitchRow(Icons.Rounded.Apps, "앱 라이브러리 페이지", "홈에서 왼쪽으로 넘기면 앱이 종류별 폴더로 정리돼 보여요", SandConfig.libraryOn) { SandConfig.edit { libraryOn = it } }
            SettingRow(Icons.Rounded.Refresh, "SAND 기본 배치로 정리", "메시지, 캘린더, 갤러리 … 보드와 같은 배치로 되돌려요", onClick = {
                vm.applySandLayout()
            })

            SectionLabel("상태 줄")
            SwitchRow(Icons.Rounded.VolumeUp, "소리 상태", "소리·진동·무음 아이콘", SandConfig.showSound) { SandConfig.edit { showSound = it } }
            SwitchRow(Icons.Rounded.Tune, "모바일 데이터", "신호 막대", SandConfig.showCell) { SandConfig.edit { showCell = it } }
            SwitchRow(Icons.Rounded.Wifi, "와이파이", "연결과 신호 세기", SandConfig.showWifi) { SandConfig.edit { showWifi = it } }
            SwitchRow(Icons.Rounded.Notifications, "알림 종", "새 알림이 있으면 점 표시, 누르면 알림 창", SandConfig.showBell) { SandConfig.edit { showBell = it } }

            SectionLabel("알림")
            SwitchRow(Icons.Rounded.Notifications, "알림 배너", "새 알림이 오면 홈 위에 유리 배너로 표시", SandConfig.bannerOn) { SandConfig.edit { bannerOn = it } }
            SettingRow(Icons.Rounded.Notifications, "알림 창 열기", "알림 접근 허용 여부도 여기서 확인해요", onClick = { onOpen(SandPage.NOTIFICATIONS) })

            SectionLabel("캘린더")
            SwitchRow(Icons.Rounded.Tune, "한 주의 시작을 월요일로", null, SandConfig.mondayFirst) { SandConfig.edit { mondayFirst = it } }

            SectionLabel("기타")
            SettingRow(Icons.Rounded.Settings, "기본 설정 열기", "배경화면, 테마, 아이콘 크기 등", onClick = {
                onClose()
                vm.open(Overlay.Settings)
            })
            SettingRow(Icons.Rounded.Refresh, "SAND 옵션 초기화", "이 화면의 값을 기본으로 되돌려요", onClick = { SandConfig.resetDefaults() })
            Spacer(Modifier.height(24.dp))
        }
    }
}
