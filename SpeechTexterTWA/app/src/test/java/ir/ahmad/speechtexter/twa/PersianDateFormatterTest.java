package ir.ahmad.speechtexter.twa;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class PersianDateFormatterTest {
    @Test
    public void convertsKnownNowruzDate() {
        assertArrayEquals(new int[]{1403, 1, 1}, PersianDateFormatter.toJalali(2024, 3, 20));
    }

    @Test
    public void convertsCurrentReleaseDate() {
        assertArrayEquals(new int[]{1405, 6, 7}, PersianDateFormatter.toJalali(2026, 8, 29));
    }

    @Test
    public void localizesDigits() {
        assertEquals("۱۴۰۵/۰۶/۰۷  ۱۲:۳۰", PersianDateFormatter.toPersianDigits(
                "1405/06/07  12:30"
        ));
    }
}
