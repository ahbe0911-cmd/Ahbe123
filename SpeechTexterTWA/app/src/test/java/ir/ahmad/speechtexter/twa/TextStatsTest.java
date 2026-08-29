package ir.ahmad.speechtexter.twa;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class TextStatsTest {
    @Test
    public void countsPersianAndEnglishWords() {
        assertEquals(5, TextStats.countWords("سلام به دنیای voice typing"));
    }

    @Test
    public void keepsZeroWidthJoinedWordTogether() {
        assertEquals(2, TextStats.countWords("می‌روم خانه"));
    }

    @Test
    public void countsUnicodeCodePoints() {
        assertEquals(3, TextStats.countCharacters("A😀ب"));
    }

    @Test
    public void handlesEmptyValues() {
        assertEquals(0, TextStats.countWords(null));
        assertEquals(0, TextStats.countCharacters(""));
    }
}
