
package com.nova.os.launcher.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

class SandKeyboardService : InputMethodService() {

    private var koreanMode = true
    private var shiftEnabled = false
    private var numberMode = false

    private var cho = -1
    private var jung = -1
    private var jong = 0

    private var toneGenerator: ToneGenerator? = null
    private val handler = Handler(Looper.getMainLooper())

    private val bg = Color.rgb(105, 105, 105)
    private val keyColor = Color.rgb(174, 175, 178)
    private val specialColor = Color.rgb(137, 139, 143)
    private val textColor = Color.WHITE
    private val enterColor = Color.rgb(190, 211, 245)

    private val initials = listOf(
        "ㄱ", "ㄲ", "ㄴ", "ㄷ", "ㄸ", "ㄹ", "ㅁ",
        "ㅂ", "ㅃ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅉ",
        "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"
    )

    private val vowels = listOf(
        "ㅏ", "ㅐ", "ㅑ", "ㅒ", "ㅓ", "ㅔ", "ㅕ",
        "ㅖ", "ㅗ", "ㅘ", "ㅙ", "ㅚ", "ㅛ", "ㅜ",
        "ㅝ", "ㅞ", "ㅟ", "ㅠ", "ㅡ", "ㅢ", "ㅣ"
    )

    private val finals = listOf(
        "", "ㄱ", "ㄲ", "ㄳ", "ㄴ", "ㄵ", "ㄶ",
        "ㄷ", "ㄹ", "ㄺ", "ㄻ", "ㄼ", "ㄽ", "ㄾ",
        "ㄿ", "ㅀ", "ㅁ", "ㅂ", "ㅄ", "ㅅ", "ㅆ",
        "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"
    )

    private val keyMap = mapOf(
        'q' to "ㅂ", 'w' to "ㅈ", 'e' to "ㄷ",
        'r' to "ㄱ", 't' to "ㅅ", 'y' to "ㅛ",
        'u' to "ㅕ", 'i' to "ㅑ", 'o' to "ㅐ",
        'p' to "ㅔ",
        'a' to "ㅁ", 's' to "ㄴ", 'd' to "ㅇ",
        'f' to "ㄹ", 'g' to "ㅎ", 'h' to "ㅗ",
        'j' to "ㅓ", 'k' to "ㅏ", 'l' to "ㅣ",
        'z' to "ㅋ", 'x' to "ㅌ", 'c' to "ㅊ",
        'v' to "ㅍ", 'b' to "ㅠ", 'n' to "ㅜ",
        'm' to "ㅡ"
    )

    private val shiftMap = mapOf(
        "ㅂ" to "ㅃ",
        "ㅈ" to "ㅉ",
        "ㄷ" to "ㄸ",
        "ㄱ" to "ㄲ",
        "ㅅ" to "ㅆ",
        "ㅐ" to "ㅒ",
        "ㅔ" to "ㅖ"
    )

    private val compoundVowels = mapOf(
        "ㅗㅏ" to "ㅘ",
        "ㅗㅐ" to "ㅙ",
        "ㅗㅣ" to "ㅚ",
        "ㅜㅓ" to "ㅝ",
        "ㅜㅔ" to "ㅞ",
        "ㅜㅣ" to "ㅟ",
        "ㅡㅣ" to "ㅢ",
        "ㅏㅣ" to "ㅐ",
        "ㅑㅣ" to "ㅒ",
        "ㅓㅣ" to "ㅔ",
        "ㅕㅣ" to "ㅖ"
    )

    private val compoundFinals = mapOf(
        "ㄱㅅ" to "ㄳ",
        "ㄴㅈ" to "ㄵ",
        "ㄴㅎ" to "ㄶ",
        "ㄹㄱ" to "ㄺ",
        "ㄹㅁ" to "ㄻ",
        "ㄹㅂ" to "ㄼ",
        "ㄹㅅ" to "ㄽ",
        "ㄹㅌ" to "ㄾ",
        "ㄹㅍ" to "ㄿ",
        "ㄹㅎ" to "ㅀ",
        "ㅂㅅ" to "ㅄ"
    )

    private val splitFinals = mapOf(
        "ㄳ" to Pair("ㄱ", "ㅅ"),
        "ㄵ" to Pair("ㄴ", "ㅈ"),
        "ㄶ" to Pair("ㄴ", "ㅎ"),
        "ㄺ" to Pair("ㄹ", "ㄱ"),
        "ㄻ" to Pair("ㄹ", "ㅁ"),
        "ㄼ" to Pair("ㄹ", "ㅂ"),
        "ㄽ" to Pair("ㄹ", "ㅅ"),
        "ㄾ" to Pair("ㄹ", "ㅌ"),
        "ㄿ" to Pair("ㄹ", "ㅍ"),
        "ㅀ" to Pair("ㄹ", "ㅎ"),
        "ㅄ" to Pair("ㅂ", "ㅅ")
    )

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
            setPadding(3.dp(), 5.dp(), 3.dp(), 3.dp())
            setBackgroundColor(bg)
        }

        if (numberMode) {
            createNumberRows(root)
        } else {
            createKoreanRows(root)
        }

        createBottomRow(root)
        createFooter(root)

        return root
    }

    private fun createKoreanRows(root: LinearLayout) {

        // 첫 번째 줄
        val row1 = listOf(
            "ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ",
            "ㅛ", "ㅕ", "ㅑ", "ㅐ", "ㅔ"
        )

        addCharacterRow(root, row1, 54) { key ->
            typeKoreanKey(key)
        }

        // 두 번째 줄
        val row2 = listOf(
            "ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ",
            "ㅗ", "ㅓ", "ㅏ", "ㅣ"
        )

        addCharacterRow(root, row2, 54) { key ->
            typeKoreanKey(key)
        }

        // 세 번째 줄
        val row3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        addSpecialKey(row3, "⇧", 1.35f) {
            shiftEnabled = !shiftEnabled
            refreshKeyboard()
        }

        listOf("ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅠ", "ㅜ", "ㅡ")
            .forEach { key ->
                addCharacterKey(row3, key, 1f) {
                    typeKoreanKey(key)
                }
            }

        addDeleteKey(row3, 1.35f)

        root.addView(
            row3,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                54.dp()
            )
        )
    }

    private fun createNumberRows(root: LinearLayout) {

        val rows = listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/"),
            listOf("=", "*", "\"", "'", ":", ";", "!", "?", "⌫")
        )

        rows.forEachIndexed { index, keys ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            keys.forEach { key ->
                if (key == "⌫") {
                    addDeleteKey(row, 1.2f)
                } else {
                    addCharacterKey(row, key, 1f) {
                        finishComposition()
                        commit(key)
                    }
                }
            }

            root.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    54.dp()
                )
            )
        }
    }

    private fun addCharacterRow(
        parent: LinearLayout,
        keys: List<String>,
        height: Int,
        action: (String) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        keys.forEach { key ->
            addCharacterKey(row, key, 1f) {
                action(key)
            }
        }

        parent.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                height.dp()
            )
        )
    }

    private fun createBottomRow(root: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        addSpecialKey(row, "123", 1.05f) {
            finishComposition()
            numberMode = !numberMode
            shiftEnabled = false
            refreshKeyboard()
        }

        addSpecialKey(row, "☻", 0.95f) {
            finishComposition()
            commit("😊")
        }

        addSpaceKey(row, 3.8f)

        addSpecialKey(row, "↵", 1.3f, enterColor) {
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
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                59.dp()
            )
        )
    }

    private fun createFooter(root: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(15.dp(), 0, 15.dp(), 0)
        }

        val globe = Button(this).apply {
            text = "◎"
            textSize = 28f
            setTextColor(Color.WHITE)
            background = null
            backgroundTintList = null
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            stateListAnimator = null
        }

        globe.setOnClickListener {
            finishComposition()
            koreanMode = !koreanMode
            shiftEnabled = false
            refreshKeyboard()
        }

        row.addView(
            globe,
            LinearLayout.LayoutParams(0, 39.dp(), 1f)
        )

        val mic = Button(this).apply {
            text = "♩"
            textSize = 27f
            setTextColor(Color.WHITE)
            background = null
            backgroundTintList = null
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            stateListAnimator = null
        }

        mic.setOnClickListener {
            Toast.makeText(
                this@SandKeyboardService,
                "음성 입력은 아직 지원하지 않아요.",
                Toast.LENGTH_SHORT
            ).show()
        }

        row.addView(
            mic,
            LinearLayout.LayoutParams(0, 39.dp(), 1f)
        )

        root.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                42.dp()
            )
        )
    }

    private fun addCharacterKey(
        parent: LinearLayout,
        key: String,
        weight: Float,
        action: () -> Unit
    ) {
        val label = if (koreanMode && shiftEnabled) {
            shiftMap[key] ?: key
        } else if (!koreanMode) {
            englishLabel(key)
        } else {
            key
        }

        val button = createKey(label)

        button.setOnClickListener {
            playSound()
            action()
        }

        parent.addView(
            button,
            LinearLayout.LayoutParams(0, 47.dp(), weight).apply {
                setMargins(2.dp(), 3.dp(), 2.dp(), 3.dp())
            }
        )
    }

    private fun addSpecialKey(
        parent: LinearLayout,
        label: String,
        weight: Float,
        color: Int = specialColor,
        action: () -> Unit
    ) {
        val button = createKey(label, color)

        button.setOnClickListener {
            playSound()
            action()
        }

        parent.addView(
            button,
            LinearLayout.LayoutParams(0, 47.dp(), weight).apply {
                setMargins(2.dp(), 3.dp(), 2.dp(), 3.dp())
            }
        )
    }

    private fun createKey(
        label: String,
        color: Int = keyColor
    ): Button {
        return Button(this).apply {
            text = label
            textSize = if (label.length > 2) 14f else 19f
            setTextColor(
                if (color == enterColor) {
                    Color.WHITE
                } else {
                    textColor
                }
            )
            typeface = Typeface.DEFAULT
            isAllCaps = false

            backgroundTintList = null
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 7.dp().toFloat()
                setColor(color)
                setStroke(
                    1.dp(),
                    Color.argb(35, 255, 255, 255)
                )
            }

            elevation = 1.dp().toFloat()
            stateListAnimator = null
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
        }
    }

    private fun englishLabel(key: String): String {
        val english = mapOf(
            "ㅂ" to "q", "ㅈ" to "w", "ㄷ" to "e",
            "ㄱ" to "r", "ㅅ" to "t", "ㅛ" to "y",
            "ㅕ" to "u", "ㅑ" to "i", "ㅐ" to "o",
            "ㅔ" to "p", "ㅁ" to "a", "ㄴ" to "s",
            "ㅇ" to "d", "ㄹ" to "f", "ㅎ" to "g",
            "ㅗ" to "h", "ㅓ" to "j", "ㅏ" to "k",
            "ㅣ" to "l", "ㅋ" to "z", "ㅌ" to "x",
            "ㅊ" to "c", "ㅍ" to "v", "ㅠ" to "b",
            "ㅜ" to "n", "ㅡ" to "m"
        )

        val value = english[key] ?: key

        return if (shiftEnabled) {
            value.uppercase()
        } else {
            value
        }
    }

    private fun typeKoreanKey(original: String) {
        if (!koreanMode) {
            val value = englishLabel(original)
            finishComposition()
            commit(value)
            if (shiftEnabled) {
                shiftEnabled = false
                refreshKeyboard()
            }
            return
        }

        val key = if (shiftEnabled) {
            shiftMap[original] ?: original
        } else {
            original
        }

        if (key in vowels || key in initials) {
            typeKorean(key)
        }

        if (shiftEnabled) {
            shiftEnabled = false
            refreshKeyboard()
        }
    }

    private fun typeKorean(key: String) {
        if (key in vowels) {
            inputVowel(key)
        } else if (key in initials) {
            inputConsonant(key)
        }
    }

    private fun inputConsonant(key: String) {
        if (cho == -1) {
            cho = initials.indexOf(key)
            updateComposition()
            return
        }

        if (jung == -1) {
            finishComposition()
            cho = initials.indexOf(key)
            updateComposition()
            return
        }

        if (jong == 0) {
            val index = finals.indexOf(key)
            if (index > 0) {
                jong = index
                updateComposition()
                return
            }
        } else {
            val combined = compoundFinals[finals[jong] + key]
            if (combined != null) {
                jong = finals.indexOf(combined)
                updateComposition()
                return
            }
        }

        finishComposition()
        cho = initials.indexOf(key)
        updateComposition()
    }

    private fun inputVowel(key: String) {
        if (cho == -1) {
            cho = initials.indexOf("ㅇ")
            jung = vowels.indexOf(key)
            updateComposition()
            return
        }

        if (jung == -1) {
            jung = vowels.indexOf(key)
            updateComposition()
            return
        }

        if (jong == 0) {
            val combined = compoundVowels[vowels[jung] + key]

            if (combined != null) {
                jung = vowels.indexOf(combined)
                updateComposition()
                return
            }

            finishComposition()
            cho = initials.indexOf("ㅇ")
            jung = vowels.indexOf(key)
            updateComposition()
            return
        }

        val oldFinal = finals[jong]
        val split = splitFinals[oldFinal]

        val moved: String

        if (split != null) {
            jong = finals.indexOf(split.first)
            moved = split.second
        } else {
            jong = 0
            moved = oldFinal
        }

        val previous = composeSyllable()
        currentInputConnection?.commitText(previous, 1)

        cho = initials.indexOf(moved)
        jung = vowels.indexOf(key)
        jong = 0

        updateComposition()
    }

    private fun composeSyllable(): String {
        if (cho < 0 || jung < 0) return ""

        val code = 0xAC00 + (cho * 21 + jung) * 28 + jong
        return code.toChar().toString()
    }

    private fun updateComposition() {
        currentInputConnection?.setComposingText(
            composeSyllable(),
            1
        )
    }

    private fun finishComposition() {
        if (cho >= 0 && jung >= 0) {
            currentInputConnection?.commitText(
                composeSyllable(),
                1
            )
        } else if (cho >= 0) {
            currentInputConnection?.commitText(
                initials[cho],
                1
            )
        }

        cho = -1
        jung = -1
        jong = 0
    }

    private fun deleteCharacter() {
        val connection = currentInputConnection ?: return

        if (cho >= 0) {
            if (jong != 0) {
                jong = 0
            } else if (jung >= 0) {
                jung = -1
            } else {
                cho = -1
            }

            if (cho >= 0) {
                updateComposition()
            } else {
                connection.setComposingText("", 1)
            }
        } else {
            connection.deleteSurroundingText(1, 0)
        }
    }

    private fun addDeleteKey(
        parent: LinearLayout,
        weight: Float
    ) {
        val button = createKey("⌫", specialColor)

        var repeating = false
        var task: Runnable? = null

        button.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    repeating = false
                    playSound()

                    val repeat = object : Runnable {
                        override fun run() {
                            repeating = true
                            deleteCharacter()
                            task = this
                            handler.postDelayed(this, 80)
                        }
                    }

                    task = repeat
                    handler.postDelayed(repeat, 450)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    task?.let { handler.removeCallbacks(it) }

                    if (!repeating) {
                        deleteCharacter()
                    }

                    view.performClick()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    task?.let { handler.removeCallbacks(it) }
                    true
                }

                else -> true
            }
        }

        parent.addView(
            button,
            LinearLayout.LayoutParams(0, 47.dp(), weight).apply {
                setMargins(2.dp(), 3.dp(), 2.dp(), 3.dp())
            }
        )
    }

    private fun addSpaceKey(
        parent: LinearLayout,
        weight: Float
    ) {
        val button = createKey("간격", specialColor)
        button.textSize = 17f

        var repeating = false
        var task: Runnable? = null

        button.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    repeating = false
                    playSound()

                    val repeat = object : Runnable {
                        override fun run() {
                            repeating = true
                            deleteCharacter()
                            task = this
                            handler.postDelayed(this, 80)
                        }
                    }

                    task = repeat
                    handler.postDelayed(repeat, 450)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    task?.let { handler.removeCallbacks(it) }

                    if (!repeating) {
                        finishComposition()
                        commit(" ")
                    }

                    view.performClick()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    task?.let { handler.removeCallbacks(it) }
                    true
                }

                else -> true
            }
        }

        parent.addView(
            button,
            LinearLayout.LayoutParams(0, 47.dp(), weight).apply {
                setMargins(3.dp(), 3.dp(), 3.dp(), 3.dp())
            }
        )
    }

    private fun refreshKeyboard() {
        setInputView(createKeyboard())
    }

    private fun commit(value: String) {
        currentInputConnection?.commitText(value, 1)
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

    private fun Int.dp(): Int {
        return (
            this * resources.displayMetrics.density
        ).toInt()
    }

    override fun onFinishInput() {
        finishComposition()
        super.onFinishInput()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        toneGenerator?.release()
        toneGenerator = null
        super.onDestroy()
    }
}
