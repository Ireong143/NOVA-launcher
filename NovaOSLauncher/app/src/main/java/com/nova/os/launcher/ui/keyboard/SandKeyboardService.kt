
package com.nova.os.launcher.keyboard

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

class SandKeyboardService : InputMethodService() {

    private var koreanMode = true
    private var toneGenerator: ToneGenerator? = null
    private var composing = ""

    private val backgroundColor = Color.rgb(225, 228, 234)
    private val keyColor = Color.WHITE
    private val textColor = Color.rgb(35, 38, 45)

    private val consonants = listOf(
        "ㄱ", "ㄲ", "ㄴ", "ㄷ", "ㄸ", "ㄹ", "ㅁ",
        "ㅂ", "ㅃ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅉ",
        "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"
    )

    private val vowels = listOf(
        "ㅏ", "ㅐ", "ㅑ", "ㅒ", "ㅓ", "ㅔ", "ㅕ",
        "ㅖ", "ㅗ", "ㅘ", "ㅙ", "ㅚ", "ㅛ", "ㅜ",
        "ㅝ", "ㅞ", "ㅟ", "ㅠ", "ㅡ", "ㅢ", "ㅣ"
    )

    private val initial = listOf(
        "ㄱ", "ㄲ", "ㄴ", "ㄷ", "ㄸ", "ㄹ", "ㅁ",
        "ㅂ", "ㅃ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅉ",
        "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"
    )

    private val medial = listOf(
        "ㅏ", "ㅐ", "ㅑ", "ㅒ", "ㅓ", "ㅔ", "ㅕ",
        "ㅖ", "ㅗ", "ㅘ", "ㅙ", "ㅚ", "ㅛ", "ㅜ",
        "ㅝ", "ㅞ", "ㅟ", "ㅠ", "ㅡ", "ㅢ", "ㅣ"
    )

    private val finalConsonants = listOf(
        "", "ㄱ", "ㄲ", "ㄳ", "ㄴ", "ㄵ", "ㄶ",
        "ㄷ", "ㄹ", "ㄺ", "ㄻ", "ㄼ", "ㄽ", "ㄾ",
        "ㄿ", "ㅀ", "ㅁ", "ㅂ", "ㅄ", "ㅅ", "ㅆ",
        "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"
    )

    override fun onCreate() {
        super.onCreate()
        toneGenerator = ToneGenerator(AudioManager.STREAM_SYSTEM, 35)
    }

    override fun onCreateInputView(): View = createKeyboard()

    private fun createKeyboard(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4.dp(), 8.dp(), 4.dp(), 8.dp())
            setBackgroundColor(backgroundColor)
        }

        addRow(root, listOf(
            "1", "2", "3", "4", "5",
            "6", "7", "8", "9", "0"
        ))

        addRow(
            root,
            if (koreanMode)
                listOf("ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "ㅛ", "ㅕ", "ㅑ", "ㅐ", "ㅔ")
            else
                listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        )

        addRow(
            root,
            if (koreanMode)
                listOf("ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "ㅗ", "ㅓ", "ㅏ", "ㅣ")
            else
                listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        )

        addRow(
            root,
            if (koreanMode)
                listOf("ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅠ", "ㅜ", "ㅡ")
            else
                listOf("z", "x", "c", "v", "b", "n", "m")
        )

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        addSpecialButton(bottom, "😊", 1f) {
            finishComposition()
            commit("😊")
        }

        addSpecialButton(bottom, "한/영", 1.2f) {
            finishComposition()
            koreanMode = !koreanMode
            setInputView(createKeyboard())
        }

        addSpecialButton(bottom, "클립", 1f) {
            finishComposition()
            pasteClipboard()
        }

        addSpecialButton(bottom, "⌫", 1f) {
            deleteCharacter()
        }

        addSpecialButton(bottom, "공백", 1.5f) {
            finishComposition()
            commit(" ")
        }

        addSpecialButton(bottom, "↵", 1f) {
            finishComposition()
            val action = currentInputEditorInfo?.imeOptions
                ?.and(EditorInfo.IME_MASK_ACTION)

            if (action != null &&
                action != EditorInfo.IME_ACTION_NONE &&
                action != EditorInfo.IME_ACTION_UNSPECIFIED
            ) {
                currentInputConnection?.performEditorAction(action)
            } else {
                commit("\n")
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

    private fun addRow(parent: LinearLayout, keys: List<String>) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        keys.forEach { key ->
            val button = createKeyButton(key)

            button.setOnClickListener {
                playSound()
                if (key.length == 1) {
                    if (koreanMode) {
                        typeKorean(key)
                    } else {
                        finishComposition()
                        commit(key)
                    }
                }
            }

            row.addView(
                button,
                LinearLayout.LayoutParams(0, 43.dp(), 1f).apply {
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
            LinearLayout.LayoutParams(0, 44.dp(), weight).apply {
                setMargins(2.dp(), 2.dp(), 2.dp(), 2.dp())
            }
        )
    }

    private fun typeKorean(key: String) {
        val connection = currentInputConnection ?: return

        if (key in consonants) {
            if (composing.isNotEmpty() && composing.last() in vowels) {
                val combined = combineSyllable(composing, key)
                if (combined != null) {
                    composing = combined
                    connection.setComposingText(composing, 1)
                    return
                }
            }
            finishComposition()
            composing = key
            connection.setComposingText(composing, 1)
            return
        }

        if (key in vowels) {
            if (composing.isEmpty()) {
                composing = key
            } else {
                val combined = combineSyllable(composing, key)
                composing = combined ?: (composing + key)
            }
            connection.setComposingText(composing, 1)
            return
        }

        finishComposition()
        commit(key)
    }

    private fun combineSyllable(current: String, next: String): String? {
        if (current.length == 1 && current[0] in initial[0][0]..initial.last()[0]) {
            if (next in medial) {
                val cho = initial.indexOf(current)
                val jung = medial.indexOf(next)
                if (cho >= 0 && jung >= 0) {
                    return String(charArrayOf(
                        (0xAC00 + (cho * 21 + jung) * 28).toChar()
                    ))
                }
            }
        }

        if (current.length == 1 && current[0].code in 0xAC00..0xD7A3) {
            val code = current[0].code
            val jong = code % 28
            val cho = (code - 0xAC00) / 588
            val jung = (code - 0xAC00) % 588 / 28

            if (jong == 0 && next in finalConsonants.drop(1)) {
                val index = finalConsonants.indexOf(next)
                if (index > 0) {
                    return String(charArrayOf(
                        (0xAC00 + (cho * 21 + jung) * 28 + index).toChar()
                    ))
                }
            }
        }

        return null
    }

    private fun finishComposition() {
        if (composing.isNotEmpty()) {
            currentInputConnection?.finishComposingText()
            composing = ""
        }
    }

    private fun deleteCharacter() {
        val connection = currentInputConnection ?: return

        if (composing.isNotEmpty()) {
            composing = composing.dropLast(1)
            if (composing.isEmpty()) {
                connection.commitText("", 1)
            } else {
                connection.setComposingText(composing, 1)
            }
        } else {
            connection.deleteSurroundingText(1, 0)
        }
    }

    private fun commit(value: String) {
        currentInputConnection?.commitText(value, 1)
    }

    private fun pasteClipboard() {
        val clipboard = getSystemService(
            Context.CLIPBOARD_SERVICE
        ) as ClipboardManager

        if (clipboard.hasPrimaryClip()) {
            val text = clipboard.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(this)
                ?.toString()

            if (!text.isNullOrEmpty()) commit(text)
        }
    }

    private fun playSound() {
        try {
            toneGenerator?.startTone(
                ToneGenerator.TONE_PROP_BEEP,
                35
            )
        } catch (_: Exception) {
        }
    }

    private fun Int.dp(): Int =
        (this * resources.displayMetrics.density).toInt()

    override fun onFinishInput() {
        finishComposition()
        super.onFinishInput()
    }

    override fun onDestroy() {
        toneGenerator?.release()
        toneGenerator = null
        super.onDestroy()
    }
}
