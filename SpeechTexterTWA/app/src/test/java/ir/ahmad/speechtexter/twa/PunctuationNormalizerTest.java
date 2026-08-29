package ir.ahmad.speechtexter.twa;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class PunctuationNormalizerTest {
    @Test
    public void convertsPersianCommandsWithoutExtraSpaces() {
        assertEquals(
                "سلام، دنیا.\nحالت چطور است؟",
                PunctuationNormalizer.normalize(
                        "سلام ویرگول دنیا نقطه خط جدید حالت چطور است علامت سؤال"
                )
        );
    }

    @Test
    public void convertsEnglishCommands() {
        assertEquals(
                "Hello, world!\nNext line.",
                PunctuationNormalizer.normalize(
                        "Hello comma world exclamation mark new line Next line period"
                )
        );
    }

    @Test
    public void doesNotChangeCommandsInsideLongerWords() {
        assertEquals("این نقطه‌نظر مهم است", PunctuationNormalizer.normalize("این نقطه‌نظر مهم است"));
    }

    @Test
    public void handlesEmptyInput() {
        assertEquals("", PunctuationNormalizer.normalize(null));
        assertEquals("", PunctuationNormalizer.normalize("   "));
    }
}
