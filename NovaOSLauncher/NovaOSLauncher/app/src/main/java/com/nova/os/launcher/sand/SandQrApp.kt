package com.nova.os.launcher.sand

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.launcher.ui.components.NovaSegmented
import com.nova.os.launcher.ui.components.ToolScaffold
import com.nova.os.launcher.ui.theme.LocalNovaColors

private fun wifiEscape(s: String): String =
    s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")

private fun qrBitmap(matrix: Array<BooleanArray>): Bitmap {
    val scale = 12
    val quiet = 4
    val n = matrix.size
    val dim = (n + quiet * 2) * scale
    val pixels = IntArray(dim * dim) { -1 } // 흰색(0xFFFFFFFF)
    for (y in 0 until n) for (x in 0 until n) {
        if (!matrix[y][x]) continue
        for (dy in 0 until scale) for (dx in 0 until scale) {
            pixels[((y + quiet) * scale + dy) * dim + (x + quiet) * scale + dx] = 0xFF000000.toInt()
        }
    }
    return Bitmap.createBitmap(pixels, dim, dim, Bitmap.Config.ARGB_8888)
}

private fun openCamera(context: Context) {
    try {
        context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "카메라를 열 수 없어요", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun SandQrScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalNovaColors.current
    var mode by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    var ssid by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var ecl by remember { mutableIntStateOf(1) }

    val payload = if (mode == 0) {
        text
    } else if (ssid.isBlank()) {
        ""
    } else {
        "WIFI:T:" + (if (pass.isBlank()) "nopass" else "WPA") + ";S:" + wifiEscape(ssid) + ";P:" + wifiEscape(pass) + ";;"
    }
    val matrix = remember(payload, ecl) {
        if (payload.isBlank()) null else try {
            SandQrCode.encode(payload, ecl)
        } catch (e: Exception) {
            null
        }
    }
    val tooLong = payload.isNotBlank() && matrix == null

    ToolScaffold("QR 코드", onClose) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NovaSegmented(listOf("텍스트 / 링크", "Wi-Fi"), mode, { mode = it })
            if (mode == 0) {
                SandTextField(
                    text, { text = it }, "QR로 만들 글자나 주소를 입력하세요",
                    Modifier.fillMaxWidth(), singleLine = false, maxLines = 4
                )
            } else {
                SandTextField(ssid, { ssid = it }, "Wi-Fi 이름(SSID)", Modifier.fillMaxWidth())
                SandTextField(pass, { pass = it }, "비밀번호(없으면 비워 두세요)", Modifier.fillMaxWidth(), password = true)
            }
            NovaSegmented(listOf("L 7%", "M 15%", "Q 25%", "H 30%"), ecl, { ecl = it })

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(290.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    if (matrix != null) {
                        Canvas(Modifier.size(270.dp)) {
                            val cells = matrix.size + 8
                            val cell = size.minDimension / cells
                            for (y in matrix.indices) for (x in matrix.indices) {
                                if (matrix[y][x]) {
                                    drawRect(
                                        Color.Black,
                                        Offset((x + 4) * cell, (y + 4) * cell),
                                        Size(cell + 0.6f, cell + 0.6f)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            if (tooLong) "내용이 너무 길어요\n(오류 정정 단계를 낮춰 보세요)" else "내용을 입력하면\nQR 코드가 만들어져요",
                            color = Color(0xFF666B7A),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SandButton(
                    "이미지로 저장",
                    {
                        val m = matrix
                        if (m != null) {
                            val ok = SandMedia.savePng(ctx, qrBitmap(m), "QR_" + System.currentTimeMillis())
                            Toast.makeText(
                                ctx,
                                if (ok) "갤러리의 Pictures/SAND에 저장했어요" else "저장하지 못했어요(Android 10 이상 필요)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    Modifier.weight(1f),
                    enabled = matrix != null
                )
                SandButton("QR 스캔(카메라)", { openCamera(ctx) }, Modifier.weight(1f))
            }
            Text(
                "스캔은 기기 카메라가 QR을 자동으로 인식해요. 카메라 화면에서 QR을 비추면 링크가 나타납니다.",
                color = c.textSecondary,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
