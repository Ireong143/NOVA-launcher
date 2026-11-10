package com.nova.os.launcher.sand

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nova.os.launcher.ui.components.CircleIconButton
import com.nova.os.launcher.ui.components.PillSlider
import com.nova.os.launcher.ui.components.ToolScaffold
import com.nova.os.launcher.ui.theme.LocalNovaColors

class PaintStroke(val color: Color, val width: Float, val points: List<Offset>)

@Composable
fun SandPaintScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalNovaColors.current
    val strokes = remember { mutableStateListOf<PaintStroke>() }
    val current = remember { mutableStateListOf<Offset>() }
    var color by remember { mutableStateOf(Color.Black) }
    var sizeValue by remember { mutableFloatStateOf(0.25f) }
    var eraser by remember { mutableStateOf(false) }
    var canvasW by remember { mutableIntStateOf(1) }
    var canvasH by remember { mutableIntStateOf(1) }

    val paper = Color.White
    val drawColor = if (eraser) paper else color
    val brush = 3f + sizeValue * 45f
    val palette = listOf(
        Color.Black, Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835), Color(0xFF43A047),
        Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFEC407A), Color(0xFF6D4C41),
    )

    ToolScaffold(
        "그림판", onClose,
        actions = {
            CircleIconButton(Icons.Rounded.Undo, "되돌리기", { if (strokes.isNotEmpty()) strokes.removeAt(strokes.size - 1) })
            Spacer(Modifier.width(8.dp))
            CircleIconButton(Icons.Rounded.Delete, "모두 지우기", { strokes.clear(); current.clear() })
            Spacer(Modifier.width(8.dp))
            CircleIconButton(
                Icons.Rounded.Save, "갤러리에 저장",
                {
                    val bmp = Bitmap.createBitmap(maxOf(canvasW, 1), maxOf(canvasH, 1), Bitmap.Config.ARGB_8888)
                    val cv = android.graphics.Canvas(bmp)
                    cv.drawColor(android.graphics.Color.WHITE)
                    val paint = android.graphics.Paint()
                    paint.isAntiAlias = true
                    paint.strokeCap = android.graphics.Paint.Cap.ROUND
                    paint.strokeJoin = android.graphics.Paint.Join.ROUND
                    for (s in strokes) {
                        paint.color = s.color.toArgb()
                        if (s.points.size == 1) {
                            paint.style = android.graphics.Paint.Style.FILL
                            cv.drawCircle(s.points[0].x, s.points[0].y, s.width / 2f, paint)
                        } else {
                            paint.style = android.graphics.Paint.Style.STROKE
                            paint.strokeWidth = s.width
                            val p = android.graphics.Path()
                            p.moveTo(s.points[0].x, s.points[0].y)
                            for (i in 1 until s.points.size) p.lineTo(s.points[i].x, s.points[i].y)
                            cv.drawPath(p, paint)
                        }
                    }
                    val ok = SandMedia.savePng(ctx, bmp, "Paint_" + System.currentTimeMillis())
                    Toast.makeText(
                        ctx,
                        if (ok) "갤러리의 Pictures/SAND에 저장했어요" else "저장하지 못했어요(Android 10 이상 필요)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(paper)
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasW = it.width; canvasH = it.height }
                    .pointerInput(drawColor, brush) {
                        detectDragGestures(
                            onDragStart = { o -> current.clear(); current.add(o) },
                            onDrag = { change, _ -> change.consume(); current.add(change.position) },
                            onDragEnd = {
                                if (current.isNotEmpty()) strokes.add(PaintStroke(drawColor, brush, current.toList()))
                                current.clear()
                            },
                            onDragCancel = { current.clear() }
                        )
                    }
                    .pointerInput(drawColor, brush) {
                        detectTapGestures { o -> strokes.add(PaintStroke(drawColor, brush, listOf(o))) }
                    }
            ) {
                fun paintStroke(col: Color, w: Float, pts: List<Offset>) {
                    if (pts.isEmpty()) return
                    if (pts.size == 1) {
                        drawCircle(col, radius = w / 2f, center = pts[0])
                    } else {
                        val path = Path()
                        path.moveTo(pts[0].x, pts[0].y)
                        for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
                        drawPath(path, col, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                for (s in strokes) paintStroke(s.color, s.width, s.points)
                paintStroke(drawColor, brush, current.toList())
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for (p in palette) {
                val selected = !eraser && p == color
                Box(
                    Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(CircleShape)
                        .background(p)
                        .border(if (selected) 3.dp else 1.dp, if (selected) c.accent else c.glassBorder, CircleShape)
                        .clickable { color = p; eraser = false }
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SandChip("펜", !eraser, { eraser = false })
            SandChip("지우개", eraser, { eraser = true })
            Box(Modifier.weight(1f)) {
                PillSlider(sizeValue, { sizeValue = it }, Icons.Rounded.Brush)
            }
        }
    }
}
