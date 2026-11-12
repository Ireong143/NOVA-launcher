package com.nova.os.launcher.sand

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.math.BigDecimal
import java.math.RoundingMode

/** Context에서 Activity를 찾는다(없으면 null). */
fun sandActivityOf(context: Context): Activity? {
    var c: Context? = context
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** 설정 화면 등 외부 화면 열기. 열 수 없으면 false. */
fun sandStart(context: Context, intent: Intent): Boolean {
    return try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }
}

fun sandCopy(context: Context, label: String, text: String) {
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    } catch (e: Exception) {
        // 클립보드를 쓸 수 없으면 무시
    }
}

/**
 * 다른 화면(검색 창 등)에서 홈 위의 SAND 화면을 열어 달라고 요청하는 통로.
 * HomeScreen이 값을 보고 열고 나서 null로 되돌린다.
 */
object SandNav {
    var page by mutableStateOf<SandPage?>(null)

    /** 터미널을 열 때 입력창에 미리 채워 둘 명령어(자동 실행은 하지 않는다) */
    var terminalInput by mutableStateOf<String?>(null)
}

/** 한글 초성 검색을 포함한 문자열 매칭. */
object SandText {
    private val CHO = charArrayOf(
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
        'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    )

    fun choseong(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            if (ch in '가'..'힣') sb.append(CHO[(ch.code - 0xAC00) / 588]) else sb.append(ch)
        }
        return sb.toString()
    }

    fun isJamo(q: String): Boolean = q.isNotEmpty() && q.all { it in 'ㄱ'..'ㅎ' }

    fun matches(text: String, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return false
        if (text.lowercase().contains(q.lowercase())) return true
        if (isJamo(q)) return choseong(text).contains(q)
        return false
    }
}

/** 검색 창과 터미널의 계산기에서 쓰는 간단한 수식 계산기(+ - * / % ^ 괄호). */
object SandMath {
    private class Parser(val s: String) {
        var i = 0

        fun expr(): Double {
            var v = term()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i]
                i++
                val r = term()
                v = if (op == '+') v + r else v - r
            }
            return v
        }

        fun term(): Double {
            var v = power()
            while (i < s.length && (s[i] == '*' || s[i] == '/' || s[i] == '%')) {
                val op = s[i]
                i++
                val r = power()
                v = when (op) {
                    '*' -> v * r
                    '/' -> v / r
                    else -> v % r
                }
            }
            return v
        }

        fun power(): Double {
            val b = unary()
            if (i < s.length && s[i] == '^') {
                i++
                return Math.pow(b, power())
            }
            return b
        }

        fun unary(): Double {
            if (i < s.length && s[i] == '-') {
                i++
                return -unary()
            }
            if (i < s.length && s[i] == '+') {
                i++
                return unary()
            }
            return atom()
        }

        fun atom(): Double {
            if (i < s.length && s[i] == '(') {
                i++
                val v = expr()
                if (i < s.length && s[i] == ')') i++ else throw IllegalArgumentException("괄호")
                return v
            }
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (st == i) throw IllegalArgumentException("숫자")
            return s.substring(st, i).toDouble()
        }
    }

    /** 수식이 아니면 null. 숫자만 있는 입력(연산자 없음)도 null. */
    fun eval(src: String): Double? {
        val s = src.replace(" ", "").replace(",", "").replace("×", "*").replace("÷", "/")
            .replace("x", "*").replace("X", "*")
        if (s.isEmpty()) return null
        if (!s.all { it.isDigit() || it in "+-*/%^()." }) return null
        if (s.none { it in "+-*/%^" }) return null
        if (s.none { it.isDigit() }) return null
        return try {
            val p = Parser(s)
            val v = p.expr()
            if (p.i != s.length || v.isNaN() || v.isInfinite()) null else v
        } catch (e: Exception) {
            null
        }
    }

    fun format(v: Double): String {
        val bd = BigDecimal(v).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros()
        return bd.toPlainString()
    }
}
