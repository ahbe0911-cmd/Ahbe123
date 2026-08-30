package ir.voxfa.keyboard

object KeyboardLayouts {
    val persianRows: List<List<String>> = listOf(
        listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج", "چ"),
        listOf("ش", "س", "ی", "ب", "ل", "ا", "ت", "ن", "م", "ک", "گ"),
        listOf("ظ", "ط", "ز", "ر", "ذ", "د", "پ", "و", "ژ", "،", "؟")
    )

    val englishRows: List<List<String>> = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("z", "x", "c", "v", "b", "n", "m")
    )

    val numberRows: List<List<String>> = listOf(
        listOf("۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹", "۰"),
        listOf("@", "#", "%", "&", "-", "+", "(", ")", "/", ":"),
        listOf(".", "،", "؟", "!", "_", "=", "؛", "«", "»")
    )
}
