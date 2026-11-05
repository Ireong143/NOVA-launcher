package com.nova.os.launcher.ui.drawer
 
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.launcher.NovaViewModel
import com.nova.os.launcher.data.SearchUtil
import com.nova.os.launcher.data.SortMode
import com.nova.os.launcher.system.SystemActions
import com.nova.os.launcher.ui.components.AppIconCell
import com.nova.os.launcher.ui.components.CircleIconButton
import com.nova.os.launcher.ui.components.GlassCard
import com.nova.os.launcher.ui.components.Pressable
import com.nova.os.launcher.ui.components.blockTouches
import com.nova.os.launcher.ui.theme.LocalNovaColors
import kotlinx.coroutines.delay
 
@Composable
fun AppDrawer(vm: NovaViewModel) {
    val c = LocalNovaColors.current
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val apps by vm.drawerApps.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }
 
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { SearchUtil.matches(it, query) }
    }
 
    LaunchedEffect(Unit) {
        if (vm.drawerAutoFocus) {
            delay(350)
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // not attached yet
            }
        }
    }
 
    // 홈 화면과 같은 유리 질감의 둥근 시트 (위쪽 모서리가 둥근 반투명 패널)
    val sheetShape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
 
    Column(
        Modifier
            .fillMaxSize()
            .blockTouches()
            .statusBarsPadding()
            .padding(top = 6.dp)
            .clip(sheetShape)
            .background(
                Brush.verticalGradient(
                    listOf(c.sheet.copy(alpha = 0.70f), c.sheet.copy(alpha = 0.88f))
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(listOf(c.glassBorder, Color.Transparent)),
                sheetShape
            )
            .blockTouches()
    ) {
        // Drag handle: swipe down to close.
        Box(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total > 60.dp.toPx()) vm.close() },
                        onDragCancel = { total = 0f },
                        onVerticalDrag = { _, dy -> total += dy },
                    )
                }
                .clickable { vm.close() },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(c.text.copy(alpha = 0.35f)))
        }
 
        // Search + sort
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassCard(Modifier.weight(1f), shape = RoundedCornerShape(50), strong = true) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, null, Modifier.size(22.dp), tint = c.textSecondary)
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = c.text, fontSize = 16.sp),
                        cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                when {
                                    filtered.size == 1 -> vm.launchApp(filtered[0])
                                    filtered.isEmpty() && query.isNotBlank() -> SystemActions.webSearch(context, query)
                                }
                                keyboard?.hide()
                            }
                        ),
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) {
                                    Text("앱 검색 (초성 검색 가능)", color = c.textSecondary, fontSize = 16.sp)
                                }
                                inner()
                            }
                        },
                    )
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Rounded.Close,
                            "지우기",
                            Modifier.size(22.dp).clip(CircleShape).clickable { query = "" },
                            tint = c.textSecondary,
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Box {
                CircleIconButton(Icons.Rounded.SortByAlpha, "정렬", { sortMenu = true }, size = 48.dp)
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortMode.values().forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            leadingIcon = {
                                if (mode == settings.sort) Icon(Icons.Rounded.Check, null)
                                else Spacer(Modifier.size(24.dp))
                            },
                            onClick = {
                                vm.update { it.copy(sort = mode) }
                                sortMenu = false
                            },
                        )
                    }
                }
            }
        }
 
        Text(
            "앱 ${filtered.size}개 · ${settings.sort.label}",
            Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
            fontSize = 12.sp,
            color = c.textSecondary,
        )
 
        LazyVerticalGrid(
            columns = GridCells.Fixed(settings.drawerColumns.coerceIn(3, 6)),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(),
        ) {
            items(filtered, key = { it.key }) { app ->
                AppIconCell(app, vm, settings.iconSizeDp.dp, settings.showLabels, Modifier.fillMaxWidth())
            }
            if (filtered.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            if (apps.isEmpty()) "앱을 불러오는 중이에요…" else "'$query'와 일치하는 앱이 없어요",
                            color = c.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                        if (apps.isNotEmpty() && query.isNotBlank()) {
                            Pressable(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(c.accent),
                                onClick = { SystemActions.webSearch(context, query) },
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Rounded.Language, null, Modifier.size(20.dp), tint = c.onAccent)
                                    Spacer(Modifier.width(8.dp))
                                    Text("웹에서 검색", color = c.onAccent, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
 
