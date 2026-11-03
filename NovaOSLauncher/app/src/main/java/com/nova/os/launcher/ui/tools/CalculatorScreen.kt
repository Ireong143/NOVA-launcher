package com.nova.os.launcher.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.launcher.NovaViewModel
import com.nova.os.launcher.ui.components.CircleIconButton
import com.nova.os.launcher.ui.components.Pressable
import com.nova.os.launcher.ui.components.ToolScaffold
import com.nova.os.launcher.ui.theme.LocalNovaColors

private enum class KeyKind { Number, Function, Operator, Equals }

private val keyRows = listOf(
    listOf("C", "()", "%", "÷"),
    listOf("7", "8", "9", "×"),
    listOf("4", "5", "6", "−"),
    listOf("1", "2", "3", "+"),
    listOf("0", ".", "⌫", "="),
)

private fun kindOf(label: String): KeyKind = when (label) {
    "C", "()", "%", "⌫" -> KeyKind.Function
    "÷", "×", "−", "+" -> KeyKind.Operator
    "=" -> KeyKind.Equals
    else -> KeyKind.Number
}

@Composable
fun CalculatorScreen(vm: NovaViewModel) {
    val c = LocalNovaColors.current
    val calc = vm.calc
    val haptic = LocalHapticFeedback.current

    ToolScaffold(
        title = "계산기",
        onBack = { vm.back() },
        actions = { CircleIconButton(Icons.Rounded.Delete, "기록 지우기", { calc.clearHistory() }) },
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Display
            Column(
                Modifier.weight(0.75f).fillMaxWidth().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.End,
            ) {
                calc.history.take(3).reversed().forEach {
                    Text(
                        it,
                        fontSize = 15.sp,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                val display = calc.expr.ifEmpty { "0" }
                val size = when {
                    display.length > 20 -> 26.sp
                    display.length > 13 -> 34.sp
                    else -> 52.sp
                }
                Text(
                    display,
                    fontSize = size,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.End,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (calc.preview.isEmpty()) " " else "= ${calc.preview}",
                    fontSize = 22.sp,
                    color = c.textSecondary,
                    maxLines = 1,
                )
            }

            // Keypad
            Column(
                Modifier.weight(1.7f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                keyRows.forEach { row ->
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { label ->
                            CalcKey(label, kindOf(label), Modifier.weight(1f).fillMaxHeight()) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                calc.input(label)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcKey(label: String, kind: KeyKind, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalNovaColors.current
    val (bg: Color, fg: Color) = when (kind) {
        KeyKind.Number -> c.glass to c.text
        KeyKind.Function -> c.glassStrong to c.text
        KeyKind.Operator -> c.accentSoft to c.accent
        KeyKind.Equals -> c.accent to c.onAccent
    }
    val shape = RoundedCornerShape(26.dp)
    Pressable(
        modifier.clip(shape).background(bg).border(1.dp, c.glassBorder, shape),
        scaleDown = 0.94f,
        onClick = onClick,
    ) {
        Text(
            label,
            Modifier.align(Alignment.Center),
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            color = fg,
        )
    }
}
