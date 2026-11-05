@file:OptIn(ExperimentalFoundationApi::class)
 
package com.nova.os.launcher.ui.components
 
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.launcher.NovaViewModel
import com.nova.os.launcher.data.AppInfo
import com.nova.os.launcher.system.SystemActions
import com.nova.os.launcher.ui.theme.LocalNovaColors
import com.nova.os.launcher.ui.theme.LocalNovaMotion
 
/** Swallows touches so nothing underneath a full-screen layer receives them. */
fun Modifier.blockTouches(): Modifier = this.pointerInput(Unit) { detectTapGestures { } }
 
/** Press feedback (scale) without a ripple, used for icons, keys and tiles. */
@Composable
fun Pressable(
    modifier: Modifier = Modifier,
    scaleDown: Float = 0.93f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motion = LocalNovaMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = motion.spec(140),
        label = "pressScale",
    )
    Box(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(modifier)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        content = content,
    )
}
 
/** Frosted-glass surface. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalNovaColors.current
    val border = Brush.linearGradient(listOf(c.glassBorder, c.glassBorder.copy(alpha = c.glassBorder.alpha * 0.25f)))
    var m = modifier
        .clip(shape)
        .background(if (strong) c.glassStrong else c.glass)
        .border(1.dp, border, shape)
    if (onClick != null || onLongClick != null) {
        m = m.combinedClickable(onLongClick = onLongClick, onClick = onClick ?: {})
    }
    Box(m, content = content)
}
 
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    active: Boolean = false,
) {
    val c = LocalNovaColors.current
    Pressable(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (active) c.accent else c.glass)
            .border(1.dp, c.glassBorder, CircleShape),
        onClick = onClick,
    ) {
        Icon(
            icon,
            contentDescription,
            Modifier.align(Alignment.Center).size(size * 0.5f),
            tint = if (active) c.onAccent else c.text,
        )
    }
}
 
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 38.dp) {
    val c = LocalNovaColors.current
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.34f)).background(c.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.55f), tint = c.accent)
    }
}
 
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val c = LocalNovaColors.current
    Text(
        text,
        modifier = modifier.padding(start = 6.dp, top = 10.dp, bottom = 2.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        color = c.textSecondary,
    )
}
 
@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val c = LocalNovaColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
                else Modifier
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = c.textSecondary)
        }
        trailing()
    }
}
 
@Composable
fun SwitchRow(icon: ImageVector, title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(icon, title, subtitle, onClick = { onChange(!checked) }) {
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
 
@Composable
fun NovaSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalNovaColors.current
    val motion = LocalNovaMotion.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.glass)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            val bg by animateColorAsState(
                targetValue = if (sel) c.accent else Color.Transparent,
                animationSpec = motion.spec(220),
                label = "segBg",
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (sel) c.onAccent else c.text,
                    maxLines = 1,
                )
            }
        }
    }
}
 
@Composable
fun NovaStepper(value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircleIconButton(Icons.Rounded.Remove, "줄이기", { if (value > range.first) onChange(value - 1) }, size = 36.dp)
        Text(
            "$value",
            Modifier.widthIn(min = 36.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
        CircleIconButton(Icons.Rounded.Add, "늘리기", { if (value < range.last) onChange(value + 1) }, size = 36.dp)
    }
}
 
/** iOS-style horizontal pill slider (brightness / volume). */
@Composable
fun PillSlider(value: Float, onValueChange: (Float) -> Unit, icon: ImageVector, modifier: Modifier = Modifier) {
    val c = LocalNovaColors.current
    val onChange by rememberUpdatedState(onValueChange)
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(c.glass)
            .border(1.dp, c.glassBorder, shape)
            .pointerInput(Unit) {
                detectTapGestures { o -> onChange((o.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(value.coerceIn(0f, 1f))
                .background(c.accent.copy(alpha = if (c.isDark) 0.85f else 0.92f))
        )
        Icon(
            icon,
            null,
            Modifier.padding(start = 18.dp).size(24.dp).align(Alignment.CenterStart),
            tint = if (value > 0.1f) c.onAccent else c.text,
        )
    }
}
 
/** Standard frame for full-screen tools: glass sheet, back button, title. */
@Composable
fun ToolScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalNovaColors.current
    Column(
        modifier
            .fillMaxSize()
            .background(c.sheet)
            .blockTouches()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로", onBack)
            Spacer(Modifier.width(14.dp))
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            actions()
        }
        content()
    }
}
 
/** App icon with label, press animation and long-press context menu. */
@Composable
fun AppIconCell(
    app: AppInfo,
    vm: NovaViewModel,
    iconSize: Dp,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
    dock: Boolean = false,
) {
    val c = LocalNovaColors.current
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Pressable(onClick = { vm.launchApp(app) }, onLongClick = { menuOpen = true }) {
            Column(
                Modifier.padding(horizontal = 2.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val sandKind = remember(app.packageName, app.label) { sandKindOf(app) }
                if (sandKind != null) {
                    // 시안 기본 앱: 시안풍으로 직접 그린 아이콘
                    SandAppIcon(sandKind, iconSize, dock = dock)
                } else {
                    // 그 외 앱: 실제 아이콘을 유지하고 둥근 사각형 + 그림자만 입힘
                    val iconShape = RoundedCornerShape(iconSize * 0.27f)
                    Image(
                        bitmap = app.icon,
                        contentDescription = app.label,
                        modifier = Modifier
                            .size(iconSize)
                            .shadow(4.dp, iconShape, clip = false, ambientColor = Color(0x33000000), spotColor = Color(0x40000000))
                            .clip(iconShape),
                        filterQuality = FilterQuality.Medium,
                    )
                }
                if (showLabel) {
                    Text(
                        app.label,
                        modifier = Modifier.padding(top = 6.dp).widthIn(max = iconSize + 22.dp),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        style = LocalTextStyle.current.copy(
                            shadow = if (c.isDark) Shadow(Color(0x66000000), blurRadius = 6f) else null
                        ),
                    )
                }
            }
        }
        AppContextMenu(app, vm, expanded = menuOpen, onDismiss = { menuOpen = false })
    }
}
 
@Composable
fun AppContextMenu(app: AppInfo, vm: NovaViewModel, expanded: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val isFav = app.packageName in settings.favorites
    val isDock = app.packageName in settings.dockApps
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(if (isFav) "즐겨찾기에서 제거" else "즐겨찾기에 추가") },
            leadingIcon = { Icon(if (isFav) Icons.Rounded.Star else Icons.Rounded.StarBorder, null) },
            onClick = { vm.toggleFavorite(app.packageName); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text(if (isDock) "독에서 제거" else "독에 추가") },
            leadingIcon = { Icon(Icons.Rounded.Dashboard, null) },
            onClick = { vm.toggleDock(app.packageName); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text("앱 정보") },
            leadingIcon = { Icon(Icons.Rounded.Info, null) },
            onClick = { SystemActions.openAppInfo(context, app.packageName); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text("앱 숨기기") },
            leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null) },
            onClick = { vm.setHidden(app.packageName, true); onDismiss() },
        )
        if (!app.isSystem) {
            DropdownMenuItem(
                text = { Text("삭제") },
                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                onClick = { SystemActions.uninstall(context, app.packageName); onDismiss() },
            )
        }
    }
}
 
/** Small helper used by several screens for a tinted pill label. */
@Composable
fun InfoPill(text: String, modifier: Modifier = Modifier) {
    val c = LocalNovaColors.current
    Box(
        modifier.clip(RoundedCornerShape(50)).background(c.glass).padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 12.sp, color = c.textSecondary)
    }
}
 
