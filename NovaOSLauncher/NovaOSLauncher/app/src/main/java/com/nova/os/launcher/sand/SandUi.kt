package com.nova.os.launcher.sand

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nova.os.launcher.ui.theme.LocalNovaColors

/** 유리 질감 입력창. */
@Composable
fun SandTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onAction: () -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
    val c = LocalNovaColors.current
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .clip(shape)
            .background(c.glass)
            .border(1.dp, c.glassBorder, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = c.textSecondary, fontSize = 15.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            maxLines = maxLines,
            textStyle = TextStyle(color = c.text, fontSize = 15.sp),
            cursorBrush = SolidColor(c.accent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onAction() }),
            modifier = if (focusRequester != null) Modifier.fillMaxWidth().focusRequester(focusRequester) else Modifier.fillMaxWidth()
        )
    }
}

/** 둥근 선택 칩. */
@Composable
fun SandChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalNovaColors.current
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) c.accent else c.glass)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) c.onAccent else c.text,
            maxLines = 1
        )
    }
}

/** 눌러서 동작하는 알약 버튼. */
@Composable
fun SandButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val c = LocalNovaColors.current
    val bg = if (danger) c.danger else c.accent
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) bg else c.glass)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) (if (danger) Color.White else c.onAccent) else c.textSecondary
        )
    }
}

/** 유리 카드 모양 대화상자. */
@Composable
fun SandDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalNovaColors.current
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.panel)
                .border(1.dp, c.glassBorder, shape)
                .padding(20.dp),
            content = content
        )
    }
}

/** 경과 시간을 "3분 전" 형태로. */
fun sandAgo(epochMs: Long): String {
    val d = (System.currentTimeMillis() - epochMs) / 1000
    return when {
        d < 45 -> "방금"
        d < 3600 -> (d / 60).coerceAtLeast(1).toString() + "분 전"
        d < 86400 -> (d / 3600).toString() + "시간 전"
        d < 86400 * 7 -> (d / 86400).toString() + "일 전"
        else -> (d / 86400 / 7).toString() + "주 전"
    }
}
