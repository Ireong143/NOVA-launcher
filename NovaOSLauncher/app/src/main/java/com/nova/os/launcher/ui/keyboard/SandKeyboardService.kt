
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

class SandKeyboardService : InputMethodService() {

    private var koreanMode = true
    private var shiftEnabled = false
    private var symbolMode = false
    private var toneGenerator: ToneGenerator? = null

    private var cho = -1
    private var jung = -1
    private var jong = 0

    private val handler = Handler(Looper.getMainLooper())

    private val backgroundColor = Color.rgb(91, 94, 100)
    private val keyColor = Color.rgb(174, 178, 184)
    private val specialKeyColor = Color.rgb(119, 124, 132)
    private val textColor = Color.WHITE
    private val accentColor = Color.rgb(111, 190, 205)

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
        'q' to "ㅃ",
        'w' to "ㅉ",
        'e' to "ㄸ",
        'r' to "ㄲ",
        't' to "ㅆ",
        'o' to "ㅒ",
        'p' to "ㅖ"
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

    private val symbolKeys = listOf(
        "!", "@", "#", "$", "%", "&", "*", "-", "+", "="
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
            setPadding(4.dp(), 7.dp(), 4.dp(), 7.dp())
            setBackgroundColor(backgroundColor)
        }

        val topKeys = if (symbolMode) {
            symbolKeys
        } else {
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        }

        addRow(root, topKeys, 39) { key ->
            finishComposition()
            commit(key)
        }

        val row1 = "qwertyuiop".map { displayKey(it) }
        addRow(root, row1, 47) { label ->
            typeEnglishOrKorean(label)
        }

        val row2 = "asdfghjkl".map { displayKey(it) }
        addRow(root, row2, 47) { label ->
            typeEnglishOrKorean(label)
        }

        val row3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        addSpecialButton(
            row3,
            if (shiftEnabled) "⇧" else "↑",
            1.25f
        ) {
            shiftEnabled = !shiftEnabled
            refreshKeyboard()
        }

        "zxcvbnm".forEach { character ->
            val label = displayKey(character)
            addKey(row3, label, 1f) {
                typeEnglishOrKorean(label)
            }
        }

        addRepeatDeleteButton(row3, "⌫", 1.3f)

        root.addView(
            row3,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                49.dp()
            )
        )

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        addSpecialButton(bottom, "😊", 0.85f) {
            finishComposition()
            commit("😊")
        }

        addSpecialButton(
            bottom,
            if (koreanMode) "한/영" else "EN/한",
            1f
        ) {
            finishComposition()
            koreanMode = !koreanMode
            shiftEnabled = false
            symbolMode = false
            refreshKeyboard()
        }

        addSpaceButton(bottom, 2.8f)

        addSpecialButton(bottom, "클립", 0.9f) {
            finishComposition()
            pasteClipboard()
        }

        addSpecialButton(bottom, "↵", 0.9f) {
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
                51.dp()
            )
        )

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dp(), 2.dp(), 12.dp(), 0)
        }

        addFooterButton(footer, "◎", 1f) {
            symbolMode = !symbolMode
            finishComposition()
            refreshKeyboard()
        }

        addFooterButton(footer, "SAND", 1f) {
            finishComposition()
            koreanMode = !koreanMode
            refreshKeyboard()
        }

        root.addView(
            footer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                29.dp()
            )
        )

        return root
    }

    private fun displayKey(character: Char): String {
        if (!koreanMode) {
            return if (shiftEnabled) {
                character.uppercaseChar().toString()
            } else {
                character.toString()
            }
        }

        return if (shiftEnabled) {
            shiftMap[character] ?: keyMap[character] ?: character.toString()
        } else {
            keyMap[character] ?: character.toString()
        }
    }

    private fun addRow(
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
            addKey(row, key, 1f) {
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

    private fun addKey(
        parent: LinearLayout,
        label: String,
        weight: Float,
        action: () -> Unit
    ) {
        val button = createKeyButton(label)

        button.setOnClickListener {
            playSound()
            action()
        }

        parent.addView(
            button,
            LinearLayout.LayoutParams(0, 43.dp(), weight).apply {
                setMargins(2.dp(), 3.dp(), 2.dp(), 3.dp())
            }
        )
    }

    // Angular rectangular key design
    private fun createKeyButton(
        label: String,
        special: Boolean = false
    ): Button {
        return Button(this).apply {
            text = label
            textSize = if (label.length > 2) 13f else 17f
            setTextColor(textColor)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false

            backgroundTintList = null
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 3.dp().toFloat()
                setColor(
                    if (special) {
                        specialKeyColor
                    } else {
                        keyColor
                    }
                )
                setStroke(
                    1.dp(),
                    Color.argb(30, 255, 255, 255)
                )
            }

            stateListAnimator = null
            elevation = 0f
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
        val button = createKeyButton(label, true)

        button.setOnClickListener {
            playSound()
            action()
        }

        parent.addView(
            button,
            LinearLayout.LayoutParams(0, 43.dp(), weight).apply {
                setMargins(2.dp(), 3.dp(), 2.dp(), 3.dp())
            }
        )
    }

    private fun addFooterButton(
        parent: LinearLayout,
        label: String,
        weight: Float,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            text = label
            textSize = 13f
            setTextColor(accentColor)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            backgroundTintList = null
            background = GradientDrawable().apply {
                setColor(backgroundColor)
                cornerRadius = 0f
            }
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            stateListAnimator = null
        }

        button.setOnClickListener {
            action()
        }

        parent.addView(
            button,
            LinearLayout.LayoutParams(0, 28.dp(), weight)
        )
    }

    private fun addSpaceButton(
        parent: LinearLayout,
        weight: Float
    ) {
        val button = createKeyButton("간격", true)
        button.textSize = 15f

        var isRepeating = false
        var repeatTask: Runnable? = null

        button.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isRepeating = false
                    playSound()

                    val task = object : Runnable {
                        override fun run() {
                            isRepeating = true
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

                    if (!isRepeating) {
                        finishComposition()
                        commit(" ")
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

        parent.addView(
            button,
            LinearLayout.LayoutParams(0, 43.dp(), weight).apply {
                setMargins(3.dp(), 3.dp(), 3.dp(), 3.dp())
            }
        )
    }

    private fun addRepeatDeleteButton(
        parent: LinearLayout,
        label: String,
        weight: Float
    ) {
        val button = createKeyButton(label, true)

        var isRepeating = false
        var repeatTask: Runnable? = null

        button.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isRepeating = false
                    playSound()

                    val task = object : Runnable {
                        override fun run() {
                            isRepeating = true
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

                    if (!isRepeating) {
                        deleteCharacter()
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

        parent.addView(
            button,
            LinearLayout.LayoutParams(0, 43.dp(), weight).apply {
                setMargins(2.dp(), 3.dp(), 2.dp(), 3.dp())
            }
        )
    }

    private fun refreshKeyboard() {
        setInputView(createKeyboard())
    }

    private fun typeEnglishOrKorean(label: String) {
        if (!koreanMode) {
            finishComposition()
            commit(label)

            if (shiftEnabled) {
                shiftEnabled = false
                refreshKeyboard()
            }
            return
        }

        val character = "qwertyuiopasdfghjklzxcvbnm"
            .firstOrNull { displayKey(it) == label }

        if (character == null) return

        val jamo = if (shiftEnabled) {
            shiftMap[character] ?: keyMap[character]
        } else {
            keyMap[character]
        }

        if (jamo != null) {
            typeKorean(jamo)
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
            val finalIndex = finals.indexOf(key)

            if (finalIndex > 0) {
                jong = finalIndex
                updateComposition()
                return
            }
        } else {
            val previous = finals[jong]
            val combined = compoundFinals[previous + key]

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

        val movedConsonant: String

        if (split != null) {
            jong = finals.indexOf(split.first)
            movedConsonant = split.second
        } else {
            jong = 0
            movedConsonant = oldFinal
        }

        val previousSyllable = composeSyllable()
        currentInputConnection?.commitText(previousSyllable, 1)

        cho = initials.indexOf(movedConsonant)
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

            if (!text.isNullOrEmpty()) {
                finishComposition()
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
        }
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
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
