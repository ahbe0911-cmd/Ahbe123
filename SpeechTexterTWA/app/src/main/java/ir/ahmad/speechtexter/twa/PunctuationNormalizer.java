package ir.ahmad.speechtexter.twa;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts optional spoken punctuation commands without touching partial results. */
public final class PunctuationNormalizer {
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final Rule[] RULES = {
            rule("پاراگراف جدید", "\n\n"),
            rule("علامت سوال", "؟"),
            rule("علامت سؤال", "؟"),
            rule("علامت تعجب", "!"),
            rule("نقطه ویرگول", "؛"),
            rule("دو نقطه", ":"),
            rule("پرانتز باز", "("),
            rule("پرانتز بسته", ")"),
            rule("گیومه باز", "«"),
            rule("گیومه بسته", "»"),
            rule("خط جدید", "\n"),
            rule("سطر جدید", "\n"),
            rule("ویرگول", "،"),
            rule("نقطه", "."),
            rule("new paragraph", "\n\n"),
            rule("question mark", "?"),
            rule("exclamation mark", "!"),
            rule("semicolon", ";"),
            rule("colon", ":"),
            rule("new line", "\n"),
            rule("comma", ","),
            rule("period", ".")
    };

    private PunctuationNormalizer() {
    }

    public static String normalize(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "";
        }

        String result = input.trim();
        for (Rule rule : RULES) {
            result = rule.pattern.matcher(result)
                    .replaceAll(Matcher.quoteReplacement(rule.replacement));
        }

        result = result.replaceAll("[ \\t]+([،؛؟!?,;:.])", "$1");
        result = result.replaceAll("([،؛؟!?,;:.])(?=[\\p{L}\\p{N}])", "$1 ");
        result = result.replaceAll("[ \\t]+\\n", "\n");
        result = result.replaceAll("\\n[ \\t]+", "\n");
        result = result.replaceAll("\\n{3,}", "\n\n");
        result = result.replaceAll("[ \\t]{2,}", " ");
        return result.trim();
    }

    private static Rule rule(String phrase, String replacement) {
        String[] words = phrase.split(" ");
        StringBuilder expression = new StringBuilder("(?<![\\p{L}\\p{N}\\u200c])");
        for (int index = 0; index < words.length; index++) {
            if (index > 0) {
                expression.append("\\s+");
            }
            expression.append(Pattern.quote(words[index]));
        }
        expression.append("(?![\\p{L}\\p{N}\\u200c])");
        return new Rule(Pattern.compile(expression.toString(), FLAGS), replacement);
    }

    private static final class Rule {
        private final Pattern pattern;
        private final String replacement;

        private Rule(Pattern pattern, String replacement) {
            this.pattern = pattern;
            this.replacement = replacement;
        }
    }
}
