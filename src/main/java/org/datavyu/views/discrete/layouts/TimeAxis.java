package org.datavyu.views.discrete.layouts;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Where times fall down a spreadsheet laid out in time. The weak temporal layout gives every onset and offset of a
 * visible cell a height in the sheet, spaced by the cells between them rather than by duration; a time between two
 * of these lies between their heights in proportion. Before the first time is the top, after the last the bottom.
 */
public final class TimeAxis {

    /** An axis for a spreadsheet without cells, or one not laid out in time: everything is at the top. */
    public static final TimeAxis NONE = new TimeAxis(new TreeMap<>());

    private final NavigableMap<Long, Integer> heights;

    /** The time at each height: heights grow with times, so this is the same map turned round. */
    private final NavigableMap<Integer, Long> times = new TreeMap<>();

    /**
     * @param heights the height in the sheet of each time; later times must be lower down
     */
    public TimeAxis(final Map<Long, Integer> heights) {
        this.heights = new TreeMap<>(heights);
        this.heights.forEach((time, height) -> times.put(height, time));
    }

    /**
     * @param time a time in milliseconds
     * @return its height in the sheet, in pixels from the top
     */
    public int y(final long time) {
        Map.Entry<Long, Integer> before = heights.floorEntry(time);
        Map.Entry<Long, Integer> after = heights.ceilingEntry(time);
        if (before == null) {
            return after == null ? 0 : after.getValue();
        }
        if (after == null || after.getKey().equals(before.getKey())) {
            return before.getValue();
        }
        double fraction = (double) (time - before.getKey()) / (after.getKey() - before.getKey());
        return before.getValue() + (int) Math.round(fraction * (after.getValue() - before.getValue()));
    }

    /**
     * @param y a height in the sheet, in pixels from the top
     * @return the time at that height, in milliseconds; above the first time it is the first, below the last the last
     */
    public long time(final int y) {
        Map.Entry<Integer, Long> above = times.floorEntry(y);
        Map.Entry<Integer, Long> below = times.higherEntry(y);
        if (above == null) {
            return below == null ? 0 : below.getValue();
        }
        if (below == null) {
            return above.getValue();
        }
        double fraction = (double) (y - above.getKey()) / (below.getKey() - above.getKey());
        return above.getValue() + Math.round(fraction * (below.getValue() - above.getValue()));
    }

    /** @return whether the axis has no times, so every time is at the top */
    public boolean isEmpty() {
        return heights.isEmpty();
    }
}
