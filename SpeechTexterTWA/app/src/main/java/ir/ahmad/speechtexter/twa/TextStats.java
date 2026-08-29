package ir.ahmad.speechtexter.twa;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextStats {
    private static final Pattern WORD_PATTERN = Pattern.compile(
            "[\\p{L}\\p{N}]+(?:[\\u200c'’-][\\p{L}\\p{N}]+)*",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    private TextStats() {
    }

    public static int countWords(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        Matcher matcher = WORD_PATTERN.matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    public static int countCharacters(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.codePointCount(0, text.length());
    }
}
