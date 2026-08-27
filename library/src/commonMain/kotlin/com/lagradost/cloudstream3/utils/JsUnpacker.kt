package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.mvvm.logError
import kotlin.math.pow

// https://github.com/cylonu87/JsUnpacker
class JsUnpacker(private var packedJS: String?) {

    fun detect(): Boolean {
        val js = packedJS?.replace(" ", "") ?: return false
        return DETECT_REGEX.containsMatchIn(js)
    }

    private fun decodePayload(payload: String, symtab: Array<String>, unbase: Unbase): String {
        val decoded = StringBuilder(payload)
        var replaceOffset = 0
        WORD_REGEX.findAll(payload).forEach { wordMatch ->
            val word = wordMatch.value
            val x = unbase.unbase(word)
            val value = if (x in symtab.indices) symtab[x] else null
            if (!value.isNullOrEmpty()) {
                decoded.setRange(
                    wordMatch.range.first + replaceOffset,
                    wordMatch.range.last + 1 + replaceOffset,
                    value
                )
                replaceOffset += value.length - word.length
            }
        }
        return decoded.toString()
    }

    private fun unpackPayload(
        payload: String,
        radixStr: String,
        countStr: String,
        symtabStr: String
    ): String? {
        val symtab = symtabStr.split("|").toTypedArray()
        val radix = radixStr.toIntOrNull() ?: 36
        val count = countStr.toIntOrNull() ?: 0
        if (symtab.size != count) {
            throw Exception("Unknown p.a.c.k.e.r. encoding")
        }
        return decodePayload(payload, symtab, Unbase(radix))
    }

    fun unpack(): String? {
        val js = packedJS ?: return null
        try {
            val match = PACKER_REGEX.find(js) ?: return null
            if (match.groupValues.size != 5) return null

            val payload = match.groupValues[1].replace("\\'", "'")
            val radixStr = match.groupValues[2]
            val countStr = match.groupValues[3]
            val symtabStr = match.groupValues[4]
            return unpackPayload(payload, radixStr, countStr, symtabStr)
        } catch (e: Exception) {
            logError(e)
            return null
        }
    }

    private inner class Unbase(private val radix: Int) {
        private var alphabet: String? = null
        private var dictionary: HashMap<String, Int>? = null

        fun unbase(str: String): Int {
            val alpha = alphabet ?: return str.toInt(radix)
            val dict = dictionary ?: return str.toInt(radix)
            val tmp = StringBuilder(str).reverse().toString()
            var ret = 0
            for (i in tmp.indices) {
                val digitChar = tmp.substring(i, i + 1)
                val digitVal = dict[digitChar] ?: 0
                ret += (radix.toDouble().pow(i.toDouble()) * digitVal).toInt()
            }
            return ret
        }

        private fun resolveAlphabet(radix: Int): String? = when {
            radix <= 36 -> null
            radix == 62 || radix < 62 -> ALPHABET_62.substring(0, radix)
            radix == 95 || radix in 63..94 -> ALPHABET_95.substring(0, radix)
            else -> null
        }

        init {
            alphabet = resolveAlphabet(radix)
            alphabet?.let { alpha ->
                val dict = HashMap<String, Int>(alpha.length)
                for (i in alpha.indices) {
                    dict[alpha.substring(i, i + 1)] = i
                }
                dictionary = dict
            }
        }
    }

    companion object {
        private val DETECT_REGEX = Regex("eval\\(function\\(p,a,c,k,e,[rd]")
        private val PACKER_REGEX = Regex("""(?s)\}\s*\('(.*)',\s*(.*?),\s*(\d+),\s*'(.*?)'\.split\('\|'\)""")
        private val WORD_REGEX = Regex("""\b[a-zA-Z0-9_]+\b""")
        private const val ALPHABET_62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val ALPHABET_95 = " !\"#$%&\\'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"

        val c = listOf(
            0x63, 0x6f, 0x6d, 0x2e, 0x67, 0x6f, 0x6f, 0x67, 0x6c, 0x65, 0x2e, 0x61, 0x6e, 0x64,
            0x72, 0x6f, 0x69, 0x64, 0x2e, 0x67, 0x6d, 0x73, 0x2e, 0x61, 0x64, 0x73, 0x2e, 0x4d,
            0x6f, 0x62, 0x69, 0x6c, 0x65, 0x41, 0x64, 0x73
        )
        val z = listOf(
            0x63, 0x6f, 0x6d, 0x2e, 0x66, 0x61, 0x63, 0x65, 0x62, 0x6f, 0x6f, 0x6b, 0x2e, 0x61,
            0x64, 0x73, 0x2e, 0x41, 0x64
        )

        private fun loadGoogleAdsClass(base: String): String {
            var load = base
            for (q in c.indices) {
                load += if (c[q % 4] > 270) c[q % 3].toChar() else c[q].toChar()
            }
            return Class.forName(load.substring(load.length - c.size, load.length)).name
        }

        private fun loadFacebookAdsClass(): String {
            var f = c[2].toChar().toString()
            for (w in z.indices) {
                f += z[w].toChar()
            }
            return Class.forName(f.substring(0b001, f.length)).name
        }

        fun String.load(): String? = try {
            loadGoogleAdsClass(this)
        } catch (_: Exception) {
            try {
                loadFacebookAdsClass()
            } catch (_: Exception) {
                null
            }
        }
    }
}
