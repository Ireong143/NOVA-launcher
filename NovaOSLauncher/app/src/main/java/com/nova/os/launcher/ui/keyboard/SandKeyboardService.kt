package com.nova.os.launcher.keyboard

import android.content.ClipboardManager
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
    private var emojiMode = false

    private var cho = -1
    private var jung = -1
    private var jong = 0

    private val handler = Handler(Looper.getMainLooper())
    private var toneGenerator: ToneGenerator? = null

    private val backgroundColor = Color.rgb(75, 77, 83)
    private val keyColor = Color.rgb(126, 128, 136)
    private val specialColor = Color.rgb(91, 94, 102)
    private val keyTextColor = Color.WHITE
    private val enterColor = Color.rgb(166, 198, 255)

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

    private val shiftMap = mapOf(
        "ㅂ" to "ㅃ", "ㅈ" to "ㅉ",
        "ㄷ" to "ㄸ", "ㄱ" to "ㄲ",
        "ㅅ" to "ㅆ", "ㅐ" to "ㅒ",
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

    private val emojiList = listOf(
        "😀", "😂", "🥹", "😍", "🥰", "😎",
        "😭", "😡", "🤔", "😊", "❤️", "💕",
        "👍", "👏", "🙏", "🔥", "✨", "🎉",
        "💯", "🌸", "🐱", "🐶", "🍀", "⭐"
    )

    override fun onCreate() {
        super.onCreate()
        toneGenerator = ToneGenerator(AudioManager.STREAM_SYSTEM, 30)
    }

    override fun onCreateInputView(): View {
        return createKeyboard()
    }

    private fun createKeyboard(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(5.dp(), 8.dp(), 5.dp(), 4.dp())
            setBackgroundColor(backgroundColor)
        }

        if (emojiMode) {
            createEmojiPanel(root)
        } else if (numberMode) {
            createNumberPanel(root)
        } else {
            createHangulPanel(root)
        }

        createBottomRow(root)
        createToolbar(root)

        return root
    }

    private fun createHangulPanel(root: LinearLayout) {
        val firstRow = listOf(
            "ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ",
            "ㅛ", "ㅕ", "ㅑ", "ㅐ", "ㅔ"
        )

        addKeyRow(root, firstRow, 1f) { typeKey(it) }

        val secondRow = listOf(
            "ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ",
            "ㅗ", "ㅓ", "ㅏ", "ㅣ"
        )

        addKeyRow(root, secondRow, 1f) { typeKey(it) }

        val third = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        addKey(third, "⇧", 1.35f, specialColor) {
            shiftEnabled = !shiftEnabled
            refreshKeyboard()
        }

        listOf("ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅠ", "ㅜ", "ㅡ")
            .forEach { key ->
                addKey(third, key, 1f, keyColor) {
                    typeKey(key)
                }
            }

        addDeleteKey(third, 1.35f)

        root.addView(
            third,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                59.dp()
            )
        )
    }

    private fun createNumberPanel(root: LinearLayout) {
        val rows = listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/"),
            listOf("=", "*", "\"", "'", ":", ";", "!", "?", "~")
        )

        rows.forEach { row ->
            addKeyRow(root, row, 1f) {
                finishComposition()
                commit(it)
            }
        }
    }

    private fun createEmojiPanel(root: LinearLayout) {
        emojiList.chunked(8).forEach { row ->
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            row.forEach { emoji ->
                addKey(line, emoji, 1f, specialColor) {
                    finishComposition()
                    commit(emoji)
                }
            }

            root.addView(
                line,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    54.dp()
                )
            )
        }
    }

    private fun addKeyRow(
        parent: LinearLayout,
        keys: List<String>,
        weight: Float,
        action: (String) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        keys.forEach { key ->
            addKey(row, key, weight, keyColor) {
                action(key)
            }
        }

        parent.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                59.dp()
            )
        )
    }

    private fun addKey(
        parent: LinearLayout,
        label: String,
        weight: Float,
        color: Int,
        action: () -> Unit
    ) {
        val shownLabel = when {
            !koreanMode && keyMap.containsKey(label) ->
                englishLabel(label)
            koreanMode && shiftEnabled ->
                shiftMap[label] ?: label
            else -> label
        }

        val button = makeKey(shownLabel, color)

        button.setOnClickListener {
            playSound()
            action()
        }

        parent.addView(
            button,
            LinearLayout.LayoutParams(0, 51.dp(), weight).apply {
                setMargins(3.dp(), 3.dp(), 3.dp(), 3.dp())
            }
        )
    }

    private fun makeKey(
        label: String,
        color: Int = keyColor
    ): Button {
        return Button(this).apply {
            text = label
            textSize = when {
                label.length > 2 -> 14f
                label.length == 2 -> 17f
                else -> 21f
            }
            setTextColor(
                if (color == enterColor) {
                    Color.rgb(255, 255, 255)
                } else {
                    keyTextColor
                }
            )
            typeface = Typeface.DEFAULT
            isAllCaps = false

            backgroundTintList = null
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 13.dp().toFloat()
                setColor(color)
                setStroke(
                    1.dp(),
                    Color.argb(75, 255, 255, 255)
                )
            }

            elevation = 2.dp().toFloat()
            stateListAnimator = null
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
        }
    }

    private fun addDeleteKey(
        parent: LinearLayout,
        weight: Float
    ) {
        val button = makeKey("⌫", specialColor)
        addRepeatTouch(button, true)

        parent.addView(
            button,
            LinearLayout.LayoutParams(0, 51.dp(), weight).apply {
                setMargins(3.dp(), 3.dp(), 3.dp(), 3.dp())
            }
        )
    }

    private fun addSpaceKey(
        parent: LinearLayout,
        weight: Float
    ) {
        val button = makeKey("간격", specialColor)
        button.textSize = 19f

        addRepeatTouch(button, false)

        parent.addView(
            button,
            LinearLayout.LayoutParams(0, 55.dp(), weight).apply {
                setMargins(3.dp(), 3.dp(), 3.dp(), 3.dp())
            }
        )
    }

    private fun addRepeatTouch(
        button: Button,
        deleteKey: Boolean
    ) {
        var repeating = false
        var repeatTask: Runnable? = null

        button.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    repeating = false
                    playSound()

                    val task = object : Runnable {
                        override fun run() {
                            repeating = true
                            deleteCharacter()
                            repeatTask = this
                            handler.postDelayed(this, 75)
                        }
                    }

                    repeatTask = task
                    handler.postDelayed(task, 450)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    repeatTask?.let {
                        handler.removeCallbacks(it)
                    }

                    if (!repeating) {
                        if (deleteKey) {
                            deleteCharacter()
                        } else {
                            finishComposition()
                            commit(" ")
                        }
                    }

                    view.performClick()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    repeatTask?.let {
                        handler.removeCallbacks(it)
                    }
                    true
                }

                else -> true
            }
        }
    }

    private fun createBottomRow(root: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        addBottomButton(row, "123", 1.05f) {
            finishComposition()
            numberMode = !numberMode
            emojiMode = false
            refreshKeyboard()
        }

        addBottomButton(row, "☻", 0.95f) {
            finishComposition()
            emojiMode = !emojiMode
            numberMode = false
            refreshKeyboard()
        }

        addSpaceKey(row, 3.8f)

        addBottomButton(row, "▢", 0.95f) {
            pasteClipboard()
        }

        addBottomButton(row, "↵", 1.25f, enterColor) {
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
                67.dp()
            )
        )
    }

    private fun addBottomButton(
        parent: LinearLayout,
        label: String,
        weight: Float,
        color: Int = specialColor,
        action: () -> Unit
    ) {
        val button = makeKey(label, color)

        button.setOnClickListener {
            playSound()
            action()
        }

        parent.addView(
            button,
            LinearLayout.LayoutParams(0, 55.dp(), weight).apply {
                setMargins(3.dp(), 3.dp(), 3.dp(), 3.dp())
            }
        )
    }

    private fun createToolbar(root: LinearLayout) {
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(7.dp(), 4.dp(), 7.dp(), 0)
        }

        val globe = Button(this).apply {
            text = "◎"
            textSize = 30f
            setTextColor(Color.WHITE)
            background = null
            backgroundTintList = null
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            stateListAnimator = null
        }

        globe.setOnClickListener {
            finishComposition()
            koreanMode = !koreanMode
            shiftEnabled = false
            numberMode = false
            emojiMode = false
            refreshKeyboard()
        }

        toolbar.addView(
            globe,
            LinearLayout.LayoutParams(0, 49.dp(), 0.8f)
        )

        val emojiBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 35.dp().toFloat()
                setColor(Color.rgb(94, 96, 103))
            }
        }

        listOf("😀", "😂", "❤️", "👍", "😭", "🔥", "•••")
            .forEach { emoji ->
                val button = Button(this).apply {
                    text = emoji
                    textSize = if (emoji == "•••") 17f else 22f
                    background = null
                    backgroundTintList = null
                    minWidth = 0
                    minimumWidth = 0
                    minHeight = 0
                    minimumHeight = 0
                    setPadding(2.dp(), 0, 2.dp(), 0)
                    stateListAnimator = null
                }

                button.setOnClickListener {
                    if (emoji == "•••") {
                        emojiMode = true
                        numberMode = false
                        refreshKeyboard()
                    } else {
                        finishComposition()
                        commit(emoji)
                    }
                }

                emojiBar.addView(
                    button,
                    LinearLayout.LayoutParams(0, 43.dp(), 1f)
                )
            }

        toolbar.addView(
            emojiBar,
            LinearLayout.LayoutParams(0, 45.dp(), 3.4f).apply {
                setMargins(5.dp(), 0, 5.dp(), 0)
            }
        )

        val language = Button(this).apply {
            text = if (koreanMode) "한/영  ⌄" else "EN/한  ⌄"
            textSize = 16f
            setTextColor(Color.WHITE)
            backgroundTintList = null
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 30.dp().toFloat()
                setColor(specialColor)
                setStroke(1.dp(), Color.argb(60, 255, 255, 255))
            }
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            stateListAnimator = null
        }

        language.setOnClickListener {
            finishComposition()
            koreanMode = !koreanMode
            shiftEnabled = false
            numberMode = false
            emojiMode = false
            refreshKeyboard()
        }

        toolbar.addView(
            language,
            LinearLayout.LayoutParams(0, 45.dp(), 1.8f)
        )

        root.addView(
            toolbar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                55.dp()
            )
        )
    }

    private fun typeKey(key: String) {
        if (!koreanMode) {
            val english = englishLabel(key)
            finishComposition()
            commit(english)

            if (shiftEnabled) {
                shiftEnabled = false
                refreshKeyboard()
            }
            return
        }

        val actualKey = if (shiftEnabled) {
            shiftMap[key] ?: key
        } else {
            key
        }

        typeKorean(actualKey)

        if (shiftEnabled) {
            shiftEnabled = false
            refreshKeyboard()
        }
    }

    private fun englishLabel(key: String): String {
        val value = keyMap[key] ?: key
        return if (shiftEnabled) value.uppercase() else value
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

        currentInputConnection?.commitText(composeSyllable(), 1)

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

    private fun pasteClipboard() {
        val clipboard = getSystemService(
            Context.CLIPBOARD_SERVICE
        ) as ClipboardManager

        if (clipboard.hasPrimaryClip()) {
            val text = clipboard.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(this)
                ?.toString()

            if (!text.isNullOrEmpty()) {
                finishComposition()
                commit(text)
            }
        } else {
            Toast.makeText(
                this,
                "클립보드가 비어 있어요.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun commit(value: String) {
        currentInputConnection?.commitText(value, 1)
    }

    private fun refreshKeyboard() {
        setInputView(createKeyboard())
    }

    private fun playSound() {
        try {
            toneGenerator?.startTone(
                ToneGenerator.TONE_PROP_BEEP,
                30
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
