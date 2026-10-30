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

    /**
     * A point cell at 1 s, 85 pixels tall from 100, and the next cell 15 pixels below it at 5 s: the time of 1 s spans
     * the point cell's height.
     */
    private final TimeAxis spans = new TimeAxis(Map.of(0L, 0, 1_000L, 100, 5_000L, 200), Map.of(1_000L, 185));

    @Test
    public void theNeedlePassesAPointCellAtItsTime() {
        assertEquals(spans.y(1_000), 100, "at its time, the needle is at the cell's top");
        assertEquals(spans.y(1_001), 185, "a millisecond later it is past the cell");
        assertEquals(spans.y(3_000), 193, "and crosses the gap below until the next time");
        assertEquals(spans.y(999), 100, "before, it reaches the cell's top from above");
    }

    @Test
    public void aHeightOnAPointCellGivesItsTime() {
        assertEquals(spans.time(100), 1_000L);
        assertEquals(spans.time(150), 1_000L);
        assertEquals(spans.time(185), 1_000L);
        assertEquals(spans.time(193), 3_133L);
        assertEquals(spans.time(50), 500L);
    }

    @Test
    public void afterTheLastTimeTheNeedleIsBelowItsCells() {
        TimeAxis last = new TimeAxis(Map.of(0L, 0, 1_000L, 100), Map.of(1_000L, 160));
        assertEquals(last.y(5_000), 160);
        assertEquals(last.time(400), 1_000L);
    }

    @Test
    public void aCellEndingBelowTheNextTimeNeverSendsTheNeedleBackUp() {
        TimeAxis overlapping = new TimeAxis(Map.of(0L, 0, 1_000L, 50, 2_000L, 100), Map.of(0L, 80));
        int previous = 0;
        for (long time = 0; time <= 2_000; time += 50) {
            int y = overlapping.y(time);
            assertTrue(y >= previous, "at " + time);
            previous = y;
        }
        assertEquals(overlapping.y(500), 50, "the span is cut at the next time");
    }

    @Test
    public void heightAndTimeAgreeBetweenSpans() {
        for (long time = 1_001; time <= 5_000; time += 7) {
            assertTrue(Math.abs(spans.time(spans.y(time)) - time) <= 300, "near " + time);
        }
    }

    @Test
    public void anAxisWithoutTimesPutsEverythingAtTheTop() {
        assertTrue(TimeAxis.NONE.isEmpty());
        assertEquals(TimeAxis.NONE.y(12_345), 0);
        assertEquals(TimeAxis.NONE.time(100), 0L);
    }
}
