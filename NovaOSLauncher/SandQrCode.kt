package com.nova.os.launcher.sand

/**
 * 외부 라이브러리 없이 만든 QR 코드 인코더 (버전 1~10, 바이트 모드).
 * 오류 정정 단계: 0=L, 1=M, 2=Q, 3=H. 결과는 true=검은 칸인 정사각 행렬.
 */
object SandQrCode {
    private val ECC_PER_BLOCK = arrayOf(
        intArrayOf(7, 10, 15, 20, 26, 18, 20, 24, 30, 18),
        intArrayOf(10, 16, 26, 18, 24, 16, 18, 22, 22, 26),
        intArrayOf(13, 22, 18, 26, 18, 24, 18, 22, 20, 24),
        intArrayOf(17, 28, 22, 16, 22, 28, 26, 26, 24, 28),
    )
    private val NUM_BLOCKS = arrayOf(
        intArrayOf(1, 1, 1, 1, 1, 2, 2, 2, 2, 4),
        intArrayOf(1, 1, 1, 2, 2, 4, 4, 4, 5, 5),
        intArrayOf(1, 1, 2, 2, 4, 4, 6, 6, 8, 8),
        intArrayOf(1, 1, 2, 4, 4, 4, 5, 6, 8, 8),
    )
    private val FORMAT_BITS = intArrayOf(1, 0, 3, 2)
    private val ALIGN = arrayOf(
        intArrayOf(),
        intArrayOf(),
        intArrayOf(6, 18),
        intArrayOf(6, 22),
        intArrayOf(6, 26),
        intArrayOf(6, 30),
        intArrayOf(6, 34),
        intArrayOf(6, 22, 38),
        intArrayOf(6, 24, 42),
        intArrayOf(6, 26, 46),
        intArrayOf(6, 28, 50),
    )

    private fun rawModules(v: Int): Int {
        var r = (16 * v + 128) * v + 64
        if (v >= 2) {
            val n = v / 7 + 2
            r -= (25 * n - 10) * n - 55
            if (v >= 7) r -= 36
        }
        return r
    }

    private fun dataCodewords(v: Int, e: Int): Int =
        rawModules(v) / 8 - ECC_PER_BLOCK[e][v - 1] * NUM_BLOCKS[e][v - 1]

    private fun gfMul(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }

    private fun rsDivisor(degree: Int): IntArray {
        val result = IntArray(degree)
        result[degree - 1] = 1
        var root = 1
        for (i in 0 until degree) {
            for (j in result.indices) {
                result[j] = gfMul(result[j], root)
                if (j + 1 < result.size) result[j] = result[j] xor result[j + 1]
            }
            root = gfMul(root, 2)
        }
        return result
    }

    private fun rsRemainder(data: IntArray, divisor: IntArray): IntArray {
        val result = IntArray(divisor.size)
        for (b in data) {
            val factor = b xor result[0]
            for (i in 0 until result.size - 1) result[i] = result[i + 1]
            result[result.size - 1] = 0
            for (i in divisor.indices) result[i] = result[i] xor gfMul(divisor[i], factor)
        }
        return result
    }

    /** 너무 길면 IllegalArgumentException. */
    fun encode(text: String, ecl: Int): Array<BooleanArray> {
        val e = ecl.coerceIn(0, 3)
        val data = text.toByteArray(Charsets.UTF_8)
        var version = 0
        for (v in 1..10) {
            val cc = if (v <= 9) 8 else 16
            if (4 + cc + data.size * 8 <= dataCodewords(v, e) * 8) {
                version = v
                break
            }
        }
        require(version != 0) { "too long" }
        val cc = if (version <= 9) 8 else 16
        val cap = dataCodewords(version, e) * 8

        val bits = ArrayList<Int>()
        fun app(value: Int, n: Int) {
            for (i in n - 1 downTo 0) bits.add((value ushr i) and 1)
        }
        app(4, 4)
        app(data.size, cc)
        for (b in data) app(b.toInt() and 0xFF, 8)
        app(0, minOf(4, cap - bits.size))
        app(0, (-bits.size) and 7)
        var pad = 0xEC
        while (bits.size < cap) {
            app(pad, 8)
            pad = pad xor 0xEC xor 0x11
        }
        val codewords = IntArray(bits.size / 8)
        for (i in codewords.indices) {
            var s = 0
            for (j in 0 until 8) s = s or (bits[i * 8 + j] shl (7 - j))
            codewords[i] = s
        }
        return build(version, e, codewords)
    }

    private fun build(v: Int, ecl: Int, data: IntArray): Array<BooleanArray> {
        val size = v * 4 + 17
        val mod = Array(size) { BooleanArray(size) }
        val fn = Array(size) { BooleanArray(size) }

        fun setF(x: Int, y: Int, d: Boolean) {
            mod[y][x] = d
            fn[y][x] = true
        }

        for (i in 0 until size) {
            setF(6, i, i % 2 == 0)
            setF(i, 6, i % 2 == 0)
        }
        fun finder(x: Int, y: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val dist = maxOf(Math.abs(dx), Math.abs(dy))
                val xx = x + dx
                val yy = y + dy
                if (xx in 0 until size && yy in 0 until size) setF(xx, yy, dist != 2 && dist != 4)
            }
        }
        finder(3, 3)
        finder(size - 4, 3)
        finder(3, size - 4)
        val pos = ALIGN[v]
        val n = pos.size
        for (i in 0 until n) for (j in 0 until n) {
            if ((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)) continue
            for (dy in -2..2) for (dx in -2..2) {
                setF(pos[i] + dx, pos[j] + dy, maxOf(Math.abs(dx), Math.abs(dy)) != 1)
            }
        }

        fun format(mask: Int) {
            val d = (FORMAT_BITS[ecl] shl 3) or mask
            var rem = d
            for (i in 0 until 10) rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
            val bits = ((d shl 10) or rem) xor 0x5412
            fun g(i: Int): Boolean = ((bits ushr i) and 1) == 1
            for (i in 0..5) setF(8, i, g(i))
            setF(8, 7, g(6))
            setF(8, 8, g(7))
            setF(7, 8, g(8))
            for (i in 9..14) setF(14 - i, 8, g(i))
            for (i in 0..7) setF(size - 1 - i, 8, g(i))
            for (i in 8..14) setF(8, size - 15 + i, g(i))
            setF(8, size - 8, true)
        }
        format(0)
        if (v >= 7) {
            var rem = v
            for (i in 0 until 12) rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
            val bits = (v shl 12) or rem
            for (i in 0 until 18) {
                val b = ((bits ushr i) and 1) == 1
                val a = size - 11 + i % 3
                val c = i / 3
                setF(a, c, b)
                setF(c, a, b)
            }
        }

        // 오류 정정 코드 계산 + 블록 인터리브
        val nb = NUM_BLOCKS[ecl][v - 1]
        val el = ECC_PER_BLOCK[ecl][v - 1]
        val raw = rawModules(v) / 8
        val ns = nb - raw % nb
        val sl = raw / nb
        val div = rsDivisor(el)
        val blocks = ArrayList<IntArray>()
        var k = 0
        for (i in 0 until nb) {
            val dl = sl - el + (if (i < ns) 0 else 1)
            val dat = data.copyOfRange(k, k + dl)
            k += dl
            val ecc = rsRemainder(dat, div)
            val block = IntArray(sl + 1)
            // 짧은 블록은 데이터 끝에 자리표시 0을 하나 둔다(인터리브에서 건너뜀).
            for (t in dat.indices) block[t] = dat[t]
            val eccStart = sl + 1 - el
            for (t in ecc.indices) block[eccStart + t] = ecc[t]
            blocks.add(block)
        }
        val all = ArrayList<Int>()
        for (i in 0..sl) {
            for (j in 0 until nb) {
                if (i != sl - el || j >= ns) all.add(blocks[j][i])
            }
        }

        // 데이터 배치(지그재그)
        var idx = 0
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until size) {
                for (j in 0..1) {
                    val x = right - j
                    val up = ((right + 1) and 2) == 0
                    val y = if (up) size - 1 - vert else vert
                    if (!fn[y][x] && idx < all.size * 8) {
                        mod[y][x] = ((all[idx ushr 3] ushr (7 - (idx and 7))) and 1) == 1
                        idx++
                    }
                }
            }
            right -= 2
        }

        fun applyMask(m: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                val inv = when (m) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (inv && !fn[y][x]) mod[y][x] = !mod[y][x]
            }
        }

        fun penalty(): Int {
            var p = 0
            for (dir in 0..1) {
                for (a in 0 until size) {
                    var run = 1
                    for (b in 1 until size) {
                        val cur = if (dir == 0) mod[a][b] else mod[b][a]
                        val prev = if (dir == 0) mod[a][b - 1] else mod[b - 1][a]
                        if (cur == prev) {
                            run++
                        } else {
                            if (run >= 5) p += run - 2
                            run = 1
                        }
                    }
                    if (run >= 5) p += run - 2
                }
            }
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                val c = mod[y][x]
                if (c == mod[y][x + 1] && c == mod[y + 1][x] && c == mod[y + 1][x + 1]) p += 3
            }
            var dark = 0
            for (y in 0 until size) for (x in 0 until size) if (mod[y][x]) dark++
            val tot = size * size
            val kk = (Math.abs(dark * 20 - tot * 10) + tot - 1) / tot - 1
            p += maxOf(kk, 0) * 10
            return p
        }

        var best = 0
        var bestPenalty = Int.MAX_VALUE
        for (m in 0 until 8) {
            applyMask(m)
            format(m)
            val pp = penalty()
            if (pp < bestPenalty) {
                bestPenalty = pp
                best = m
            }
            applyMask(m)
        }
        applyMask(best)
        format(best)
        return mod
    }
}
