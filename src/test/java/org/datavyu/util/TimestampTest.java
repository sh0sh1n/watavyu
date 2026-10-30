package org.datavyu.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class TimestampTest {

    @DataProvider
    public Object[][] times() {
        return new Object[][] {
            {0L, "00:00:00:000"},
            {1L, "00:00:00:001"},
            {59_999L, "00:00:59:999"},
            {60_000L, "00:01:00:000"},
            {3_723_004L, "01:02:03:004"},
            {86_400_000L, "24:00:00:000"},
            {360_000_000L, "100:00:00:000"},
        };
    }

    @Test(dataProvider = "times")
    public void formatWritesHoursMinutesSecondsMillis(final long millis, final String text) {
        assertEquals(Timestamp.format(millis), text);
    }

    @Test(dataProvider = "times")
    public void parseCanonicalReadsWhatFormatWrites(final long millis, final String text) {
        assertEquals(Timestamp.parseCanonical(text), millis);
    }

    @Test(dataProvider = "times")
    public void parseReadsWhatFormatWrites(final long millis, final String text) {
        assertEquals(Timestamp.parse(text), millis);
    }

    /** B13: no time is negative. */
    @Test
    public void formatRefusesNegativeTimes() {
        assertThrows(IllegalArgumentException.class, () -> Timestamp.format(-1_500L));
        assertThrows(IllegalArgumentException.class, () -> Timestamp.requireNotNegative(-1L));
        assertEquals(Timestamp.requireNotNegative(0L), 0L);
    }

    @Test
    public void parseCanonicalRefusesNegativeTimes() {
        assertThrows(IllegalArgumentException.class, () -> Timestamp.parseCanonical("-00:00:01:500"));
    }

    @Test
    public void parseCarriesOverfullFields() {
        assertEquals(Timestamp.parse("00:75:00:000"), 75 * 60_000L);
        assertEquals(Timestamp.parse("0:0:1:5"), 1_005L);
    }

    /** B13: Datavyu wrote negative times as negative hours plus positive fields; they are not times. */
    @Test
    public void parseRefusesNegativeTimesAsDatavyuWroteThem() {
        assertThrows(IllegalArgumentException.class, () -> Timestamp.parse("-1:59:59:999"));
        assertThrows(IllegalArgumentException.class, () -> Timestamp.parse("-01:00:00:000"));
        assertThrows(IllegalArgumentException.class, () -> Timestamp.parse("-1500"));
    }

    @Test
    public void parseTakesAPlainNumberAsMilliseconds() {
        assertEquals(Timestamp.parse("1500"), 1_500L);
    }

    @DataProvider
    public Object[][] notTimes() {
        return new Object[][] {{null}, {""}, {"abc"}, {"00:00:00"}, {"00:00:00:000:000"}, {"00:-1:01:000"},
            {"00:00:0a:000"}, {"99999999999999999999:00:00:000"}};
    }

    @Test(dataProvider = "notTimes")
    public void parseRejectsNonTimes(final String text) {
        assertThrows(IllegalArgumentException.class, () -> Timestamp.parse(text));
    }

    @DataProvider
    public Object[][] nonCanonical() {
        return new Object[][] {{"1500"}, {"0:00:00:000"}, {"00:60:00:000"}, {"00:00:60:000"}, {"00:00:00:01"},
            {"00:00:00:0000"}, {"-00:00:00:000"}, {"+00:00:01:000"}, {" 00:00:00:000"}};
    }

    @Test(dataProvider = "nonCanonical")
    public void parseCanonicalRejectsAnythingFormatWouldNotWrite(final String text) {
        assertThrows(IllegalArgumentException.class, () -> Timestamp.parseCanonical(text));
    }
}
