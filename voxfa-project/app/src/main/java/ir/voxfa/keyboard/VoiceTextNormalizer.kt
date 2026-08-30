package ir.voxfa.keyboard

/**
 * Small deterministic Persian post-processor.
 * It intentionally avoids AI/network logic so it is fast, testable, and safe to run on every result.
 */
object VoiceTextNormalizer {
    private val commandReplacements = listOf(
        Regex("(?:علامت\\s+)?س[ؤو]ال", RegexOption.IGNORE_CASE) to "؟",
        Regex("علامت\\s+تعجب", RegexOption.IGNORE_CASE) to "!",
        Regex("ویرگول", RegexOption.IGNORE_CASE) to "،",
        Regex("کاما", RegexOption.IGNORE_CASE) to "،",
        Regex("دو\\s*نقطه", RegexOption.IGNORE_CASE) to ":",
        Regex("نقطه", RegexOption.IGNORE_CASE) to ".",
        Regex("خط\\s+جدید", RegexOption.IGNORE_CASE) to "\n",
        Regex("برو\\s+خط\\s+بعد", RegexOption.IGNORE_CASE) to "\n"
    )

    fun normalize(raw: String): String {
        if (raw.isBlank()) return ""

        var value = raw
            .replace('ي', 'ی')
            .replace('ك', 'ک')
            .replace(Regex("[ \\t]+"), " ")
            .trim()

        commandReplacements.forEach { (pattern, replacement) ->
            value = value.replace(pattern, replacement)
        }

        value = value
            .replace(Regex("\\s+([،؟!:.])"), "$1")
            .replace(Regex("([،؟!:.])(?=[^\\s\\n،؟!:.])"), "$1 ")
            .replace(Regex(" *\\n *"), "\n")
            .replace(Regex(" {2,}"), " ")
            .trim()

        return value
    }
}
