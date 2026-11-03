package com.nova.os.launcher.ui.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.launcher.NovaViewModel
import com.nova.os.launcher.data.Note
import com.nova.os.launcher.system.SystemActions
import com.nova.os.launcher.ui.components.CircleIconButton
import com.nova.os.launcher.ui.components.GlassCard
import com.nova.os.launcher.ui.components.ToolScaffold
import com.nova.os.launcher.ui.theme.LocalNovaColors
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NotesScreen(vm: NovaViewModel) {
    var editingId by remember { mutableStateOf<Long?>(null) }
    val id = editingId
    if (id == null) {
        NotesList(vm, onOpen = { editingId = it })
    } else {
        NoteEditor(vm, id, onClose = { editingId = null })
    }
}

@Composable
private fun NotesList(vm: NovaViewModel, onOpen: (Long) -> Unit) {
    val c = LocalNovaColors.current
    val notes by vm.notes.collectAsState()
    val visible = remember(notes) {
        notes.filter { it.text.isNotBlank() }
            .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
    }
    val formatter = remember { DateTimeFormatter.ofPattern("M월 d일 HH:mm") }

    ToolScaffold(
        title = "메모",
        onBack = { vm.back() },
        actions = { CircleIconButton(Icons.Rounded.Add, "새 메모", { onOpen(vm.createNote()) }, active = true) },
    ) {
        if (visible.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.EditNote, null, Modifier.size(56.dp), tint = c.textSecondary)
                Spacer(Modifier.height(12.dp))
                Text("아직 메모가 없어요", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "오른쪽 위 + 버튼으로 첫 메모를 만들어 보세요",
                    fontSize = 13.sp,
                    color = c.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visible, key = { it.id }) { note ->
                    GlassCard(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        onClick = { onOpen(note.id) },
                        onLongClick = {
                            vm.togglePin(note.id)
                            vm.toast(if (note.pinned) "고정을 해제했어요" else "메모를 고정했어요")
                        },
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (note.pinned) {
                                    Icon(Icons.Rounded.PushPin, "고정됨", Modifier.size(16.dp), tint = c.accent)
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    note.title,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (note.preview.isNotEmpty()) {
                                Text(
                                    note.preview,
                                    fontSize = 14.sp,
                                    color = c.textSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                Instant.ofEpochMilli(note.updatedAt).atZone(ZoneId.systemDefault()).format(formatter),
                                fontSize = 11.sp,
                                color = c.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteEditor(vm: NovaViewModel, id: Long, onClose: () -> Unit) {
    val c = LocalNovaColors.current
    val context = LocalContext.current
    val notes by vm.notes.collectAsState()
    val note = notes.firstOrNull { it.id == id }
    var text by remember(id) { mutableStateOf(note?.text ?: "") }
    val focusRequester = remember { FocusRequester() }

    fun finish() {
        if (text.isBlank()) vm.deleteNote(id)
        onClose()
    }

    BackHandler { finish() }
    LaunchedEffect(id) {
        if (text.isEmpty()) {
            delay(300)
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // not attached yet
            }
        }
    }

    ToolScaffold(
        title = "메모",
        onBack = { finish() },
        actions = {
            CircleIconButton(
                Icons.Rounded.PushPin, "고정", { vm.togglePin(id) },
                active = note?.pinned == true, size = 40.dp,
            )
            Spacer(Modifier.width(8.dp))
            CircleIconButton(
                Icons.Rounded.Share, "공유",
                { if (text.isNotBlank()) SystemActions.shareText(context, text) }, size = 40.dp,
            )
            Spacer(Modifier.width(8.dp))
            CircleIconButton(
                Icons.Rounded.Delete, "삭제",
                {
                    vm.deleteNote(id)
                    vm.toast("메모를 삭제했어요")
                    onClose()
                },
                size = 40.dp,
            )
        },
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 8.dp)
        ) {
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it
                    vm.updateNote(id, it)
                },
                textStyle = TextStyle(color = c.text, fontSize = 17.sp, lineHeight = 26.sp),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 400.dp)
                    .focusRequester(focusRequester),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text("메모를 입력하세요…", color = c.textSecondary, fontSize = 17.sp)
                        inner()
                    }
                },
            )
        }
    }
}
