package ir.voxfa.keyboard

/**
 * Conservative Persian typing helper.
 * Only high-confidence forms are auto-corrected. Ambiguous candidates are suggestions only.
 */
object SmartTextCorrector {
    private val highConfidence = mapOf(
        "نصپ" to "نصب",
        "ظبط" to "ضبط",
        "زبط" to "ضبط",
        "میخام" to "می‌خوام",
        "میخوام" to "می‌خوام",
        "میخای" to "می‌خوای",
        "میخواد" to "می‌خواد",
        "میخایم" to "می‌خوایم",
        "میخاین" to "می‌خواین",
        "میتونم" to "می‌تونم",
        "میتونی" to "می‌تونی",
        "میتونه" to "می‌تونه",
        "میتونیم" to "می‌تونیم",
        "میشه" to "می‌شه",
        "نمیشه" to "نمی‌شه",
        "نمیتونم" to "نمی‌تونم",
        "نمیتونی" to "نمی‌تونی",
        "نمیدونم" to "نمی‌دونم",
        "میگم" to "می‌گم",
        "میگه" to "می‌گه",
        "میرم" to "می‌رم",
        "میری" to "می‌ری",
        "میره" to "می‌ره",
        "میام" to "میام",
        "برام" to "برام",
        "خونه" to "خونه",
        "کتابخونه" to "کتابخونه"
    )

    private val lexicon = listOf(
        "سلام", "خوب", "خیلی", "ممنون", "لطفاً", "برنامه", "کیبورد", "فارسی", "انگلیسی",
        "هوش", "مصنوعی", "تایپ", "صوتی", "صدا", "متن", "نصب", "دانلود", "گوشی", "اندروید",
        "کتاب", "کتابخانه", "امروز", "فردا", "دیروز", "اینترنت", "تنظیمات", "میکروفون", "پیام",
        "ارسال", "دریافت", "درست", "غلط", "اصلاح", "کلمه", "جمله", "سریع", "بهتر", "عالی",
        "می‌خوام", "می‌خوای", "می‌خواد", "می‌تونم", "می‌تونی", "می‌تونه", "می‌شه", "نمی‌شه",
        "نمی‌دونم", "می‌گم", "می‌گه", "می‌رم", "می‌ری", "می‌ره"
    )

    fun correctWord(word: String): String = highConfidence[word] ?: word

    fun suggestions(prefix: String): List<String> {
        val clean = prefix.trim()
        if (clean.isEmpty()) return listOf("سلام", "ممنون", "باشه")

        val direct = highConfidence[clean]
        val candidates = linkedSetOf<String>()
        if (direct != null && direct != clean) candidates += direct

        lexicon.asSequence()
            .filter { it.startsWith(clean) && it != clean }
            .take(3)
            .forEach { candidates += it }

        if (candidates.size < 3 && clean.length >= 2) {
            lexicon.asSequence()
                .map { it to editDistance(clean, it.take((clean.length + 2).coerceAtMost(it.length))) }
                .filter { (_, distance) -> distance <= 2 }
                .sortedBy { it.second }
                .map { it.first }
                .filter { it != clean }
                .take(3 - candidates.size)
                .forEach { candidates += it }
        }

        return candidates.take(3)
    }

    private fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
