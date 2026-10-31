package org.datavyu.views.discrete.layouts;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Where the temporal layout puts each time down the sheet. Times are spaced by the cells that start at them, not by
 * duration, except for the tail after the last onset, which grows with its duration so that a clip running on past
 * every annotation can be seen and scrubbed through (F15).
 */
public class SheetLayoutWeakTemporalTest {

    private static final long MINUTE = 60_000;

    private static Map<Long, Integer> tops(final Map<Long, Integer> heights, final Long... times) {
        return SheetLayoutWeakTemporal.tops(new TreeSet<>(List.of(times)), heights);
    }

    @Test
    public void anEmptySheetHasNoTimes() {
        assertEquals(tops(Map.of()), Map.of());
    }

    @Test
    public void timesAreSpacedByTheCellsThatStartAtThemAndAGapNotByDuration() {
        Map<Long, Integer> tops = tops(Map.of(0L, 40, 1_000L, 30, 90 * MINUTE, 20),
                0L, 1_000L, 90 * MINUTE);
        assertEquals(tops, Map.of(0L, 0, 1_000L, 55, 90 * MINUTE, 100));
    }

    @Test
    public void sheetsOfPointCellsHaveNoTail() {
        Map<Long, Integer> tops = tops(Map.of(0L, 40, 5 * MINUTE, 40), 0L, 5 * MINUTE);
        assertEquals(tops, Map.of(0L, 0, 5 * MINUTE, 55));
    }

    /** The test project's case: a clip running half an hour past the last bookmark. */
    @Test
    public void aTailIsAPixelHighForEachTenSeconds() {
        long lastBookmark = 31 * MINUTE;
        long clipEnd = lastBookmark + 30 * MINUTE;
        Map<Long, Integer> tops = tops(Map.of(0L, 40, lastBookmark, 40), 0L, lastBookmark, clipEnd);
        assertEquals((int) tops.get(clipEnd), 55 + 40 + 180);
    }

    @Test
    public void aShortTailIsAtLeastSixtyPixels() {
        Map<Long, Integer> tops = tops(Map.of(0L, 40), 0L, 2_000L);
        assertEquals((int) tops.get(2_000L), 40 + SheetLayoutWeakTemporal.TAIL_MIN);
    }

    @Test
    public void aLongTailIsAtMostSixHundredPixels() {
        Map<Long, Integer> tops = tops(Map.of(0L, 40), 0L, 240 * MINUTE);
        assertEquals((int) tops.get(240 * MINUTE), 40 + SheetLayoutWeakTemporal.TAIL_MAX);
    }

    @Test
    public void timesInATailLieInItInProportion() {
        Map<Long, Integer> tops = tops(Map.of(0L, 40), 0L, 10 * MINUTE, 40 * MINUTE);
        assertEquals((int) tops.get(10 * MINUTE), 40 + 60);
        assertEquals((int) tops.get(40 * MINUTE), 40 + 240);
    }

    @Test
    public void timesInATailKeepAtLeastAGapBetweenThem() {
        Map<Long, Integer> tops = tops(Map.of(0L, 40), 0L, 1L, 2L, 30 * MINUTE);
        assertEquals((int) tops.get(1L), 40 + SheetLayoutWeakTemporal.GAP);
        assertEquals((int) tops.get(2L), 40 + 2 * SheetLayoutWeakTemporal.GAP);
        assertEquals((int) tops.get(30 * MINUTE), 40 + 180);
    }

    /** The needle crosses the tail at a steady pace rather than jumping through a sliver. */
    @Test
    public void theNeedleCrossesATailInProportionToTime() {
        // A bookmark 40 pixels high at the start of a half-hour clip.
        long clipEnd = 30 * MINUTE;
        TimeAxis axis = new TimeAxis(tops(Map.of(0L, 40), 0L, clipEnd), Map.of(0L, 40));
        assertEquals(axis.y(0), 0);
        assertEquals(axis.y(clipEnd / 2), 40 + 90);
        assertEquals(axis.y(clipEnd), 40 + 180);
    }
}
