package ir.voxfa.keyboard

/**
 * Compact layouts inspired by the spacing and grouping of modern mobile keyboards.
 * This is an original layout implementation; no Gboard code/assets are used.
 */
object KeyboardLayouts {
    val persianDigitRow: List<String> = listOf("۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹", "۰")
    val englishDigitRow: List<String> = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    val persianRows: List<List<String>> = listOf(
        listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج"),
        listOf("ش", "س", "ی", "ب", "ل", "ا", "ت", "ن", "م", "ک", "گ"),
        listOf("ظ", "ط", "ژ", "ز", "ر", "ذ", "د", "پ", "و", "چ")
    )

    val englishRows: List<List<String>> = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("z", "x", "c", "v", "b", "n", "m")
    )

    val persianNumberPadRows: List<List<String>> = listOf(
        listOf("۱", "۲", "۳"),
        listOf("۴", "۵", "۶"),
        listOf("۷", "۸", "۹")
    )

    val englishNumberPadRows: List<List<String>> = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9")
    )

    val symbolRows: List<List<String>> = listOf(
        listOf("@", "#", "%", "&", "*", "+", "-", "=", "(", ")"),
        listOf("/", "\\", ":", ";", "!", "?", "_", "\"", "'", "~"),
        listOf("[", "]", "{", "}", "<", ">", "^", "|", "€", "$")
    )
}
