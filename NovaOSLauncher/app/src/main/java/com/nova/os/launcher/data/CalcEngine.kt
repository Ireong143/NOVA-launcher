package com.nova.os.launcher.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Expression evaluator: + − × ÷ % and parentheses, with operator precedence. */
object CalcEngine {
    class CalcError(message: String) : Exception(message)

    fun evaluate(input: String): Double {
        val parser = Parser(input.replace('×', '*').replace('÷', '/').replace('−', '-').replace(" ", ""))
        val v = parser.parseExpr()
        if (!parser.atEnd()) throw CalcError("식이 올바르지 않아요")
        if (v.isNaN() || v.isInfinite()) throw CalcError("계산할 수 없어요")
        return v
    }

    fun plain(v: Double): String {
        if (v == 0.0) return "0"
        val bd = BigDecimal(v).round(MathContext(12, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        return bd.toPlainString().replace('-', '−')
    }

    fun format(v: Double): String {
        val p = plain(v)
        val negative = p.startsWith("−")
        val parts = p.removePrefix("−").split('.')
        val grouped = parts[0].reversed().chunked(3).joinToString(",").reversed()
        return (if (negative) "−" else "") + grouped + (if (parts.size > 1) "." + parts[1] else "")
    }

    private class Parser(private val s: String) {
        private var pos = 0
        private var lastPercent = false

        fun atEnd() = pos >= s.length
        private fun peek(): Char? = if (pos < s.length) s[pos] else null

        fun parseExpr(): Double {
            var left = parseTerm()
            while (true) {
                val c = peek()
                if (c == '+' || c == '-') {
                    pos++
                    val right = parseTerm()
                    // "200 + 10%" means 200 + (10% of 200)
                    val rv = if (lastPercent) left * right else right
                    left = if (c == '+') left + rv else left - rv
                } else break
            }
            return left
        }

        private fun parseTerm(): Double {
            var left = parseFactor()
            while (true) {
                val c = peek()
                if (c == '*' || c == '/') {
                    pos++
                    val right = parseFactor()
                    lastPercent = false
                    if (c == '/') {
                        if (right == 0.0) throw CalcError("0으로 나눌 수 없어요")
                        left /= right
                    } else {
                        left *= right
                    }
                } else break
            }
            return left
        }

        private fun parseFactor(): Double {
            val c = peek()
            if (c == '-') {
                pos++
                return -parseFactor()
            }
            if (c == '+') {
                pos++
                return parseFactor()
            }
            var v = parsePrimary()
            lastPercent = false
            while (peek() == '%') {
                pos++
                v /= 100.0
                lastPercent = true
            }
            return v
        }

        private fun parsePrimary(): Double {
            val c = peek() ?: throw CalcError("식이 완성되지 않았어요")
            if (c == '(') {
                pos++
                val v = parseExpr()
                if (peek() == ')') pos++ else throw CalcError("괄호가 닫히지 않았어요")
                return v
            }
            if (c.isDigit() || c == '.') {
                val start = pos
                while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
                return s.substring(start, pos).toDoubleOrNull() ?: throw CalcError("숫자 형식 오류")
            }
            throw CalcError("식이 올바르지 않아요")
        }
    }
}

/** Observable calculator state (keypad logic). */
class CalcState {
    var expr by mutableStateOf("")
        private set
    var preview by mutableStateOf("")
        private set
    val history = mutableStateListOf<String>()
    private var justEvaluated = false

    fun input(key: String) {
        when (key) {
            "C" -> {
                expr = ""
                preview = ""
                justEvaluated = false
            }
            "⌫" -> {
                if (justEvaluated) {
                    expr = ""
                    justEvaluated = false
                } else {
                    expr = expr.dropLast(1)
                }
                refresh()
            }
            "=" -> evaluateNow()
            "()" -> paren()
            "." -> dot()
            "+", "−", "×", "÷" -> applyOperator(key)
            "%" -> percent()
            else -> if (key.length == 1 && key[0].isDigit()) digit(key)
        }
    }

    fun clearHistory() = history.clear()

    private fun currentNumber(): String = expr.takeLastWhile { it.isDigit() || it == '.' }

    private fun endsWithValue(): Boolean {
        val last = expr.lastOrNull() ?: return false
        return last.isDigit() || last == ')' || last == '%'
    }

    private fun digit(d: String) {
        if (justEvaluated) {
            expr = ""
            justEvaluated = false
        }
        if (currentNumber() == "0") expr = expr.dropLast(1)
        expr += if (expr.lastOrNull() == ')' || expr.lastOrNull() == '%') "×$d" else d
        refresh()
    }

    private fun dot() {
        if (justEvaluated) {
            expr = ""
            justEvaluated = false
        }
        val cur = currentNumber()
        if (cur.contains('.')) return
        expr += when {
            cur.isEmpty() && endsWithValue() -> "×0."
            cur.isEmpty() -> "0."
            else -> "."
        }
        refresh()
    }

    private fun applyOperator(op: String) {
        justEvaluated = false
        if (expr.isEmpty()) {
            if (op == "−") expr = "−"
            return
        }
        val last = expr.last()
        if (last == '(') {
            if (op == "−") expr += op
            refresh()
            return
        }
        if (last in "+−×÷") {
            if (expr.length == 1) return
            expr = expr.dropLast(1) + op
        } else {
            expr += op
        }
        refresh()
    }

    private fun percent() {
        if (endsWithValue()) {
            justEvaluated = false
            expr += "%"
            refresh()
        }
    }

    private fun paren() {
        justEvaluated = false
        val open = expr.count { it == '(' }
        val close = expr.count { it == ')' }
        val canClose = open > close && endsWithValue()
        expr += when {
            canClose -> ")"
            endsWithValue() -> "×("
            else -> "("
        }
        refresh()
    }

    private fun autoClose(e: String): String {
        val missing = e.count { it == '(' } - e.count { it == ')' }
        return if (missing > 0) e + ")".repeat(missing) else e
    }

    private fun evaluateNow() {
        if (expr.isEmpty()) return
        try {
            val closed = autoClose(expr)
            val value = CalcEngine.evaluate(closed)
            history.add(0, "$closed = ${CalcEngine.format(value)}")
            while (history.size > 30) history.removeAt(history.lastIndex)
            expr = CalcEngine.plain(value)
            preview = ""
            justEvaluated = true
        } catch (e: CalcEngine.CalcError) {
            preview = e.message ?: "오류"
        }
    }

    private fun refresh() {
        val hasOperator = expr.any { it in "+×÷%()" } || expr.drop(1).contains('−')
        preview = if (hasOperator) {
            try {
                CalcEngine.format(CalcEngine.evaluate(autoClose(expr)))
            } catch (e: Exception) {
                ""
            }
        } else {
            ""
        }
    }
}
