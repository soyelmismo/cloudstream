/**
 * MIT License
 *
 * Copyright (c) 2026 Konstantin Tskhovrebov
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.Prerelease
import kotlin.math.round

// Taken from https://github.com/terrakok/FuzzyKot/blob/f794d43/fuzzykot/src/commonMain/kotlin/com/github/terrakok/fuzzykot/Levenshtein.kt
object Levenshtein {
    fun ratio(s1: String, s2: String, processor: (String) -> String = { it }): Int {
        val p1 = processor(s1)
        val p2 = processor(s2)
        return round(100 * basicRatio(p1, p2)).toInt()
    }

    fun partialRatio(s1: String, s2: String, processor: (String) -> String = { it }): Int {
        val p1 = processor(s1)
        val p2 = processor(s2)

        val shorter: String
        val longer: String

        if (p1.length < p2.length) {
            shorter = p1
            longer = p2
        } else {
            shorter = p2
            longer = p1
        }

        if (shorter.isEmpty()) return if (longer.isEmpty()) 100 else 0

        val matchingBlocks = getMatchingBlocks(shorter.length, longer.length, getEditOps(shorter, longer))
        val scores = mutableListOf<Double>()

        for (mb in matchingBlocks) {
            val dist = mb.dpos - mb.spos
            val longStart = if (dist > 0) dist else 0
            var longEnd = longStart + shorter.length
            if (longEnd > longer.length) longEnd = longer.length

            val longSubstr = longer.substring(longStart, longEnd)
            val ratio = basicRatio(shorter, longSubstr)

            if (ratio > .995) return 100
            scores.add(ratio)
        }

        return round(100 * (scores.maxOrNull() ?: 0.0)).toInt()
    }

    private fun basicRatio(s1: String, s2: String): Double {
        val lensum = s1.length + s2.length
        if (lensum == 0) return 1.0
        val editDistance = levEditDistance(s1, s2, 1)
        return (lensum - editDistance) / lensum.toDouble()
    }
}

private enum class EditType {
    DELETE,
    EQUAL,
    INSERT,
    REPLACE,
    KEEP
}

private data class EditOp(
    var type: EditType? = null,
    var spos: Int = 0,
    var dpos: Int = 0
) {
    override fun toString(): String = "${type?.name ?: "null"}($spos,$dpos)"
}

private data class MatchingBlock(
    val spos: Int = 0,
    val dpos: Int = 0,
    val length: Int = 0
) {
    override fun toString(): String = "($spos,$dpos,$length)"
}

private fun getEditOps(s1: String, s2: String): Array<EditOp> {
    var len1Copy = s1.length
    var len2Copy = s2.length

    var len1o = 0
    var i = 0

    val matrix: IntArray

    val c1 = s1
    val c2 = s2

    var p1 = 0
    var p2 = 0

    while (len1Copy > 0 && len2Copy > 0 && c1[p1] == c2[p2]) {
        len1Copy--
        len2Copy--
        p1++
        p2++
        len1o++
    }

    val len2o = len1o

    while (len1Copy > 0 && len2Copy > 0 && c1[p1 + len1Copy - 1] == c2[p2 + len2Copy - 1]) {
        len1Copy--
        len2Copy--
    }

    len1Copy++
    len2Copy++

    matrix = IntArray(len2Copy * len1Copy)

    while (i < len2Copy) {
        matrix[i] = i
        i++
    }
    i = 1
    while (i < len1Copy) {
        matrix[len2Copy * i] = i
        i++
    }

    i = 1
    while (i < len1Copy) {
        var ptrPrev = (i - 1) * len2Copy
        var ptrC = i * len2Copy
        val ptrEnd = ptrC + len2Copy - 1

        val char1 = c1[p1 + i - 1]
        var ptrChar2 = p2

        var x = i
        ptrC++

        while (ptrC <= ptrEnd) {
            var c3 = matrix[ptrPrev++] + if (char1 != c2[ptrChar2++]) 1 else 0
            x++
            if (x > c3) x = c3
            c3 = matrix[ptrPrev] + 1
            if (x > c3) x = c3
            matrix[ptrC++] = x
        }
        i++
    }

    return editOpsFromCostMatrix(len1Copy, c1, p1, len1o, len2Copy, c2, p2, len2o, matrix)
}

private fun editOpsFromCostMatrix(
    len1: Int, c1: String, p1: Int, o1: Int,
    len2: Int, c2: String, p2: Int, o2: Int,
    matrix: IntArray
): Array<EditOp> {
    var i: Int = len1 - 1
    var j: Int = len2 - 1
    var pos: Int = matrix[len1 * len2 - 1]
    var ptr: Int = len1 * len2 - 1
    val ops: Array<EditOp?> = arrayOfNulls(pos)
    var dir = 0

    while (i > 0 || j > 0) {
        if (dir < 0 && j != 0 && matrix[ptr] == matrix[ptr - 1] + 1) {
            val eop = EditOp()
            pos--
            ops[pos] = eop
            eop.type = EditType.INSERT
            eop.spos = i + o1
            eop.dpos = --j + o2
            ptr--
            continue
        }

        if (dir > 0 && i != 0 && matrix[ptr] == matrix[ptr - len2] + 1) {
            val eop = EditOp()
            pos--
            ops[pos] = eop
            eop.type = EditType.DELETE
            eop.spos = --i + o1
            eop.dpos = j + o2
            ptr -= len2
            continue
        }

        if (i != 0 && j != 0 && matrix[ptr] == matrix[ptr - len2 - 1] && c1[p1 + i - 1] == c2[p2 + j - 1]) {
            i--
            j--
            ptr -= len2 + 1
            dir = 0
            continue
        }

        if (i != 0 && j != 0 && matrix[ptr] == matrix[ptr - len2 - 1] + 1) {
            pos--
            val eop = EditOp()
            ops[pos] = eop
            eop.type = EditType.REPLACE
            eop.spos = --i + o1
            eop.dpos = --j + o2
            ptr -= len2 + 1
            dir = 0
            continue
        }

        if (dir == 0 && j != 0 && matrix[ptr] == matrix[ptr - 1] + 1) {
            pos--
            val eop = EditOp()
            ops[pos] = eop
            eop.type = EditType.INSERT
            eop.spos = i + o1
            eop.dpos = --j + o2
            ptr--
            dir = -1
            continue
        }

        if (dir == 0 && i != 0 && matrix[ptr] == matrix[ptr - len2] + 1) {
            pos--
            val eop = EditOp()
            ops[pos] = eop
            eop.type = EditType.DELETE
            eop.spos = --i + o1
            eop.dpos = j + o2
            ptr -= len2
            dir = 1
            continue
        }
    }

    return ops.requireNoNulls()
}

private data class ScanState(val i: Int, val o: Int, val spos: Int, val dpos: Int)

private fun advanceMatchingOps(
    ops: Array<EditOp>,
    initialO: Int,
    initialI: Int,
    type: EditType?,
    initialSpos: Int,
    initialDpos: Int
): ScanState {
    var o = initialO
    var i = initialI
    var spos = initialSpos
    var dpos = initialDpos

    when (type) {
        EditType.REPLACE -> do {
            spos++
            dpos++
            i--
            o++
        } while (i != 0 && ops[o].type === type && spos == ops[o].spos && dpos == ops[o].dpos)

        EditType.DELETE -> do {
            spos++
            i--
            o++
        } while (i != 0 && ops[o].type === type && spos == ops[o].spos && dpos == ops[o].dpos)

        EditType.INSERT -> do {
            dpos++
            i--
            o++
        } while (i != 0 && ops[o].type === type && spos == ops[o].spos && dpos == ops[o].dpos)

        else -> {}
    }
    return ScanState(i, o, spos, dpos)
}

private fun getMatchingBlocks(len1: Int, len2: Int, ops: Array<EditOp>): Array<MatchingBlock> {
    val blocks = mutableListOf<MatchingBlock>()
    var spos = 0
    var dpos = 0
    var i = ops.size
    var o = 0

    while (i != 0) {
        while (ops[o].type === EditType.KEEP && --i != 0) o++
        if (i == 0) break

        if (spos < ops[o].spos || dpos < ops[o].dpos) {
            blocks.add(MatchingBlock(spos = spos, dpos = dpos, length = ops[o].spos - spos))
            spos = ops[o].spos
            dpos = ops[o].dpos
        }

        val nextState = advanceMatchingOps(ops, o, i, ops[o].type, spos, dpos)
        i = nextState.i
        o = nextState.o
        spos = nextState.spos
        dpos = nextState.dpos
    }

    if (spos < len1 || dpos < len2) {
        blocks.add(MatchingBlock(spos = spos, dpos = dpos, length = len1 - spos))
    }
    blocks.add(MatchingBlock(spos = len1, dpos = len2, length = 0))
    return blocks.toTypedArray()
}

private data class AffixTrimResult(
    val c1: String,
    val c2: String,
    val str1: Int,
    val str2: Int,
    val len1: Int,
    val len2: Int
)

private fun trimAffixes(s1: String, s2: String): AffixTrimResult {
    var c1 = s1
    var c2 = s2
    var str1 = 0
    var str2 = 0
    var len1 = s1.length
    var len2 = s2.length

    while (len1 > 0 && len2 > 0 && c1[str1] == c2[str2]) {
        len1--; len2--; str1++; str2++
    }
    while (len1 > 0 && len2 > 0 && c1[str1 + len1 - 1] == c2[str2 + len2 - 1]) {
        len1--; len2--
    }
    if (len1 > len2) {
        return AffixTrimResult(c2, c1, str2, str1, len2, len1)
    }
    return AffixTrimResult(c1, c2, str1, str2, len1, len2)
}

private fun computeDistanceWithCost(
    len1: Int,
    len2: Int,
    c1: String,
    str1: Int,
    c2: String,
    str2: Int
): Int {
    val row = IntArray(len2)
    val end = len2 - 1
    for (i in 0..end) row[i] = i

    for (i in 1 until len1) {
        var p = 1
        val ch1 = c1[str1 + i - 1]
        var c2p = str2
        var d = i
        var x = i
        while (p <= end) {
            if (ch1 == c2[c2p++]) {
                x = --d
            } else {
                x++
            }
            d = row[p] + 1
            if (x > d) x = d
            row[p++] = x
        }
    }
    return row[end]
}

private fun computeDistanceWithoutCost(
    len1: Int,
    len2: Int,
    c1: String,
    str1: Int,
    c2: String,
    str2: Int
): Int {
    val half = len1 shr 1
    val row = IntArray(len2)
    var end = len2 - 1
    for (i in 0 until (len2 - half)) row[i] = i
    row[0] = len1 - half - 1

    for (i in 1 until len1) {
        var p: Int
        val ch1 = c1[str1 + i - 1]
        var c2p: Int
        var d: Int
        var x: Int

        if (i >= len1 - half) {
            val offset = i - (len1 - half)
            c2p = str2 + offset
            p = offset
            val c3 = row[p++] + if (ch1 != c2[c2p++]) 1 else 0
            x = row[p] + 1
            d = x
            if (x > c3) x = c3
            row[p++] = x
        } else {
            p = 1
            c2p = str2
            x = i
            d = x
        }
        if (i <= half + 1) end = len2 + i - half - 2
        while (p <= end) {
            val c3 = --d + if (ch1 != c2[c2p++]) 1 else 0
            x++
            if (x > c3) x = c3
            d = row[p] + 1
            if (x > d) x = d
            row[p++] = x
        }
        if (i <= half) {
            val c3 = --d + if (ch1 != c2[c2p]) 1 else 0
            x++
            if (x > c3) x = c3
            row[p] = x
        }
    }
    return row[end]
}

private fun levEditDistance(s1: String, s2: String, xcost: Int): Int {
    val trimmed = trimAffixes(s1, s2)
    val len1 = trimmed.len1
    val len2 = trimmed.len2
    if (len1 == 0) return len2
    if (len2 == 0) return len1

    if (len1 == 1) {
        val matches = memchr(trimmed.c2, trimmed.str2, trimmed.c1[trimmed.str1], len2)
        return if (xcost != 0) len2 + 1 - 2 * matches else len2 - matches
    }

    return if (xcost != 0) {
        computeDistanceWithCost(len1 + 1, len2 + 1, trimmed.c1, trimmed.str1, trimmed.c2, trimmed.str2)
    } else {
        computeDistanceWithoutCost(len1 + 1, len2 + 1, trimmed.c1, trimmed.str1, trimmed.c2, trimmed.str2)
    }
}

private fun memchr(haystack: String, offset: Int, needle: Char, num: Int): Int {
    var numCopy = num
    if (numCopy != 0) {
        var p = 0
        do {
            if (haystack[offset + p] == needle) return 1
            p++
        } while (--numCopy != 0)
    }
    return 0
}
