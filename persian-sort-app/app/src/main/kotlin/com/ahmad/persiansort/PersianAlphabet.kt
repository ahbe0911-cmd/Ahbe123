package com.ahmad.persiansort

import java.text.Normalizer
import kotlin.math.min

object PersianAlphabet {
    val letters = listOf(
        'ا','ب','پ','ت','ث','ج','چ','ح','خ','د','ذ','ر','ز','ژ','س','ش',
        'ص','ض','ط','ظ','ع','غ','ف','ق','ک','گ','ل','م','ن','و','ه','ی'
    )

    private val rank = letters.withIndex().associate { it.value to it.index }

    fun sort(values: List<String>): List<String> =
        values.sortedWith(Comparator(::compare))

    fun compare(a: String, b: String): Int {
        val ka = sortKey(a)
        val kb = sortKey(b)
        val common = min(ka.size, kb.size)
        for (i in 0 until common) {
            if (ka[i] != kb[i]) return ka[i] - kb[i]
        }
        if (ka.size != kb.size) return ka.size - kb.size
        return normalizeForDisplay(a).compareTo(normalizeForDisplay(b))
    }

    fun isSinglePersianLetter(value: String): Boolean {
        val normalized = normalizeForDisplay(value).trim()
        return normalized.length == 1 && rank.containsKey(canonical(normalized[0]))
    }

    fun normalizeForDisplay(value: String): String = value.trim()
        .replace('ي', 'ی')
        .replace('ى', 'ی')
        .replace('ك', 'ک')
        .replace('ۀ', 'ه')
        .replace('ة', 'ه')
        .replace('ؤ', 'و')
        .replace('ئ', 'ی')
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')

    private fun sortKey(input: String): IntArray {
        val normalized = Normalizer.normalize(normalizeForDisplay(input), Normalizer.Form.NFKC)
        val out = ArrayList<Int>(normalized.length)
        normalized.forEach { raw ->
            if (Character.getType(raw) == Character.NON_SPACING_MARK.toInt()) return@forEach
            val c = canonical(raw)
            val letterRank = rank[c]
            when {
                letterRank != null -> out.add(letterRank)
                c.isWhitespace() || c == '\u200C' || c == '\u200D' -> Unit
                Character.isLetterOrDigit(c) -> out.add(1000 + c.code)
            }
        }
        return out.toIntArray()
    }

    private fun canonical(c: Char): Char = when (c) {
        'ي', 'ى', 'ئ' -> 'ی'
        'ك' -> 'ک'
        'ۀ', 'ة' -> 'ه'
        'ؤ' -> 'و'
        'أ', 'إ', 'آ' -> 'ا'
        else -> c
    }
}
