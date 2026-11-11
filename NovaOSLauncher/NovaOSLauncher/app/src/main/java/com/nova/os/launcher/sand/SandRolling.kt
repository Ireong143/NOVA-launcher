package com.nova.os.launcher.sand

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private val RollEase = CubicBezierEasing(0.22f, 0.9f, 0.24f, 1f)

/**
 * 글자 하나하나가 아래에서 위로 굴러 올라가며 바뀌는 텍스트(시계용).
 * 바뀌는 글자만 움직이고, 같은 글자는 그대로 있다.
 * 자리를 "오른쪽 끝부터" 세어 키로 쓰므로 9:59 → 10:00처럼 자릿수가 바뀌어도 분·초 자리는 이어서 움직인다.
 */
@Composable
fun RollingText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    lineHeight: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    color: Color = Color.Unspecified,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val n = text.length
        for (i in 0 until n) {
            key(n - 1 - i) {
                RollingChar(text[i], fontSize, lineHeight, fontWeight, letterSpacing, color)
            }
        }
    }
}

@Composable
private fun RollingChar(
    ch: Char,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight?,
    letterSpacing: TextUnit,
    color: Color,
) {
    AnimatedContent(
        targetState = ch,
        modifier = Modifier.clipToBounds(),
        transitionSpec = {
            ContentTransform(
                targetContentEnter = slideInVertically(animationSpec = tween(560, easing = RollEase)) { h -> h } +
                    fadeIn(animationSpec = tween(380)),
                initialContentExit = slideOutVertically(animationSpec = tween(560, easing = RollEase)) { h -> -h } +
                    fadeOut(animationSpec = tween(260)),
            )
        },
        label = "rollChar"
    ) { c ->
        Text(
            c.toString(),
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            letterSpacing = letterSpacing,
            color = color,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
}
