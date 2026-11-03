
package com.nova.os.launcher.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class SandKeyboardService : InputMethodService() {

    private var koreanMode = true
    private var toneGenerator: ToneGenerator? = null

    private val backgroundColor = Color.rgb(225, 228, 234)
    private val keyColor = Color.WHITE
    private val textColor = Color.rgb(35, 38, 45)

    override fun onCreate() {
        super.onCreate()
        toneGenerator = ToneGenerator(AudioManager.STREAM_SYSTEM, 35)
    }

    override fun onCreateInputView(): View {
        return createKeyboard()
    }

    private fun createKeyboard(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4, 8, 4, 8)
            setBackgroundColor(backgroundColor)
        }

        // 숫자 상단 배열
        addRow(root, listOf(
            "1", "2", "3", "4", "5",
            "6", "7", "8", "9", "0"
        ))

        // 첫 번째 문자 줄
        addRow(
            root,
            if (koreanMode) {
                listOf("ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "ㅛ", "ㅕ", "ㅑ", "ㅐ", "ㅔ")
            } else {
                listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
            }
        )

        // 두 번째 문자 줄
        addRow(
            root,
            if (koreanMode) {
                listOf("ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "ㅗ", "ㅓ", "ㅏ", "ㅣ")
            } else {
                listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
            }
        )

        // 세 번째 문자 줄
        addRow(
            root,
            if (koreanMode) {
                listOf("ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅠ", "ㅜ", "ㅡ")
            } else {
                listOf("z", "x", "c", "v", "b", "n", "m")
            }
        )

        // 하단 기능 버튼
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        addSpecialButton(bottom, "😊", 1f) {
            commit("😊")
        }

        addSpecialButton(bottom, "한/영", 1.2f) {
            koreanMode = !koreanMode
            setInputView(createKeyboard())
        }

        addSpecialButton(bottom, "클립", 1f) {
            pasteClipboard()
        }

        addSpecialButton(bottom, "⌫", 1f) {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }

        addSpecialButton(bottom, "공백", 1.5f) {
            commit(" ")
        }

        addSpecialButton(bottom, "↵", 1f) {
            val connection = currentInputConnection
            val action = currentInputEditorInfo?.imeOptions
                ?.and(EditorInfo.IME_MASK_ACTION)

            if (action != null && action != EditorInfo.IME_ACTION_NONE &&
                action != EditorInfo.IME_ACTION_UNSPECIFIED
            ) {
                connection?.performEditorAction(action)
            } else {
                connection?.commitText("\n", 1)
            }
        }

        root.addView(
            bottom,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                52.dp()
            )
        )

        return root
    }

    private fun addRow(
        parent: LinearLayout,
        keys: List<String>
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        keys.forEach { key ->
            val button = createKeyButton(key)

            button.setOnClickListener {
                playSound()
                commit(key)
            }

            row.addView(
                button,
                LinearLayout.LayoutParams(
                    0,
                    43.dp(),
                    1f
                ).apply {
                    setMargins(2.dp(), 3.dp(), 2.dp(), 3.dp())
                }
            )
        }

        parent.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                49.dp()
            )
        )
    }

    private fun createKeyButton(label: String): Button {
        return Button(this).apply {
            text = label
            textSize = 17f
            setTextColor(textColor)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            backgroundTintList =
                android.content.res.ColorStateList.valueOf(keyColor)
            stateListAnimator = null
            elevation = 1.dp().toFloat()
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
        }
    }

    private fun addSpecialButton(
        parent: LinearLayout,
        label: String,
        weight: Float,
        action: () -> Unit
    ) {
        val button = createKeyButton(label).apply {
            textSize = 13f
            setOnClickListener {
                playSound()
                action()
            }
        }

        parent.addView(
            button,
            LinearLayout.LayoutParams(
                0,
                44.dp(),
                weight
            ).apply {
                setMargins(2.dp(), 2.dp(), 2.dp(), 2.dp())
            }
        )
    }

    private fun commit(value: String) {
        currentInputConnection?.commitText(value, 1)
    }

    private fun pasteClipboard() {
        val clipboard = getSystemService(
            Context.CLIPBOARD_SERVICE
        ) as ClipboardManager

        if (clipboard.hasPrimaryClip()) {
            val item = clipboard.primaryClip?.getItemAt(0)
            val text = item?.coerceToText(this)?.toString()

            if (!text.isNullOrEmpty()) {
                commit(text)
            }
        }
    }

    private fun playSound() {
        try {
            toneGenerator?.startTone(
                ToneGenerator.TONE_PROP_BEEP,
                35
            )
        } catch (_: Exception) {
            // 효과음 재생에 실패해도 입력은 계속 작동
        }
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        toneGenerator?.release()
        toneGenerator = null
        super.onDestroy()
    }
}
