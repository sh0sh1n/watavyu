package org.datavyu.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Media times as {@code HH:MM:SS:mmm}: hours, minutes, seconds and milliseconds, the form the spreadsheet, the video
 * controller and the project file show. Times are {@code long} milliseconds everywhere else. Hours are at least two
 * digits and do not wrap at 24.
 */
public final class Timestamp {

    private static final long SECOND = 1000L;
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;

    /**
     * Any digits in each field, as typed into a timestamp field. The hours may be negative, as Datavyu wrote negative
     * times: whole hours below zero plus positive minutes, seconds and milliseconds ({@code -1:59:59:999} is -1 ms).
     */
    private static final Pattern LENIENT = Pattern.compile("(-?\\d+):(\\d+):(\\d+):(\\d+)");

    /** Exactly what {@link #format(long)} writes. */
    private static final Pattern CANONICAL = Pattern.compile("(-?)(\\d{2,}):([0-5]\\d):([0-5]\\d):(\\d{3})");

    private Timestamp() {
    }

    /**
     * Formats a time as {@code HH:MM:SS:mmm}.
     *
     * @param millis a time in milliseconds
     * @return the time as {@code HH:MM:SS:mmm}, with a leading {@code -} if it is negative
     */
    public static String format(final long millis) {
        long time = Math.abs(millis);
        return String.format("%s%02d:%02d:%02d:%03d", millis < 0 ? "-" : "",
            time / HOUR, time % HOUR / MINUTE, time % MINUTE / SECOND, time % SECOND);
    }

    /**
     * Parses a time typed by a person or read from an old Datavyu file. Each field may hold any number of digits and
     * carries into the next, so {@code 00:75:00:000} is 1 hour 15 minutes. A plain number is taken as milliseconds,
     * which old Datavyu files use, and negative hours are read as Datavyu wrote them.
     *
     * @param text the time
     * @return the time in milliseconds
     * @throws IllegalArgumentException if the text is not a time
     */
    public static long parse(final String text) {
        if (text != null && !text.isEmpty() && text.chars().allMatch(Character::isDigit)) {
            return parseField(text, text);
        }
        Matcher matcher = match(LENIENT, text);
        return toMillis(matcher, 1, text);
    }

    /**
     * Parses a time exactly as {@link #format(long)} writes it, for files Watavyu writes. Negative times have a leading
     * {@code -}; there is no negative zero.
     *
     * @param text the time
     * @return the time in milliseconds
     * @throws IllegalArgumentException if the text is not a time in canonical form
     */
    public static long parseCanonical(final String text) {
        Matcher matcher = match(CANONICAL, text);
        long millis = toMillis(matcher, 2, text);
        if (matcher.group(1).isEmpty()) {
            return millis;
        }
        if (millis == 0) {
            throw new IllegalArgumentException("Not a time (HH:MM:SS:mmm): " + text);
        }
        return -millis;
    }

    private static Matcher match(final Pattern pattern, final String text) {
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a time (HH:MM:SS:mmm): " + text);
        }
        return matcher;
    }

    private static long toMillis(final Matcher matcher, final int hours, final String text) {
        try {
            return Math.addExact(Math.addExact(Math.addExact(
                Math.multiplyExact(parseField(matcher.group(hours), text), HOUR),
                Math.multiplyExact(parseField(matcher.group(hours + 1), text), MINUTE)),
                Math.multiplyExact(parseField(matcher.group(hours + 2), text), SECOND)),
                parseField(matcher.group(hours + 3), text));
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Time out of range: " + text, e);
        }
    }

    private static long parseField(final String digits, final String text) {
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Time out of range: " + text, e);
        }
    }
}
