package org.datavyu.views.discrete.layouts;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import java.util.Map;

/** Where the playhead falls among the cells of a spreadsheet laid out in time, and the time at a height. */
public class TimeAxisTest {

    /** Cell times at 0 s, 2 s and 10 minutes: the long gap takes no more room than the short one. */
    private final TimeAxis axis = new TimeAxis(Map.of(0L, 0, 2_000L, 40, 600_000L, 80));

    @Test
    public void aCellTimeIsAtItsHeight() {
        assertEquals(axis.y(0), 0);
        assertEquals(axis.y(2_000), 40);
        assertEquals(axis.y(600_000), 80);
    }

    @Test
    public void aTimeBetweenTwoCellTimesLiesBetweenThemInProportion() {
        assertEquals(axis.y(1_000), 20);
        assertEquals(axis.y(301_000), 60);
    }

    @Test
    public void timesBeyondTheCellsStayAtTheEnds() {
        TimeAxis later = new TimeAxis(Map.of(5_000L, 30, 9_000L, 70));
        assertEquals(later.y(0), 30, "before the first cell time");
        assertEquals(later.y(60_000), 70, "after the last");
    }

    @Test
    public void aHeightGivesTheTimeThere() {
        assertEquals(axis.time(0), 0L);
        assertEquals(axis.time(20), 1_000L);
        assertEquals(axis.time(40), 2_000L);
        assertEquals(axis.time(60), 301_000L);
        assertEquals(axis.time(500), 600_000L, "below the last cell time");
        assertEquals(new TimeAxis(Map.of(5_000L, 30)).time(0), 5_000L, "above the first");
    }

    @Test
    public void heightAndTimeAgree() {
        for (long time = 0; time <= 600_000; time += 7_000) {
            assertEquals(axis.time(axis.y(time)), time, 7_500, "near " + time);
        }
    }

    @Test
    public void anAxisWithoutTimesPutsEverythingAtTheTop() {
        assertTrue(TimeAxis.NONE.isEmpty());
        assertEquals(TimeAxis.NONE.y(12_345), 0);
        assertEquals(TimeAxis.NONE.time(100), 0L);
    }
}
