package ir.ahmad.speechtexter.twa;

import java.util.Calendar;
import java.util.Locale;

public final class PersianDateFormatter {
    private static final char[] PERSIAN_DIGITS = {
            '۰', '۱', '۲', '۳', '۴',
            '۵', '۶', '۷', '۸', '۹'
    };

    private PersianDateFormatter() {
    }

    public static String format(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        int[] jalali = toJalali(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        String latin = String.format(
                Locale.US,
                "%04d/%02d/%02d  %02d:%02d",
                jalali[0], jalali[1], jalali[2],
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE)
        );
        return toPersianDigits(latin);
    }

    static int[] toJalali(int gregorianYear, int gregorianMonth, int gregorianDay) {
        int gy = gregorianYear;
        int jy;
        if (gy > 1600) {
            jy = 979;
            gy -= 1600;
        } else {
            jy = 0;
            gy -= 621;
        }

        int gy2 = gregorianMonth > 2 ? gy + 1 : gy;
        int days = 365 * gy
                + (gy2 + 3) / 4
                - (gy2 + 99) / 100
                + (gy2 + 399) / 400
                - 80
                + gregorianDay;
        int[] monthDays = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
        days += monthDays[gregorianMonth - 1];

        jy += 33 * (days / 12053);
        days %= 12053;
        jy += 4 * (days / 1461);
        days %= 1461;
        if (days > 365) {
            jy += (days - 1) / 365;
            days = (days - 1) % 365;
        }

        int jm;
        int jd;
        if (days < 186) {
            jm = 1 + days / 31;
            jd = 1 + days % 31;
        } else {
            jm = 7 + (days - 186) / 30;
            jd = 1 + (days - 186) % 30;
        }
        return new int[]{jy, jm, jd};
    }

    public static String toPersianDigits(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character >= '0' && character <= '9') {
                result.append(PERSIAN_DIGITS[character - '0']);
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }
}
